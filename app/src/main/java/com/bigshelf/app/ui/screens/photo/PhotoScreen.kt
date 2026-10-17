package com.bigshelf.app.ui.screens.photo

import android.content.Intent
import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import com.bigshelf.app.data.CropBox
import com.bigshelf.app.data.WHOLE_PHOTO_FACE_INDEX
import com.bigshelf.app.ui.photos.ImageCard
import com.bigshelf.app.ui.screens.people.PersonList
import com.bigshelf.app.data.CameraInfo
import com.bigshelf.app.data.PlaceNames
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsInput
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.screens.explore.rememberPlaceName
import com.bigshelf.app.ui.shell.formatBytes
import com.bigshelf.app.ui.theme.MonoFamily
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Downloads
import com.bigshelf.app.data.Photo
import com.bigshelf.app.data.USER_MARKED_AI
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.ConfirmModal
import com.bigshelf.app.ui.components.Skeleton
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.FaceTile
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.bigshelf.app.ui.theme.BsMotion

private const val PROCESSING_POLL_MS = 2000L

// "Sep 24, 2026, 3:12 PM" from an ISO timestamp, or null if it doesn't parse
private fun formatDate(iso: String?): String? = iso?.let {
    runCatching {
        val local = runCatching { OffsetDateTime.parse(it).toLocalDateTime() }.getOrElse { _ -> LocalDateTime.parse(it) }
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.US).format(local)
    }.getOrNull()
}

/**
 * Port of modules/home/preview.tsx as a mobile viewer: edge-to-edge photo, a back button on
 * top and the actions along the bottom, both hidden by a tap. Info opens as a bottom sheet.
 * Swipe left/right walks the grid it was opened from (web: arrow keys / ImageNav); only the
 * photo moves - the bars stay put and follow the current page. Pinch or double-tap zooms (and
 * holds the pager still); at 1x, swiping down closes the viewer and swiping up opens Info.
 */
@Composable
fun PhotoScreen(
    photoId: String,
    onBack: () -> Unit,
    onPerson: (String) -> Unit,
    onMap: (Double, Double) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onFramer: (String) -> Unit,
) {
    // The grid's list if it contains this photo, else just this one
    val list = remember(photoId) {
        ViewerList.photos.takeIf { l -> l.any { it.id == photoId } } ?: listOf(Photo(id = photoId))
    }
    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.id == photoId }.coerceAtLeast(0)) { list.size }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    // Detail records by id, filled by the pages and read by the fixed bars
    val details = remember { mutableStateMapOf<String, Photo>() }
    // A zoomed-in photo pans instead of paging
    var zoomed by remember { mutableStateOf(false) }
    // Crop mode belongs to one photo; it takes over the screen until saved or cancelled
    var croppingId by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize(),
            key = { list[it].id },
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed && croppingId == null,
        ) { page ->
            val id = list[page].id
            PhotoPage(
                gridPhoto = list[page],
                image = details[id],
                isCurrent = page == pager.currentPage,
                onLoaded = { details[id] = it },
                onToggleChrome = { chrome = !chrome },
                onZoomChange = { zoomed = it },
                onSwipeDown = onBack,
                onSwipeUp = { if (details[id] != null) showInfo = true },
            )
        }

        val current = details[list[pager.currentPage].id]
        val cropping = current != null && croppingId == current.id
        AnimatedVisibility(chrome && !cropping, Modifier.align(Alignment.TopCenter), enter = fadeIn(BsMotion.fast()), exit = fadeOut(BsMotion.fast())) {
            TopBar(current, onBack)
        }
        if (current != null) {
            AnimatedVisibility(chrome && !cropping, Modifier.align(Alignment.BottomCenter), enter = fadeIn(BsMotion.fast()), exit = fadeOut(BsMotion.fast())) {
                ActionBar(
                    current,
                    onInfo = { showInfo = true },
                    onChanged = { details[it.id] = it },
                    onDeleted = onBack,
                    onCrop = { croppingId = current.id },
                    // The original is in the Bin now, so the first new photo takes its place
                    onSplit = { id ->
                        onBack()
                        onOpenPhoto(id)
                    },
                    onFramer = { onFramer(current.id) },
                )
            }
            if (cropping) CropOverlay(current, onClose = { croppingId = null })
            if (showInfo) {
                InfoSheet(
                    current, onPerson, onMap,
                    onChanged = { details[it.id] = it },
                    onOpenPhoto = { id ->
                        showInfo = false
                        onOpenPhoto(id)
                    },
                    onDismiss = { showInfo = false },
                )
            }
        }
    }
}

