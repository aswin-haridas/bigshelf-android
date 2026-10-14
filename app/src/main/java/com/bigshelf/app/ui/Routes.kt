package com.bigshelf.app.ui

import android.net.Uri

/** Route strings - same paths as the web router where it makes sense. */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val FAVOURITES = "favourites"
    const val PEOPLE = "people"
    const val FACES = "faces"
    const val UNTAGGED = "people/untagged"
    const val PERSON = "people/{personId}"
    const val FACE = "faces/{faceId}"
    const val BIN = "bin"
    const val ARCHIVE = "archive"
    const val ARCHIVE_FOLDER = "archive/folder?id={folder}"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val SHARING = "sharing"
    const val PIPELINES = "pipelines"
    const val RUN = "pipelines/runs/{runId}"
    const val DUPLICATES = "duplicates"
    const val ORGANIZE = "organize"
    const val PHOTO = "photos/{photoId}"
    const val TRIPTYCH = "triptych"
    const val TRIPLETS = "triplets"
    const val COMPOSER = "composer"
    // ?photo=<id>: open a library video (from the photo viewer)
    const val FRAMER = "framer?photo={photo}"

    // Search tab: albums, places and the map, events, colours, bursts, unorganized
    const val SEARCH = "search"
    const val ALBUMS = "albums"
    const val ALBUM = "albums/{albumId}"
    const val PLACES = "places"
    const val MAP = "map?lat={lat}&lon={lon}"
    const val EVENTS = "events"
    const val COLOR = "colors/{color}"
    const val BURSTS = "bursts"
    // Photos matching a smart filter (a place, a map cluster, an event, unorganized)
    const val FILTERED = "filtered?title={title}&from={from}&to={to}&bbox={bbox}&unorganized={unorganized}"

    fun person(id: String) = "people/$id"
    fun face(id: String) = "faces/$id"
    fun photo(id: String) = "photos/$id"
    fun archiveFolder(id: String) = "archive/folder?id=${Uri.encode(id)}"
    fun run(id: String) = "pipelines/runs/$id"
    fun framer(photoId: String? = null) = if (photoId == null) "framer" else "framer?photo=$photoId"
    fun album(id: String) = "albums/$id"
    fun color(name: String) = "colors/$name"
    fun map(lat: Double? = null, lon: Double? = null) = if (lat == null || lon == null) "map" else "map?lat=$lat&lon=$lon"
    fun filtered(title: String, filter: com.bigshelf.app.data.PhotoFilter) = buildString {
        append("filtered?title=${Uri.encode(title)}")
        filter.takenFrom?.let { append("&from=${Uri.encode(it)}") }
        filter.takenTo?.let { append("&to=${Uri.encode(it)}") }
        filter.bbox?.let { append("&bbox=${it.joinToString(",")}") }
        if (filter.unorganized) append("&unorganized=true")
    }
}
