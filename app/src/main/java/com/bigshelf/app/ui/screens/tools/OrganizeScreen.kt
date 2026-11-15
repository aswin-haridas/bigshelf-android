package com.bigshelf.app.ui.screens.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.MonoFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val ROUND_SIZE = 5

// Same order as the web Organize review. Match questions use the server's signal filter.
internal enum class OrganizeKind(val question: String, val isMatch: Boolean = false) {
    OUTFIT("Are they wearing the same outfit?", isMatch = true),
    PERSON("Is it the same person in both photos?", isMatch = true),
    SCENE("Were these taken in the same setting?", isMatch = true),
    HUMAN("Is there a person in this photo?"),
    COLLAGE("Is this a collage of several photos?"),
    BIN("Is this photo bad enough to bin?"),
    DUPLICATE("Are these copies of the same photo?"),
    FACE("");

    val next get() = entries[(ordinal + 1) % entries.size]
    val apiName get() = name.lowercase()
}

internal data class OrganizeRound(
    val kind: OrganizeKind = OrganizeKind.OUTFIT,
    val count: Int = 0,
    val skip: String? = null,
) {
    fun forKind(loaded: OrganizeKind) = if (loaded == kind) this else OrganizeRound(loaded)

    fun advance(skip: String?): OrganizeRound =
        if (count + 1 == ROUND_SIZE) OrganizeRound(kind.next) else copy(count = count + 1, skip = skip)
}

private data class ReviewImage(val source: String?, val description: String, val caption: String? = null)

private data class ReviewPrompt(
    val question: String,
    val images: List<ReviewImage>,
    val photos: List<Photo> = emptyList(),
    val save: suspend (Boolean?) -> Unit,
)

private class OrganizeReviewModel : ViewModel() {
    private val api = BigShelfApp.api
    var round by mutableStateOf(OrganizeRound())
        private set
    var prompt by mutableStateOf<ReviewPrompt?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun retry() = load(round.copy(skip = null))

    fun nextKind() {
        if (!saving && prompt != null) load(OrganizeRound(round.kind.next))
    }

    fun answer(value: Boolean?) {
        val current = prompt ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                current.save(value)
                load(round.advance(current.photos.firstOrNull()?.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast.error(e.userMessage("Couldn’t save the answer"))
            } finally {
                saving = false
            }
        }
    }

    private fun load(start: OrganizeRound = round) {
        if (loading) return
        round = start
        prompt = null
        loadFailed = false
        loading = true
        viewModelScope.launch {
            try {
                var kind = start.kind
                repeat(OrganizeKind.entries.size) {
                    val candidate = start.forKind(kind)
                    val question = fetch(candidate)
                    if (question != null) {
                        round = candidate
                        prompt = question
                        return@launch
                    }
                    kind = kind.next
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loadFailed = true
            } finally {
                loading = false
            }
        }
    }

    private suspend fun fetch(candidate: OrganizeRound): ReviewPrompt? {
        val kind = candidate.kind
        if (kind.isMatch) {
            val photos = api.matchFeedbackPair(kind.apiName, candidate.skip)
            if (photos.size != 2) return null
            return photoPrompt(kind, photos) { answer ->
                api.saveMatchFeedback(photos.map { it.id }, kind.apiName, answer)
                PhotoEvents.invalidate("people", "triptych")
            }
        }
        if (kind == OrganizeKind.FACE) {
            val batch = api.faceReview().batch ?: return null
            val face = batch.faces.firstOrNull() ?: return null
            return ReviewPrompt(
                question = "Is this ${batch.personName} too?",
                images = listOf(
                    ReviewImage(batch.reference.faceThumbnail, batch.personName, batch.personName),
                    ReviewImage(face.faceThumbnail ?: face.photoThumbnail, "Suggested face"),
                ),
            ) { answer ->
                api.resolveFaceReview(
                    batch.personId,
                    confirmed = if (answer == true) listOf(face.faceId) else emptyList(),
                    rejected = if (answer == false) listOf(face.faceId) else emptyList(),
                    unsure = if (answer == null) listOf(face.faceId) else emptyList(),
                )
                PhotoEvents.invalidate("face-confirmations", "faces", "people", "photos")
            }
        }
        val review = api.reviewQuestion(kind.apiName, candidate.skip) ?: return null
        if (review.photos.isEmpty()) return null
        return photoPrompt(kind, review.photos) { answer ->
            api.saveReviewAnswer(review, answer)
            PhotoEvents.invalidate("organize", "tags", "duplicates", "photos")
        }
    }

    private fun photoPrompt(kind: OrganizeKind, photos: List<Photo>, save: suspend (Boolean) -> Unit) =
        ReviewPrompt(
            question = kind.question,
            images = photos.map { ReviewImage(it.imageSrc, it.title ?: it.filename) },
            photos = photos,
        ) { answer ->
            // Not sure skips photo questions without recording a label.
            if (answer != null) save(answer)
        }
}

/** Organize is the human feedback loop: one question at a time about the pipeline's guesses. */
@Composable
fun OrganizeScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val review = viewModel { OrganizeReviewModel() }
    BackHandler(review.saving) {}