/** One photo's zoom: scale, pan offset, and the vertical drag of a swipe at 1x. */
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var drag by mutableFloatStateOf(0f)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private val SWIPE_CLOSE = 96.dp

// How far the scaled photo may move: until its edge reaches the screen's
private fun PointerInputScope.clamp(offset: Offset, scale: Float): Offset {
    val maxX = size.width * (scale - 1) / 2
    val maxY = size.height * (scale - 1) / 2
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/**
 * Pinch / double-tap zoom and pan. One finger at 1x is left to the pager when it moves
 * sideways; a mostly vertical move is a swipe ([onSwipeDown] closes, [onSwipeUp] opens Info).
 */
private fun Modifier.zoomable(
    state: ZoomState,
    onTap: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
): Modifier = this
    .pointerInput(state) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { tap ->
                if (state.scale > 1f) {
                    state.reset()
                } else {
                    // Zoom in on the tapped spot
                    val center = Offset(size.width / 2f, size.height / 2f)
                    state.scale = DOUBLE_TAP_ZOOM
                    state.offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1), DOUBLE_TAP_ZOOM)
                }
            },
        )
    }
    .pointerInput(state) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            // 0 undecided, 1 zoom/pan, 2 vertical swipe, 3 left to the pager
            var mode = 0
            var travel = Offset.Zero
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                if (mode == 0 || mode == 3) {
                    if (fingers > 1 || state.scale > 1f) {
                        mode = 1
                    } else if (mode == 0) {
                        travel += event.calculatePan()
                        if (travel.getDistance() > slop) mode = if (abs(travel.y) > abs(travel.x) * 1.5f) 2 else 3
                    }
                }
                when (mode) {
                    1 -> {
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (centroid != Offset.Unspecified) {
                            // Keep the point under the fingers still while scaling around it
                            val newScale = (state.scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val c = centroid - Offset(size.width / 2f, size.height / 2f)
                            val moved = c - (c - state.offset) * (newScale / state.scale) + event.calculatePan()
                            state.scale = newScale
                            state.offset = clamp(moved, newScale)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                    2 -> {
                        state.drag += event.calculatePan().y
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (mode == 2) {
                val threshold = SWIPE_CLOSE.toPx()
                if (state.drag > threshold) onSwipeDown() else if (state.drag < -threshold) onSwipeUp()
                state.drag = 0f
            }
            if (state.scale <= 1.01f) state.reset()
        }
    }

@Composable
private fun PhotoPage(
    gridPhoto: Photo,
    image: Photo?,
    isCurrent: Boolean,
    onLoaded: (Photo) -> Unit,
    onToggleChrome: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
) {
    val api = BigShelfApp.api
    val zoom = remember { ZoomState() }
    val zoomedIn = zoom.scale > 1f
    // Only the page on screen drives the pager lock; one swiped away starts fresh next time
    LaunchedEffect(zoomedIn, isCurrent) {
        if (isCurrent) onZoomChange(zoomedIn) else zoom.reset()
    }
    var notFound by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    // Detail record; polled while the pipeline is still working on it (web usePhoto)
    LaunchedEffect(gridPhoto.id, refresh) {
        while (true) {
            try {
                val photo = api.photo(gridPhoto.id)
                onLoaded(photo)
                if (!photo.isAwaitingProcessing) break
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                notFound = image == null
                break
            }
            delay(PROCESSING_POLL_MS)
        }
    }
    LaunchedEffect(gridPhoto.id) {
        PhotoEvents.invalidations.collect { if ("photo" in it) refresh++ }
    }

    // Until the detail lands, the grid item's (already cached) thumbnail stands in
    val shown = image ?: gridPhoto.takeIf { it.thumbnailUrl != null }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .zoomable(zoom, onTap = onToggleChrome, onSwipeDown = onSwipeDown, onSwipeUp = onSwipeUp)
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    // A swipe down drags the photo along (and fades it) before closing
                    translationY = zoom.offset.y + zoom.drag.coerceAtLeast(0f)
                    alpha = 1f - (zoom.drag.coerceAtLeast(0f) / (size.height.coerceAtLeast(1f))).coerceIn(0f, 0.5f)
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                shown != null -> {
                    // The 3:4 grid thumbnail stands in until the original loads, then steps aside -
                    // its crop differs, so leaving it underneath would show around the photo
                    var originalLoaded by remember(shown.url) { mutableStateOf(false) }
                    if (!originalLoaded || shown.isVideo) {
                        shown.thumbnailUrl?.let {
                            AsyncImage(api.mediaUrl(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                    if (!shown.isVideo && shown.url != null) {
                        AsyncImage(
                            api.mediaUrl(shown.url),
                            shown.title ?: shown.filename,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onSuccess = { originalLoaded = true },
                        )
                    }
                    if (shown.isVideo) {
                        // Videos play in the system player
                        Box(
                            Modifier
                                .bsClickable {
                                    shown.url?.let { url ->
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(api.url(url)), shown.mimeType ?: "video/*"),
                                        )
                                    }
                                }
                                .clip(BsShapes.Full)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(20.dp),
                        ) {
                            BsIcon(BsIcons.PlayFill, size = 32.dp, tint = Color.White.copy(alpha = 0.9f), contentDescription = "Play")
                        }
                    }
                }
                notFound -> Text("Image not found", color = BsColors.Muted)
                else -> Skeleton(Modifier.fillMaxSize())
            }
        }
    }
}

/** Back button and the photo's name/date over a black-to-clear scrim. */
@Composable
private fun TopBar(image: Photo?, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp).clip(BsShapes.Full).bsClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            BsIcon(BsIcons.ArrowLeft, size = 24.dp, tint = BsColors.Foreground, contentDescription = "Back")
        }
        if (image != null) {
            Column(Modifier.weight(1f)) {
                formatDate(image.takenAt ?: image.createdAt)?.let {
                    Text(it, style = BsText.Sm.copy(fontWeight = BsWeight.Medium), maxLines = 1)
                }
                Text(
                    image.title ?: image.filename,
                    style = BsText.Xs,
                    color = BsColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One bottom-bar action: 24dp icon over a `text-xs` label, 64dp wide touch area. */
@Composable
private fun Action(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tint: Color = BsColors.Foreground,
) {
    Column(
        modifier.clip(BsShapes.Lg).bsClickable(enabled = enabled && !loading, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val color = if (enabled) tint else BsColors.MutedFaint
        if (loading) Spinner(size = 24.dp, tint = color) else BsIcon(icon, size = 24.dp, tint = color)
        Text(label, style = BsText.Xs2, color = if (enabled) BsColors.MutedSoft else BsColors.MutedFaint, maxLines = 1)
    }
}

/**
 * Port of image-header.tsx as a bottom bar: favourite, info, download, mark-as-AI, rotate,
 * delete - over a clear-to-black scrim.
 */
@Composable
private fun ActionBar(
    image: Photo,
    onInfo: () -> Unit,
    onChanged: (Photo) -> Unit,
    onDeleted: () -> Unit,
    onCrop: () -> Unit,
    onSplit: (String) -> Unit,
    onFramer: () -> Unit,
) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    // Only the user's own mark can be undone; photos detected as AI stay AI
    val markedByUser = image.aiSource == USER_MARKED_AI
    val detectedAi = image.isAiImage && !markedByUser

    fun act(name: String, failure: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = name
        scope.launch {
            try {
                block()
            } catch (_: Exception) {
                toast.error(failure)
            } finally {
                busy = null
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val cell = Modifier.weight(1f)
        Action(
            if (image.isFavorite) BsIcons.HeartFill else BsIcons.Heart,
            "Favourite",
            onClick = {
                act("favorite", "Failed to update favourite") {
                    onChanged(api.setFavorite(image.id, !image.isFavorite))
                    PhotoEvents.invalidate("photos")
                }
            },
            modifier = cell,
            loading = busy == "favorite",
        )
        Action(BsIcons.Info, "Info", onClick = onInfo, modifier = cell)
        Action(
            BsIcons.DownloadSimple,
            "Download",
            onClick = {
                act("download", "Failed to download") {
                    Downloads.save(context, listOf(image))
                    toast.success("Downloading ${image.filename}")
                }
            },
            modifier = cell,
            loading = busy == "download",
        )
        if (!image.isVideo) {
            // Filled when the photo counts as AI; disabled when that was detected, not marked
            Action(
                if (image.isAiImage) BsIcons.FlowerTulipFill else BsIcons.FlowerTulip,
                if (detectedAi) "AI" else if (markedByUser) "Unmark AI" else "Mark AI",
                onClick = {
                    act("ai", "Failed to update AI mark") {
                        onChanged(api.markAi(image.id, !markedByUser))
                        PhotoEvents.invalidate("photos")
                        toast.success(if (markedByUser) "Removed AI mark" else "Marked as AI")
                    }
                },
                modifier = cell,
                enabled = !detectedAi,
                loading = busy == "ai",
            )
            Action(
                BsIcons.ArrowClockwise,
                "Rotate",
                onClick = {
                    act("rotate", "Failed to rotate photo") {
                        onChanged(api.rotate(image.id))
                        PhotoEvents.invalidate("photos")
                    }
                },
                modifier = cell,
                loading = busy == "rotate",
            )
            // Crop and split re-encode the image, so they're images-only like rotate
            Action(BsIcons.DotsThreeVertical, "More", onClick = { showMore = true }, modifier = cell, loading = busy == "split")
        } else {
            // Videos: pick the best frames of this one in Framer
            Action(BsIcons.FilmStrip, "Framer", onClick = onFramer, modifier = cell)
        }
        Action(BsIcons.Trash, "Delete", onClick = { confirmDelete = true }, modifier = cell, loading = busy == "delete", tint = BsColors.Danger)
    }

    if (showMore) {
        BsModal(title = "Edit", onClose = { showMore = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // The 3:4 window needs the dimensions to size itself
                if (image.width != null && image.height != null) {
                    MenuRow(BsIcons.Crop, "Crop to 3:4") {
                        showMore = false
                        onCrop()
                    }
                }
                // Splits a collage into its photos; the original moves to the Bin
                MenuRow(BsIcons.SquareSplitVertical, "Split") {
                    showMore = false
                    act("split", "Failed to split photo") {
                        val created = api.split(image.id).createdPhotos
                        PhotoEvents.invalidate("photos", "bin-photos")
                        toast.success("Photo split initiated")
                        // Show the first new photo now that the original is in the Bin
                        created.firstOrNull()?.let { onSplit(it.id) } ?: onDeleted()
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmModal(
            title = "Delete photo",
            message = "Move ${image.filename} to the Bin?",
            confirmLabel = "Delete",
            onConfirm = {
                act("delete", "Failed to delete photo") {
                    api.deletePhoto(image.id)
                    PhotoEvents.invalidate("photos", "bin-photos")
                    toast.success("Photo deleted")
                    onDeleted()
                }
            },
            onClose = { confirmDelete = false },
        )
    }
}

/** Port of image-info-sidebar.tsx as a bottom sheet on the surface colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(
    image: Photo,
    onPerson: (String) -> Unit,
    onMap: (Double, Double) -> Unit,
    onChanged: (Photo) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Index of the face picked for tagging (WHOLE_PHOTO_FACE_INDEX: the photo itself)
    var tagging by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BsColors.Surface,
        contentColor = BsColors.Foreground,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = BsColors.BorderFocus) },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Info", style = BsText.Lg.copy(fontWeight = BsWeight.Semibold))
            InfoField("Name") { Text(image.title ?: image.filename, style = BsText.Sm.copy(fontWeight = BsWeight.Medium)) }
            formatDate(image.takenAt ?: image.createdAt)?.let { date ->
                InfoField(if (image.takenAt != null) "Taken" else "Uploaded") { Text(date, style = BsText.Sm, color = BsColors.MutedSoft) }
            }
            if (image.width != null && image.height != null) {
                val megapixels = image.width * image.height / 1_000_000.0
                InfoField("Dimensions") {
                    Text(
                        "${image.width} × ${image.height}" + if (megapixels >= 0.1) String.format(Locale.US, " · %.1f MP", megapixels) else "",
                        style = BsText.Sm,
                        color = BsColors.MutedSoft,
                    )
                }
                if (image.width > 0 && image.height > 0) {
                    InfoField("Aspect ratio") { Text(aspectRatio(image.width, image.height), style = BsText.Sm, color = BsColors.MutedSoft) }
                }
            }
            InfoField("File") {
                Text(image.filename, style = BsText.Sm, color = BsColors.MutedSoft)
                Text(
                    listOfNotNull(image.mimeType, image.sizeBytes?.let(::formatBytes)).joinToString(" · "),
                    style = BsText.Xs,
                    color = BsColors.Muted,
                )
            }
            image.exif?.let { camera -> CameraField(camera) }
            if (image.latitude != null && image.longitude != null) {
                LocationField(image.latitude, image.longitude, onMap = {
                    onDismiss()
                    onMap(image.latitude, image.longitude)
                })
            }
            // Faces only exist for images; a video would sit at "Detecting…" forever
            if (!image.isVideo) {
                val faces = image.faces.orEmpty().withIndex().filterNot { it.value.isIgnored }
                // A person can be tagged on the photo itself when no face of them was found
                // (back turned, face covered); offered once detection has run and found none
                val canTagPhoto = image.faceCount != null && faces.isEmpty()
                InfoField("Faces") {
                    Text(
                        if (image.faceCount == null) "Detecting…" else "${faces.size} detected",
                        style = BsText.Sm,
                        color = BsColors.MutedSoft,
                    )
                    val tiles = faces.map { (i, face) ->
                        Triple(i, face.thumbnailUrl ?: face.faceId?.let { "/api/v1/faces/$it/thumbnail" }, face.personName ?: face.suggestedName?.let { "$it?" } ?: "Add name")
                    } + if (image.photoPerson != null || canTagPhoto) {
                        // The whole photo stands in for the face
                        listOf(Triple(WHOLE_PHOTO_FACE_INDEX, image.thumbnailUrl, image.photoPerson?.personName ?: "Tag person"))
                    } else {
                        emptyList()
                    }
                    if (tiles.isNotEmpty()) {
                        // Any face opens the tag modal: name it, change or remove its tag, or forget it
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tiles.chunked(4).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (index, src, name) ->
                                        FaceTile(imageSrc = src, name = name, subtitle = null, onClick = { tagging = index }, modifier = Modifier.weight(1f))
                                    }
                                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
            SimilarPhotos(image.id, onOpenPhoto)
            Box(Modifier.height(8.dp))
        }
    }
    tagging?.let { index ->
        FaceTagModal(
            photo = image,
            faceIndex = index,
            onChanged = onChanged,
            onPerson = { id ->
                onDismiss()
                onPerson(id)
            },
            onClose = { tagging = null },
        )
    }
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

// Reduced ratio like "3:4"; odd sizes (e.g. 1080×1349) don't reduce cleanly, so show a decimal instead
private fun aspectRatio(width: Int, height: Int): String {
    val d = gcd(width, height)
    val (w, h) = width / d to height / d
    return if (w <= 32 && h <= 32) "$w:$h" else String.format(Locale.US, "%.2f:1", width.toDouble() / height)
}

// "1/120 s" under a second, "2 s" above
private fun exposure(seconds: Double): String =
    if (seconds < 1) "1/${(1 / seconds).roundToInt()} s" else "${String.format(Locale.US, "%.1f", seconds).removeSuffix(".0")} s"

/** Camera, lens and exposure settings from the upload's EXIF. */
@Composable
private fun CameraField(camera: CameraInfo) {
    // "Google Pixel 8", without doubling a make the model already starts with ("Apple iPhone")
    val name = listOfNotNull(
        camera.make?.takeUnless { make -> camera.model?.startsWith(make, ignoreCase = true) == true },
        camera.model,
    ).joinToString(" ")
    val settings = listOfNotNull(
        camera.fNumber?.let { "ƒ/${String.format(Locale.US, "%.1f", it).removeSuffix(".0")}" },
        camera.exposureTime?.takeIf { it > 0 }?.let(::exposure),
        camera.iso?.let { "ISO $it" },
        camera.focalLength?.let { "${String.format(Locale.US, "%.1f", it).removeSuffix(".0")} mm" },
    ).joinToString(" · ")
    if (name.isEmpty() && settings.isEmpty() && camera.lens == null) return
    InfoField("Camera") {
        if (name.isNotEmpty()) Text(name, style = BsText.Sm, color = BsColors.MutedSoft)
        camera.lens?.let { Text(it, style = BsText.Xs, color = BsColors.Muted) }
        if (settings.isNotEmpty()) Text(settings, style = BsText.Xs.copy(fontFamily = MonoFamily), color = BsColors.Muted)
    }
}

/** Where it was taken: the town (Geocoder), the coordinates, and a way onto the map. */
@Composable
private fun LocationField(lat: Double, lon: Double, onMap: () -> Unit) {
    val place = rememberPlaceName(lat, lon)
    InfoField("Location") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(place ?: PlaceNames.coordinates(lat, lon), style = BsText.Sm, color = BsColors.MutedSoft)
                if (place != null) Text(PlaceNames.coordinates(lat, lon), style = BsText.Xs.copy(fontFamily = MonoFamily), color = BsColors.Muted)
            }
            BsButton(onClick = onMap, variant = ButtonVariant.Secondary, textStyle = BsText.Xs) {
                BsIcon(BsIcons.MapTrifold, size = 14.dp)
                Text("Map")
            }
        }
    }
}

// Strips numbers and emoji from an AI-suggested name, then tidies the spaces they leave
private fun cleanName(name: String) =
    name.replace(Regex("[\\p{N}\\p{So}\\p{Sk}\\p{Cs}\\u200D\\uFE0F]"), "").replace(Regex("\\s+"), " ").trim()

/**
 * Port of face-tag-modal.tsx: tag face [faceIndex] of [photo] (or the photo itself, for
 * [WHOLE_PHOTO_FACE_INDEX]) as an existing person or a new name, change or remove its tag,
 * or forget a background face. People are filtered as you type and ranked by how much they
 * look like this face; the backend also tags close lookalikes elsewhere.
 */
@Composable
private fun FaceTagModal(photo: Photo, faceIndex: Int, onChanged: (Photo) -> Unit, onPerson: (String) -> Unit, onClose: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val wholePhoto = faceIndex == WHOLE_PHOTO_FACE_INDEX
    val face = if (wholePhoto) photo.photoPerson else photo.faces?.getOrNull(faceIndex)
    val currentName = face?.personName
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // The face's own guess first; else ask the backend for one from the filename
    var suggested by remember { mutableStateOf(face?.suggestedName?.trim()?.ifEmpty { null }) }
    LaunchedEffect(photo.id) {
        if (suggested == null && currentName == null) suggested = runCatching { api.suggestName(photo.id) }.getOrNull()
    }
    val suggestedName = suggested?.let(::cleanName)?.ifEmpty { null }

    // Debounced server-side search; a whole-photo tag ranks people by look-alike photos instead
    var people by remember { mutableStateOf<List<com.bigshelf.app.data.Person>?>(null) }
    LaunchedEffect(query) {
        delay(250)
        people = runCatching {
            api.people(query.trim(), faceId = face?.faceId, photoId = if (wholePhoto) photo.id else null).items
        }.getOrNull() ?: people
    }

    // Each action closes at once and toasts when the server answers
    fun run(failure: String, block: suspend () -> String) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val message = block()
                PhotoEvents.invalidate("photo", "people", "faces", "photos")
                toast.success(message)
                onClose()
            } catch (e: Exception) {
                toast.error(e.userMessage(failure))
            } finally {
                saving = false
            }
        }
    }

    fun tag(personId: String?, label: String) = run("Failed to save face tag") {
        val result = api.tagPhotoFace(photo.id, faceIndex, name = label, personId = personId)
        onChanged(result.photo)
        "Tagged as $label" + if (result.autoTaggedCount > 0) " & linked ${result.autoTaggedCount} matching photos" else ""
    }

    val clean = query.trim()
    BsModal(
        title = when {
            currentName != null -> "Edit: $currentName"
            wholePhoto -> "Tag person"
            else -> "Tag face"
        },
        onClose = onClose,
        footer = {
            // No face to forget for a whole-photo tag
            if (!wholePhoto) {
                BsButton(
                    onClick = { run("Failed to forget face") { onChanged(api.photoFaceAction(photo.id, faceIndex, "ignore").photo); "Face forgotten" } },
                    variant = ButtonVariant.Ghost,
                    enabled = !saving,
                    contentColor = BsColors.Danger,
                    textStyle = BsText.Xs,
                ) { Text("Forget face") }
            }
            if (currentName != null) {
                BsButton(
                    onClick = { run("Failed to remove tag") { onChanged(api.photoFaceAction(photo.id, faceIndex, "untag").photo); "Tag removed" } },
                    variant = ButtonVariant.Secondary,
                    enabled = !saving,
                    textStyle = BsText.Xs,
                ) { Text("Remove tag") }
            }
            face?.personId?.let { id ->
                BsButton(onClick = { onClose(); onPerson(id) }, variant = ButtonVariant.Secondary, textStyle = BsText.Xs) { Text("View") }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val src = if (wholePhoto) photo.thumbnailUrl else face?.thumbnailUrl ?: face?.faceId?.let { "/api/v1/faces/$it/thumbnail" }
                FaceTile(imageSrc = src, name = null, subtitle = null, onClick = {}, modifier = Modifier.size(64.dp))
                BsInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = suggestedName ?: "Name or search…",
                    autoFocus = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        // An exact name match tags that person; anything else is a new name
                        val match = people.orEmpty().firstOrNull { it.name.equals(clean, ignoreCase = true) }
                        if (match != null) tag(match.id, match.name) else if (clean.isNotEmpty()) tag(null, clean)
                    }),
                )
            }
            // The suggested name, one tap to use (web: Tab accepts the placeholder)
            if (clean.isEmpty() && suggestedName != null) {
                BsButton(onClick = { tag(null, suggestedName) }, variant = ButtonVariant.Secondary, enabled = !saving, textStyle = BsText.Xs) {
                    Text("Tag as \"$suggestedName\"")
                }
            } else if (clean.isNotEmpty()) {
                BsButton(onClick = { tag(null, clean) }, enabled = !saving, loading = saving, textStyle = BsText.Xs) {
                    Text("Tag as \"$clean\"")
                }
            }
            PersonList(
                people = people.orEmpty().filter { it.name != currentName },
                enabled = !saving,
                empty = if (people == null) "Searching…" else "No people found",
                onPick = { tag(it.id, it.name) },
            )
        }
    }
}

/** Menu entry in the viewer's Edit sheet: icon and label. */
@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(BsShapes.Md).bsClickable(scale = 0.98f, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BsIcon(icon, size = 20.dp, tint = BsColors.Muted)
        Text(label, style = BsText.Sm)
    }
}

