package com.bigshelf.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.photos.photoCount
import com.bigshelf.app.ui.shell.rememberUploadPicker
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import kotlinx.coroutines.delay

/**
 * Port of modules/home/home.tsx (mobile layout): Photos title with the count, upload
 * action, full-width search, then the edge-to-edge timeline grid - or ranked text-search
 * results while a query is set.
 */
@Composable
fun HomeScreen(onOpenPhoto: (String) -> Unit) {
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

    val upload = rememberUploadPicker()

    Column(Modifier.fillMaxSize()) {
        PageHeader("Photos", count = totalCount?.let(::photoCount)) {
            BsIconButton(
                BsIcons.UploadSimple,
                "Upload images",
                onClick = upload,
                tint = BsColors.Foreground,
            )
        }
        BsSearchBar(
            search,
            { search = it },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            placeholder = "Search your photos",
        )

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
