package com.bigshelf.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.bigshelf.app.BigShelfApp

/**
 * Saves photos to Downloads/BigShelf with the system DownloadManager. The web zips
 * multi-photo downloads in the browser; on Android each file is its own download.
 */
object Downloads {
    suspend fun save(context: Context, photos: Collection<Photo>) {
        val api = BigShelfApp.api
        val manager = context.getSystemService(DownloadManager::class.java)
        for (listed in photos) {
            // Grid items may omit the original's URL; the detail response always has it
            val photo = if (listed.url != null) listed else api.photo(listed.id)
            val url = api.mediaUrl(photo.url) ?: continue
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(photo.filename)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "BigShelf/${photo.filename}")
            // Media served by the API itself (not presigned storage) needs the session
            if (url.startsWith(api.baseUrl)) api.accessToken?.let { request.addRequestHeader("Authorization", "Bearer $it") }
            manager.enqueue(request)
        }
    }
}
