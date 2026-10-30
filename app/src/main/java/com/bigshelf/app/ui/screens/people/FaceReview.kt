package com.bigshelf.app.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.FaceReview
import com.bigshelf.app.data.FaceReviewBatch
import com.bigshelf.app.data.MergeSuggestion
import com.bigshelf.app.data.MergeSuggestionPerson
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.QueryState
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch

/**
 * Port of face-confirmation-popup.tsx: a dismissible "Face review" card that opens the
 * review modal. Renders nothing when the queue is empty.
 */
@Composable
fun FaceReviewBanner(modifier: Modifier = Modifier) {
    val query = rememberQuery("face-review", invalidatedBy = setOf("people", "faces")) { BigShelfApp.api.faceReview() }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val data = query.data
    val batch = data?.batch
    val merge = data?.merge

    if (open) FaceReviewModal(query, onClose = { open = false })
    if (dismissed || (batch == null && merge == null)) return

    Column(
        modifier
            .fillMaxWidth()
            .clip(BsShapes.Xl)
            .background(BsColors.Surface)
            .border(1.dp, BsColors.Border, BsShapes.Xl)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The pair for a merge question (asked first), else the reference + first faces
            val thumbs = if (merge != null) listOf(merge.keep.faceThumbnail, merge.merge.faceThumbnail)
            else listOfNotNull(batch?.reference?.faceThumbnail) + batch?.faces.orEmpty().take(2).map { it.faceThumbnail }
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                thumbs.forEach { FaceThumb(it, 40.dp, Modifier.border(2.dp, BsColors.Surface, BsShapes.Full).clip(BsShapes.Full)) }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (merge != null) "Is ${merge.merge.personName} the same as ${merge.keep.personName}?"
                    else "Are these all ${batch?.personName}?",
                    style = BsText.Xs.copy(fontWeight = BsWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (merge != null) "Two people look nearly identical"
                    else "${data?.total} face${if (data?.total == 1) "" else "s"} to review",
                    style = BsText.Xs2,
                    color = BsColors.Muted,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            BsButton(onClick = { dismissed = true }, variant = ButtonVariant.Ghost) { Text("Dismiss") }
            BsButton(onClick = { open = true }) { Text("Review") }
        }
    }
}

/** Port of face-confirmation-modal.tsx: the merge question first, then one face grid per person. */
@Composable
private fun FaceReviewModal(query: QueryState<FaceReview>, onClose: () -> Unit) {
    val data = query.data
    val merge = data?.merge
    val batch = data?.batch
    when {
        merge != null -> key(merge.keep.personId, merge.merge.personId) { MergeQuestion(merge, query, onClose) }
        // Keyed per screen so tick state resets when the next batch arrives
        batch != null -> key(batch.personId, batch.faces.firstOrNull()?.faceId) { FaceGrid(batch, data.total, query, onClose) }
        // Queue emptied: nothing left to show
        else -> LaunchedEffect(Unit) { onClose() }
    }
}

