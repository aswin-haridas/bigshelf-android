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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bigshelf.app.BigShelfApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * so it waits while BigShelf is on screen.
 */
object Updater {
    private const val WORK = "self-update"
    private const val LATEST = "https://api.github.com/repos/aswin-haridas/bigshelf-android/releases/latest"

    fun schedule(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
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
    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        if (!canInstall(context) || onScreen()) return@withContext
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
        // A session left by an earlier run (failed download, killed process) would pile up
        installer.mySessions.forEach { installer.abandonSession(it.sessionId) }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
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
            // The installer needs somewhere to report; nothing listens. Android checks the APK is
            // signed with the same key as this app, so only our own releases can install. If it
            // ever wants a prompt instead, that is dropped (never shown) and the next run retries.
            val status = PendingIntent.getBroadcast(
                context,
                0,
                Intent("com.bigshelf.app.UPDATE_STATUS").setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(status.intentSender)
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Failures (offline, GitHub rate limit) just wait for the next run
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Updater.run(applicationContext) }.onFailure { Log.w("Updater", "Update check failed", it) }
        }
        return Result.success()
    }
}
