package com.bigshelf.app.ui.screens.photo

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Downloads
import com.bigshelf.app.data.Photo
import com.bigshelf.app.data.USER_MARKED_AI
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ConfirmModal
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

private const val PROCESSING_POLL_MS = 2000L

/**
 * Port of modules/home/preview.tsx: full-view photo with the icon-button header and the
 * Info drawer. Swipe left/right walks the grid it was opened from (web: arrow keys / ImageNav).
 */
@Composable
fun PhotoScreen(photoId: String, onBack: () -> Unit, onPerson: (String) -> Unit) {
    // The grid's list if it contains this photo, else just this one
    val list = remember(photoId) {
        ViewerList.photos.takeIf { l -> l.any { it.id == photoId } } ?: listOf(Photo(id = photoId))
    }
    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.id == photoId }.coerceAtLeast(0)) { list.size }
    // Info panel overlays the photo on phones, so it starts closed (web: closed below md)
    var showInfo by rememberSaveable { mutableStateOf(false) }

    HorizontalPager(pager, Modifier.fillMaxSize(), key = { list[it].id }, beyondViewportPageCount = 1) { page ->
        PhotoPage(
            gridPhoto = list[page],
            active = page == pager.currentPage,
            showInfo = showInfo,
            onToggleInfo = { showInfo = !showInfo },
            onBack = onBack,
            onPerson = onPerson,
        )
    }
}

