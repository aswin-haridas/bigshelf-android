package com.bigshelf.app.ui.screens.explore

import android.net.Uri
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.theme.BsShapes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.PhotoFilter
import com.bigshelf.app.data.PlaceNames
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.photos.photoCount
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsText
import kotlinx.coroutines.delay

private val CARD = 136.dp

/**
 * The Search tab. With no query it's the way into the library's groupings: albums, places
 * (and the map), events and trips, colours, then best shots and unorganized photos. Typing
 * searches people, albums, places and colours by name, and photos by content (MobileCLIP),
 * file name and tagged person.
 */
@Composable
fun SearchScreen(onNavigate: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    // Debounced 300ms, like Home's search box
    LaunchedEffect(search) {
        delay(300)
        q = search.trim()
    }
    // Reverse image search: the picked photo's look-alikes replace the text results
    var image by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            search = "" // image and text queries are exclusive
            image = uri
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Search") {
            BsIconButton(
                BsIcons.Image,
                "Search by image",
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                active = image != null,
            )
        }
        BsSearchBar(
            search,
            {
                search = it
                image = null // typing switches back to text search
            },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            placeholder = "People, places, things, colours…",
        )
        val searching = image
        when {
            searching != null -> ImageResults(searching, onClear = { image = null }, onOpenPhoto)
            q.isEmpty() -> Explore(onNavigate)
            else -> Results(q, onNavigate, onOpenPhoto)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Explore(onNavigate: (String) -> Unit) {
    val api = BigShelfApp.api
    val context = LocalContext.current
    val albums = rememberQuery("albums-top", invalidatedBy = setOf("albums", "photos")) { api.albums(topLevel = true) }
    val places = rememberQuery("places", invalidatedBy = setOf("photos")) { PlaceNames.places(context) }
    val events = rememberQuery("events-preview", invalidatedBy = setOf("photos")) { api.events(limit = 12) }
    val bursts = rememberQuery("bursts-count", invalidatedBy = setOf("photos")) { api.bursts(limit = 1).total }
    val unorganized = rememberQuery("unorganized-count", invalidatedBy = setOf("photos", "albums")) {
        api.photos(1, size = 1, filter = PhotoFilter(unorganized = true)).total
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    val row = PaddingValues(horizontal = 16.dp)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "albums") {
            SectionHeader("Albums", actions = listOf("See all" to { onNavigate(Routes.ALBUMS) }))
            LazyRow(contentPadding = row, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "new") {
                    CoverCard("New album", null, null, onClick = { creating = true }, Modifier.width(CARD), icon = BsIcons.Plus)
                }
                items(albums.data.orEmpty(), key = { it.id }) { album ->
                    CoverCard(
                        album.title,
                        albumSubtitle(album),
                        album.coverPhoto,
                        onClick = { onNavigate(Routes.album(album.id)) },
                        Modifier.width(CARD),
                        icon = BsIcons.Stack,
                    )
                }
            }
        }
        item(key = "places") {
            SectionHeader(
                "Places",
                actions = listOf("Map" to { onNavigate(Routes.map()) }, "See all" to { onNavigate(Routes.PLACES) }),
            )
            val list = places.data.orEmpty()
            if (list.isEmpty()) {
                Hint(if (places.isLoading) "Finding places…" else "No places yet")
            } else {
                LazyRow(contentPadding = row, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list.take(15), key = { "${it.name}-${it.latitude}" }) { place ->
                        CoverCard(
                            place.name,
                            photoCount(place.count),
                            place.cover,
                            onClick = { onNavigate(Routes.filtered(place.name, PhotoFilter(bbox = place.bbox))) },
                            Modifier.width(CARD),
                        )
                    }
                }
            }
        }
        item(key = "events") {
            SectionHeader("Events & trips", actions = listOf("See all" to { onNavigate(Routes.EVENTS) }))
            val list = events.data?.items.orEmpty()
            if (list.isEmpty()) {
                Hint(if (events.isLoading) "Grouping your photos…" else "No events yet")
            } else {
                LazyRow(contentPadding = row, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { event ->
                        EventCard(event, onNavigate, Modifier.width(CARD))
                    }
                }
            }
        }
        item(key = "colors") {
            SectionHeader("Colours")
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PHOTO_COLORS.forEach { (id, label, swatch) ->
                    Chip(label, onClick = { onNavigate(Routes.color(id)) }, swatch = swatch)
                }
            }
        }
        item(key = "tidy") {
            SectionHeader("Tidy up")
            RowGroup(
                Modifier.padding(horizontal = 16.dp),
                listOf(
                    Triple("Best shots", BsIcons.Sparkle, bursts.data?.let { countLabel(it, "burst") }) to { onNavigate(Routes.BURSTS) },
                    Triple("Unorganized", BsIcons.Broom, unorganized.data?.let(::photoCount)) to {
                        onNavigate(Routes.filtered("Unorganized", PhotoFilter(unorganized = true)))
                    },
                ),
            )
        }
    }

    if (creating) {
        AlbumFormModal(
            title = "New album",
            onClose = { creating = false },
            onCreated = { onNavigate(Routes.album(it.id)) },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = BsText.Xs, color = BsColors.Muted)
}

