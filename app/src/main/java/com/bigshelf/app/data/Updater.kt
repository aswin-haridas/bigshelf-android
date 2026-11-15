package com.bigshelf.app.data

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bigshelf.app.BigShelfApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Self-update from the GitHub releases: a job on Wi-Fi downloads a newer release's APK and
 * installs it through PackageInstaller without a prompt. Android allows that only on 12+ (an
 * app updating itself) and once "Install unknown apps" is on for BigShelf, a one-time switch
 * no app can flip for itself; until then the job does nothing. Installing restarts the app,
 * so the periodic job waits while BigShelf is on screen; the check at launch installs right away.
 */
object Updater {
    private val installMutex = kotlinx.coroutines.sync.Mutex()
    fun lastResult(context: Context): String? = context.getSharedPreferences("bigshelf-updater", Context.MODE_PRIVATE).getString("result", null)
    private const val WORK = "self-update"
    const val NOW = "now"
    private const val LATEST = "https://api.github.com/repos/aswin-haridas/bigshelf-android/releases/latest"

    fun schedule(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        val launch = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(workDataOf(NOW to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).apply {
            enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
            enqueueUniqueWork("$WORK-launch", ExistingWorkPolicy.KEEP, launch)
        }
    }

    fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun canInstall(context: Context) = supported() && context.packageManager.canRequestPackageInstalls()

    /** The system's "Install unknown apps" page for BigShelf, where the one-time switch lives. */
    fun allowIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    // "v1.18.0" -> [1, 18, 0]
    private fun parts(version: String) = version.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }

    fun isNewer(tag: String, current: String): Boolean {
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // Visible or foreground: installing now would kill the app under the user
    private fun onScreen() = ActivityManager.RunningAppProcessInfo()
        .also(ActivityManager::getMyMemoryState)
        .importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun run(context: Context, now: Boolean = false) = installMutex.withLock { withContext(Dispatchers.IO) {
        if (!canInstall(context) || (!now && onScreen())) return@withContext
        val client = BigShelfApp.api.bareClient
        val release = client.newCall(Request.Builder().url(LATEST).build()).execute().use {
            check(it.isSuccessful) { "releases ${it.code}" }
            Json.parseToJsonElement(it.body!!.string()).jsonObject
        }
        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@withContext
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return@withContext
        if (!isNewer(tag, current)) return@withContext
        val apkUrl = release["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.content
            ?: return@withContext

        val installer = context.packageManager.packageInstaller
        val prefs = context.getSharedPreferences("bigshelf-updater", Context.MODE_PRIVATE)
        val existing = prefs.getInt("session", -1)
        if (installer.getSessionInfo(existing) != null) {
            // Preserve an install waiting for confirmation rather than abandoning it every launch.
            if (System.currentTimeMillis() - prefs.getLong("started", 0) < TimeUnit.DAYS.toMillis(1)) return@withContext
            installer.abandonSession(existing)
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        prefs.edit().putInt("session", id).putLong("started", System.currentTimeMillis()).apply()
        installer.openSession(id).use { session ->
            try {
                client.newCall(Request.Builder().url(apkUrl).build()).execute().use { res ->
                    check(res.isSuccessful) { "apk ${res.code}" }
                    session.openWrite("base.apk", 0, res.body!!.contentLength()).use { out ->
                        res.body!!.byteStream().copyTo(out)
                        session.fsync(out)
                    }
                }
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
            // Receive installer outcomes and expose required user confirmation.
            val status = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, UpdateStatusReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(status.intentSender)
        }
    } }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Failures (offline, GitHub rate limit) just wait for the next run
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Updater.run(applicationContext, inputData.getBoolean(Updater.NOW, false))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { Log.w("Updater", "Update check failed", e) }
        }
        return Result.success()
    }
}


/** Explicit PendingIntent target: installation failures and confirmation requests are observable. */
class UpdateStatusReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "Update installed successfully"
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Tap the update notification to finish installation"
            else -> "Update could not be installed; it will be retried"
        }
        val prefs = context.getSharedPreferences("bigshelf-updater", Context.MODE_PRIVATE)
        prefs.edit().putString("result", message).apply()
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) prefs.edit().remove("session").apply()
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            val manager = androidx.core.app.NotificationManagerCompat.from(context)
            manager.createNotificationChannel(androidx.core.app.NotificationChannelCompat.Builder(
                "updates", androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Updates").build())
            val action = PendingIntent.getActivity(context, 0, confirmation,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.notify(3, androidx.core.app.NotificationCompat.Builder(context, "updates")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Finish BigShelf update")
                    .setContentText(message).setContentIntent(action).setAutoCancel(true).build())
            }
        }
    }
}
