package com.bigshelf.app.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.PaddingValues
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.MonoFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** One Month-Year section of the timeline (web `DateGroup`). */
private data class DateGroup(val key: String, val label: String, val photos: MutableList<Photo> = mutableListOf())

private val YEAR_MONTH = Regex("""^(\d{4})-(\d{2})""")

// Same grouping as useImagesGrid.getGroupInfo: "May 2024", undated photos last-seen as "Undated"
private fun groupInfo(date: String?): Pair<String, String> {
    val match = date?.let(YEAR_MONTH::find) ?: return "unknown" to "Undated"
    val (year, month) = match.destructured
    val name = Month.of(month.toInt()).getDisplayName(TextStyle.FULL, Locale.US)
    return "$year-$month" to "$name $year"
}

private fun groupByMonth(photos: List<Photo>): List<DateGroup> {
    val map = LinkedHashMap<String, DateGroup>()
    for (photo in photos) {
        val (key, label) = groupInfo(photo.takenAt ?: photo.createdAt)
        map.getOrPut(key) { DateGroup(key, label) }.photos += photo
    }
    return map.values.toList()
}

// Responsive column count from useImagesGrid (container width in dp ~ CSS px)
private fun columnsFor(widthDp: Float) = when {
    widthDp >= 1900 -> 7
    widthDp >= 1300 -> 6
    widthDp >= 950 -> 5
    widthDp >= 700 -> 4
    else -> 3
}

// Instagram Explore rhythm: a tall tile beside four squares, a row of three, the same block
// mirrored, another row of three
private val EXPLORE_PATTERN = intArrayOf(5, 3, 5, 3)

private fun exploreBlocks(photos: List<Photo>): List<List<Photo>> {
    val blocks = mutableListOf<List<Photo>>()
    var i = 0
    while (i < photos.size) {
        val n = EXPLORE_PATTERN[blocks.size % EXPLORE_PATTERN.size]
        blocks += photos.subList(i, minOf(i + n, photos.size))
        i += n
    }
    return blocks
}

fun photoCount(n: Int) = "${NumberFormat.getIntegerInstance(Locale.US).format(n)} ${if (n == 1) "photo" else "photos"}"

/**
 * Port of modules/home/components/images-grid.tsx: 3:4 tiles in responsive columns,
 * Month-Year headers (`text-xs font-semibold text-muted-soft uppercase font-mono`),
 * infinite scroll, and the "Loading…" / "No photos found" / total footer.
 *
 * Tap opens a photo, or toggles it while a selection is active (web usePhotoSelectClick).
 */
