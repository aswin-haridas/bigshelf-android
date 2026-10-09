package com.bigshelf.app.state

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.BigShelfApp
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

    fun start(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val api = BigShelfApp.api
        scope.launch {
            val sizes = uris.associateWith { api.fileSize(it).coerceAtLeast(1) }
            val total = sizes.values.sum().toDouble()
            val sent = HashMap<Uri, Long>()
            var processed = 0
            var failed = 0
            var lastError: String? = null
            fun report() {
                val raw = (sent.values.sum() / total * 100).toInt().coerceAtMost(99)
                upload = Progress(uris.size, processed, raw)
            }
            report()
            uris.map { uri ->
                async {
                    lanes.withPermit {
                        try {
                            api.upload(uri) { bytes ->
                                scope.launch {
                                    sent[uri] = bytes
                                    report()
                                }
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
            if (failed == 0) toast.success("${uris.size} file${if (uris.size == 1) "" else "s"} uploaded")
            else toast.error(lastError ?: "$failed upload${if (failed == 1) "" else "s"} failed")
            PhotoEvents.invalidate("photos")
            delay(2500)
            upload = null
        }
    }
}
