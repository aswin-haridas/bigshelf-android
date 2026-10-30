package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.RatingPair
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.ImageCard
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

// Pairs fetched per call; more are fetched once the queue runs this low (web useRating)
private const val WINDOW_SIZE = 10
private const val REFILL_AT = 2
// How many answers Undo can walk back
private const val UNDO_LIMIT = 3

// An answer; `done` completes with the stored answer's id once the server has it
private class Answer(val pair: RatingPair, val done: CompletableDeferred<String?>)

/**
 * Port of modules/rating: one random pair at a time, tap the hotter photo. Each answer
 * retrains the taste model on the server, which re-rates every photo's heat. The queue is
 * local so a refill never swaps the pair on screen.
 */
@Composable
fun RatingScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val queue = remember { mutableStateListOf<RatingPair>() }
    val history = remember { mutableStateListOf<Answer>() }
    var rated by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var resetting by remember { mutableStateOf(false) }

    // Top the queue up; stops on its own when the library is too small (an empty fetch keeps it at 0)
    LaunchedEffect(queue.size <= REFILL_AT) {
        if (queue.size > REFILL_AT) return@LaunchedEffect
        try {
            val data = api.ratingPairs(WINDOW_SIZE)
            // Server count only once: later it may lag answers still in flight
            if (rated == null) rated = data.rated
            queue.addAll(data.pairs.filter { p -> queue.none { it.id == p.id } })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            toast.error("Failed to load photos")
        } finally {
            loading = false
        }
    }

    val current = queue.firstOrNull()

    // Warm the cache with the next pair so it appears instantly
    val next = queue.getOrNull(1)
    LaunchedEffect(next?.id) {
        val loader = SingletonImageLoader.get(context)
        next?.items?.forEach { p ->
            p.thumbnailUrl?.let { loader.enqueue(ImageRequest.Builder(context).data(api.mediaUrl(it)).build()) }
        }
    }

    // Heat lives on the photo detail; refresh it so it shows the new rating
    fun refresh() = PhotoEvents.invalidate("photo")

    // The user found `winner` hotter: show the next pair at once, the server catches up
    fun pick(pair: RatingPair, winner: Photo) {
        val loser = pair.items.firstOrNull { it.id != winner.id } ?: return
        queue.remove(pair)
        rated = (rated ?: 0) + 1
        val answer = Answer(pair, CompletableDeferred())
        history.add(answer)
        if (history.size > UNDO_LIMIT) history.removeAt(0)
        scope.launch {
            try {
                answer.done.complete(api.compare(winner.id, loser.id))
                refresh()
            } catch (_: Exception) {
                answer.done.complete(null)
                toast.error("Failed to save your pick")
                rated = maxOf((rated ?: 1) - 1, 0)
                history.remove(answer)
            }
        }
    }

    // Walk back the latest answer: the pair is asked again, then the server forgets it
    fun undo() {
        val last = history.removeLastOrNull() ?: return
        queue.add(0, last.pair)
        rated = maxOf((rated ?: 1) - 1, 0)
        scope.launch {
            val id = last.done.await() ?: return@launch
            try {
                api.undoCompare(id)
                refresh()
            } catch (_: Exception) {
                toast.error("Failed to undo")
            }
        }
    }

    // Forget every answer so the taste is learned from scratch
    fun reset() {
        resetting = true
        scope.launch {
            try {
                val cleared = api.resetRating().clearedCount
                rated = 0
                history.clear()
                refresh()
                toast.success("$cleared answer${if (cleared == 1) "" else "s"} forgotten")
            } catch (_: Exception) {
                toast.error("Failed to reset")
            } finally {
                resetting = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Rating",
            count = rated?.let { "$it rated" },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            BsIconButton(BsIcons.ArrowUUpLeft, "Undo the last answer", onClick = ::undo, enabled = history.isNotEmpty())
            BsIconButton(
                BsIcons.ArrowCounterClockwise, "Forget every answer",
                onClick = ::reset, enabled = !resetting && (rated ?: 0) > 0, loading = resetting,
            )
        }

        when {
            loading -> LoadingText(text = "Picking photos…")
            current == null -> EmptyState(
                "Not enough photos to compare",
                Modifier.padding(horizontal = 24.dp),
                description = "Photos can be rated once processing has finished",
            )
            else -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = LocalBottomBarInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Which is hotter?", Modifier.weight(1f), style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
                    // Can't decide: move on without teaching the model anything
                    BsButton(onClick = { queue.remove(current) }, variant = ButtonVariant.Ghost) {
                        BsIcon(BsIcons.Shuffle, size = 14.dp)
                        Text("Skip")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.items.forEach { photo ->
                        Box(Modifier.weight(1f).clip(BsShapes.Lg)) {
                            // A tap always picks; the corner arrow opens the photo
                            ImageCard(photo = photo, selected = false, selectionActive = false, onClick = { pick(current, photo) }, onToggle = {})
                            BsIconButton(
                                BsIcons.ArrowUpRight, "Open ${photo.filename}",
                                onClick = { onOpenPhoto(photo.id) },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(BsShapes.Full)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                tint = BsColors.Foreground,
                            )
                        }
                    }
                }
            }
        }
    }
}
