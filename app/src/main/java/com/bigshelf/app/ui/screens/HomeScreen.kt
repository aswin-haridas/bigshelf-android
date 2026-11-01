package com.bigshelf.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.data.PhotoPersonTag
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// A post holds the photos taken within this long of its first (newest) one, up to MAX_SLIDES
private val POST_WINDOW: Duration = Duration.ofHours(1)
private const val MAX_SLIDES = 20

private fun takenAt(photo: Photo) = photo.takenAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

/** A feed post; [burst] when its photos are one burst (backend `/photos/bursts`). */
internal class FeedPost(val photos: MutableList<Photo>, val burst: Boolean)

/**
 * The feed's posts: the library (newest capture first) cut into runs of photos taken within
 * an hour of the run's first photo, except that each burst ([burstOf]: photo id -> burst id)
 * gets a post of its own. Pages only ever append older photos, so a post's key (its first
 * photo) stays put as the feed grows.
 */
internal fun groupIntoPosts(photos: List<Photo>, burstOf: Map<String, String> = emptyMap()): List<FeedPost> {
    val posts = mutableListOf<FeedPost>()
    var start: OffsetDateTime? = null
    var startBurst: String? = null
    for (photo in photos) {
        val t = takenAt(photo)
        val burst = burstOf[photo.id]
        val current = posts.lastOrNull()
        val joins = current != null && current.photos.size < MAX_SLIDES && if (burst != null) {
            burst == startBurst
        } else {
            !current.burst && t != null && start != null && Duration.between(t, start) <= POST_WINDOW
        }
        if (joins) {
            current!!.photos += photo
        } else {
            posts += FeedPost(mutableListOf(photo), burst != null)
            start = t
            startBurst = burst
        }
    }
    return posts
}

/**
 * Photos tab laid out like the Instagram home feed: search bar and profile badge on top,
 * then one post per hour of photos - who's in them, when, and the photos as swipeable
 * slides. A text search swaps the feed for a grid of ranked results.
 */
@Composable
fun HomeScreen(onOpenPhoto: (String) -> Unit, onProfile: () -> Unit) {
    val api = BigShelfApp.api
    val showAi = BigShelfApp.settings.showAi
    var search by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    // Debounced 300ms, like the web search box
    LaunchedEffect(search) {
        delay(300)
        q = search.trim()
    }

    val feed = rememberPagedPhotos("home", invalidatedBy = setOf("photos")) { page, cursor -> api.photos(page, cursor = cursor, people = true) }
    val textSearch = rememberQuery("search", q, invalidatedBy = setOf("photos")) {
        if (q.isEmpty()) emptyList() else api.searchPhotos(q)
    }
    // Burst membership comes from the server: its rule needs scene embeddings the app never loads.
    // ponytail: pages the whole bursts list (30 a call, each a full server pass); add an ids-only endpoint if that gets slow
    val bursts = rememberQuery("home-bursts", invalidatedBy = setOf("photos")) {
        buildMap {
            var offset = 0
            do {
                val page = api.bursts(offset = offset, limit = 30)
                page.groups.forEach { g -> g.items.forEach { put(it.id, g.id) } }
                offset += page.groups.size
            } while (page.groups.isNotEmpty() && offset < page.total)
        }
    }
    val filter: (List<Photo>) -> List<Photo> = { all -> if (showAi) all else all.filterNot { it.isAiImage } }
    val results = filter(textSearch.data.orEmpty())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BsSearchBar(search, { search = it }, Modifier.weight(1f), placeholder = "Search photos")
            // The signed-in user's initial; opens Settings (upload lives in Library and the share sheet)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(BsShapes.Full)
                    .background(BsColors.Surface)
                    .bsClickable(onClick = onProfile)
                    .semantics { contentDescription = "Profile and settings" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    AuthStore.user?.username?.take(1)?.uppercase().orEmpty(),
                    style = BsText.Sm.copy(fontWeight = BsWeight.Semibold),
                )
            }
        }

        if (q.isNotEmpty()) {
            // Ranked by relevance, so a grid rather than time-grouped posts
            ImagesGrid(
                photos = results,
                onOpen = { onOpenPhoto(it.id) },
                isFetchingNextPage = textSearch.isLoading,
                totalCount = if (textSearch.isLoading) null else results.size,
                explore = true,
            )
        } else {
            val posts = remember(feed.photos, showAi, bursts.data) {
                groupIntoPosts(filter(feed.photos), bursts.data.orEmpty())
            }
            val listState = rememberLazyListState()
            // Load the next page while the last couple of posts are still off screen
            LaunchedEffect(listState, posts.size) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .collect { if (it >= posts.size - 3) feed.fetchNextPage() }
            }
            when {
                posts.isEmpty() && feed.isFetching -> LoadingText()
                posts.isEmpty() -> EmptyState("No photos yet", description = "Upload some from Library")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(posts, key = { it.photos.first().id }) { post -> Post(post.photos, post.burst, onOpenPhoto) }
                }
            }
        }
    }
}

