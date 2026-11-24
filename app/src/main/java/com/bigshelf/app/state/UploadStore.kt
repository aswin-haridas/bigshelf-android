package com.bigshelf.app.state

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.ApiException
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.ui.components.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

/** App-wide upload queue. Transfer slots are released as soon as the server queues an import. */
object UploadStore {
    data class Progress(val fileCount: Int, val checked: Int, val progress: Int)
    data class Preview(val id: String, val uri: Uri, val photoId: String? = null)

    var upload by mutableStateOf<Progress?>(null)
        private set
    var previews by mutableStateOf<List<Preview>>(emptyList())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lanes = Semaphore(4)
    private val importChecks = Semaphore(4)
    private var hideProgress: Job? = null
    private var fileCount = 0
    private var checked = 0
    private var totalBytes = 0L
    private var sentBytes = 0L

    /** With [albumId], each photo also lands in that album (yours or one shared with you). */
    fun start(uris: List<Uri>, albumId: String? = null) {
        if (uris.isEmpty()) return
        val batch = uris.map { Preview(UUID.randomUUID().toString(), it) }
        previews = batch + previews
        hideProgress?.cancel()
        if (checked == fileCount) resetProgress()
        fileCount += batch.size
        report()
        scope.launch {
            val api = BigShelfApp.api
            val sizes = withContext(Dispatchers.IO) {
                uris.map { uri -> runCatching { api.fileSize(uri).coerceAtLeast(1) }.getOrDefault(1L) }
            }
            totalBytes += sizes.sum()
            val sentHashes = HashSet<String>()
            var added = 0
            var duplicates = 0
            var failed = 0
            var lastError: String? = null
            batch.zip(sizes).map { (preview, size) ->
                async {
                    try {
                        val jobId = lanes.withPermit {
                            var sent = 0L
                            try {
                                val hash = withContext(Dispatchers.IO) {
                                    runCatching { BigShelfApp.backup.sha256(preview.uri) }.getOrNull()
                                }
                                if (hash != null && !sentHashes.add(hash)) throw ApiException(409, "Duplicate")
                                api.queueUpload(preview.uri, hash, albumId) { bytes ->
                                    launch {
                                        val next = bytes.coerceIn(sent, size)
                                        if (next == sent) return@launch
                                        sentBytes += next - sent
                                        sent = next
                                        report()
                                    }
                                }
                            } finally {
                                if (currentCoroutineContext().isActive) {
                                    sentBytes += size - sent
                                    sent = size
                                    checked++
                                    report()
                                }
                            }
                        }
                        val photoId = importChecks.withPermit { api.awaitUpload(jobId) }
                        finishPreview(preview.id, photoId)
                        added++
                        PhotoEvents.invalidate("photos", "albums")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        finishPreview(preview.id)
                        if (e is ApiException && e.code == 409) duplicates++ else {
                            failed++
                            lastError = e.userMessage("Upload failed")
                        }
                    }
                }
            }.awaitAll()
            when {
                failed > 0 -> toast.error(lastError ?: "$failed upload(s) failed")
                added > 0 && duplicates > 0 -> toast.success("Added $added file(s), skipped $duplicates duplicate(s)")
                duplicates > 0 -> toast.info("$duplicates file(s) already in your library")
                else -> toast.success("Added $added file(s)")
            }
            // Successful previews usually hand off as soon as Home shows the stored photo.
            // shortcut: clear older previews too when a large batch has pushed them beyond page one.
            delay(60_000)
            val ids = batch.mapTo(HashSet()) { it.id }
            previews = previews.filterNot { it.id in ids }
        }
    }

    fun removeListedPreviews(photoIds: Set<String>) {
        previews = previews.filterNot { it.photoId in photoIds }
    }

    fun clear() {
        scope.coroutineContext.cancelChildren()
        previews = emptyList()
        resetProgress()
        upload = null
    }

    private fun finishPreview(id: String, photoId: String? = null) {
        previews = if (photoId == null) previews.filterNot { it.id == id }
        else previews.map { if (it.id == id) it.copy(photoId = photoId) else it }
    }

    private fun report() {
        if (fileCount == 0) return
        if (checked == fileCount) {
            upload = Progress(fileCount, checked, 100)
            hideProgress = scope.launch {
                delay(2_500)
                upload = null
            }
        } else {
            val percent = (100.0 * sentBytes / totalBytes.coerceAtLeast(1)).toInt().coerceAtMost(99)
            upload = Progress(fileCount, checked, percent)
        }
    }

    private fun resetProgress() {
        fileCount = 0
        checked = 0
        totalBytes = 0
        sentBytes = 0
    }
}
