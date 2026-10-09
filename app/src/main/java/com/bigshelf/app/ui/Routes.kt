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
    const val PIPELINES = "pipelines"
    const val RUN = "pipelines/runs/{runId}"
    const val DUPLICATES = "duplicates"
    const val ORGANIZE = "organize"
    const val PHOTO = "photos/{photoId}"
    const val SOON = "soon/{label}"

    fun person(id: String) = "people/$id"
    fun face(id: String) = "faces/$id"
    fun photo(id: String) = "photos/$id"
    fun archiveFolder(id: String) = "archive/folder?id=${Uri.encode(id)}"
    fun run(id: String) = "pipelines/runs/$id"
    fun soon(label: String) = "soon/${Uri.encode(label)}"

    /** Tools still web-only, listed in Library (same labels as the web sidebar). */
    val tools = listOf("Framer", "Triptych", "Triplets", "Composer")
}
