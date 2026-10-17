package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import coil3.compose.AsyncImage
import com.bigshelf.app.data.Downloads
import com.bigshelf.app.data.UglyPage
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.photos.photoCount
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.MonoFamily
import com.bigshelf.app.ui.theme.Neutral
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Kept in sync with the backend's organize bucket cap (web MAX_ZIP_PHOTOS)
private const val BUCKET_LIMIT = 300

// Same buckets and labels as modules/organize/organize.tsx
private val BUCKETS = listOf(
    "multi_face" to "Multi-face",
    "no_face" to "No faces",
    "person" to "Has people",
    "non_person" to "Non-person",
    "non_34" to "Not 3:4",
    "low_res" to "Low resolution",
    "collage" to "Collages",
    "screenshot" to "Screenshots",
    "failed" to "Failed",
    "videos" to "Videos",
)

/**
 * Port of modules/organize: photos sorted into review buckets, picked from a row of chips.
 * Select all, Download all, and on Failed, Reprocess all.
 */
@Composable
fun OrganizeScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val query = rememberQuery("organize", invalidatedBy = setOf("organize")) { api.organize(BUCKET_LIMIT) }
    var tab by rememberSaveable { mutableStateOf(BUCKETS.first().first) }
    var downloading by remember { mutableStateOf(false) }
    var reprocessing by remember { mutableStateOf(false) }
    // "Clean up" review of low-scoring photos, across the whole library (not the tab)
    var cleaningUp by remember { mutableStateOf(false) }

    val bucket = query.data?.get(tab)
    val items = bucket?.items.orEmpty()
    val allSelected = items.isNotEmpty() && items.all { SelectionStore.isSelected(it.id) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Organize",
            count = bucket?.let { photoCount(it.count) },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            BsIconButton(BsIcons.Sparkle, "Clean up", onClick = { cleaningUp = true })
            if (items.isNotEmpty()) {
                if (tab == "failed") {
                    // Pushes each photo to the front of the pipeline queue
                    BsIconButton(BsIcons.ArrowClockwise, "Reprocess all", loading = reprocessing, onClick = {
                        reprocessing = true
                        scope.launch {
                            try {
                                coroutineScope { items.map { async { api.prioritize(it.id) } }.awaitAll() }
                                toast.success("Re-queued ${items.size} photos for processing")
                                PhotoEvents.invalidate("organize")
                            } catch (_: Exception) {
                                toast.error("Failed to reprocess some photos")
                            } finally {
                                reprocessing = false
                            }
                        }
                    })
                }
                BsIconButton(BsIcons.DownloadSimple, "Download all", loading = downloading, onClick = {
                    downloading = true
                    scope.launch {
                        try {
                            Downloads.save(context, items)
                            toast.success("Downloading ${items.size} photos")
                        } catch (_: Exception) {
                            toast.error("Failed to download")
                        } finally {
                            downloading = false
                        }
                    }
                })
                BsIconButton(
                    BsIcons.SelectionAll,
                    if (allSelected) "Clear selection" else "Select all",
                    onClick = { if (allSelected) SelectionStore.clear() else SelectionStore.selectMany(items) },
                    active = allSelected,
                )
            }
        }

        LazyRow(
            Modifier.padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BUCKETS, key = { it.first }) { (id, label) ->
                val selected = id == tab
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(BsShapes.Full)
                        .background(if (selected) Neutral.N800 else BsColors.Surface)
                        .border(1.dp, if (selected) BsColors.BorderFocus else BsColors.Border, BsShapes.Full)
                        .bsClickable(scale = 0.96f) {
                            // Fresh slate per bucket
                            if (id != tab) SelectionStore.clear()
                            tab = id
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(label, style = BsText.Xs.copy(fontWeight = BsWeight.Medium), color = if (selected) BsColors.Foreground else BsColors.Muted)
                    Text(
                        "${query.data?.get(id)?.count ?: 0}",
                        style = BsText.Xs2.copy(fontFamily = MonoFamily),
                        color = if (selected) BsColors.MutedSoft else BsColors.MutedFaint,
                    )
                }
            }
        }

        when {
            query.data == null && query.isLoading -> LoadingText(text = "Sorting photos…")
            items.isEmpty() -> EmptyState("Nothing here")
            else -> ImagesGrid(
                photos = items,
                onOpen = { onOpenPhoto(it.id) },
                totalCount = bucket?.count,
                groupByDate = false,
            )
        }
    }
    if (cleaningUp) CleanUpModal(onClose = { cleaningUp = false }, onOpenPhoto = onOpenPhoto)
}

