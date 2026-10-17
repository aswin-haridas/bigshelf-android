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

    // Let Triptych use AI images (at most one per set). On by default.
    var triptychAi by mutableStateOf(prefs.getBoolean("triptychAi", true))
        private set

    fun updateTriptychAi(value: Boolean) {
        triptychAi = value
        prefs.edit().putBoolean("triptychAi", value).apply()
    }

    // Leave out Triptych sets of 3 or fewer photos. Off by default.
    var triptychSkipSmall by mutableStateOf(prefs.getBoolean("triptychSkipSmall", false))
        private set

    fun updateTriptychSkipSmall(value: Boolean) {
        triptychSkipSmall = value
        prefs.edit().putBoolean("triptychSkipSmall", value).apply()
    }

    // Never put two different tagged people in one Triptych set. Off by default.
    var triptychFaces by mutableStateOf(prefs.getBoolean("triptychFaces", false))
        private set

    fun updateTriptychFaces(value: Boolean) {
        triptychFaces = value
        prefs.edit().putBoolean("triptychFaces", value).apply()
    }

    // Let Triplets (and Composer) pick AI images too
    var includeAi by mutableStateOf(prefs.getBoolean("includeAi", false))
        private set

    fun updateIncludeAi(value: Boolean) {
        includeAi = value
        prefs.edit().putBoolean("includeAi", value).apply()
    }

    // Photos per Triplets set (3-6)
    var tripletCount by mutableStateOf(prefs.getInt("tripletCount", 3))
        private set

    fun updateTripletCount(value: Int) {
        tripletCount = value
        prefs.edit().putInt("tripletCount", value).apply()
    }

    // Triplets only uses photos with no other tagged person in them
    var tripletSolo by mutableStateOf(prefs.getBoolean("tripletSolo", false))
        private set

    fun updateTripletSolo(value: Boolean) {
        tripletSolo = value
        prefs.edit().putBoolean("tripletSolo", value).apply()
    }
}
