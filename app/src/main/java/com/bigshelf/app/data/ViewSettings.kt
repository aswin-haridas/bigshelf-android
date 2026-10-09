package com.bigshelf.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Per-device view preferences - the Android twin of the web `useViewStore`
 * (persisted under "bigshelf-view-settings" there, SharedPreferences here).
 */
class ViewSettings(context: Context) {
    private val prefs = context.getSharedPreferences("bigshelf-view-settings", Context.MODE_PRIVATE)

    // Show AI (ChatGPT) images on the Home page
    var showAi by mutableStateOf(prefs.getBoolean("showAi", true))
        private set

    fun updateShowAi(value: Boolean) {
        showAi = value
        prefs.edit().putBoolean("showAi", value).apply()
    }

    // Move photos to Archive after downloading them. Off by default.
    var archiveOnDownload by mutableStateOf(prefs.getBoolean("archiveOnDownload", false))
        private set

    fun updateArchiveOnDownload(value: Boolean) {
        archiveOnDownload = value
        prefs.edit().putBoolean("archiveOnDownload", value).apply()
    }
}
