package com.bigshelf.app.ui.screens.explore

import androidx.compose.foundation.background
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Burst
import com.bigshelf.app.data.LibraryEvent
import com.bigshelf.app.data.PhotoFilter
import com.bigshelf.app.data.PlaceNames
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.ConfirmModal
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
import kotlinx.coroutines.launch

/** "Trip to Goa, Mar 1 – 3, 2026": an event's album title, unique by its dates. */
private fun eventAlbumTitle(event: LibraryEvent, place: String?): String {
    val title = eventTitle(event, place)
    val dated = event.occasion != null || event.isTrip || place != null
    return if (dated) "$title, ${dateRange(event.start, event.end)}" else title
}

private fun eventFilter(event: LibraryEvent) = PhotoFilter(takenFrom = event.start, takenTo = event.end)

@Composable
fun EventCard(event: LibraryEvent, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val place = rememberPlaceName(event.latitude, event.longitude)
    CoverCard(
        eventTitle(event, place),
        "${dateRange(event.start, event.end)} · ${event.count}",
        event.cover,
        onClick = { onNavigate(Routes.filtered(eventAlbumTitle(event, place), eventFilter(event))) },
        modifier,
        icon = if (event.isTrip) BsIcons.AirplaneTilt else BsIcons.CalendarBlank,
    )
}

/**
 * Events and trips, newest first, paged as you scroll. "Save all" turns every event into an
 * album (skipping ones already saved under the same title).
 */
@Composable
fun EventsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val api = BigShelfApp.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<LibraryEvent>>(emptyList()) }
    var total by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val page = api.events(offset = if (reset) 0 else items.size, limit = 30)
                items = if (reset) page.items else items + page.items
                total = page.total
            } catch (_: Exception) {
                if (reset) toast.error("Failed to load events")
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) {
        load(true)
        PhotoEvents.invalidations.collect { if ("photos" in it) load(true) }
    }

    // Every event becomes an album (photos captured between its first and last shot)
    fun saveAll() {
        saving = true
        scope.launch {
            try {
                val all = mutableListOf<LibraryEvent>()
                do {
                    val page = api.events(offset = all.size, limit = 100)
                    all += page.items
                } while (page.items.isNotEmpty() && all.size < page.total)
                val existing = api.albums().map { it.title }.toMutableSet()
                var created = 0
                for (event in all) {
                    val place = event.latitude?.let { lat -> event.longitude?.let { PlaceNames.name(context, lat, it) } }
                    val title = eventAlbumTitle(event, place)
                    if (!existing.add(title)) continue
                    api.createAlbum(title, filter = eventFilter(event))
                    created++
                }
                PhotoEvents.invalidate("albums")
                toast.success(if (created == 0) "Every event already has an album" else "Created ${countLabel(created, "album")}")
            } catch (_: Exception) {
                toast.error("Failed to create some albums")
                PhotoEvents.invalidate("albums")
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Events & trips",
            count = total?.let { countLabel(it, "event") },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            if (items.isNotEmpty()) {
                BsButton(onClick = { confirmSave = true }, variant = ButtonVariant.Secondary, loading = saving, textStyle = BsText.Xs) {
                    Text("Save all as albums")
                }
            }
        }
        when {
            items.isEmpty() && (loading || total == null) -> LoadingText(text = "Grouping your photos…")
            items.isEmpty() -> EmptyState("No events yet")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(items, key = { _, e -> e.id }) { i, event ->
                    // Next page when the last few cards come into view
                    if (i >= items.size - 4 && items.size < (total ?: 0)) LaunchedEffect(items.size) { load(false) }
                    EventCard(event, onNavigate)
                }
            }
        }
    }

    if (confirmSave) {
        ConfirmModal(
            title = "Save events as albums",
            message = "Create an album for each of the ${total ?: items.size} events? Events that already have one are skipped.",
            confirmLabel = "Create albums",
            onConfirm = ::saveAll,
            onClose = { confirmSave = false },
        )
    }
}

/**
 * Photos matching a smart filter: a place, a map cluster, an event or date range, or the
 * unorganized ones. Date and place filters can be saved as an album that fills itself.
 */
@Composable
fun FilteredPhotosScreen(title: String, filter: PhotoFilter, onBack: () -> Unit, onOpenPhoto: (String) -> Unit, onNavigate: (String) -> Unit) {
    val api = BigShelfApp.api
    val feed = rememberPagedPhotos("filtered", filter, invalidatedBy = setOf("photos", "albums")) { page, cursor -> api.photos(page, filter = filter, cursor = cursor) }
    var saving by remember { mutableStateOf(false) }
    val allSelected = feed.photos.isNotEmpty() && feed.photos.all { SelectionStore.isSelected(it.id) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title,
            count = feed.total?.let(::photoCount),
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            if (feed.photos.isNotEmpty()) {
                BsIconButton(
                    BsIcons.SelectionAll,
                    if (allSelected) "Clear selection" else "Select all",
                    onClick = { if (allSelected) SelectionStore.clear() else SelectionStore.selectMany(feed.photos) },
                    active = allSelected,
                )
                // Unorganized has no album rule: select and add them instead
                if (!filter.unorganized) BsIconButton(BsIcons.FolderSimplePlus, "Save as album", onClick = { saving = true })
            }
        }
        ImagesGrid(
            photos = feed.photos,
            onOpen = { onOpenPhoto(it.id) },
            fetchNextPage = feed::fetchNextPage,
            hasNextPage = feed.hasNextPage,
            isFetchingNextPage = feed.isFetching,
            totalCount = feed.total,
        )
    }

    if (saving) {
        TextPromptModal(title = "Save as album", initial = title, confirmLabel = "Create album", onClose = { saving = false }) { name ->
            val album = api.createAlbum(name, filter = filter)
            PhotoEvents.invalidate("albums")
            toast.success("Album created with ${photoCount(album.photoCount)}")
            onNavigate(Routes.album(album.id))
        }
    }
}

