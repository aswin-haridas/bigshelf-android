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

    // Split collages into their panels on upload. On by default.
    var autosplit by mutableStateOf(prefs.getBoolean("autosplit", true))
        private set

    fun updateAutosplit(value: Boolean) {
        autosplit = value
        prefs.edit().putBoolean("autosplit", value).apply()
    }

    // Triplets groups every photo of one outfit and scene (the old Triptych). On by default;
    // off gives random sets of one person.
    var tripletOutfit by mutableStateOf(prefs.getBoolean("tripletOutfit", true))
        private set

    fun updateTripletOutfit(value: Boolean) {
        tripletOutfit = value
        prefs.edit().putBoolean("tripletOutfit", value).apply()
    }

    // Outfit sets: leave out sets smaller than this (2-9); 2 shows every set. Replaces the old
    // "Skip small sets" toggle, so a phone that had it on starts at 4.
    var triptychMinSize by mutableStateOf(
        prefs.getInt("triptychMinSize", if (prefs.getBoolean("triptychSkipSmall", false)) 4 else 2),
    )
        private set

    fun updateTriptychMinSize(value: Int) {
        triptychMinSize = value
        prefs.edit().putInt("triptychMinSize", value).apply()
    }

    // Outfit sets: show at most this many photos of a set, its best-framed ones. 0 = no limit.
    var triptychMaxSize by mutableStateOf(prefs.getInt("triptychMaxSize", 0))
        private set

    fun updateTriptychMaxSize(value: Int) {
        triptychMaxSize = value
        prefs.edit().putInt("triptychMaxSize", value).apply()
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