/** Port of similar-images.tsx: visually similar photos, three across, in the Info sheet. */
@Composable
private fun SimilarPhotos(photoId: String, onOpenPhoto: (String) -> Unit) {
    val similar = rememberQuery("similar", photoId, invalidatedBy = setOf("photos")) {
        BigShelfApp.api.similar(photoId).filter { it.id != photoId }
    }
    val photos = similar.data.orEmpty()
    InfoField("Similar photos") {
        when {
            similar.isLoading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(size = 16.dp, tint = BsColors.Muted)
                Text("Finding similar photos…", style = BsText.Xs, color = BsColors.Muted)
            }
            photos.isEmpty() -> Text("No similar photos found", style = BsText.Xs, color = BsColors.Muted)
            else -> Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                photos.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { photo ->
                            ImageCard(
                                photo = photo,
                                selected = false,
                                selectionActive = false,
                                onClick = {
                                    ViewerList.photos = photos
                                    onOpenPhoto(photo.id)
                                },
                                onToggle = {},
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * Port of crop-overlay.tsx: the 3:4 window the crop keeps, over the photo with everything
 * outside dimmed. It spans the full width or height, so dragging slides it along the other
 * axis. Saving overwrites the original; the viewer reloads it under its new URL.
 */
@Composable
private fun CropOverlay(image: Photo, onClose: () -> Unit) {
    val w = image.width ?: return
    val h = image.height ?: return
    val scope = rememberCoroutineScope()
    var pos by remember(image.id) { mutableFloatStateOf(0.5f) }
    var saving by remember { mutableStateOf(false) }
    val box = CropBox.portrait(w, h, pos)
    val sideways = box.width < w // wider than 3:4: the window slides left/right
    val slack = if (sideways) w - box.width else h - box.height // image px the window can travel
    // Back leaves crop mode, not the viewer
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(BigShelfApp.api.mediaUrl(image.url ?: image.thumbnailUrl), image.filename, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(image.id, slack) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (slack <= 0) return@detectDragGestures
                        // Screen px moved -> image px -> fraction of the travel
                        val scale = minOf(size.width / w.toFloat(), size.height / h.toFloat())
                        val moved = if (sideways) drag.x else drag.y
                        pos = (pos + moved / scale / slack).coerceIn(0f, 1f)
                    }
                }
                .drawBehind {
                    // Where the Fit-scaled photo sits, then the kept window inside it
                    val scale = minOf(size.width / w, size.height / h)
                    val left = (size.width - w * scale) / 2 + box.x * scale
                    val top = (size.height - h * scale) / 2 + box.y * scale
                    val keep = Rect(left, top, left + box.width * scale, top + box.height * scale)
                    val dim = Color.Black.copy(alpha = 0.7f)
                    drawRect(dim, Offset.Zero, Size(size.width, keep.top))
                    drawRect(dim, Offset(0f, keep.bottom), Size(size.width, size.height - keep.bottom))
                    drawRect(dim, Offset(0f, keep.top), Size(keep.left, keep.height))
                    drawRect(dim, Offset(keep.right, keep.top), Size(size.width - keep.right, keep.height))
                    drawRect(BsColors.Foreground, keep.topLeft, keep.size, style = Stroke(2.dp.toPx()))
                },
        )
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BsButton(onClick = onClose, variant = ButtonVariant.Secondary, enabled = !saving) { Text("Cancel") }
            BsButton(
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            BigShelfApp.api.crop(image.id, box)
                            PhotoEvents.invalidate("photo", "photos")
                            toast.success("Cropped to 3:4")
                            onClose()
                        } catch (_: Exception) {
                            toast.error("Failed to crop photo")
                            saving = false
                        }
                    }
                },
                loading = saving,
            ) { Text("Save crop") }
        }
    }
}

@Composable
private fun InfoField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, Modifier.padding(bottom = 4.dp), style = BsText.Xs, color = BsColors.Muted)
        content()
    }
}
