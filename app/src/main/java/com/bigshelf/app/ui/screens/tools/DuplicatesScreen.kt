package com.bigshelf.app.ui.screens.tools

import com.bigshelf.app.ui.components.ShinyText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.DuplicateGroup
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.InfinityLoader
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.MonoFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val DEFAULT_THRESHOLD = 0.9
// Pairs fetched ahead, so the next one is already there when the current is resolved
private const val WINDOW_SIZE = 10
// How many decisions Undo can walk back
private const val UNDO_LIMIT = 3

/** A resolved pair: [binned] is empty for Keep both. [job] settles once the server has it. */
private class Decision(val group: DuplicateGroup, val binned: List<Photo>, var job: Job? = null)

/**
 * Port of modules/duplicates: one near-duplicate pair at a time. Tap the photo to keep (the
 * other goes to Bin), Keep both, or Pick the best. Every decision applies at once and the
 * request runs behind it; Undo walks back the last three.
 */
@Composable
fun DuplicatesScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val query = rememberQuery("duplicates", invalidatedBy = setOf("duplicates")) { api.duplicates(WINDOW_SIZE, DEFAULT_THRESHOLD) }
    val data = query.data
    // Pairs resolved this visit, hidden before the refetch confirms it
    val hidden = remember { mutableStateListOf<String>() }
    // Pairs brought back by Undo, shown ahead of the queue (latest first)
    val undone = remember { mutableStateListOf<DuplicateGroup>() }
    val history = remember { mutableStateListOf<Decision>() }
    var clearing by remember { mutableStateOf(false) }

    val server = data?.groups.orEmpty()
    val undoneKeys = undone.map { it.key }.toSet()
    val groups = undone + server.filter { it.items.size >= 2 && it.key !in hidden && it.key !in undoneKeys }
    val current = groups.firstOrNull()
    val pairsLeft = (data?.totalGroups ?: 0) - server.count { it.key in hidden }

    fun decide(group: DuplicateGroup, keep: Photo?) {
        val binned = if (keep != null) group.items.filter { it.id != keep.id } else emptyList()
        // Binned photos leave the selection, so the selection bar never acts on them
        binned.filter { SelectionStore.isSelected(it.id) }.forEach(SelectionStore::toggle)
        hidden += group.key
        undone.removeAll { it.key == group.key }
        val decision = Decision(group, binned)
        decision.job = scope.launch {
            try {
                if (keep != null) api.flagDeleted(binned.map { it.id }, true) else api.dismissDuplicate(group)
                PhotoEvents.invalidate("photos", "bin-photos", "organize", "people", "faces")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed bin puts the pair back
                toast.error(if (keep != null) "Failed to move photo to Bin" else "Failed to keep both")
                hidden -= group.key
                history.remove(decision)
            }
        }
        history += decision
        while (history.size > UNDO_LIMIT) history.removeAt(0)
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        hidden -= last.group.key
        undone.add(0, last.group)
        scope.launch {
            last.job?.join()
            try {
                if (last.binned.isNotEmpty()) api.flagDeleted(last.binned.map { it.id }, false)
                else api.undismissDuplicate(last.group.key)
                PhotoEvents.invalidate("photos", "bin-photos", "organize", "people", "faces")
            } catch (_: Exception) {
                toast.error("Failed to undo")
            }
        }
    }

    val relaxedTo = data?.thresholdUsed?.takeIf { it < DEFAULT_THRESHOLD }
    val best = current?.items?.firstOrNull { it.quality?.best == true }

    // One slim toolbar row so the pair gets nearly the whole screen
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                if (current != null) {
                    Text(
                        "${(current.similarity * 100).toInt()}% match" + if (pairsLeft > 0) " · $pairsLeft left" else "",
                        style = BsText.Xs.copy(fontFamily = MonoFamily), color = BsColors.MutedSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // No pairs at the standard match %, so the backend lowered the bar to show one
                    if (relaxedTo != null) Text("Relaxed to ${(relaxedTo * 100).toInt()}%", style = BsText.Xs2, color = BsColors.Warning, maxLines = 1)
                } else Text("Duplicates", style = BsText.Sm.copy(fontWeight = BsWeight.Medium), maxLines = 1)
            }
            if (current != null) {
                BsIconButton(BsIcons.CheckBold, "Keep both", onClick = { decide(current, null) }, tint = BsColors.Foreground)
                if (best != null) BsIconButton(BsIcons.Star, "Pick the best", onClick = { decide(current, best) }, tint = BsColors.Success)
            }
            BsIconButton(BsIcons.ArrowUUpLeft, "Undo", onClick = ::undo, enabled = history.isNotEmpty(), tint = if (history.isNotEmpty()) BsColors.Foreground else null)
            // Forget every Keep both so those pairs come up again
            BsIconButton(
                BsIcons.ArrowCounterClockwise,
                "Reset kept pairs",
                loading = clearing,
                onClick = {
                    clearing = true
                    scope.launch {
                        try {
                            val n = api.clearDismissed().clearedCount
                            toast.success("$n kept pair${if (n == 1) "" else "s"} will be asked again")
                            query.refetch()
                        } catch (_: Exception) {
                            toast.error("Failed to clear kept pairs")
                        } finally {
                            clearing = false
                        }
                    }
                },
            )
        }

        when {
            data == null && query.isLoading -> Column(
                Modifier.fillMaxWidth().padding(vertical = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfinityLoader(tint = BsColors.Muted)
                ShinyText("Finding duplicates…")
            }
            current == null -> {
                val auto = data?.autoRemovedCount ?: 0
                EmptyState(
                    if (auto > 0) "$auto exact duplicate${if (auto > 1) "s" else ""} moved to Bin" else "No duplicates found",
                    Modifier.padding(horizontal = 24.dp),
                )
            }
            else -> key(current.key) {
                PairReview(
                    group = current,
                    onKeep = { decide(current, it) },
                    onOpen = { photo ->
                        ViewerList.photos = current.items
                        onOpenPhoto(photo.id)
                    },
                )
            }
        }
    }
}

