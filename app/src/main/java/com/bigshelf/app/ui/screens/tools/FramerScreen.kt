package com.bigshelf.app.ui.screens.tools

import android.content.ContentValues
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Frame
import com.bigshelf.app.data.FramerResult
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.BsTabs
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.TabItem
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.shell.formatBytes
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// Frames to keep, the web slider's stops (min 6, max 30, step 6)
private val FRAME_COUNTS = listOf(6, 12, 18, 24, 30).map { TabItem(it, "$it") }

/**
 * The video Framer works on: a picked file, or a library video copied to the cache. [playUri]
 * is what the system player gets: a cache file:// URI can't be shared with another app.
 */
private data class Video(val uri: Uri, val name: String, val size: Long, val mimeType: String?, val playUri: Uri = uri)

/** A frame's JPEG bytes, out of its `data:image/jpeg;base64,` URL. */
private fun Frame.bytes(): ByteArray = Base64.decode(imageData.substringAfter(","), Base64.DEFAULT)

/**
 * Saves one frame to Pictures/BigShelf (web `downloadFrame`).
 * ponytail: MediaStore inserts need Android 10+ without the storage permission, so older
 * phones only get Save to library; add WRITE_EXTERNAL_STORAGE (maxSdk 28) if that matters
 */
private suspend fun saveFrame(context: Context, frame: Frame) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "frame_${frame.timestampFormatted.replace(":", "-")}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BigShelf")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
    resolver.openOutputStream(uri)!!.use { it.write(frame.bytes()) }
}

