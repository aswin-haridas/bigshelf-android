package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.layout.fillMaxWidth
import com.bigshelf.app.ui.components.ShinyText
import com.bigshelf.app.ui.components.TriangleLoader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import coil3.compose.AsyncImage
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
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.screens.explore.CoverCard
import com.bigshelf.app.ui.photos.photoCount
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.MonoFamily
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
 * Port of modules/organize: photos sorted into review buckets, shown as cards; a card opens its photos.
 * Select all, and on Failed, Reprocess all.
 */
@Composable
fun OrganizeScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit, onDuplicates: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val query = rememberQuery("organize", invalidatedBy = setOf("organize")) { api.organize(BUCKET_LIMIT) }
    // null: the bucket cards; picking one opens its photos
    var tab by rememberSaveable { mutableStateOf<String?>(null) }
    val closeBucket = {
        SelectionStore.clear() // fresh slate per bucket
        tab = null
    }
    BackHandler(tab != null, closeBucket)
    var reprocessing by remember { mutableStateOf(false) }

    val bucket = tab?.let { query.data?.get(it) }
    val items = bucket?.items.orEmpty()
    val allSelected = items.isNotEmpty() && items.all { SelectionStore.isSelected(it.id) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            BUCKETS.firstOrNull { it.first == tab }?.second ?: "Organize",
            count = bucket?.let { photoCount(it.count) },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = if (tab != null) closeBucket else onBack, tint = BsColors.Foreground) },
        ) {
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
                BsIconButton(
                    BsIcons.SelectionAll,
                    if (allSelected) "Clear selection" else "Select all",
                    onClick = { if (allSelected) SelectionStore.clear() else SelectionStore.selectMany(items) },
                    active = allSelected,
                )
            }
        }

        val open = tab
        when {
            query.data == null && query.isLoading -> LoadingText(text = "Sorting photos…")
            open == null -> LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                gridItems(BUCKETS, key = { it.first }) { (id, label) ->
                    val b = query.data?.get(id)
                    CoverCard(label, photoCount(b?.count ?: 0), b?.items?.firstOrNull(), onClick = { tab = id }, icon = BsIcons.Funnel)
                }
                // Duplicates lives here too, as one more card next to the buckets
                item(key = "duplicates") { CoverCard("Duplicates", null, null, onClick = onDuplicates, icon = BsIcons.Copy) }
            }
            items.isEmpty() -> EmptyState("Nothing here")
            else -> ImagesGrid(
                photos = items,
                onOpen = { onOpenPhoto(it.id) },
                totalCount = bucket?.count,
                groupByDate = false,
            )
        }
    }
}

/**
 * Port of organize/components/ugly-photos-modal.tsx: low framer scores (blurry, dark, badly
 * framed) a page at a time. Every photo starts marked for the Bin; tap to keep, long-press to open.
 */
@Composable
fun CleanUpModal(onClose: () -> Unit, onOpenPhoto: (String) -> Unit) {
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
                items.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TriangleLoader()
                    ShinyText("Scoring photos…")
                }
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