/** Query typed: matching people, albums, places and colours as chips over the photo results. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Results(q: String, onNavigate: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val context = LocalContext.current
    val showAi = BigShelfApp.settings.showAi
    val people = rememberQuery("people-all", invalidatedBy = setOf("people")) { api.people().items }
    val albums = rememberQuery("albums-all", invalidatedBy = setOf("albums")) { api.albums() }
    val places = rememberQuery("places", invalidatedBy = setOf("photos")) { PlaceNames.places(context) }
    val photos = rememberQuery("search", q, invalidatedBy = setOf("photos")) { api.searchPhotos(q) }

    fun matches(name: String) = name.contains(q, ignoreCase = true)
    val personHits = people.data.orEmpty().filter { !it.isSkipped && matches(it.name) }.take(8)
    val albumHits = albums.data.orEmpty().filter { matches(it.title) }.take(8)
    val placeHits = places.data.orEmpty().filter { matches(it.name) }.take(8)
    val colorHits = PHOTO_COLORS.filter { (id, label) -> matches(label) || id.startsWith(q, ignoreCase = true) }
    val results = photos.data.orEmpty().let { all -> if (showAi) all else all.filterNot { it.isAiImage } }

    ImagesGrid(
        photos = results,
        onOpen = { onOpenPhoto(it.id) },
        isFetchingNextPage = photos.isLoading,
        totalCount = if (photos.isLoading) null else results.size,
        groupByDate = false,
        header = {
            if (personHits.isNotEmpty() || albumHits.isNotEmpty() || placeHits.isNotEmpty() || colorHits.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    personHits.forEach { Chip(it.name, onClick = { onNavigate(Routes.person(it.id)) }, icon = BsIcons.Users) }
                    albumHits.forEach { Chip(it.title, onClick = { onNavigate(Routes.album(it.id)) }, icon = BsIcons.Stack) }
                    placeHits.forEach {
                        Chip(it.name, onClick = { onNavigate(Routes.filtered(it.name, PhotoFilter(bbox = it.bbox))) }, icon = BsIcons.MapPin)
                    }
                    colorHits.forEach { (id, label, swatch) -> Chip(label, onClick = { onNavigate(Routes.color(id)) }, swatch = swatch) }
                }
            }
        },
    )
}

/** Port of the web's image search: library photos that look like [image], best match first. */
@Composable
private fun ImageResults(image: Uri, onClear: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val showAi = BigShelfApp.settings.showAi
    val matches = rememberQuery("search-image", image, invalidatedBy = setOf("photos")) { BigShelfApp.api.searchByImage(image) }
    LaunchedEffect(matches.isError) { if (matches.isError) toast.error("Reverse image search failed") }
    val results = matches.data.orEmpty().let { all -> if (showAi) all else all.filterNot { it.isAiImage } }
    ImagesGrid(
        photos = results,
        onOpen = { onOpenPhoto(it.id) },
        isFetchingNextPage = matches.isLoading,
        totalCount = if (matches.isLoading) null else results.size,
        // Ranked by similarity, so no date grouping
        groupByDate = false,
        header = {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(image, "Current image search", Modifier.size(40.dp).clip(BsShapes.Md), contentScale = ContentScale.Crop)
                Text("Current search", Modifier.weight(1f), style = BsText.Xs, color = BsColors.Muted)
                BsButton(onClick = onClear, variant = ButtonVariant.Ghost, textStyle = BsText.Xs) {
                    BsIcon(BsIcons.X, size = 12.dp)
                    Text("Clear")
                }
            }
        },
    )
}