private val canSaveFrames = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * Port of modules/framer: pick a video (or open one from the library with [photoId]), extract
 * its best frames, pick the keepers and save them to the library. Tap a frame to (de)select
 * it, long-press to preview it with its score breakdown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FramerScreen(photoId: String?, onBack: () -> Unit) {
    val api = BigShelfApp.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var video by remember { mutableStateOf<Video?>(null) }
    var topK by remember { mutableStateOf(12) }
    var extracting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FramerResult?>(null) }
    val selected = remember { mutableStateListOf<Int>() }
    var saving by remember { mutableStateOf(false) }
    // True once the current frames are saved; a new video or extract clears it
    var saved by remember { mutableStateOf(false) }
    var loadingVideo by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Cleared once the library original is binned, which hides the button
    var libraryId by remember { mutableStateOf(photoId) }
    var preview by remember { mutableStateOf<Frame?>(null) }
    var showGrid by remember { mutableStateOf(false) }
    val frames = result?.frames.orEmpty()

    fun useVideo(next: Video) {
        video = next
        result = null
        saved = false
        selected.clear()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var name = "video.mp4"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0) ?: name
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        // A picked file was never uploaded, so it has no library original to bin
        libraryId = null
        useVideo(Video(uri, name, size, context.contentResolver.getType(uri)))
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    // Opened from the viewer: copy that library video to the cache so it uploads like a picked one.
    // ponytail: bytes round-trip through the phone; add a photo_id mode to /framer/extract if big videos make this slow
    LaunchedEffect(photoId) {
        if (photoId == null) return@LaunchedEffect
        loadingVideo = true
        try {
            val photo = api.photo(photoId)
            val url = photo.url
            if (!photo.isVideo || url == null) error("not a video")
            val file = File(context.cacheDir, "framer-${photo.filename}")
            api.fetchToFile(url, file)
            useVideo(Video(Uri.fromFile(file), photo.filename, file.length(), photo.mimeType, Uri.parse(api.url(url))))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            toast.error("Couldn't load that video from your library")
        } finally {
            loadingVideo = false
        }
    }

    suspend fun saveFrames(toSave: List<Frame>) {
        if (toSave.isEmpty() || saving) return
        saving = true
        try {
            val res = api.saveFrames(toSave)
            PhotoEvents.invalidate("photos")
            toast.success("Saved ${res.savedCount} frames to library")
            saved = true
        } catch (e: Exception) {
            toast.error(e.userMessage("Failed to save frames to library"))
        } finally {
            saving = false
        }
    }

    // Resolves with the frames (null on failure) so Quick extract can save them
    suspend fun extract(): List<Frame>? {
        val v = video ?: return null
        if (extracting) return null
        extracting = true
        result = null
        saved = false
        selected.clear()
        return try {
            val res = api.extractFrames(v.uri, topK, v.name, v.mimeType)
            result = res
            selected.addAll(res.frames.indices)
            toast.success("Extracted ${res.frames.size} frames")
            res.frames
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The backend's reason (unsupported format, too large, analysis failed)
            toast.error(e.userMessage("Failed to extract frames"))
            null
        } finally {
            extracting = false
        }
    }

    // Library video: move the original to Bin (frames stay so they can still be saved).
    // Picked file: just clear it from Framer.
    fun deleteVideo() {
        val id = libraryId
        if (id == null) {
            video = null
            result = null
            selected.clear()
            saved = false
            return
        }
        deleting = true
        scope.launch {
            try {
                api.flagDeleted(listOf(id), true)
                PhotoEvents.invalidate("photos", "bin-photos", "organize", "photo")
                toast.success("Video moved to Bin")
                libraryId = null
            } catch (e: Exception) {
                toast.error(e.userMessage("Failed to move video to Bin"))
            } finally {
                deleting = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Framer",
            count = result?.let { "${frames.size} frames · ${it.analyzedCount} analyzed (${it.durationSeconds.roundToInt()}s)" },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            if (frames.isNotEmpty()) {
                BsIconButton(BsIcons.GridFour, "Grid", onClick = { showGrid = !showGrid }, active = showGrid)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarInset.current),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Controls(
                    video = video,
                    topK = topK,
                    onTopK = { topK = it },
                    onPick = pick,
                    extracting = extracting,
                    saving = saving,
                    onExtract = { scope.launch { extract() } },
                    onQuickExtract = { scope.launch { extract()?.let { saveFrames(it) } } },
                    onPlay = {
                        video?.let { v ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(v.playUri, v.mimeType ?: "video/*")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            }
                        }
                    },
                )
            }
            if (extracting || loadingVideo) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spinner(size = 24.dp, tint = BsColors.Muted)
                        Text(
                            if (loadingVideo) "Loading video…" else "Analyzing…",
                            Modifier.padding(top = 8.dp),
                            style = BsText.Xs,
                            color = BsColors.Muted,
                        )
                    }
                }
            }
            if (frames.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BsButton(
                            onClick = {
                                if (selected.size == frames.size) selected.clear()
                                else selected.addAll(frames.indices.filterNot(selected::contains))
                            },
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Ghost,
                        ) { Text(if (selected.size == frames.size) "Deselect all" else "Select all") }
                        if (saved) {
                            // Frames are in the library: offer to drop the video they came from
                            BsButton(
                                onClick = ::deleteVideo,
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Secondary,
                                contentColor = BsColors.Danger,
                                loading = deleting,
                                enabled = !loadingVideo && (libraryId != null || video != null),
                            ) {
                                BsIcon(BsIcons.Trash, size = 16.dp)
                                Text(if (libraryId != null) "Bin video" else "Remove video")
                            }
                        } else {
                            BsButton(
                                onClick = { scope.launch { saveFrames(frames.filterIndexed { i, _ -> i in selected }) } },
                                modifier = Modifier.weight(1f),
                                enabled = selected.isNotEmpty(),
                                loading = saving,
                            ) {
                                BsIcon(BsIcons.FloppyDisk, size = 16.dp)
                                Text("Save ${selected.size}")
                            }
                        }
                    }
                }
            }
            itemsIndexed(frames, key = { _, f -> f.frameIndex }) { i, frame ->
                FrameTile(
                    frame = frame,
                    selected = i in selected,
                    showGrid = showGrid,
                    modifier = Modifier.combinedClickable(
                        onClick = { if (!selected.remove(i)) selected.add(i) },
                        onLongClick = { preview = frame },
                    ),
                )
            }
        }
    }

    preview?.let { frame ->
        BsModal(
            title = "${frame.timestampFormatted} · ${frame.score.roundToInt()}",
            onClose = { preview = null },
            footer = {
                BsButton(onClick = { showGrid = !showGrid }, variant = ButtonVariant.Ghost) {
                    BsIcon(BsIcons.GridFour, size = 16.dp)
                    Text("Grid")
                }
                if (canSaveFrames) {
                    BsButton(onClick = {
                        scope.launch {
                            try {
                                saveFrame(context, frame)
                                toast.success("Frame saved to Pictures")
                            } catch (_: Exception) {
                                toast.error("Failed to save frame")
                            }
                        }
                    }, variant = ButtonVariant.Secondary) {
                        BsIcon(BsIcons.DownloadSimple, size = 16.dp)
                        Text("Download")
                    }
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().clip(BsShapes.Md).background(Color.Black)) {
                    AsyncImage(
                        remember(frame) { frame.bytes() },
                        "Frame at ${frame.timestampFormatted}",
                        Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                    if (showGrid) ThirdsGrid()
                }
                if (frame.highlights.isNotEmpty()) {
                    Text(frame.highlights.joinToString(" · "), style = BsText.Xs, color = BsColors.MutedSoft)
                }
                // Score breakdown: sharpness, framing, portrait, lighting
                Text(
                    listOf(
                        "shp" to frame.breakdown.sharpness,
                        "frm" to frame.breakdown.framing,
                        "prt" to frame.breakdown.portrait,
                        "lgt" to frame.breakdown.lighting,
                    ).joinToString("   ") { (label, value) -> "$label ${value.roundToInt()}" },
                    style = BsText.Xs,
                    color = BsColors.Muted,
                )
            }
        }
    }
}