@Composable
private fun PhotoPage(
    gridPhoto: Photo,
    active: Boolean,
    showInfo: Boolean,
    onToggleInfo: () -> Unit,
    onBack: () -> Unit,
    onPerson: (String) -> Unit,
) {
    val api = BigShelfApp.api
    var image by remember { mutableStateOf<Photo?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    // Detail record; polled while the pipeline is still working on it (web usePhoto)
    LaunchedEffect(gridPhoto.id, refresh) {
        while (true) {
            try {
                val photo = api.photo(gridPhoto.id)
                image = photo
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
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            when {
                shown != null -> {
                    // Thumbnail as a backdrop so the photo shows at once; the original draws over it
                    shown.thumbnailUrl?.let {
                        AsyncImage(
                            model = api.mediaUrl(it),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!shown.isVideo && shown.url != null) {
                        AsyncImage(
                            model = api.mediaUrl(shown.url),
                            contentDescription = shown.title ?: shown.filename,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
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
                                .padding(16.dp),
                        ) {
                            BsIcon(BsIcons.PlayFill, size = 32.dp, tint = Color.White.copy(alpha = 0.9f), contentDescription = "Play")
                        }
                    }
                }
                else -> Text(if (notFound) "Image not found" else "Loading…", color = BsColors.Muted)
            }
        }

        // `fixed top-4 left-4 rounded-full bg-surface/80 border` back button
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .bsClickable(onClick = onBack)
                .clip(BsShapes.Full)
                .background(BsColors.Surface.copy(alpha = 0.8f))
                .border(1.dp, BsColors.Border, BsShapes.Full)
                .padding(8.dp),
        ) {
            BsIcon(BsIcons.ArrowLeft, size = 16.dp, contentDescription = "Back")
        }

        val current = image
        if (current != null && active) {
            ImageHeader(
                image = current,
                showInfo = showInfo,
                onToggleInfo = onToggleInfo,
                onChanged = { image = it },
                onDeleted = onBack,
                modifier = Modifier.align(Alignment.TopEnd).padding(start = 56.dp),
            )
            AnimatedVisibility(
                visible = showInfo,
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = 56.dp),
                enter = fadeIn() + slideInHorizontally { it / 3 },
                exit = fadeOut() + slideOutHorizontally { it / 3 },
            ) {
                InfoPanel(current, onPerson)
            }
        }
    }
}

/**
 * Port of image-header.tsx: `p-2 bg-surface border border-border rounded-lg` icon buttons -
 * download, info, favourite, mark-as-AI, rotate, delete. Scrolls when it outgrows a phone.
 */
@Composable
private fun ImageHeader(
    image: Photo,
    showInfo: Boolean,
    onToggleInfo: () -> Unit,
    onChanged: (Photo) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
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
        modifier.padding(6.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BsIconButton(BsIcons.DownloadSimple, "Download", onClick = {
            act("download", "Failed to download") {
                Downloads.save(context, listOf(image))
                toast.success("Downloading ${image.filename}")
            }
        })
        BsIconButton(BsIcons.Info, "Info", onClick = onToggleInfo, active = showInfo)
        // Filled heart = favourite
        BsIconButton(
            if (image.isFavorite) BsIcons.HeartFill else BsIcons.Heart,
            if (image.isFavorite) "Remove from favourites" else "Favourite",
            onClick = {
                act("favorite", "Failed to update favourite") {
                    onChanged(api.setFavorite(image.id, !image.isFavorite))
                    PhotoEvents.invalidate("photos")
                }
            },
            tint = if (image.isFavorite) BsColors.Foreground else null,
        )
        if (!image.isVideo) {
            // Filled when the photo counts as AI; disabled when that was detected, not marked
            BsIconButton(
                if (image.isAiImage) BsIcons.FlowerTulipFill else BsIcons.FlowerTulip,
                if (detectedAi) "Detected as AI" else if (markedByUser) "Unmark as AI" else "Mark as AI",
                onClick = {
                    act("ai", "Failed to update AI mark") {
                        onChanged(api.markAi(image.id, !markedByUser))
                        PhotoEvents.invalidate("photos")
                        toast.success(if (markedByUser) "Removed AI mark" else "Marked as AI")
                    }
                },
                enabled = !detectedAi,
                loading = busy == "ai",
                tint = if (image.isAiImage) BsColors.Foreground else null,
            )
            BsIconButton(
                BsIcons.ArrowClockwise,
                "Rotate 90° clockwise",
                onClick = {
                    act("rotate", "Failed to rotate photo") {
                        onChanged(api.rotate(image.id))
                        PhotoEvents.invalidate("photos")
                    }
                },
                loading = busy == "rotate",
            )
        }
        BsIconButton(BsIcons.Trash, "Delete", onClick = { confirmDelete = true })
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

/** Port of image-info-sidebar.tsx as the phone overlay: `w-90 bg-surface rounded-lg`. */
@Composable
private fun InfoPanel(image: Photo, onPerson: (String) -> Unit) {
    Column(
        Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(BsShapes.Lg)
            .background(BsColors.Surface),
    ) {
        Text(
            "Info",
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(BsColors.Border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(12.dp),
            style = BsText.Base.copy(fontWeight = BsWeight.Semibold),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoField("Name") { Text(image.title ?: image.filename, style = BsText.Sm.copy(fontWeight = BsWeight.Medium)) }
            if (image.width != null && image.height != null) {
                InfoField("Dimensions") { Text("${image.width} × ${image.height}", style = BsText.Sm, color = BsColors.MutedSoft) }
            }
            // Faces only exist for images; a video would sit at "Detecting…" forever
            if (!image.isVideo) {
                val faces = image.faces.orEmpty().filterNot { it.isIgnored }
                InfoField("Faces") {
                    Text(
                        if (image.faceCount == null) "Detecting…" else "${faces.size} detected",
                        style = BsText.Xs,
                        color = BsColors.MutedSoft,
                    )
                    if (faces.isNotEmpty()) {
                        // `grid grid-cols-3 gap-0.5 mt-2.5`; tagged faces open the person page
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            faces.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    row.forEach { face ->
                                        FaceTile(
                                            imageSrc = face.thumbnailUrl,
                                            name = face.personName ?: "Untagged",
                                            subtitle = null,
                                            onClick = { face.personId?.let(onPerson) },
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
            image.photoPerson?.personName?.let { name ->
                InfoField("Tagged") { Text("$name (no face detected)", style = BsText.Xs, color = BsColors.MutedSoft) }
            }
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

