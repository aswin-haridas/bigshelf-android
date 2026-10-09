package com.bigshelf.app.ui.screens.photo

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
 * photo moves - the bars stay put and follow the current page.
 */
@Composable
fun PhotoScreen(photoId: String, onBack: () -> Unit, onPerson: (String) -> Unit) {
    // The grid's list if it contains this photo, else just this one
    val list = remember(photoId) {
        ViewerList.photos.takeIf { l -> l.any { it.id == photoId } } ?: listOf(Photo(id = photoId))
    }
    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.id == photoId }.coerceAtLeast(0)) { list.size }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    // Detail records by id, filled by the pages and read by the fixed bars
    val details = remember { mutableStateMapOf<String, Photo>() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { list[it].id }, beyondViewportPageCount = 1) { page ->
            val id = list[page].id
            PhotoPage(
                gridPhoto = list[page],
                image = details[id],
                onLoaded = { details[id] = it },
                onToggleChrome = { chrome = !chrome },
            )
        }

        val current = details[list[pager.currentPage].id]
        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter), enter = fadeIn(BsMotion.fast()), exit = fadeOut(BsMotion.fast())) {
            TopBar(current, onBack)
        }
        if (current != null) {
            AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter), enter = fadeIn(BsMotion.fast()), exit = fadeOut(BsMotion.fast())) {
                ActionBar(current, onInfo = { showInfo = true }, onChanged = { details[it.id] = it }, onDeleted = onBack)
            }
            if (showInfo) InfoSheet(current, onPerson, onDismiss = { showInfo = false })
        }
    }
}

@Composable
private fun PhotoPage(gridPhoto: Photo, image: Photo?, onLoaded: (Photo) -> Unit, onToggleChrome: () -> Unit) {
    val api = BigShelfApp.api
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
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggleChrome),
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
                else -> Text(if (notFound) "Image not found" else "Loading…", color = BsColors.Muted)
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
private fun ActionBar(image: Photo, onInfo: () -> Unit, onChanged: (Photo) -> Unit, onDeleted: () -> Unit) {
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
        }
        Action(BsIcons.Trash, "Delete", onClick = { confirmDelete = true }, modifier = cell, loading = busy == "delete", tint = BsColors.Danger)
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
private fun InfoSheet(image: Photo, onPerson: (String) -> Unit, onDismiss: () -> Unit) {
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
                InfoField("Dimensions") { Text("${image.width} × ${image.height}", style = BsText.Sm, color = BsColors.MutedSoft) }
            }
            // Faces only exist for images; a video would sit at "Detecting…" forever
            if (!image.isVideo) {
                val faces = image.faces.orEmpty().filterNot { it.isIgnored }
                InfoField("Faces") {
                    Text(
                        if (image.faceCount == null) "Detecting…" else "${faces.size} detected",
                        style = BsText.Sm,
                        color = BsColors.MutedSoft,
                    )
                    if (faces.isNotEmpty()) {
                        // Tagged faces open the person page
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            faces.chunked(4).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { face ->
                                        FaceTile(
                                            imageSrc = face.thumbnailUrl,
                                            name = face.personName ?: "Untagged",
                                            subtitle = null,
                                            onClick = {
                                                face.personId?.let {
                                                    onDismiss()
                                                    onPerson(it)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
            image.photoPerson?.personName?.let { name ->
                InfoField("Tagged") { Text("$name (no face detected)", style = BsText.Sm, color = BsColors.MutedSoft) }
            }
            Box(Modifier.height(8.dp))
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