/** Port of duplicate-group.tsx: the pair splits the screen along its long side; tap the one to keep. */
@Composable
private fun PairReview(group: DuplicateGroup, onKeep: (Photo?) -> Unit, onOpen: (Photo) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
        // Stacked when tall, side by side when wide
        val tall = maxHeight >= maxWidth
        val gap = 4.dp
        @Composable
        fun Card(photo: Photo, modifier: Modifier) {
            Box(
                modifier
                    .clip(BsShapes.Lg)
                    .background(BsColors.Surface)
                    .bsClickable(scale = 0.97f) { onKeep(photo) },
            ) {
                // Fit, not Crop: comparing needs the whole frame
                AsyncImage(
                    BigShelfApp.api.mediaUrl(photo.thumbnailUrl ?: photo.imageSrc ?: ""),
                    photo.filename,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                ScoreBadge(photo, Modifier.align(Alignment.BottomStart).padding(6.dp))
                // Opening is its own target, so it never keeps
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(36.dp)
                        .clip(BsShapes.Full)
                        .background(BsColors.Background.copy(alpha = 0.7f))
                        .bsClickable { onOpen(photo) },
                    contentAlignment = Alignment.Center,
                ) {
                    BsIcon(BsIcons.ArrowUpRight, size = 16.dp, tint = BsColors.Foreground, contentDescription = "Open ${photo.filename}")
                }
            }
        }
        val pair = group.items.take(2)
        if (tall) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            pair.forEach { Card(it, Modifier.weight(1f).fillMaxWidth()) }
        } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            pair.forEach { Card(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/** Port of score-badge.tsx, plus the size: the group's pick reads "Best" in success green. */
@Composable
private fun ScoreBadge(photo: Photo, modifier: Modifier = Modifier) {
    val q = photo.quality
    val size = if (photo.width != null && photo.height != null) "${photo.width} × ${photo.height}" else null
    if (q == null && size == null) return
    Row(
        modifier
            .clip(BsShapes.Md)
            .background(BsColors.Background.copy(alpha = 0.8f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (q != null) {
            if (q.best) {
                BsIcon(BsIcons.StarFill, size = 12.dp, tint = BsColors.Success)
                Text("Best", style = BsText.Xs2.copy(fontWeight = BsWeight.Medium), color = BsColors.Success)
            }
            Text("${q.score.toInt()}", style = BsText.Xs2.copy(fontFamily = MonoFamily), color = if (q.best) BsColors.Success else BsColors.Muted)
        }
        if (size != null) Text(size, style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint)
    }
}