    val prompt = review.prompt
    // One slim toolbar row so the photos get nearly the whole screen
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BsIconButton(BsIcons.ArrowLeft, "Exit Organize", onBack, enabled = !review.saving, tint = BsColors.Foreground)
            Text(
                if (prompt != null) {
                    val prefix = if (review.round.kind.isMatch) "Pair " else ""
                    "${prefix}${review.round.count + 1} of $ROUND_SIZE"
                } else "Organize",
                Modifier.weight(1f).padding(start = 4.dp),
                style = BsText.Xs.copy(fontFamily = MonoFamily),
                color = BsColors.Muted,
                maxLines = 1,
            )
            if (prompt != null) {
                BsButton(review::nextKind, variant = ButtonVariant.Ghost, enabled = !review.saving) {
                    Text("Next question")
                    BsIcon(BsIcons.ArrowRight, size = 16.dp)
                }
            }
        }

        when {
            review.loading -> LoadingText(text = "Loading a question…")
            prompt == null -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    if (review.loadFailed) "Couldn’t load a question" else "Nothing to review",
                    description = if (review.loadFailed) "Try loading again." else "Questions come from processed photos in your library.",
                )
                BsButton(review::retry, variant = ButtonVariant.Secondary) { Text("Try again") }
            }
            else -> OrganizePrompt(prompt, review, onOpenPhoto)
        }
    }
}

@Composable
private fun OrganizePrompt(prompt: ReviewPrompt, review: OrganizeReviewModel, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    Column(
        Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Two photos split along the long side: stacked when tall, side by side when wide
            val tall = maxHeight >= maxWidth
            val gap = 4.dp
            @Composable
            fun Tile(index: Int, modifier: Modifier) {
                val image = prompt.images[index]
                val photo = prompt.photos.getOrNull(index)
                val tile = modifier.clip(BsShapes.Lg).background(BsColors.Surface)
                Box(
                    if (photo == null) tile else tile.clickable(
                        enabled = !review.saving,
                        onClickLabel = "Open full photo",
                    ) {
                        ViewerList.photos = prompt.photos
                        onOpenPhoto(photo.id)
                    },
                ) {
                    AsyncImage(
                        api.mediaUrl(image.source),
                        image.description,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    if (photo != null) {
                        BsIcon(
                            BsIcons.ArrowUpRight,
                            Modifier.align(Alignment.TopEnd).padding(6.dp)
                                .background(BsColors.Background, BsShapes.Full).padding(6.dp),
                            size = 16.dp,
                        )
                    }
                    image.caption?.let {
                        Text(
                            it,
                            Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .background(BsColors.Background.copy(alpha = 0.8f), BsShapes.Md)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = BsText.Xs, color = BsColors.MutedSoft,
                        )
                    }
                }
            }
            val count = prompt.images.size
            if (count <= 1 || !tall) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(count) { Tile(it, Modifier.weight(1f).fillMaxHeight()) }
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(count) { Tile(it, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
        Text(prompt.question, Modifier.fillMaxWidth().padding(horizontal = 8.dp), style = BsText.Base, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BsButton(
                { review.answer(true) }, Modifier.weight(1f).heightIn(min = 48.dp),
                variant = ButtonVariant.Secondary, enabled = !review.saving,
            ) {
                BsIcon(BsIcons.CheckCircleFill, size = 16.dp, tint = BsColors.Success)
                Text("Yes")
            }
            BsButton(
                { review.answer(false) }, Modifier.weight(1f).heightIn(min = 48.dp),
                variant = ButtonVariant.Secondary, enabled = !review.saving,
            ) {
                BsIcon(BsIcons.X, size = 16.dp, tint = BsColors.Danger)
                Text("No")
            }
            BsButton(
                { review.answer(null) }, Modifier.weight(1f).heightIn(min = 48.dp),
                variant = ButtonVariant.Ghost, enabled = !review.saving, loading = review.saving,
            ) { Text("Not sure", textAlign = TextAlign.Center) }
        }
    }
}
