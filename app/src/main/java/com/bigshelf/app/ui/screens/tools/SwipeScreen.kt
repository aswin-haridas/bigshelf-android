package com.bigshelf.app.ui.screens.tools

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import com.bigshelf.app.ui.components.FitText
import com.bigshelf.app.ui.components.bsClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.bigshelf.app.BigShelfApp
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
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.CancellationException
import com.bigshelf.app.ui.components.LoadError
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlin.math.abs

// Pairs fetched per call (2 photos each); more are fetched once the queue runs this low (web useSwipe)
private const val SWIPE_WINDOW = 10
private const val SWIPE_REFILL_AT = 3
private const val SWIPE_UNDO_LIMIT = 3
// A keep this soon (ms) after the photo came up is a gut "yes": it gets a point of heat
private const val QUICK_KEEP_MS = 1500L
// How far a drag must travel to count as a swipe
private val THRESHOLD = 100.dp

private enum class Verdict(val label: String, val color: Color) {
    Keep("Keep", BsColors.Success),
    Bin("Bin", BsColors.Danger),
    Favorite("Favourite", Color(0xFFF472B6)),
    Rework("Rework", Color(0xFFFBBF24)),
}

internal fun swipeHeatPoints(elapsedMs: Long, rework: Boolean = false): Double = when {
    rework -> -1.0
    elapsedMs < QUICK_KEEP_MS -> 1.0
    else -> 0.25
}

// Filled in by its write once that lands; `saved` false means there's nothing to revert
private class Swipe(var photo: Photo, val verdict: Verdict) {
    var points = 0.0
    var previousRework = false
    var saved = false
    var write: Job? = null
}

/** Lower-heat and unrated photos get more turns; swipes adjust their heat directly. */
@Composable
fun SwipeScreen(onBack: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val queue = remember { mutableStateListOf<Photo>() }
    val history = remember { mutableStateListOf<Swipe>() }
    // Every photo shown this session, so a refill never repeats one
    val seen = remember { mutableSetOf<String>() }
    var count by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var refill by remember { mutableStateOf(0) }

    // The seen set keeps refills from repeating photos, even after their heat changes.
    LaunchedEffect(queue.size, refill) {
        if (queue.size > SWIPE_REFILL_AT) return@LaunchedEffect
        loading = true
        loadError = false
        try {
            val fresh = api.swipePhotos(seen, SWIPE_WINDOW * 2).filter { seen.add(it.id) }
            queue.addAll(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadError = true
        } finally {
            loading = false
        }
    }

    val current = queue.firstOrNull()
    val next = queue.getOrNull(1)
    // When the photo on top came up, to time the swipe on it
    var shownAt by remember { mutableStateOf(0L) }
    LaunchedEffect(current?.id) { shownAt = SystemClock.elapsedRealtime() }

    // Warm the cache with the next photo so it appears instantly
    LaunchedEffect(next?.id) {
        next?.imageSrc?.let {
            SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(api.mediaUrl(it)).build())
        }
    }

    fun refresh() = PhotoEvents.invalidate("photos", "photo", "bin-photos", "people")

    // Optimistic: the card moves on at once and the write runs behind it. A failed write puts the
    // photo back on top without a word. NonCancellable so leaving the screen doesn't drop a write.
    fun swipe(photo: Photo, verdict: Verdict) {
        val points = when (verdict) {
            Verdict.Keep -> swipeHeatPoints(SystemClock.elapsedRealtime() - shownAt)
            Verdict.Rework -> swipeHeatPoints(0, rework = true)
            else -> 0.0
        }
        val entry = Swipe(photo, verdict)
        queue.remove(photo)
        count++
        history.add(entry)
        if (history.size > SWIPE_UNDO_LIMIT) history.removeAt(0)
        entry.write = scope.launch(NonCancellable) {
            try {
                when (verdict) {
                    Verdict.Bin -> api.flagDeleted(listOf(photo.id), true)
                    Verdict.Favorite -> {
                        entry.photo = api.photo(photo.id)
                        api.setFavorite(photo.id, true)
                    }
                    Verdict.Keep, Verdict.Rework -> {
                        val adjustment = api.boostHeat(photo.id, points, rework = if (verdict == Verdict.Rework) true else null)
                        entry.points = adjustment.points
                        entry.previousRework = adjustment.previousRework
                    }
                }
                entry.saved = true
                refresh()
            } catch (_: Exception) {
                // Still in history, or aged out of it: bring it back. Undone already: it's back on top.
                if (history.remove(entry) || queue.none { it.id == photo.id }) {
                    queue.add(0, photo)
                    count = maxOf(count - 1, 0)
                }
            }
        }
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        queue.add(0, last.photo)
        count = maxOf(count - 1, 0)
        scope.launch(NonCancellable) {
            // Let the swipe's own write land first, or the revert could be overtaken by it
            last.write?.join()
            if (!last.saved) return@launch
            try {
                when (last.verdict) {
                    Verdict.Bin -> api.flagDeleted(listOf(last.photo.id), false)
                    Verdict.Favorite -> api.setFavorite(last.photo.id, last.photo.isFavorite)
                    Verdict.Rework -> api.boostHeat(last.photo.id, -last.points, rework = last.previousRework)
                    Verdict.Keep -> api.boostHeat(last.photo.id, -last.points)
                }
                refresh()
            } catch (_: Exception) {
                toast.error("Couldn't undo swipe. Try again.")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Swipe",
            count = if (count > 0) "$count swiped" else null,
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            BsIconButton(BsIcons.ArrowUUpLeft, "Undo the last swipe", onClick = ::undo, enabled = history.isNotEmpty())
        }

        when {
            current == null && loading -> LoadingText(text = "Picking photos…")
            current == null && loadError -> LoadError(onRetry = { refill++ })
            current == null -> EmptyState(
                "Nothing left to swipe",
                Modifier.padding(horizontal = 24.dp),
                description = "Photos show up here once processing has finished",
            )
            else -> Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = LocalBottomBarInset.current + 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The 3:4 card takes the widest size that fits both width and height
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val cardModifier = if (maxWidth / maxHeight < 3f / 4f) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
                    val card = cardModifier.aspectRatio(3f / 4f, matchHeightConstraintsFirst = maxWidth / maxHeight >= 3f / 4f)
                    next?.let { key(it.id) { SwipeCard(it, card.graphicsLayer { scaleX = 0.95f; scaleY = 0.95f; alpha = 0.6f }, null) } }
                    // key remounts per photo so the drag offset starts at zero
                    key(current.id) { SwipeCard(current, card) { verdict -> swipe(current, verdict) } }
                }
                if (loadError) LoadError(onRetry = { refill++ })
                // Left to right as the swipes go: Bin is a left swipe, Keep a right one
                Row(Modifier.fillMaxWidth()) {
                    VerdictButton(BsIcons.Trash, Verdict.Bin) { swipe(current, Verdict.Bin) }
                    VerdictButton(BsIcons.Wrench, Verdict.Rework) { swipe(current, Verdict.Rework) }
                    VerdictButton(BsIcons.Heart, Verdict.Favorite) { swipe(current, Verdict.Favorite) }
                    VerdictButton(BsIcons.CheckBold, Verdict.Keep) { swipe(current, Verdict.Keep) }
                }
            }
        }
    }
}

