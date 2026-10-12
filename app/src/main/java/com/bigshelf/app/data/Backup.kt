package com.bigshelf.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.MainActivity
import com.bigshelf.app.state.PhotoEvents
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Automatic backup of every photo and video on the phone, oldest first, through the same
 * presigned upload as the Upload button. A periodic WorkManager job does the work on Wi-Fi;
 * the MediaStore id of the last item handled is the watermark, so each run only reads what's
 * new and a paused or interrupted run picks up where it stopped.
 */
class Backup(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("bigshelf-backup", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(appContext)
    private val notifier = NotificationManagerCompat.from(appContext)

    var enabled by mutableStateOf(prefs.getBoolean("enabled", false))
        private set
    var paused by mutableStateOf(prefs.getBoolean("paused", false))
        private set

    // MediaStore ids only grow, so everything above this hasn't been backed up yet
    var lastId: Long
        get() = prefs.getLong("lastId", 0)
        set(value) = prefs.edit().putLong("lastId", value).apply()

    init {
        notifier.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName("Backup").build(),
        )
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        paused = false
        prefs.edit().putBoolean("enabled", value).putBoolean("paused", false).apply()
        if (value) schedule() else work.cancelUniqueWork(WORK)
    }

    /** Pausing cancels the running job, aborting the file in flight; resuming starts it again. */
    fun updatePaused(value: Boolean) {
        paused = value
        prefs.edit().putBoolean("paused", value).apply()
        if (value) work.cancelUniqueWork(WORK) else schedule()
    }

    // A new periodic job runs as soon as its constraints allow, then every 15 minutes
    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun workInfo() = work.getWorkInfosForUniqueWorkFlow(WORK)

    fun hasPermission() = mediaPermissions.all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    data class Item(val id: Long, val uri: Uri)

    /** Photos and videos above the watermark, oldest first. Empty without media permission. */
    // ponytail: an item still IS_PENDING (a video being recorded) when a newer one is backed up
    // falls below the watermark and is skipped; switch to a set of uploaded ids if that bites.
    fun pending(): List<Item> {
        val items = mutableListOf<Item>()
        appContext.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(FileColumns._ID, FileColumns.MEDIA_TYPE),
            "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO}) AND ${FileColumns._ID} > ?",
            arrayOf(lastId.toString()),
            "${FileColumns._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val collection = if (c.getInt(1) == FileColumns.MEDIA_TYPE_VIDEO) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                items += Item(id, ContentUris.withAppendedId(collection, id))
            }
        }
        return items
    }

    /** SHA-256 hex of the file, the backend's content_hash: photos already uploaded get a 409. */
    fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        appContext.contentResolver.openInputStream(uri)!!.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------- Notifications ----------

    private fun builder(title: String) = NotificationCompat.Builder(appContext, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(title)
        .setContentIntent(
            PendingIntent.getActivity(
                appContext, 0,
                Intent(appContext, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    fun progressNotification(done: Int, total: Int): Notification = builder("Backing up ${done + 1} of $total")
        .setProgress(total, done, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(
            0, "Pause",
            PendingIntent.getBroadcast(appContext, 0, Intent(appContext, BackupPauseReceiver::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    // Dropped silently by the system when POST_NOTIFICATIONS was refused
    @SuppressLint("MissingPermission")
    fun notify(id: Int, notification: Notification) = notifier.notify(id, notification)

    fun notifyResult(title: String, text: String) =
        notify(RESULT_ID, builder(title).setContentText(text).setAutoCancel(true).build())

    fun cancelProgress() = notifier.cancel(PROGRESS_ID)

    companion object {
        const val WORK = "backup"
        private const val CHANNEL = "backup"
        const val PROGRESS_ID = 1
        private const val RESULT_ID = 2

        /** Runtime permissions backup asks for; notifications are optional. */
        val mediaPermissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val requestedPermissions = mediaPermissions +
            if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val backup = BigShelfApp.backup
        if (!backup.enabled || backup.paused) return Result.success()
        if (!backup.hasPermission()) {
            backup.notifyResult("Backup stopped", "Allow BigShelf to access your photos and videos to keep backing up.")
            return Result.success()
        }
        val items = backup.pending()
        if (items.isEmpty()) return Result.success()

        var uploaded = 0
        var rejected = 0
        try {
            items.forEachIndexed { i, item ->
                setProgress(workDataOf(DONE to i, TOTAL to items.size))
                show(backup.progressNotification(i, items.size))
                try {
                    BigShelfApp.api.upload(item.uri, backup.sha256(item.uri)) {}
                    uploaded++
                } catch (e: StorageUploadException) {
                    throw e
                } catch (e: ApiException) {
                    when (e.code) {
                        401 -> {
                            backup.notifyResult("Backup stopped", "Sign in to BigShelf to keep backing up.")
                            return Result.success()
                        }
                        409 -> Unit // already in the library
                        in 400..499 -> rejected++ // too large or unsupported: the API will never take it
                        else -> throw e
                    }
                } catch (_: FileNotFoundException) {
                    // deleted from the phone since the scan
                }
                backup.lastId = item.id
            }
        } catch (e: CancellationException) {
            throw e // paused, or Wi-Fi lost: the next run resumes from the watermark
        } catch (e: Exception) {
            backup.notifyResult("Backup failed", "${e.userMessage("Upload failed")}. It will try again shortly.")
            return Result.retry()
        } finally {
            backup.cancelProgress()
            if (uploaded > 0) PhotoEvents.invalidate("photos")
        }

        if (uploaded > 0 || rejected > 0) {
            val text = "$uploaded ${if (uploaded == 1) "item" else "items"} backed up" +
                if (rejected > 0) ", $rejected skipped (too large or unsupported)." else "."
            backup.notifyResult("Backup complete", text)
        }
        return Result.success()
    }

    // Foreground keeps a long backup alive; Android 12+ refuses it while the app is in the
    // background, and then the notification is posted plainly.
    private suspend fun show(notification: Notification) {
        try {
            setForeground(ForegroundInfo(Backup.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } catch (_: IllegalStateException) {
            BigShelfApp.backup.notify(Backup.PROGRESS_ID, notification)
        }
    }

    companion object {
        const val DONE = "done"
        const val TOTAL = "total"
    }
}

/** The Pause button on the backup notification. */
class BackupPauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BigShelfApp.backup.updatePaused(true)
    }
}