/** One post: people + date header, the photos as slides with a counter and dots shown while swiping. */
@Composable
private fun Post(photos: List<Photo>, burst: Boolean, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val people = photos.flatMap { it.people.orEmpty() }.distinctBy { it.id }
    val pager = rememberPagerState { photos.size }

    Column {
        PostHeader(people, photos.first(), if (burst) "Burst · ${photos.size} shots" else null)
        Box {
            HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(3f / 4f), key = { photos[it].id }) { i ->
                val photo = photos[i]
                Box(Modifier.fillMaxSize().background(BsColors.Surface).bsClickable(scale = 1f) {
                    // A burst opens on its own shots, so the viewer swipes through just those
                    if (burst) ViewerList.photos = photos
                    onOpenPhoto(photo.id)
                }) {
                    AsyncImage(
                        api.mediaUrl(photo.thumbnailUrl ?: photo.url ?: ""),
                        photo.title ?: photo.filename,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (photo.isVideo) {
                        BsIcon(BsIcons.Play, Modifier.align(Alignment.Center), size = 32.dp, tint = BsColors.Foreground)
                    }
                }
            }
            if (photos.size > 1) {
                // Counter and dots sit on the photo and only show while swiping, fading out after
                var showDots by remember { mutableStateOf(false) }
                LaunchedEffect(pager.isScrollInProgress, pager.currentPage) {
                    if (pager.isScrollInProgress) showDots = true
                    else {
                        delay(1500)
                        showDots = false
                    }
                }
                val dotsAlpha by animateFloatAsState(if (showDots) 1f else 0f, label = "dots")
                Text(
                    "${pager.currentPage + 1}/${photos.size}",
                    Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { alpha = dotsAlpha }
                        .padding(12.dp)
                        .clip(BsShapes.Full)
                        .background(BsColors.Background.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = BsText.Xs2,
                    color = BsColors.Foreground,
                )
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = dotsAlpha }
                        .padding(12.dp)
                        .clip(BsShapes.Full)
                        .background(BsColors.Background.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(photos.size) { i ->
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(BsShapes.Full)
                                .background(if (i == pager.currentPage) BsColors.Foreground else BsColors.Foreground.copy(alpha = 0.4f)),
                        )
                    }
                }
            }
        }
    }
}

/** Avatars and names of everyone tagged in the post (or the user's own name), then when. */
@Composable
private fun PostHeader(people: List<PhotoPersonTag>, first: Photo, label: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            if (people.isEmpty()) {
                Avatar(null, AuthStore.user?.username)
            } else {
                people.take(3).forEach { Avatar(it.faceThumbnail, it.name) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    people.isEmpty() -> AuthStore.user?.username ?: "You"
                    people.size <= 2 -> people.joinToString(" and ") { it.name }
                    else -> "${people[0].name}, ${people[1].name} and ${people.size - 2} more"
                },
                style = BsText.Xs.copy(fontWeight = BsWeight.Semibold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            takenAt(first)?.let {
                val time = it.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a"))
                Text(
                    listOfNotNull(time, label).joinToString(" · "),
                    style = BsText.Xs2,
                    color = BsColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun Avatar(src: String?, name: String?) {
    Box(
        Modifier
            .size(32.dp)
            .clip(BsShapes.Full)
            .border(2.dp, BsColors.Background, BsShapes.Full)
            .background(BsColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (src != null) {
            AsyncImage(BigShelfApp.api.mediaUrl(src), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name?.take(1)?.uppercase().orEmpty(), style = BsText.Xs2.copy(fontWeight = BsWeight.Semibold))
        }
    }
}
