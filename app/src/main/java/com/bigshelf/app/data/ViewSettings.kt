package com.bigshelf.app.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Per-device view preferences - the Android twin of the web `useViewStore`
 * (persisted under "bigshelf-view-settings" there, SharedPreferences here).
 */
class ViewSettings(context: Context) {
    private val prefs = context.getSharedPreferences("bigshelf-view-settings", Context.MODE_PRIVATE)

    /** A compose-state property persisted under its own name; supports Boolean, Int and String defaults. */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> pref(default: T) = object : ReadWriteProperty<Any?, T> {
        private var state: MutableState<T>? = null
        private fun state(key: String) = state ?: mutableStateOf(
            when (default) {
                is Boolean -> prefs.getBoolean(key, default)
                is Int -> prefs.getInt(key, default)
                else -> prefs.getString(key, default as String) ?: default
            } as T,
        ).also { state = it }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state(property.name).value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state(property.name).value = value
            prefs.edit().apply {
                when (value) {
                    is Boolean -> putBoolean(property.name, value)
                    is Int -> putInt(property.name, value)
                    else -> putString(property.name, value as String)
                }
            }.apply()
        }
    }

    // Show AI (ChatGPT) images on the Home page
    var showAi by pref(true)
        private set

    fun updateShowAi(value: Boolean) { showAi = value }

    var showPersonRanks by pref(false)
        private set

    fun updateShowPersonRanks(value: Boolean) { showPersonRanks = value }

    // Move photos to Archive after downloading them. Off by default.
    var archiveOnDownload by pref(false)
        private set

    fun updateArchiveOnDownload(value: Boolean) { archiveOnDownload = value }

    // Split collages into their panels on upload. Off by default.
    var autosplit by pref(false)
        private set

    fun updateAutosplit(value: Boolean) { autosplit = value }

    // Let Composer pick AI images too
    var includeAi by pref(false)
        private set

    fun updateIncludeAi(value: Boolean) { includeAi = value }
}
