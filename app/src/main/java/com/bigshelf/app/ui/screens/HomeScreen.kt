package com.bigshelf.app.ui.screens

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
import com.bigshelf.app.state.PagedPhotos
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsTabs
import com.bigshelf.app.ui.components.TabItem
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.LoadError
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_SLIDES = 20

private fun takenAt(photo: Photo) = photo.takenAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

internal class FeedPost(val photos: MutableList<Photo>, val burst: Boolean)

/** Only the same tagged person within the same server burst shares a post. */
internal fun groupIntoPosts(photos: List<Photo>, burstOf: Map<String, String> = emptyMap()): List<FeedPost> {
    val posts = mutableListOf<FeedPost>()
    var startBurst: String? = null
    var startPerson: String? = null
    for (photo in photos) {
        val burst = burstOf[photo.id]
        val people = photo.people.orEmpty().distinctBy { it.id }
        val person = people.singleOrNull()?.id
        val current = posts.lastOrNull()
        val joins = current != null && current.photos.size < MAX_SLIDES &&
            burst != null && burst == startBurst && person == startPerson && people.size <= 1
        if (joins) {
            current!!.photos += photo
        } else {
            posts += FeedPost(mutableListOf(photo), burst != null)
        }
        // Group photos remain standalone; the next portrait must start a fresh post too.
        startBurst = if (people.size <= 1) burst else null
        startPerson = person
    }
    return posts
}

/**
 * Photo timeline with an optional swipeable burst feed and ranked search results.
 */
@Composable
fun HomeScreen(onOpenPhoto: (String) -> Unit, onProfile: () -> Unit) {
    val api = BigShelfApp.api
    val settings = BigShelfApp.settings
    val showAi = settings.showAi
    var search by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    // Debounced 300ms, like the web search box
    LaunchedEffect(search) {
        delay(300)
        q = search.trim()
    }

    val feed = rememberPagedPhotos("home", invalidatedBy = setOf("photos")) { page, cursor -> api.photos(page, cursor = cursor, people = true, sort = "uploaded") }
    val textSearch = rememberQuery("search", q, invalidatedBy = setOf("photos")) {
        if (q.isEmpty()) emptyList() else api.searchPhotos(q)
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
                isError = textSearch.isError,
                onRetry = textSearch::refetch,
                totalCount = if (textSearch.isLoading) null else results.size,
                explore = true,
            )
        } else {
            BsTabs(
                listOf(TabItem(false, "All photos"), TabItem(true, "Feed")),
                settings.photoFeed,
                settings::updatePhotoFeed,
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            if (settings.photoFeed) {
                PhotoFeed(feed, showAi, onOpenPhoto)
            } else {
                val photos = remember(feed.photos, showAi) { filter(feed.photos) }
                ImagesGrid(
                    photos = photos,
                    onOpen = { onOpenPhoto(it.id) },
                    fetchNextPage = feed::fetchNextPage,
                    hasNextPage = feed.hasNextPage,
                    isFetchingNextPage = feed.isFetching,
                    isError = feed.isError,
                    onRetry = feed::retry,
                    totalCount = if (showAi) feed.total else null,
                )
            }
        }
    }
}

/** Burst discovery is only needed in Feed mode, never for the photo timeline or search. */
@Composable
private fun PhotoFeed(feed: PagedPhotos, showAi: Boolean, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    // Burst membership comes from the server: its rule needs scene embeddings the app never loads.
    // shortcut: Feed pages all burst groups; use an ids-only endpoint if burst discovery becomes slow.
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
    val posts = remember(feed.photos, showAi, bursts.data) {
        val photos = if (showAi) feed.photos else feed.photos.filterNot { it.isAiImage }
        groupIntoPosts(photos, bursts.data.orEmpty())
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, posts.size, feed.isFetching, feed.hasNextPage, feed.isError) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { if (!feed.isError && it >= posts.size - 3) feed.fetchNextPage() }
    }
    when {
        posts.isEmpty() && feed.isFetching -> LoadingText()
        posts.isEmpty() && feed.isError -> LoadError(feed::retry)
        posts.isEmpty() -> EmptyState("No photos yet", description = "Tap Upload to add photos")
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(posts, key = { it.photos.first().id }, contentType = { "post" }) { post ->
                Post(post.photos, post.burst, onOpenPhoto)
            }
            if (feed.isError) item { LoadError(feed::retry) }
            else if (feed.isFetching) item { Text("Loading…", Modifier.padding(16.dp), style = BsText.Sm, color = BsColors.Muted) }
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
        PostHeader(people, photos.first(), if (burst && photos.size > 1) "Burst · ${photos.size} shots" else null)
        Box {
            HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(3f / 4f), key = { photos[it].id }) { i ->
                val photo = photos[i]
                Box(Modifier.fillMaxSize().background(BsColors.Surface).bsClickable(scale = 1f) {
                    // The viewer always follows the post that was opened
                    ViewerList.photos = photos
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
                Text(
                    "${pager.currentPage + 1}/${photos.size}",
                    Modifier
                        .align(Alignment.TopEnd)
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