/**
 * Port of organize/components/ugly-photos-modal.tsx: low framer scores (blurry, dark, badly
 * framed) a page at a time. Every photo starts marked for the Bin; tap to keep, long-press to open.
 */
@Composable
private fun CleanUpModal(onClose: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    // ponytail: cursor lives only while the modal is open, so photos kept earlier come back next time
    var before by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<UglyPage?>(null) }
    var kept by remember { mutableStateOf(setOf<String>()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(before) {
        page = null
        page = try {
            api.uglyPhotos(before).also {
                // A page with nothing ugly on it: skip straight to the next one
                if (it.items.isEmpty() && it.nextBefore != null) before = it.nextBefore
            }
        } catch (_: Exception) {
            toast.error("Failed to load suggestions")
            onClose()
            null
        }
    }

    val items = page?.items.orEmpty()
    val toBin = items.filter { it.id !in kept }
    val allKept = items.isNotEmpty() && toBin.isEmpty()
    val done = page != null && items.isEmpty() && page?.nextBefore == null

    BsModal(
        "Bin these photos?",
        onClose = onClose,
        footer = {
            BsButton(onClose, variant = ButtonVariant.Ghost) { Text("Later") }
            BsButton(
                { kept = if (allKept) emptySet() else items.map { it.id }.toSet() },
                variant = ButtonVariant.Secondary,
                enabled = !saving && items.isNotEmpty(),
            ) { Text(if (allKept) "Bin all" else "Keep all") }
            BsButton(
                {
                    saving = true
                    scope.launch {
                        try {
                            if (toBin.isNotEmpty()) {
                                api.flagDeleted(toBin.map { it.id }, true)
                                toast.success("${toBin.size} photo${if (toBin.size > 1) "s" else ""} moved to Bin")
                                PhotoEvents.invalidateAll()
                            }
                            kept = emptySet()
                            page?.nextBefore?.let { before = it } ?: onClose()
                        } catch (_: Exception) {
                            toast.error("Failed to move photos to Bin")
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && items.isNotEmpty(),
                loading = saving,
            ) { Text(if (toBin.isNotEmpty()) "Bin ${toBin.size}" else "Next") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Blurry, dark or badly framed. Tap a photo to keep it.", style = BsText.Xs, color = BsColors.Muted)
            when {
                done -> EmptyState("No more photos to suggest")
                // Scoring reads every thumbnail of the page, so this can take a few seconds
                items.isEmpty() -> LoadingText(text = "Scoring photos…")
                else -> LazyVerticalGrid(
                    GridCells.Fixed(3),
                    Modifier.heightIn(max = 480.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(items, key = { it.id }) { photo ->
                        val isKept = photo.id in kept
                        Box(
                            Modifier
                                .aspectRatio(3f / 4f)
                                .clip(BsShapes.Lg)
                                .border(2.dp, if (isKept) BsColors.Success else BsColors.Danger, BsShapes.Lg)
                                .combinedClickable(
                                    // The viewer opens behind this dialog, so close it first
                                    onLongClick = { onClose(); onOpenPhoto(photo.id) },
                                ) { kept = if (isKept) kept - photo.id else kept + photo.id },
                        ) {
                            AsyncImage(
                                api.mediaUrl(photo.thumbnailUrl ?: photo.imageSrc ?: ""),
                                photo.filename,
                                Modifier.fillMaxSize().alpha(if (isKept) 1f else 0.6f),
                                contentScale = ContentScale.Crop,
                            )
                            // Framer score, so it's clear why it was suggested
                            photo.quality?.let {
                                Text(
                                    "${it.score.toInt()}",
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(BsShapes.Md)
                                        .background(BsColors.Background.copy(alpha = 0.6f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = BsText.Xs2.copy(fontFamily = MonoFamily),
                                    color = BsColors.Foreground,
                                )
                            }
                            BsIcon(
                                if (isKept) BsIcons.CheckCircleFill else BsIcons.Trash,
                                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                size = 20.dp,
                                tint = if (isKept) BsColors.Success else BsColors.Danger,
                            )
                        }
                    }
                }
            }
        }
    }
}
