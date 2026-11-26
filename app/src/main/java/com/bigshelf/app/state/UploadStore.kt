package com.bigshelf.app.state

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.UploadQueue
import com.bigshelf.app.ui.components.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * In-app view of uploads: progress modal, Home previews and toasts. Files are handed to the
 * durable [UploadQueue], whose worker reports back here while the app process is alive.
 */
object UploadStore {
    data class Progress(val fileCount: Int, val checked: Int, val progress: Int)
    data class Preview(val id: String, val uri: Uri, val photoId: String? = null)
    enum class Outcome { Added, Duplicate, Failed }

    var upload by mutableStateOf<Progress?>(null)
        private set
    var previews by mutableStateOf<List<Preview>>(emptyList())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hideProgress: Job? = null
    private var fileCount = 0
    private var checked = 0
    private var totalBytes = 0L
    private var sentBytes = 0L
    private val sent = HashMap<String, Long>()
    private val sizes = HashMap<String, Long>()
    private var added = 0
    private var duplicates = 0
    private var failed = 0
    private var lastError: String? = null

    /** With [albumId], each photo also lands in that album (yours or one shared with you). */
    fun start(uris: List<Uri>, albumId: String? = null) {
        val context = BigShelfApp.instance
        if (uris.isEmpty()) {
            UploadQueue.start(context) // signed in again: finish anything left from before
            return
        }
        val batch = uris.map { Preview(UUID.randomUUID().toString(), it) }
        previews = batch + previews
        hideProgress?.cancel()
        if (checked == fileCount) resetProgress()
        fileCount += batch.size
        report()
        scope.launch {
            for (preview in batch) {
                try {
                    val size = withContext(Dispatchers.IO) { UploadQueue.add(context, preview.id, preview.uri, albumId) }
                    sizes[preview.id] = size
                    totalBytes += size
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    onFinished(preview.id, Outcome.Failed, null, "Couldn't read a shared file")
                    continue
                }
                report()
                UploadQueue.start(context) // first files start sending while the rest copy
            }
        }
    }

    /** Whether someone is likely looking at the in-app progress (else the worker notifies). */
    fun isWatching() = android.app.ActivityManager.RunningAppProcessInfo()
        .also(android.app.ActivityManager::getMyMemoryState)
        .importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    internal fun onProgress(id: String, bytes: Long) = scope.launch {
        val size = sizes[id] ?: return@launch
        val next = bytes.coerceIn(sent[id] ?: 0L, size)
        sentBytes += next - (sent[id] ?: 0L)
        sent[id] = next
        report()
    }

    internal fun onFinished(id: String, outcome: Outcome, photoId: String?, error: String?) = scope.launch {
        if (previews.none { it.id == id }) return@launch // from an earlier app run
        sizes[id]?.let { sentBytes += it - (sent[id] ?: 0L); sent[id] = it }
        finishPreview(id, photoId)
        when (outcome) {
            Outcome.Added -> added++
            Outcome.Duplicate -> duplicates++
            Outcome.Failed -> { failed++; lastError = error }
        }
        checked++
        report()
        if (checked == fileCount) {
            when {
                failed > 0 -> toast.error(lastError ?: "$failed upload(s) failed")
                added > 0 && duplicates > 0 -> toast.success("Added $added file(s), skipped $duplicates duplicate(s)")
                duplicates > 0 -> toast.info("$duplicates file(s) already in your library")
                else -> toast.success("Added $added file(s)")
            }
        }
        // Successful previews usually hand off as soon as Home shows the stored photo.
        delay(60_000)
        previews = previews.filterNot { it.id == id }
    }

    fun removeListedPreviews(photoIds: Set<String>) {
        previews = previews.filterNot { it.photoId in photoIds }
    }

    fun clear() {
        scope.coroutineContext.cancelChildren()
        UploadQueue.clear(BigShelfApp.instance)
        previews = emptyList()
        resetProgress()
        upload = null
    }

    private fun finishPreview(id: String, photoId: String? = null) {
        previews = if (photoId == null) previews.filterNot { it.id == id }
        else previews.map { if (it.id == id) it.copy(photoId = photoId) else it }
    }

    private fun report() {
        if (fileCount == 0) return
        if (checked == fileCount) {
            upload = Progress(fileCount, checked, 100)
            hideProgress = scope.launch {
                delay(2_500)
                upload = null
            }
        } else {
            val percent = (100.0 * sentBytes / totalBytes.coerceAtLeast(1)).toInt().coerceAtMost(99)
            upload = Progress(fileCount, checked, percent)
        }
    }

    private fun resetProgress() {
        sent.clear()
        sizes.clear()
        added = 0
        duplicates = 0
        failed = 0
        lastError = null
        fileCount = 0
        checked = 0
        totalBytes = 0
        sentBytes = 0
    }
}