/** Photos of one colour chip, the most of it first. */
@Composable
fun ColorScreen(color: String, onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val feed = rememberPagedPhotos("color", color, invalidatedBy = setOf("photos")) { page, _ -> api.photosByColor(color, page) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            colorLabel(color),
            count = feed.total?.let(::photoCount),
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        )
        ImagesGrid(
            photos = feed.photos,
            onOpen = { onOpenPhoto(it.id) },
            fetchNextPage = feed::fetchNextPage,
            hasNextPage = feed.hasNextPage,
            isFetchingNextPage = feed.isFetching,
            totalCount = feed.total,
            // Ranked by how much of the colour, not by date
            groupByDate = false,
        )
    }
}

/** Photos carrying one auto tag ("Things"), newest first. */
@Composable
fun TagScreen(tag: String, onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val feed = rememberPagedPhotos("tag", tag, invalidatedBy = setOf("photos")) { page, _ -> api.photosByTag(tag, page) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            tag.replaceFirstChar { it.uppercase() },
            count = feed.total?.let(::photoCount),
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        )
        ImagesGrid(
            photos = feed.photos,
            onOpen = { onOpenPhoto(it.id) },
            fetchNextPage = feed::fetchNextPage,
            hasNextPage = feed.hasNextPage,
            isFetchingNextPage = feed.isFetching,
            totalCount = feed.total,
        )
    }
}

/**
 * Bursts and runs of similar shots, newest first, with the best one marked (sharpness,
 * faces, framing, eyes open and facing the camera - the Duplicates scorer). "Keep best" bins the rest, with Undo.
 */
@Composable
fun BurstsScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<Burst>>(emptyList()) }
    var total by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }

    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val page = api.bursts(offset = if (reset) 0 else groups.size, limit = 10)
                groups = if (reset) page.groups else groups + page.groups.filter { g -> groups.none { it.id == g.id } }
                total = page.total
            } catch (_: Exception) {
                if (reset) toast.error("Failed to load bursts")
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { load(true) }

    fun keepBest(burst: Burst) {
        val best = burst.items.firstOrNull { it.quality?.best == true } ?: burst.items.first()
        val rest = burst.items.filter { it.id != best.id }.map { it.id }
        busy = burst.id
        scope.launch {
            try {
                api.flagDeleted(rest, true)
                groups = groups.filterNot { it.id == burst.id }
                total = total?.minus(1)
                PhotoEvents.invalidateAll()
                toast.success("${photoCount(rest.size)} moved to Bin", "Undo") {
                    scope.launch {
                        runCatching { api.flagDeleted(rest, false) }
                        PhotoEvents.invalidateAll()
                        load(true)
                    }
                }
            } catch (_: Exception) {
                toast.error("Failed to move photos to Bin")
            } finally {
                busy = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Best shots",
            count = total?.let { countLabel(it, "burst") },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        )
        when {
            groups.isEmpty() && (loading || total == null) -> LoadingText(text = "Scoring similar shots…")
            groups.isEmpty() -> EmptyState("No bursts")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                itemsIndexed(groups, key = { _, g -> g.id }) { i, burst ->
                    if (i >= groups.size - 3 && groups.size < (total ?: 0)) LaunchedEffect(groups.size) { load(false) }
                    BurstRow(burst, busy == burst.id, onKeepBest = { keepBest(burst) }, onOpen = { id ->
                        ViewerList.photos = burst.items
                        onOpenPhoto(id)
                    })
                }
            }
        }
    }
}

@Composable
private fun BurstRow(burst: Burst, busy: Boolean, onKeepBest: () -> Unit, onOpen: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(countLabel(burst.items.size, "shot"), style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
                burst.items.firstOrNull()?.let { first ->
                    Text(dateRange(first.takenAt ?: "", first.takenAt ?: ""), style = BsText.Xs, color = BsColors.Muted)
                }
            }
            BsButton(onClick = onKeepBest, variant = ButtonVariant.Secondary, loading = busy, textStyle = BsText.Xs) { Text("Keep best") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(burst.items, key = { it.id }) { photo ->
                val best = photo.quality?.best == true
                Box(
                    Modifier
                        .width(108.dp)
                        .aspectRatio(3f / 4f)
                        .clip(BsShapes.Md)
                        .then(if (best) Modifier.border(2.dp, BsColors.Primary, BsShapes.Md) else Modifier)
                        .bsClickable(scale = 0.97f) { onOpen(photo.id) },
                ) {
                    AsyncImage(
                        model = BigShelfApp.api.mediaUrl(photo.thumbnailUrl ?: photo.imageSrc),
                        contentDescription = photo.title ?: photo.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    photo.quality?.let { q ->
                        Text(
                            if (best) "Best" else "${q.score.toInt()}",
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(BsShapes.Full)
                                .background(if (best) BsColors.Primary else BsColors.Background.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            style = BsText.Xs2.copy(fontFamily = if (best) null else MonoFamily, fontWeight = BsWeight.Medium),
                            color = BsColors.Foreground,
                        )
                    }
                }
            }
        }
    }
}
