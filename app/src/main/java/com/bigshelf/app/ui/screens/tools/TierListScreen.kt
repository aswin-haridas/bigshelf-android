package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.photos.ImageCard
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsText

internal fun tierForHeat(heat: Double?): String = when {
    heat == null || !heat.isFinite() -> "Unrated"
    heat >= 9 -> "S"
    heat >= 8 -> "A"
    heat >= 6 -> "B"
    heat >= 4 -> "C"
    heat >= 2 -> "D"
    else -> "F"
}

@Composable
fun TierListScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit, onRate: () -> Unit) {
    val query = rememberQuery("tiers", invalidatedBy = setOf("photos", "rating")) {
        val photos = mutableListOf<Photo>()
        var page = 1
        var cursor: String? = null
        do {
            val batch = BigShelfApp.api.photos(page, size = 100, cursor = cursor)
            photos.addAll(batch.items)
            cursor = batch.nextCursor
            page++
        } while (batch.hasNext && batch.items.isNotEmpty())
        photos.distinctBy { it.id }.sortedByDescending { it.heat ?: -1.0 }
    }
    val photos = query.data.orEmpty()
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Tier list", count = "Photos grouped by Heat · ${photos.size} photos",
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack) },
        ) {
            BsButton(onClick = onRate, variant = ButtonVariant.Secondary) { Text("Rate photos") }
        }
        when {
            query.isLoading -> LoadingText()
            query.isError -> {
                EmptyState("Unable to load photos")
                BsButton(onClick = query::refetch) { Text("Retry") }
            }
            photos.isEmpty() -> EmptyState("No photos yet")
            else -> LazyColumn {
                items(listOf("S", "A", "B", "C", "D", "F", "Unrated")) { tier ->
                    val row = photos.filter { tierForHeat(it.heat) == tier }
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.width(64.dp).padding(8.dp)) {
                            Text(tier, style = BsText.Sm)
                            Text("${row.size}", style = BsText.Xs, color = BsColors.Muted)
                        }
                        if (row.isEmpty()) Text("No photos in this tier", style = BsText.Xs, color = BsColors.Muted)
                        else LazyRow(
                            contentPadding = PaddingValues(end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(row, key = { it.id }) { photo ->
                                ImageCard(
                                    photo, selected = false, selectionActive = false, onToggle = {}, modifier = Modifier.width(80.dp),
                                    onClick = { ViewerList.photos = photos; onOpenPhoto(photo.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
