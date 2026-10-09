package com.bigshelf.app.ui

import android.net.Uri

/** Route strings - same paths as the web router where it makes sense. */
object Routes {
    const val HOME = "home"
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
    const val PHOTO = "photos/{photoId}"
    const val SOON = "soon/{label}"

    fun person(id: String) = "people/$id"
    fun face(id: String) = "faces/$id"
    fun photo(id: String) = "photos/$id"
    fun archiveFolder(id: String) = "archive/folder?id=${Uri.encode(id)}"
    fun soon(label: String) = "soon/${Uri.encode(label)}"

    /** Sidebar items, same order and labels as modules/home/components/sidebar.tsx. */
    val sidebar = listOf(
        "Home" to HOME,
        "Favourites" to FAVOURITES,
        "People" to PEOPLE,
        "Framer" to soon("Framer"),
        "Triptych" to soon("Triptych"),
        "Triplets" to soon("Triplets"),
        "Composer" to soon("Composer"),
        "Duplicates" to soon("Duplicates"),
        "Pipelines" to soon("Pipelines"),
        "Organize" to soon("Organize"),
        "Bin" to BIN,
        "Archive" to ARCHIVE,
        "Settings" to SETTINGS,
    )
}
