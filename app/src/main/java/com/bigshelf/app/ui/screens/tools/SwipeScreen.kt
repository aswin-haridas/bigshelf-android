package com.bigshelf.app.ui.screens.tools

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.math.abs

// Pairs fetched per call (2 photos each); more are fetched once the queue runs this low (web useSwipe)
private const val SWIPE_WINDOW = 10
private const val SWIPE_REFILL_AT = 3
private const val SWIPE_UNDO_LIMIT = 3
// How far a drag must travel to count as a swipe
private val THRESHOLD = 100.dp

private enum class Verdict(val label: String, val color: Color) {
    Keep("Keep", BsColors.Success),
    Bin("Bin", BsColors.Danger),
    Favorite("Favourite", Color(0xFFF472B6)),
}

// A swipe; `compared` completes with the Rating answer it produced, if any
private class Swipe(val photo: Photo, val verdict: Verdict, val compared: CompletableDeferred<String?>)

/**
 * Port of modules/swipe: one photo at a time, swipe left to bin, right to keep, up to favourite.
 * Cleanup and rating at once: every kept photo is recorded as hotter than the last binned one
 * (and vice versa), so swipes train the same taste model Rating does.
 */
@Composable
fun SwipeScreen(onBack: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val queue = remember { mutableStateListOf<Photo>() }
    val history = remember { mutableStateListOf<Swipe>() }
    // Every photo shown this session, so a refill never repeats one
    val seen = remember { mutableSetOf<String>() }
    // Last kept / binned photo, the opponent for the next swipe the other way
    var lastKept by remember { mutableStateOf<Photo?>(null) }
    var lastBinned by remember { mutableStateOf<Photo?>(null) }
    var count by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    // Photos come from the Rating pairs: live, embedded, least-compared first
    LaunchedEffect(queue.size <= SWIPE_REFILL_AT) {
        if (queue.size > SWIPE_REFILL_AT) return@LaunchedEffect
        try {
            val fresh = api.ratingPairs(SWIPE_WINDOW).pairs.flatMap { it.items }.filter { seen.add(it.id) }
            queue.addAll(fresh)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            toast.error("Failed to load photos")
        } finally {
            loading = false
        }
    }

    val current = queue.firstOrNull()
    val next = queue.getOrNull(1)

    // Warm the cache with the next photo so it appears instantly
    LaunchedEffect(next?.id) {
        next?.imageSrc?.let {
            SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(api.mediaUrl(it)).build())
        }
    }

    fun refresh() = PhotoEvents.invalidate("photo")

    fun swipe(photo: Photo, verdict: Verdict) {
        queue.remove(photo)
        count++
        val opponent = if (verdict == Verdict.Bin) lastKept else lastBinned
        if (verdict == Verdict.Bin) lastBinned = photo else lastKept = photo
        val entry = Swipe(photo, verdict, CompletableDeferred())
        history.add(entry)
        if (history.size > SWIPE_UNDO_LIMIT) history.removeAt(0)
        scope.launch {
            try {
                when (verdict) {
                    Verdict.Bin -> api.flagDeleted(listOf(photo.id), true)
                    Verdict.Favorite -> api.setFavorite(photo.id, true)
                    Verdict.Keep -> Unit
                }
            } catch (_: Exception) {
                toast.error(if (verdict == Verdict.Bin) "Failed to move to Bin" else "Failed to favourite")
            }
            if (opponent == null) {
                entry.compared.complete(null)
                return@launch
            }
            try {
                val id = if (verdict == Verdict.Bin) api.compare(opponent.id, photo.id) else api.compare(photo.id, opponent.id)
                entry.compared.complete(id)
                refresh()
            } catch (_: Exception) {
                entry.compared.complete(null)
                toast.error("Failed to save the rating")
            }
        }
    }

    // Walk back the latest swipe: the photo comes back on top and its effects are reverted
    fun undo() {
        val last = history.removeLastOrNull() ?: return
        queue.add(0, last.photo)
        count = maxOf(count - 1, 0)
        // Don't rate the next swipe against a verdict that was taken back
        if (lastKept?.id == last.photo.id) lastKept = null
        if (lastBinned?.id == last.photo.id) lastBinned = null
        scope.launch {
            try {
                when (last.verdict) {
                    Verdict.Bin -> api.flagDeleted(listOf(last.photo.id), false)
                    Verdict.Favorite -> api.setFavorite(last.photo.id, false)
                    Verdict.Keep -> Unit
                }
                last.compared.await()?.let { api.undoCompare(it) }
                refresh()
            } catch (_: Exception) {
                toast.error("Failed to undo")
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
            loading -> LoadingText(text = "Picking photos…")
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
                    key(current.id) { SwipeCard(current, card) { swipe(current, it) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VerdictButton(BsIcons.Trash, "Bin") { swipe(current, Verdict.Bin) }
                    VerdictButton(BsIcons.Heart, "Favourite") { swipe(current, Verdict.Favorite) }
                    VerdictButton(BsIcons.CheckBold, "Keep") { swipe(current, Verdict.Keep) }
                }
            }
        }
    }
}

@Composable
private fun VerdictButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    BsButton(onClick = onClick, variant = ButtonVariant.Secondary) {
        BsIcon(icon, size = 14.dp)
        Text(label)
    }
}

// The draggable photo; `onSwipe` null is the next one, peeking under the top card
@Composable
private fun SwipeCard(photo: Photo, modifier: Modifier, onSwipe: ((Verdict) -> Unit)?) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val threshold = with(LocalDensity.current) { THRESHOLD.toPx() }
    fun verdictOf(o: Offset) = when {
        -o.y > threshold && -o.y > abs(o.x) -> Verdict.Favorite
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
