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

    // Triplets groups every photo of one outfit and scene (the old Triptych). On by default;
    // off gives random sets of one person.
    var tripletOutfit by mutableStateOf(prefs.getBoolean("tripletOutfit", true))
        private set

    fun updateTripletOutfit(value: Boolean) {
        tripletOutfit = value
        prefs.edit().putBoolean("tripletOutfit", value).apply()
    }

    // Outfit sets: leave out sets of 3 or fewer photos. Off by default.
    var triptychSkipSmall by mutableStateOf(prefs.getBoolean("triptychSkipSmall", false))
        private set

    fun updateTriptychSkipSmall(value: Boolean) {
        triptychSkipSmall = value
        prefs.edit().putBoolean("triptychSkipSmall", value).apply()
    }

    // Outfit sets: never put two different tagged people in one set. Off by default.
    var triptychFaces by mutableStateOf(prefs.getBoolean("triptychFaces", false))
        private set

    fun updateTriptychFaces(value: Boolean) {
        triptychFaces = value
        prefs.edit().putBoolean("triptychFaces", value).apply()
    }

    // Stance sets also share one outfit. On by default.
    var stanceOutfit by mutableStateOf(prefs.getBoolean("stanceOutfit", true))
        private set

    fun updateStanceOutfit(value: Boolean) {
        stanceOutfit = value
        prefs.edit().putBoolean("stanceOutfit", value).apply()
    }

    // Let Triplets (at most one per outfit set) and Composer pick AI images too
    var includeAi by mutableStateOf(prefs.getBoolean("includeAi", false))
        private set

    fun updateIncludeAi(value: Boolean) {
        includeAi = value
        prefs.edit().putBoolean("includeAi", value).apply()
    }

    // Photos per random Triplets set (3-6); outfit sets are sized by the outfit
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

    // Random Triplets fill the set with the faces most like the first photo's. Off by default.
    var tripletMatchFaces by mutableStateOf(prefs.getBoolean("tripletMatchFaces", false))
        private set

    fun updateTripletMatchFaces(value: Boolean) {
        tripletMatchFaces = value
        prefs.edit().putBoolean("tripletMatchFaces", value).apply()
    }
}
