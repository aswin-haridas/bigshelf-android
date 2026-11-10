package com.bigshelf.app.ui.screens.tools

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

// Photos fetched per call; more are fetched once the queue runs this low
private const val SWIPE_WINDOW = 20
private const val SWIPE_REFILL_AT = 3
private const val SWIPE_UNDO_LIMIT = 3
// A keep this soon (ms) after the photo came up is a gut "yes" and gets the bigger boost. Triplets draw
// photos scored above 6/10 less often (almost never past 8.5), so both keeps push the photo toward rare.
private const val QUICK_KEEP_MS = 1500L
// How far a drag must travel to count as a swipe
private val THRESHOLD = 100.dp

private enum class Verdict(val label: String, val color: Color, val icon: ImageVector) {
    Keep("Keep", BsColors.Success, BsIcons.CheckBold),
    Bin("Bin", BsColors.Danger, BsIcons.Trash),
    Favorite("Favourite", Color(0xFFF472B6), BsIcons.Heart),
    Rework("Rework", Color(0xFFFBBF24), BsIcons.Wrench),
}

// Which verdict a drag is heading for, and how far there it is (1 = past the threshold)
private fun leanOf(o: Offset, threshold: Float): Pair<Verdict, Float>? = when {
    -o.y > abs(o.x) -> Verdict.Rework to -o.y / threshold
    o.x > 0f -> Verdict.Keep to o.x / threshold
    o.x < 0f -> Verdict.Bin to -o.x / threshold
    else -> null
}

internal fun swipeTastePoints(elapsedMs: Long, rework: Boolean = false): Double = when {
    rework -> -1.0
    elapsedMs < QUICK_KEEP_MS -> 5.0
    else -> 3.0
}

// Filled in by its write once that lands; `saved` false means there's nothing to revert
private class Swipe(var photo: Photo, val verdict: Verdict) {
    var points = 0.0
    var previousKept = false
    var previousRework = false
    var saved = false
    var write: Job? = null
}