@Composable
fun ImagesGrid(
    photos: List<Photo>,
    onOpen: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    fetchNextPage: (() -> Unit)? = null,
    hasNextPage: Boolean = false,
    isFetchingNextPage: Boolean = false,
    totalCount: Int? = null,
    groupByDate: Boolean = true,
    // Square mosaic with tall tiles (Instagram Explore) instead of 3:4 tiles; never date-grouped
    explore: Boolean = false,
    // Full-width content above the photos (search matches, sub-albums), scrolling with them
    header: (@Composable () -> Unit)? = null,
) {
    // Publish this grid's photos as the current view; cleared when the grid leaves
    DisposableEffect(photos) {
        SelectionStore.visible = photos
        onDispose { SelectionStore.visible = emptyList() }
    }
    val groups = remember(photos, groupByDate, explore) { if (groupByDate && !explore) groupByMonth(photos) else emptyList() }
    val blocks = remember(photos, explore) { if (explore) exploreBlocks(photos) else emptyList() }
    val state = rememberLazyGridState()
    val loadMore by rememberUpdatedState(fetchNextPage)
    val canLoad by rememberUpdatedState(hasNextPage && !isFetchingNextPage)

    // Fetch the next page when the last few rows come into view (rootMargin: 400px)
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 12
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd && canLoad) loadMore?.invoke() }
    }
    // A page that didn't fill the screen still needs the next one
    LaunchedEffect(photos.size, hasNextPage, isFetchingNextPage) {
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (canLoad && info.totalItemsCount > 0 && last >= info.totalItemsCount - 12) loadMore?.invoke()
    }

    val selectionActive = SelectionStore.count > 0
    val open: (Photo) -> Unit = { photo ->
        if (selectionActive) SelectionStore.toggle(photo)
        else {
            ViewerList.photos = photos
            onOpen(photo)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cols = if (explore) 3 else columnsFor(maxWidth.value)
        val gap = if (maxWidth.value >= 640) 4.dp else 2.dp
        val tile = (maxWidth - gap * 2) / 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            // Fixed first item: the grid anchors on it, so pages arriving above the footer
            // don't drag the view down to "Loading…"
            item(key = "top", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth()) }
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            if (explore) {
                blocks.forEachIndexed { i, block ->
                    item(key = "block-${block.first().id}", span = { GridItemSpan(maxLineSpan) }) {
                        // Every other five-tile block puts its tall tile on the right
                        ExploreBlock(block, tallLeft = i % 4 == 0, tile, gap, selectionActive, open)
                    }
                }
            } else if (groupByDate && groups.isNotEmpty()) {
                groups.forEach { group ->
                    item(key = "header-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                        DateHeader(group.label, group.photos.size, if (selectionActive) group.photos else null)
                    }
                    items(group.photos, key = { it.id }) { photo -> Tile(photo, selectionActive, open) }
                }
            } else {
                items(photos, key = { it.id }) { photo -> Tile(photo, selectionActive, open) }
            }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                when {
                    isFetchingNextPage -> FooterText("Loading…", 16.dp)
                    photos.isEmpty() -> FooterText("No photos found", 64.dp)
                    !hasNextPage && totalCount != null -> FooterText(photoCount(totalCount), 24.dp, BsText.Xs)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    photo: Photo,
    selectionActive: Boolean,
    open: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = 3f / 4f,
) {
    ImageCard(
        photo = photo,
        selected = SelectionStore.isSelected(photo.id),
        selectionActive = selectionActive,
        onClick = { open(photo) },
        onToggle = { SelectionStore.toggle(photo) },
        modifier = modifier,
        aspectRatio = aspectRatio,
    )
}

/** One explore block: a tall tile beside a 2x2 of squares, or plain rows of three squares. */
@Composable
private fun ExploreBlock(
    photos: List<Photo>,
    tallLeft: Boolean,
    tile: Dp,
    gap: Dp,
    selectionActive: Boolean,
    open: (Photo) -> Unit,
) {
    @Composable
    fun Square(photo: Photo) = Tile(photo, selectionActive, open, Modifier.size(tile), aspectRatio = null)

    if (photos.size < 5) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            photos.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { Square(it) } }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        val tall = @Composable {
            Tile(photos[0], selectionActive, open, Modifier.size(tile, tile * 2 + gap), aspectRatio = null)
        }
        if (tallLeft) tall()
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            photos.drop(1).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { Square(it) } }
            }
        }
        if (!tallLeft) tall()
    }
}

@Composable
private fun DateHeader(label: String, count: Int, selectable: List<Photo>?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .background(BsColors.Background.copy(alpha = 0.85f))
            .drawBehind {
                drawLine(BsColors.Border.copy(alpha = 0.4f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // While selecting, a tap on the month's circle picks (or drops) the whole month
        if (selectable != null) {
            val all = selectable.all { SelectionStore.isSelected(it.id) }
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(24.dp)
                    .clip(BsShapes.Full)
                    .background(if (all) BsColors.Primary else Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, BsColors.Muted.copy(alpha = 0.7f), BsShapes.Full)
                    .bsClickable {
                        if (all) selectable.forEach { if (SelectionStore.isSelected(it.id)) SelectionStore.toggle(it) }
                        else SelectionStore.selectMany(selectable)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (all) BsIcon(BsIcons.CheckBold, size = 14.dp, tint = BsColors.Foreground, contentDescription = "Deselect $label")
            }
        }
        Text(
            label.uppercase(Locale.US),
            Modifier.weight(1f),
            style = BsText.Xs.copy(
                fontWeight = BsWeight.Semibold,
                fontFamily = MonoFamily,
                letterSpacing = BsText.TrackingWider,
            ),
            color = BsColors.MutedSoft,
        )
        Text(
            "$count ${if (count == 1) "photo" else "photos"}",
            style = BsText.Xs2.copy(fontFamily = MonoFamily),
            color = BsColors.MutedFaint,
        )
    }
}

@Composable
private fun FooterText(text: String, vertical: androidx.compose.ui.unit.Dp, style: androidx.compose.ui.text.TextStyle = BsText.Sm) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = vertical),
        style = style,
        color = BsColors.Muted,
        textAlign = TextAlign.Center,
    )
}