/** Equal-width thumb target: the verdict's colour on a 56dp circle, a small label under it. */
@Composable
private fun RowScope.VerdictButton(icon: androidx.compose.ui.graphics.vector.ImageVector, verdict: Verdict, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).bsClickable(scale = 0.92f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(56.dp).clip(BsShapes.Full).background(BsColors.Surface), contentAlignment = Alignment.Center) {
            BsIcon(icon, size = 24.dp, tint = verdict.color)
        }
        FitText(verdict.label, BsText.Xs2.copy(color = BsColors.Muted))
    }
}

// The draggable photo; `onSwipe` null is the next one, peeking under the top card
@Composable
private fun SwipeCard(photo: Photo, modifier: Modifier, onSwipe: ((Verdict) -> Unit)?) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val threshold = with(LocalDensity.current) { THRESHOLD.toPx() }
    fun verdictOf(o: Offset) = when {
        -o.y > threshold && -o.y > abs(o.x) -> Verdict.Rework
        o.x > threshold -> Verdict.Keep
        o.x < -threshold -> Verdict.Bin
        else -> null
    }
    val verdict = verdictOf(offset.value)

    Box(
        modifier
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = offset.value.x / 20f
            }
            .clip(BsShapes.Lg)
            .background(BsColors.Surface)
            .then(
                if (onSwipe == null) Modifier else Modifier.pointerInput(photo.id) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + amount) }
                        },
                        onDragEnd = {
                            verdictOf(offset.value)?.let(onSwipe) ?: scope.launch { offset.animateTo(Offset.Zero) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                    )
                },
            ),
    ) {
        AsyncImage(
            model = BigShelfApp.api.mediaUrl(photo.imageSrc),
            contentDescription = photo.title ?: photo.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        verdict?.let {
            Text(
                it.label.uppercase(),
                Modifier
                    .align(if (it == Verdict.Keep) Alignment.TopStart else if (it == Verdict.Bin) Alignment.TopEnd else Alignment.TopCenter)
                    .padding(24.dp)
                    .border(2.dp, it.color, BsShapes.Md)
                    .background(Color.Black.copy(alpha = 0.4f), BsShapes.Md)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = it.color,
                style = BsText.Lg.copy(fontWeight = BsWeight.Semibold),
            )
        }
    }
}