/** Video picker, frame count and the two extract buttons (web FramerControls). */
@Composable
private fun Controls(
    video: Video?,
    topK: Int,
    onTopK: (Int) -> Unit,
    onPick: () -> Unit,
    extracting: Boolean,
    saving: Boolean,
    onExtract: () -> Unit,
    onQuickExtract: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(BsShapes.Xl).background(BsColors.Surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BsIcon(BsIcons.VideoCamera, size = 24.dp, tint = BsColors.Muted)
            Column(Modifier.weight(1f)) {
                Text(
                    video?.name ?: "No video selected",
                    style = BsText.Sm.copy(fontWeight = BsWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (video != null && video.size > 0) Text(formatBytes(video.size), style = BsText.Xs, color = BsColors.Muted)
            }
            if (video != null) BsIconButton(BsIcons.Play, "Play video", onClick = onPlay)
            BsButton(onClick = onPick, variant = ButtonVariant.Secondary) {
                BsIcon(BsIcons.UploadSimple, size = 16.dp)
                Text(if (video != null) "Replace" else "Select video")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Frames to keep", style = BsText.Xs, color = BsColors.Muted)
            BsTabs(FRAME_COUNTS, topK, onTopK)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BsButton(onClick = onExtract, modifier = Modifier.weight(1f), enabled = video != null && !extracting, loading = extracting) {
                Text("Extract frames")
            }
            // Extract, then save every frame to the library in one go
            BsButton(
                onClick = onQuickExtract,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Secondary,
                enabled = video != null && !extracting && !saving,
            ) {
                BsIcon(BsIcons.Lightning, size = 16.dp)
                Text("Quick extract")
            }
        }
    }
}

/** 3:4 frame with its time and score, tinted when selected (web FrameCard). */
@Composable
private fun FrameTile(frame: Frame, selected: Boolean, showGrid: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(3f / 4f).background(BsColors.Surface.copy(alpha = 0.3f))) {
        AsyncImage(
            remember(frame) { frame.bytes() },
            "Frame at ${frame.timestampFormatted}",
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (selected) Box(Modifier.fillMaxSize().background(BsColors.Primary.copy(alpha = 0.3f)))
        if (showGrid) ThirdsGrid()
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .size(24.dp)
                .clip(BsShapes.Full)
                .background(if (selected) BsColors.Primary else Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) BsIcon(BsIcons.CheckBold, size = 14.dp, tint = BsColors.Foreground)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(frame.timestampFormatted, Modifier.weight(1f), style = BsText.Xs2, color = BsColors.MutedSoft)
            Text("${frame.score.roundToInt()}", style = BsText.Xs2, color = BsColors.MutedSoft)
        }
    }
}

/** Viewfinder rule-of-thirds lines laid over a frame (web GridOverlay). */
@Composable
private fun BoxScope.ThirdsGrid() {
    Box(
        Modifier.matchParentSize().drawWithContent {
            val line = Color.White.copy(alpha = 0.2f)
            for (i in 1..2) {
                val x = size.width * i / 3
                val y = size.height * i / 3
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
        },
    )
}
