package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText

@Composable
internal fun rememberPersonRanks(): Map<String, Int>? {
    val people = rememberQuery(invalidatedBy = setOf("people"), active = BigShelfApp.settings.showPersonRanks) {
        BigShelfApp.api.people().items
    }.data ?: return null
    val scores = people.mapNotNull { it.tasteScore }.distinct().sortedDescending()
    return people.mapNotNull { person ->
        person.tasteScore?.let { person.id to (scores.indexOf(it) + 1) }
    }.toMap()
}

@Composable
internal fun PersonRanks(photo: Photo, ranks: Map<String, Int>?, modifier: Modifier) {
    if (!BigShelfApp.settings.showPersonRanks) return
    val ranked = photo.people.orEmpty().mapNotNull { person ->
        val rank = if (ranks == null) person.rank else ranks[person.id]
        rank?.let { it to person.name }
    }
    if (ranked.isEmpty()) return
    Box(modifier.fillMaxWidth(0.7f)) {
        Column(
            Modifier.clip(BsShapes.Lg).background(BsColors.Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            for ((rank, name) in ranked) {
                Text("#$rank $name", color = BsColors.Foreground, style = BsText.Xs)
            }
        }
    }
}
