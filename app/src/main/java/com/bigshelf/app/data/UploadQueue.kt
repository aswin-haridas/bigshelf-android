package com.bigshelf.app.data

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.UploadStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Durable upload queue for picked and shared files. Each file is copied into app storage first
 * (share/picker read grants end with the activity or process), then a foreground WorkManager job
 * drains the folder, so uploads carry on with the app closed or the screen off and resume after
 * a lost connection or a killed process. Item = `upload-queue/<id>/<filename>` (+ `album`).
 */
object UploadQueue {
    private const val WORK = "uploads"

    private fun dir(context: Context) = File(context.noBackupFilesDir, "upload-queue")

    /** Copies [uri] (location intact) into the queue under [id]; returns its size in bytes. */
    fun add(context: Context, id: String, uri: Uri, albumId: String?): Long {
        val resolver = context.contentResolver
        var name = "upload"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        name = name.replace('/', '_')
        // The backend checks the extension; shared streams sometimes have none
        if ('.' !in name) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri))?.let { name += ".$it" }
        }
        val item = File(dir(context), id).apply { mkdirs() }
        if (albumId != null) File(item, "album").writeText(albumId)
        val part = File(item, "$name.part")
        BigShelfApp.api.openOriginal(uri).use { input -> part.outputStream().use { input.copyTo(it) } }
        val file = File(item, name)
        if (!part.renameTo(file)) throw IOException("Couldn't queue $name")
        return file.length()
    }

    /** Ready items (copy finished) as (id, file, albumId). */
    fun items(context: Context) = dir(context).listFiles().orEmpty().mapNotNull { item ->
        val file = item.listFiles().orEmpty().firstOrNull { it.name != "album" && !it.name.endsWith(".part") }
            ?: return@mapNotNull null
        Triple(item.name, file, File(item, "album").takeIf { it.exists() }?.readText())
    }

    fun remove(context: Context, id: String) = File(dir(context), id).deleteRecursively()

    fun start(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // Appending means files added while a run is finishing still get a run of their own
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Signed out: drop everything so it can't land in the next account. */
    fun clear(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        dir(context).deleteRecursively()
    }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val backup = BigShelfApp.backup

    override suspend fun getForegroundInfo() = foreground(0, 1)

    override suspend fun doWork(): Result {
        val items = withContext(Dispatchers.IO) { UploadQueue.items(applicationContext) }
        if (items.isEmpty()) return Result.success()
        val api = BigShelfApp.api
        val lanes = Semaphore(4)
        val lock = Mutex()
        val hashes = HashSet<String>()
        var done = 0
        var added = 0
        var skipped = 0
        var retry = false
        var signedOut = false
        show(0, items.size)
        try {
            coroutineScope {
                items.map { (id, file, albumId) ->
                    async {
                        val uri = Uri.fromFile(file)
                        var photoId: String? = null
                        var outcome = UploadStore.Outcome.Failed
                        var message: String? = null
                        var keep = false
                        try {
                            val jobId = lanes.withPermit {
                                val hash = withContext(Dispatchers.IO) { backup.sha256(uri) }
                                if (!lock.withLock { hashes.add(hash) }) throw ApiException(409, "Duplicate")
                                api.queueUpload(uri, hash, albumId) { UploadStore.onProgress(id, it) }
                            }
                            // Bytes are on the server now; a re-run would only get a 409
                            withContext(Dispatchers.IO) { UploadQueue.remove(applicationContext, id) }
                            photoId = api.awaitUpload(jobId)
                            outcome = UploadStore.Outcome.Added
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ApiException) {
                            when {
                                e.code == 409 -> outcome = UploadStore.Outcome.Duplicate
                                e is RejectedUploadException -> message = e.userMessage("Unsupported file")
                                e.code == 401 -> { keep = true; signedOut = true }
                                else -> { keep = true; retry = true }
                            }
                        } catch (_: FileNotFoundException) {
                            message = "File disappeared before upload"
                        } catch (e: IOException) {
                            keep = true; retry = true // offline
                        }
                        if (!keep) withContext(Dispatchers.IO) { UploadQueue.remove(applicationContext, id) }
                        lock.withLock {
                            done++
                            if (outcome == UploadStore.Outcome.Added) added++
                            if (outcome == UploadStore.Outcome.Duplicate || (!keep && message != null)) skipped++
                        }
                        // Kept items stay "in progress" in the app until a later run settles them
                        if (!keep) UploadStore.onFinished(id, outcome, photoId, message)
                        show(done, items.size)
                    }
                }.awaitAll()
            }
        } finally {
            backup.cancelProgress(PROGRESS_ID)
            if (added > 0) PhotoEvents.invalidate("photos", "albums")
        }
        when {
            signedOut -> backup.notifyResult("Uploads waiting", "Sign in to BigShelf to finish uploading.", RESULT_ID)
            retry -> return Result.retry() // network trouble: WorkManager backs off and tries again
            !UploadStore.isWatching() -> backup.notifyResult(
                "Upload complete",
                "$added ${if (added == 1) "file" else "files"} added" + if (skipped > 0) ", $skipped skipped." else ".",
                RESULT_ID,
            )
        }
        return Result.success()
    }

    private fun foreground(done: Int, total: Int) = ForegroundInfo(
        PROGRESS_ID,
        backup.builder("Uploading ${minOf(done + 1, total)} of $total")
            .setProgress(total, done, false).setOngoing(true).setOnlyAlertOnce(true).build(),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    // Foreground keeps the job (and network) alive with the screen off; refused from the background
    // on Android 12+, where the plain notification still shows progress.
    private suspend fun show(done: Int, total: Int) {
        val info = foreground(done, total)
        try {
            setForeground(info)
        } catch (_: IllegalStateException) {
            backup.notify(PROGRESS_ID, info.notification)
        }
    }

    companion object {
        private const val PROGRESS_ID = 3
        private const val RESULT_ID = 4
    }
}
