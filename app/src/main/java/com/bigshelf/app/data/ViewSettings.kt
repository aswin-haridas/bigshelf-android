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

    // Split collages into their panels on upload. Off by default.
    var autosplit by mutableStateOf(prefs.getBoolean("autosplit", false))
        private set

    fun updateAutosplit(value: Boolean) {
        autosplit = value
        prefs.edit().putBoolean("autosplit", value).apply()
    }

    // Triplets fill the set with the clothes (garment, cut, pattern) most like the first photo's. Off by default.
    var tripletOutfit by mutableStateOf(prefs.getBoolean("tripletOutfit", false))
        private set

    fun updateTripletOutfit(value: Boolean) {
        tripletOutfit = value
        prefs.edit().putBoolean("tripletOutfit", value).apply()
    }

    // Let Triplets and Composer pick AI images too
    var includeAi by mutableStateOf(prefs.getBoolean("includeAi", false))
        private set

    fun updateIncludeAi(value: Boolean) {
        includeAi = value
        prefs.edit().putBoolean("includeAi", value).apply()
    }

    // Photos per random Triplets set (2-9); outfit sets are sized by the outfit
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

    // Random Triplets fill the set with the body poses most like the first photo's. Off by default.
    var tripletMatchPose by mutableStateOf(prefs.getBoolean("tripletMatchPose", false))
        private set

    fun updateTripletMatchPose(value: Boolean) {
        tripletMatchPose = value
        prefs.edit().putBoolean("tripletMatchPose", value).apply()
    }

    // Random Triplets fill the set with the clothes colours most like the first photo's. Off by default.
    var tripletMatchColors by mutableStateOf(prefs.getBoolean("tripletMatchColors", false))
        private set

    fun updateTripletMatchColors(value: Boolean) {
        tripletMatchColors = value
        prefs.edit().putBoolean("tripletMatchColors", value).apply()
    }

    // Random Triplets fill the set with the settings most like the first photo's (overrides variety). Off by default.
    var tripletMatchScene by mutableStateOf(prefs.getBoolean("tripletMatchScene", false))
        private set

    fun updateTripletMatchScene(value: Boolean) {
        tripletMatchScene = value
        prefs.edit().putBoolean("tripletMatchScene", value).apply()
    }

    // Random Triplets spread each set across scenes. On by default; off allows one scene's photos together.
    var tripletVariety by mutableStateOf(prefs.getBoolean("tripletVariety", true))
        private set

    fun updateTripletVariety(value: Boolean) {
        tripletVariety = value
        prefs.edit().putBoolean("tripletVariety", value).apply()
    }

    // Random Triplets lead with a clear face (big, sharp, typical). On by default; off orders the whole set.
    var tripletClearLead by mutableStateOf(prefs.getBoolean("tripletClearLead", true))
        private set

    fun updateTripletClearLead(value: Boolean) {
        tripletClearLead = value
        prefs.edit().putBoolean("tripletClearLead", value).apply()
    }

    // Order of a set's photos (newest / oldest / best / random), random and outfit sets alike
    var tripletOrder by mutableStateOf(prefs.getString("tripletOrder", "newest") ?: "newest")
        private set

    fun updateTripletOrder(value: String) {
        tripletOrder = value
        prefs.edit().putString("tripletOrder", value).apply()
    }
}