/** Unreviewed photos only; swipes adjust their taste score directly. */
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

    // The seen set keeps refills from repeating photos, even after their taste scores change.
    // Watches the queue size instead of keying on it: keying restarted the effect on every swipe,
    // cancelling the fetch in flight, and its loading = false flashed "Nothing left to swipe"
    // until the restarted fetch landed. A fetch now runs to the end while swipes continue.
    LaunchedEffect(refill) {
        snapshotFlow { queue.size }.filter { it <= SWIPE_REFILL_AT }.collect {
            loading = true
            loadError = false
            try {
                val fresh = api.swipePhotos(seen, SWIPE_WINDOW).filter { seen.add(it.id) }
                queue.addAll(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loadError = true
            } finally {
                loading = false
            }
        }
    }

    val current = queue.firstOrNull()
    val next = queue.getOrNull(1)
    // When the photo on top came up, to time the swipe on it
    var shownAt by remember { mutableStateOf(0L) }
    LaunchedEffect(current?.id) { shownAt = SystemClock.elapsedRealtime() }

    // Warm the cache with the next photo so it appears instantly
    LaunchedEffect(next?.id) {
        (next?.thumbnailUrl ?: next?.imageSrc)?.let {
            SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(api.mediaUrl(it)).build())
        }
    }

    fun refresh() = PhotoEvents.invalidate("photos", "photo", "bin-photos", "people")

    // Optimistic: the card moves on at once and the write runs behind it. A failed write puts the
    // photo back on top without a word. NonCancellable so leaving the screen doesn't drop a write.
    fun swipe(photo: Photo, verdict: Verdict) {
        val points = when (verdict) {
            Verdict.Keep -> swipeTastePoints(SystemClock.elapsedRealtime() - shownAt)
            Verdict.Rework -> swipeTastePoints(0, rework = true)
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
                        val adjustment = api.boostTaste(photo.id, points, rework = if (verdict == Verdict.Rework) true else null, kept = if (verdict == Verdict.Keep) true else null)
                        entry.points = adjustment.points
                        entry.previousRework = adjustment.previousRework
                        entry.previousKept = adjustment.previousKept
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
                    Verdict.Rework -> api.boostTaste(last.photo.id, -last.points, rework = last.previousRework)
                    Verdict.Keep -> api.boostTaste(last.photo.id, -last.points, kept = last.previousKept)
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
            else -> {
                // A fresh offset per photo, so each card starts centred
                val offset = remember(current.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
                val threshold = with(LocalDensity.current) { THRESHOLD.toPx() }
                val lean = leanOf(offset.value, threshold)
                val committed = lean?.takeIf { it.second >= 1f }?.first
                val haptics = LocalHapticFeedback.current
                LaunchedEffect(committed) { if (committed != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

                Column(
                    Modifier
                        .fillMaxSize()
                        // The drag works anywhere below the header; taps still reach the buttons
                        .pointerInput(current.id) {
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    change.consume()
                                    scope.launch { offset.snapTo(offset.value + amount) }
                                },
                                onDragEnd = {
                                    leanOf(offset.value, threshold)?.takeIf { it.second >= 1f }?.let { swipe(current, it.first) }
                                        ?: scope.launch { offset.animateTo(Offset.Zero) }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                            )
                        }
                        .padding(horizontal = 16.dp)
                        .padding(bottom = LocalBottomBarInset.current + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The 3:4 card takes the widest size that fits both width and height
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val cardModifier = if (maxWidth / maxHeight < 3f / 4f) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
                        val card = cardModifier.aspectRatio(3f / 4f, matchHeightConstraintsFirst = maxWidth / maxHeight >= 3f / 4f)
                        next?.let { key(it.id) { SwipeCard(it, card.graphicsLayer { scaleX = 0.95f; scaleY = 0.95f; alpha = 0.6f }) } }
                        key(current.id) { SwipeCard(current, card, offset.value, lean) }
                    }
                    if (loadError) LoadError(onRetry = { refill++ })
                    // Left to right as the swipes go: Bin is a left swipe, Keep a right one
                    Row(Modifier.fillMaxWidth()) {
                        for (v in listOf(Verdict.Bin, Verdict.Rework, Verdict.Favorite, Verdict.Keep)) {
                            VerdictButton(v, active = committed == v) { swipe(current, v) }
                        }
                    }
                }
            }
        }
    }
}

/** Equal-width thumb target: the verdict's colour on a 56dp circle, filled while a drag is set to pick it. */
@Composable
private fun RowScope.VerdictButton(verdict: Verdict, active: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(if (active) verdict.color else BsColors.Surface, label = "fill")
    val pop by animateFloatAsState(if (active) 1.12f else 1f, label = "pop")
    Column(
        Modifier.weight(1f).bsClickable(scale = 0.92f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(56.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(BsShapes.Full).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            BsIcon(verdict.icon, size = 24.dp, tint = if (active) Color.White else verdict.color)
        }
        FitText(verdict.label, BsText.Xs2.copy(color = if (active) verdict.color else BsColors.Muted))
    }
}

// The photo, moved by the screen's drag. `lean` fades in the verdict it's heading for: a colour wash
// over the photo and a centred icon that grows with the drag and pops once letting go would pick it.
@Composable
private fun SwipeCard(photo: Photo, modifier: Modifier, offset: Offset = Offset.Zero, lean: Pair<Verdict, Float>? = null) {
    val committed = lean != null && lean.second >= 1f
    val pop by animateFloatAsState(if (committed) 1.15f else 1f, label = "pop")
    Box(
        modifier
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y
                rotationZ = offset.x / 20f
            }
            .clip(BsShapes.Lg)
            .background(BsColors.Surface),
    ) {
        AsyncImage(
            model = BigShelfApp.api.mediaUrl(photo.thumbnailUrl ?: photo.imageSrc),
            contentDescription = photo.title ?: photo.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        lean?.let { (verdict, progress) ->
            val p = progress.coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().background(verdict.color.copy(alpha = 0.35f * p)))
            Column(
                Modifier.align(Alignment.Center).graphicsLayer {
                    val s = (0.6f + 0.4f * p) * pop
                    scaleX = s; scaleY = s; alpha = p
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(88.dp).clip(BsShapes.Full).background(verdict.color).border(3.dp, Color.White, BsShapes.Full),
                    contentAlignment = Alignment.Center,
                ) {
                    BsIcon(verdict.icon, size = 40.dp, tint = Color.White)
                }
                Text(
                    verdict.label,
                    Modifier.background(Color.Black.copy(alpha = 0.45f), BsShapes.Full).padding(horizontal = 14.dp, vertical = 4.dp),
                    color = Color.White,
                    style = BsText.Lg.copy(fontWeight = BsWeight.Semibold),
                )
            }
        }
    }
}
