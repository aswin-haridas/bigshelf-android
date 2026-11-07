package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.RatingPair
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.MonoFamily
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

// Pairs kept queued: every answer tops the queue back up, so later pairs use the new taste scores (web useRating)
private const val WINDOW_SIZE = 10
// How many answers Undo can walk back
private const val UNDO_LIMIT = 3

// An answer; `done` completes with the stored answer's id once the server has it
private class Answer(val pair: RatingPair, val done: CompletableDeferred<String?>)

/**
 * Port of modules/rating: one pair of similar taste score at a time, tap the photo you like more. Each answer
 * retrains the taste model on the server, which re-scores every photo. The queue is
 * local so a refill never swaps the pair on screen.
 */
@Composable
fun RatingScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val queue = remember { mutableStateListOf<RatingPair>() }
    val history = remember { mutableStateListOf<Answer>() }
    // Pairs answered here: the server may serve one again while its answer is still in flight
    val answered = remember { mutableSetOf<String>() }
    var rated by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var resetting by remember { mutableStateOf(false) }

    // Top the queue up to WINDOW_SIZE; stops on its own when there's nothing new to ask
    // (a fetch that adds nothing keeps the size)
    LaunchedEffect(queue.size) {
        if (queue.size >= WINDOW_SIZE) return@LaunchedEffect
        try {
            val data = api.ratingPairs(WINDOW_SIZE)
            // Server count only once: later it may lag answers still in flight
            if (rated == null) rated = data.rated
            queue.addAll(
                data.pairs
                    .filter { p -> p.id !in answered && queue.none { it.id == p.id } }
                    .take(maxOf(WINDOW_SIZE - queue.size, 0))
            )
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

    // Taste score lives on the photo detail; refresh it so it shows the new rating
    fun refresh() = PhotoEvents.invalidate("photo")

    // The user likes `winner` more: show the next pair at once, the server catches up
    fun pick(pair: RatingPair, winner: Photo) {
        val loser = pair.items.firstOrNull { it.id != winner.id } ?: return
        answered.add(pair.id)
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
                answered.remove(pair.id)
                rated = maxOf((rated ?: 1) - 1, 0)
                history.remove(answer)
            }
        }
    }

    // Walk back the latest answer: the pair is asked again, then the server forgets it
    fun undo() {
        val last = history.removeLastOrNull() ?: return
        answered.remove(last.pair.id)
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

    // One slim toolbar row so the photos get nearly the whole screen
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground)
            Text(
                rated?.let { "$it rated" } ?: "Rating",
                Modifier.weight(1f).padding(start = 4.dp),
                style = BsText.Xs.copy(fontFamily = MonoFamily),
                color = BsColors.Muted,
                maxLines = 1,
            )
            // Can't decide: move on without teaching the model anything
            if (current != null) BsIconButton(BsIcons.Shuffle, "Skip this pair", onClick = { queue.remove(current) })
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
            else -> BoxWithConstraints(
                Modifier.fillMaxSize().padding(horizontal = 4.dp).padding(bottom = LocalBottomBarInset.current + 4.dp),
            ) {
                // Split along the long side: stacked when tall, side by side when wide
                val tall = maxHeight >= maxWidth
                val gap = 4.dp
                @Composable
                fun Card(photo: Photo, modifier: Modifier) {
                    Box(modifier.clip(BsShapes.Lg).background(BsColors.Surface).bsClickable { pick(current, photo) }) {
                        // A tap always picks; the corner arrow opens the photo
                        AsyncImage(
                            api.mediaUrl(photo.thumbnailUrl ?: photo.imageSrc),
                            photo.title ?: photo.filename,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        BsIconButton(
                            BsIcons.ArrowUpRight, "Open ${photo.filename}",
                            onClick = { onOpenPhoto(photo.id) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(BsShapes.Full)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            tint = BsColors.Foreground,
                        )
                    }
                }
                if (tall) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    current.items.forEach { Card(it, Modifier.weight(1f).fillMaxWidth()) }
                } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    current.items.forEach { Card(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}
