package com.bigshelf.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.data.Photo

/**
 * Port of stores/selectionStore.ts: the app-wide multi-select behind the floating bulk bar.
 * Holds the Photo for every selected id so bulk actions work wherever the photos came from.
 */
object SelectionStore {
    val photos = mutableStateMapOf<String, Photo>()
    var lastId by mutableStateOf<String?>(null)
        private set

    // Photos the on-screen grid is showing, so the bar can "Select all" them
    var visible by mutableStateOf<List<Photo>>(emptyList())

    val count get() = photos.size
    val ids get() = photos.keys.toSet()

    fun isSelected(id: String) = photos.containsKey(id)

    fun toggle(photo: Photo) {
        if (photos.remove(photo.id) == null) photos[photo.id] = photo
        lastId = photo.id
    }

    fun selectMany(batch: List<Photo>) {
        if (batch.isEmpty()) return
        batch.forEach { photos[it.id] = it }
        lastId = batch.last().id
    }

    fun clear() {
        photos.clear()
        lastId = null
    }
}
