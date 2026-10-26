package com.bigshelf.app.ui.screens.people

import com.bigshelf.app.ui.components.TriangleLoader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.data.Person
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.photos.Avatar
import com.bigshelf.app.ui.photos.personImageSrc
import com.bigshelf.app.ui.photos.photoCountLabel
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText

/**
 * Scrollable list of people to pick from (web FaceCard grids in the tag / merge modals):
 * avatar, name and photo count per row, [selectedId] outlined. Faces tell apart people
 * with the same or similar names.
 */
@Composable
fun PersonList(
    people: List<Person>,
    onPick: (Person) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    enabled: Boolean = true,
    /** Shown when [people] is empty; null means still loading. */
    empty: String? = "No people found",
) {
    Column(
        modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (people.isEmpty()) {
            if (empty == null) {
                TriangleLoader(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
            } else {
                Text(empty, Modifier.fillMaxWidth().padding(vertical = 16.dp), style = BsText.Xs, color = BsColors.Muted)
            }
        }
        people.forEach { person ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(BsShapes.Md)
                    .bsClickable(enabled = enabled, scale = 0.98f) { onPick(person) }
                    .background(BsColors.Surface)
                    .border(1.dp, if (person.id == selectedId) BsColors.BorderFocus else Color.Transparent, BsShapes.Md)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(personImageSrc(person), person.name, Modifier.size(36.dp))
                Text(person.name, Modifier.weight(1f), style = BsText.Sm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(photoCountLabel(person.photoCount), style = BsText.Xs, color = BsColors.Muted)
            }
        }
    }
}
