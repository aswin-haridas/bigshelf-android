package com.bigshelf.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.state.AuthStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/**
 * Photos tab laid out like Instagram Explore: search bar and profile badge on top, then an
 * edge-to-edge square mosaic of the library - or ranked text-search results while a query is set.
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

    val feed = rememberPagedPhotos("home", invalidatedBy = setOf("photos")) { page -> api.photos(page) }
    val textSearch = rememberQuery("search", q, invalidatedBy = setOf("photos")) {
        if (q.isEmpty()) emptyList() else api.searchPhotos(q)
    }
    val filter: (List<Photo>) -> List<Photo> = { all -> if (showAi) all else all.filterNot { it.isAiImage } }
    val photos = filter(feed.photos)
    val results = filter(textSearch.data.orEmpty())

    val totalCount: Int? = when {
        q.isNotEmpty() -> if (textSearch.isLoading) null else results.size
        showAi -> feed.total
        else -> if (feed.hasNextPage) null else photos.size
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BsSearchBar(search, { search = it }, Modifier.weight(1f), placeholder = "Search your photos")
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
            // Ranked by relevance, so no date grouping
            ImagesGrid(
                photos = results,
                onOpen = { onOpenPhoto(it.id) },
                isFetchingNextPage = textSearch.isLoading,
                totalCount = totalCount,
                explore = true,
            )
        } else {
            ImagesGrid(
                photos = photos,
                onOpen = { onOpenPhoto(it.id) },
                fetchNextPage = feed::fetchNextPage,
                hasNextPage = feed.hasNextPage,
                isFetchingNextPage = feed.isFetching,
                totalCount = totalCount,
                explore = true,
            )
        }
    }
}
