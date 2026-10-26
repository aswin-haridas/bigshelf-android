package com.bigshelf.app.ui.screens.tools

import com.bigshelf.app.ui.components.ShinyText
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.PageHeader
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
 * other goes to Bin), Keep both, or Pick the best. Locked to landscape so the pair gets full width. Every decision applies at once and the
 * request runs behind it; Undo walks back the last three.
 */
@Composable
fun DuplicatesScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Skip the restore while rotating, or the recreated screen would flip back to portrait first
        onDispose { if (activity?.isChangingConfigurations == false) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
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

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        PageHeader(
            "Duplicates",
            count = if (current != null && pairsLeft > 0) "$pairsLeft left" else null,
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
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
                    relaxedTo = data?.thresholdUsed?.takeIf { it < DEFAULT_THRESHOLD },
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

/** Port of duplicate-group.tsx, laid out for landscape: the pair side by side, actions on the right. */
@Composable
private fun PairReview(group: DuplicateGroup, relaxedTo: Double?, onKeep: (Photo?) -> Unit, onOpen: (Photo) -> Unit) {
    val best = group.items.firstOrNull { it.quality?.best == true }
    Row(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        group.items.take(2).forEach { photo ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                    photo.quality?.let { q -> ScoreBadge(q.score, q.best, Modifier.align(Alignment.BottomStart).padding(6.dp)) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(photo.filename, Modifier.weight(1f), style = BsText.Xs, color = BsColors.MutedSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (photo.width != null && photo.height != null) {
                        Text("${photo.width} × ${photo.height}", style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint)
                    }
                }
            }
        }
        Column(
            Modifier.width(192.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("${(group.similarity * 100).toInt()}% match", style = BsText.Base.copy(fontWeight = BsWeight.Medium))
            // No pairs at the standard match %, so the backend lowered the bar to show one
            if (relaxedTo != null) Text("Relaxed to ${(relaxedTo * 100).toInt()}%", style = BsText.Xs, color = BsColors.Warning)
            Text("Tap the one to keep", style = BsText.Xs, color = BsColors.Muted)
            BsButton(onClick = { onKeep(null) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary) {
                BsIcon(BsIcons.CheckBold, size = 18.dp)
                Text("Keep both")
            }
            if (best != null) {
                BsButton(onClick = { onKeep(best) }, Modifier.fillMaxWidth()) {
                    BsIcon(BsIcons.Star, size = 18.dp)
                    Text("Pick the best")
                }
            }
        }
    }
}

/** Port of score-badge.tsx: the group's pick reads "Best" in success green. */
@Composable
private fun ScoreBadge(score: Double, best: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(BsShapes.Md)
            .background(BsColors.Background.copy(alpha = 0.8f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (best) {
            BsIcon(BsIcons.StarFill, size = 12.dp, tint = BsColors.Success)
            Text("Best", style = BsText.Xs2.copy(fontWeight = BsWeight.Medium), color = BsColors.Success)
        }
        Text("${score.toInt()}", style = BsText.Xs2.copy(fontFamily = MonoFamily), color = if (best) BsColors.Success else BsColors.Muted)
    }
}
