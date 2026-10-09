package com.bigshelf.app.state

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Stand-in for react-query's `invalidateQueries`: screens collect this and refetch when a
 * key they show is invalidated (same key names as the web: "photos", "bin-photos", ...).
 */
object PhotoEvents {
    private val _invalidations = MutableSharedFlow<Set<String>>(extraBufferCapacity = 16)
    val invalidations: SharedFlow<Set<String>> = _invalidations

    /** Web `PHOTO_VIEW_KEYS`: every view that lists photos. */
    val PHOTO_VIEW_KEYS = setOf("photos", "bin-photos", "archive-photos", "organize", "people", "faces", "duplicates", "photo")

    fun invalidate(vararg keys: String) {
        _invalidations.tryEmit(keys.toSet())
    }

    fun invalidateAll() {
        _invalidations.tryEmit(PHOTO_VIEW_KEYS)
    }
}

/**
 * Last grid the user opened a photo from, so the viewer can swipe through the same list
 * (the web viewer walks the Home feed; this follows whichever grid you came from).
 */
object ViewerList {
    var photos: List<com.bigshelf.app.data.Photo> = emptyList()
}