/** Port of face-review-grid.tsx: every face starts ticked; tap the ones that aren't them. */
@Composable
private fun FaceGrid(batch: FaceReviewBatch, total: Int, query: QueryState<FaceReview>, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val unticked = remember { mutableStateListOf<String>() }
    var saving by remember { mutableStateOf(false) }
    val tickedCount = batch.faces.size - unticked.size

    fun save(rejected: List<String> = unticked.toList()) {
        saving = true
        scope.launch {
            try {
                val next = BigShelfApp.api.resolveFaceReview(
                    batch.personId,
                    batch.faces.map { it.faceId }.filterNot { it in rejected },
                    rejected,
                )
                val confirmed = batch.faces.size - rejected.size
                toast.success("$confirmed confirmed as ${batch.personName}" + if (rejected.isNotEmpty()) ", ${rejected.size} removed" else "")
                if (next.batch == null && next.merge == null) toast.success("All faces reviewed")
                query.data = next
                PhotoEvents.invalidate("photos", "faces", "people")
            } catch (_: Exception) {
                toast.error("Failed to save face review")
            } finally {
                saving = false
            }
        }
    }

    BsModal(
        title = "Are these all ${batch.personName}?",
        onClose = onClose,
        footer = {
            // Rejects the whole batch and moves on to the next one
            BsButton(onClick = { save(batch.faces.map { it.faceId }) }, variant = ButtonVariant.Ghost, enabled = !saving) { Text("None are them") }
            BsButton(onClick = { save() }, loading = saving, enabled = !saving) {
                Text(if (tickedCount > 0) "Confirm $tickedCount" else "Save")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FaceThumb(batch.reference.faceThumbnail, 48.dp, Modifier.border(2.dp, BsColors.Primary, BsShapes.Lg).clip(BsShapes.Lg))
                Text(
                    "Tap any face that isn't them" + if (total > batch.faces.size) " · $total faces left to review" else "",
                    style = BsText.Xs,
                    color = BsColors.Muted,
                )
            }
            LazyVerticalGrid(
                GridCells.Adaptive(72.dp),
                Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(batch.faces, key = { it.faceId }) { face ->
                    val out = face.faceId in unticked
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .bsClickable(scale = 0.95f) { if (out) unticked -= face.faceId else unticked += face.faceId },
                    ) {
                        // Ticked is the default, so only rejected faces are marked
                        FaceThumb(
                            face.faceThumbnail,
                            Dp.Unspecified,
                            Modifier
                                .fillMaxSize()
                                .alpha(if (out) 0.4f else 1f)
                                .then(if (out) Modifier.border(2.dp, BsColors.Danger, BsShapes.Lg) else Modifier)
                                .clip(BsShapes.Lg),
                        )
                        if (out) BsIcon(
                            BsIcons.XCircleFill,
                            Modifier.align(Alignment.BottomEnd).padding(4.dp),
                            size = 20.dp,
                            tint = BsColors.Danger,
                            contentDescription = "Not ${batch.personName}",
                        )
                    }
                }
            }
        }
    }
}

/** Port of merge-suggestion-card.tsx: "Same person?" for two near-identical people. */
@Composable
private fun MergeQuestion(suggestion: MergeSuggestion, query: QueryState<FaceReview>, onClose: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    fun answer(same: Boolean) {
        saving = true
        scope.launch {
            try {
                if (same) {
                    api.mergePeople(suggestion.merge.personId, suggestion.keep.personId)
                    toast.success("Merged \"${suggestion.merge.personName}\" into \"${suggestion.keep.personName}\"")
                    query.data = api.faceReview()
                } else {
                    query.data = api.markDistinct(listOf(suggestion.keep.personId, suggestion.merge.personId))
                }
                PhotoEvents.invalidate("photos", "faces", "people")
            } catch (_: Exception) {
                toast.error("Failed to save answer")
            } finally {
                saving = false
            }
        }
    }

    BsModal(
        title = "Same person?",
        onClose = onClose,
        footer = {
            BsButton(onClick = { answer(false) }, variant = ButtonVariant.Ghost, enabled = !saving) { Text("Different") }
            BsButton(onClick = { answer(true) }, loading = saving, enabled = !saving) { Text("Same, merge") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("These two look nearly identical", style = BsText.Xs, color = BsColors.Muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                MergePerson(suggestion.keep)
                MergePerson(suggestion.merge)
            }
        }
    }
}

@Composable
private fun MergePerson(person: MergeSuggestionPerson) {
    Column(Modifier.width(128.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FaceThumb(person.faceThumbnail, 128.dp, Modifier.clip(BsShapes.Lg))
        Text(person.personName, style = BsText.Xs.copy(fontWeight = BsWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${person.faceCount} face${if (person.faceCount == 1) "" else "s"}", style = BsText.Xs2, color = BsColors.Muted)
    }
}

/** Web FaceCrop: the pre-cropped face, or a people icon. [size] Unspecified = fill the modifier's bounds. */
@Composable
private fun FaceThumb(src: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        (if (size != Dp.Unspecified) Modifier.size(size) else Modifier).then(modifier).background(BsColors.SurfaceHover),
        contentAlignment = Alignment.Center,
    ) {
        if (src != null) {
            AsyncImage(BigShelfApp.api.mediaUrl(src), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BsIcon(BsIcons.Users, size = 20.dp, tint = BsColors.Muted)
        }
    }
}
