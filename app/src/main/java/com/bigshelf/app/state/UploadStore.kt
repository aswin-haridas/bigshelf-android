package com.bigshelf.app.state

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.ApiException
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.ui.components.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * App-wide upload queue (web `useUploadStore` + `startUpload`). Drives the
 * UploadProgressModal; runs up to 4 files in parallel like the web.
 */
object UploadStore {
    data class Progress(val fileCount: Int, val processed: Int, val progress: Int)

    var upload by mutableStateOf<Progress?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lanes = Semaphore(4)

    /** With [albumId], each photo also lands in that album (yours or one shared with you). */
    fun start(uris: List<Uri>, albumId: String? = null) {
        if (uris.isEmpty()) return
        val api = BigShelfApp.api
        scope.launch {
            val sizes = uris.associateWith { api.fileSize(it).coerceAtLeast(1) }
            val total = sizes.values.sum().toDouble()
            val sent = HashMap<Uri, Long>()
            var processed = 0
            var failed = 0
            var duplicates = 0
            var lastError: String? = null
            // Hashes sent in this batch, so a repeat within one pick is skipped too
            val sentHashes = HashSet<String>()
            fun report() {
                val raw = (sent.values.sum() / total * 100).toInt().coerceAtMost(99)
                upload = Progress(uris.size, processed, raw)
            }
            report()
            uris.map { uri ->
                async {
                    lanes.withPermit {
                        try {
                            // SHA-256 lets the API refuse an exact copy already in the library (409)
                            // before any bytes are sent (web `uploads/check`)
                            val hash = withContext(Dispatchers.IO) { runCatching { BigShelfApp.backup.sha256(uri) }.getOrNull() }
                            if (hash != null && !synchronized(sentHashes) { sentHashes.add(hash) }) throw ApiException(409, "Duplicate")
                            api.upload(uri, contentHash = hash, albumId = albumId) { bytes ->
                                scope.launch {
                                    sent[uri] = bytes
                                    report()
                                }
                            }
                        } catch (e: ApiException) {
                            if (e.code == 409) duplicates++
                            else {
                                failed++
                                lastError = e.userMessage("Upload failed")
                            }
                        } catch (e: Exception) {
                            failed++
                            lastError = e.userMessage("Upload failed")
                        }
                        sent[uri] = sizes.getValue(uri)
                        processed++
                        report()
                    }
                }
            }.awaitAll()
            upload = Progress(uris.size, processed, 100)
            val uploaded = uris.size - failed - duplicates
            when {
                failed > 0 -> toast.error(lastError ?: "$failed upload${if (failed == 1) "" else "s"} failed")
                uploaded > 0 && duplicates > 0 -> toast.success("Uploaded $uploaded file${if (uploaded == 1) "" else "s"}, skipped $duplicates duplicate${if (duplicates == 1) "" else "s"}")
                duplicates > 0 -> toast.info("$duplicates file${if (duplicates == 1) " was" else "s were"} already in your library")
                else -> toast.success("$uploaded file${if (uploaded == 1) "" else "s"} uploaded")
            }
            PhotoEvents.invalidate("photos")
            delay(2500)
            upload = null
        }
    }
}
