package com.bigshelf.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.UploadStore
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.photos.photoCount
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.MonoFamily
import kotlinx.coroutines.delay

/**
 * Port of modules/home/home.tsx + home-header.tsx (mobile layout): photo count, AI and
 * upload buttons, full-width search below, then the timeline grid - or ranked text-search
 * results while a query is set.
 */
@Composable
fun HomeScreen(onOpenPhoto: (String) -> Unit, onPipelines: () -> Unit) {
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        UploadStore.start(uris)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    if (totalCount != null) {
                        Text(photoCount(totalCount), style = BsText.Xs.copy(fontFamily = MonoFamily), color = BsColors.Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // `bg-surface border border-border` link to the AI pipelines page
                    Row(
                        Modifier
                            .bsClickable(onClick = onPipelines)
                            .clip(BsShapes.Md)
                            .background(BsColors.Surface)
                            .border(1.dp, BsColors.Border, BsShapes.Md)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BsIcon(BsIcons.Grains, size = 16.dp)
                        Text("AI", style = BsText.Sm)
                    }
                    // Icon-only below `sm`, like the web ("upload images" label is hidden)
                    BsButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        variant = ButtonVariant.Secondary,
                    ) {
                        BsIcon(BsIcons.UploadSimple, size = 16.dp, contentDescription = "Upload images")
                    }
                }
            }
            BsSearchBar(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Search your photos")
        }

        if (q.isNotEmpty()) {
            // Ranked by relevance, so no date grouping
            ImagesGrid(
                photos = results,
                onOpen = { onOpenPhoto(it.id) },
                isFetchingNextPage = textSearch.isLoading,
                totalCount = totalCount,
                groupByDate = false,
            )
        } else {
            ImagesGrid(
                photos = photos,
                onOpen = { onOpenPhoto(it.id) },
                fetchNextPage = feed::fetchNextPage,
                hasNextPage = feed.hasNextPage,
                isFetchingNextPage = feed.isFetching,
                totalCount = totalCount,
            )
        }
    }
}
