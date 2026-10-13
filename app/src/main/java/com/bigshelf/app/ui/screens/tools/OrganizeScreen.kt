package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Downloads
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsIconButton
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
    "non_person" to "Non-person",
    "non_34" to "Not 3:4",
    "low_res" to "Low resolution",
    "collage" to "Collages",
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

    val bucket = query.data?.get(tab)
    val items = bucket?.items.orEmpty()
    val allSelected = items.isNotEmpty() && items.all { SelectionStore.isSelected(it.id) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Organize",
            count = bucket?.let { photoCount(it.count) },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
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
}
