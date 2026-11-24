package com.bigshelf.app.ui.screens.tools

import android.content.ClipData
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import com.bigshelf.app.ui.shell.rememberUploadPicker
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Downloads
import com.bigshelf.app.data.PhotoSet
import com.bigshelf.app.data.ViewSettings
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.UploadStore
import com.bigshelf.app.state.ViewerList
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.ImageCard
import com.bigshelf.app.ui.screens.people.PersonList
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsText
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

const val TRIPLETS_ENDPOINT = "/api/v1/triplets"

/**
 * The Settings options [endpoint] takes, as its query params (the web's endpointParams). Only
 * the ones it reads, so an option for another view never resets this one's sets.
 */
private fun setOptions(endpoint: String, s: ViewSettings): Map<String, Any?> {
    val common = mapOf<String, Any?>("include_ai" to s.includeAi)
    return when (endpoint) {
        TRIPLETS_ENDPOINT -> common + mapOf(
            "repair_only" to true, "count" to s.tripletCount, "similar_faces" to s.tripletMatchFaces,
            "similar_outfit" to s.tripletOutfit, "similar_colors" to s.tripletMatchColors,
            "similar_scene" to s.tripletMatchScene,
            "order" to s.tripletOrder,
        )
        else -> common
    }
}
const val COMPOSER_ENDPOINT = "/api/v1/composer"

private const val REPAIR_PROMPT = """Repair each image whose filename begins with repair-. Use files beginning with reference- only as visual references for identity, clothing, or scene. Return one separate corrected image per repair file.
Inspect each original and correct visible issues such as awkward framing, exposure, colour cast, noise, or mild blur. Preserve the person's identity, expression, body proportions, clothing, and setting. Keep edits natural. If detail is too damaged to recover faithfully, say so instead of inventing it."""

// Upload both photos in order (the face photo first, then the outfit) with this prompt.
const val OUTFIT_SWAP_PROMPT = """Edit the first image: replace the person's outfit with the outfit worn in the second image. Both images show the same person.
Keep everything else from the first image exactly as it is: the face and facial identity, expression, hair, skin tone, body shape and proportions, pose, hands, background, lighting, camera angle and framing.
From the second image take only the clothes, plus shoes and accessories where they show: the same garments, colours, fabric, pattern, texture and cut. Fit them naturally to the first image's body and pose, with realistic folds, creases and drape; do not tighten, distort or body-enhance them. Where the first image's pose or framing hides part of the outfit, continue it plausibly in the same style.
Do not take the face, hair, pose, background or lighting from the second image. Relight the outfit to match the first image's light direction, shadows and white balance so it looks photographed in the same moment.
Keep the first image's aspect ratio and resolution, and its photographic look: natural skin texture, grain and colour.
Avoid: changing or beautifying the face, slimming or reshaping the body, plastic skin, AI look, over-sharpening, HDR, extra limbs, distorted hands, text, watermark."""

// Last position and person filter per endpoint, kept outside composition so opening a photo
// and coming back resumes the same set (web `lastOffset` / `lastPerson`).
// ponytail: in-memory only, an app restart starts over
private val lastOffset = mutableMapOf<String, Int>()
private val lastPerson = mutableMapOf<String, String?>()

/**
 * Port of modules/triptych: one set of photos at a time with Download and Next. Triplets
 * shows up to the requested number of matching photos; Composer a clear
 * face plus another outfit, with the image-model [prompt] to copy. Sets showing one person
 * can skip that person for good; a person filter limits sets to one person.
 */
@Composable
fun TriptychScreen(
    title: String,
    subtitle: String,
    endpoint: String,
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    prompt: String? = null,
    // Shows a gear in the header (Triplets: its settings, edited in place)
    onSettings: (() -> Unit)? = null,
) {
    val api = BigShelfApp.api
    val settings = BigShelfApp.settings
    val repairMode = endpoint == TRIPLETS_ENDPOINT
    val editPrompt = if (repairMode) REPAIR_PROMPT else prompt
    val uploadFixes = if (repairMode) rememberUploadPicker() else null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var offset by remember { mutableStateOf(lastOffset[endpoint] ?: 0) }
    var onlyPerson by remember { mutableStateOf(lastPerson[endpoint]) }
    // The endpoint's query params from Settings, plus the person filter; a change starts fresh sets
    val options = setOptions(endpoint, settings) + ("person_id" to onlyPerson)

    // Keep the visible set and four prepared sets; options changes get a fresh cache.
    val cache = remember(options) { mutableStateMapOf<Int, PhotoSet>() }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    // Keep the last set on screen while the next one loads (keepPreviousData)
    var shown by remember(options) { mutableStateOf<PhotoSet?>(null) }
    var revision by remember { mutableStateOf(0) }

    suspend fun fetch(at: Int) = api.photoSet(endpoint, at, options)

    fun moveTo(next: Int) {
        offset = next
        lastOffset[endpoint] = next
    }

    val current = cache[offset]
    val loading = current == null && !failed
    LaunchedEffect(current) {
        if (current != null) shown = current
    }

    // One loader per filter, so Next can adopt the request already preparing that set.
    LaunchedEffect(options, revision) {
        snapshotFlow { offset }.collect {
            failed = false
            try {
                while (true) {
                    cache.keys.filter { it < offset || it >= offset + 5 }.forEach { cache.remove(it) }
                    val at = (offset until offset + 5).firstOrNull { it !in cache } ?: break
                    if (cache[at - 1]?.photos?.isEmpty() == true) break
                    val next = fetch(at)
                    coroutineContext.ensureActive()
                    cache[at] = next
                    if (next.photos.isEmpty()) break
                    if (at > offset) {
                        val loader = SingletonImageLoader.get(context)
                        next.photos.forEach { photo ->
                            photo.thumbnailUrl?.let {
                                loader.enqueue(ImageRequest.Builder(context).data(api.mediaUrl(it)).build())
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed warm-up retries when the user reaches its position.
                failed = cache[offset] == null
            }
        }
    }

    fun refetchShifted() {
        cache.keys.filter { it >= offset }.forEach { cache.remove(it) }
        revision++
    }

    LaunchedEffect(endpoint, options) {
        if (endpoint == TRIPLETS_ENDPOINT) {
            PhotoEvents.invalidations.collect { keys ->
                if ("triplets" in keys && cache.containsKey(offset)) {
                    cache.keys.filter { it > offset }.forEach { cache.remove(it) }
                    revision++
                }
            }
        }
    }

    fun setPerson(id: String?) {
        onlyPerson = id
        lastPerson[endpoint] = id
        moveTo(0)
    }

    val set = current ?: shown
    val photos = set?.photos.orEmpty()
    val repairIds = photos.filter { it.repairTarget == true }.map { it.id }
    val canRepair = repairIds.isNotEmpty() && photos.any { it.repairTarget == false }
    val uploading = UploadStore.previews.any { it.photoId == null }

    fun download() {
        if (photos.isEmpty() || busy != null || (repairMode && !canRepair)) return
        busy = "download"
        scope.launch {
            try {
                Downloads.save(context, photos, repairSet = repairMode)
                toast.success("Downloading ${photos.size} photos")
                if (!repairMode) {
                    if (settings.archiveOnDownload) refetchShifted() else moveTo(offset + 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                toast.error("Download failed")
            } finally {
                busy = null
            }
        }
    }

    fun archiveOriginals() {
        if (repairIds.isEmpty() || busy != null || uploading) return
        busy = "archive"
        scope.launch {
            try {
                api.flagArchived(repairIds, true)
                PhotoEvents.invalidateAll()
                refetchShifted()
                toast.success("Repair originals archived")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                toast.error("Couldn't archive the repair originals")
            } finally {
                busy = null
            }
        }
    }

    fun skipPerson() {
        val personId = set?.personId ?: return
        if (busy != null) return
        busy = "skip"
        scope.launch {
            try {
                api.skipPerson(personId)
                PhotoEvents.invalidate("people")
                toast.success("${set.personName ?: "This person"} won't appear in Matches again")
                // Filtered to them: they have no sets left, so go back to everyone
                if (onlyPerson == personId) setPerson(null) else refetchShifted()
            } catch (_: Exception) {
                toast.error("Couldn't skip this person")
            } finally {
                busy = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title,
            count = subtitle,
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            if (onSettings != null) BsIconButton(BsIcons.Gear, "${title} settings", onClick = onSettings)
            BsIconButton(BsIcons.Funnel, "Only this person", onClick = { picking = true }, active = onlyPerson != null)
            if (editPrompt != null && (!repairMode || canRepair)) {
                BsIconButton(BsIcons.Copy, if (repairMode) "Copy repair prompt" else "Copy prompt", onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Prompt", editPrompt))
                    toast.success("Prompt copied")
                })
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                set == null && loading -> Spinner(Modifier.align(Alignment.Center), size = 20.dp, tint = BsColors.Muted)
                failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unable to load ${title.lowercase()}")
                    BsButton(onClick = { revision++ }) { Text("Retry") }
                }
                photos.isEmpty() -> EmptyState(
                    if (repairMode) "No repair sets with a good reference are available" else "No ${title.lowercase()} are available yet",
                    Modifier.padding(horizontal = 24.dp),
                )
                else -> LazyVerticalGrid(
                    // Two across for a few photos, three when the set is bigger
                    columns = GridCells.Fixed(if (photos.size <= 4) 2 else 3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val selecting = SelectionStore.count > 0
                        Column {
                            if (repairMode) {
                                Text(
                                    if (photo.repairTarget == true) "Repair original" else "Reference",
                                    style = BsText.Xs,
                                    color = BsColors.Foreground,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            ImageCard(
                                photo = photo,
                                selected = SelectionStore.isSelected(photo.id),
                                selectionActive = selecting,
                                onClick = {
                                    // While selecting, a tap toggles instead of opening (same as the grids)
                                    if (selecting) SelectionStore.toggle(photo)
                                    else {
                                        ViewerList.photos = photos
                                        onOpenPhoto(photo.id)
                                    }
                                },
                                onToggle = { SelectionStore.toggle(photo) },
                            )
                        }
                    }
                }
            }
        }

        if (photos.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = LocalBottomBarInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uploadFixes != null) {
                    Text(
                        "Open the photos in ChatGPT with the repair prompt. Upload the fixes here, then archive the originals.",
                        style = BsText.Xs,
                        color = BsColors.Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BsButton(
                            onClick = uploadFixes,
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Secondary,
                            enabled = repairIds.isNotEmpty() && busy == null && !loading && !uploading,
                        ) { Text("Upload fixes") }
                        BsButton(
                            onClick = ::archiveOriginals,
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Secondary,
                            enabled = repairIds.isNotEmpty() && busy == null && !loading && !uploading,
                            loading = busy == "archive",
                        ) { Text("Archive originals") }
                    }
                }
                // Shown when the set names its person (outfit sets of untagged photos don't)
                if (set?.personId != null) {
                    BsButton(
                        onClick = ::skipPerson,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.DangerGhost,
                        enabled = busy == null && !loading,
                        loading = busy == "skip",
                    ) {
                        BsIcon(BsIcons.UserMinus, size = 16.dp)
                        Text("Skip ${set.personName ?: "person"}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BsButton(onClick = ::download, modifier = Modifier.weight(1f), enabled = busy == null && !loading && !failed && (!repairMode || canRepair), loading = busy == "download") {
                        BsIcon(BsIcons.DownloadSimple, size = 16.dp)
                        Text(if (repairMode) "Download for repair" else "Download")
                    }
                    BsButton(
                        onClick = { moveTo(offset + 1) },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Secondary,
                        enabled = (busy == null || (repairMode && busy == "download")) && !loading && !failed,
                        loading = loading,
                    ) {
                        BsIcon(BsIcons.ArrowRight, size = 16.dp)
                        Text("Next")
                    }
                }
            }
        }
    }

    if (picking) {
        // Skipped people never appear here, so they're left out
        val people = rememberQuery("people-all", invalidatedBy = setOf("people")) { api.people().items }
        BsModal(
            title = "Only this person",
            onClose = { picking = false },
            footer = {
                BsButton(onClick = { setPerson(null); picking = false }, variant = ButtonVariant.Secondary, enabled = onlyPerson != null) {
                    Text("All people")
                }
            },
        ) {
            PersonList(
                people = people.data.orEmpty().filter { !it.isSkipped && it.photoCount > 0 },
                selectedId = onlyPerson,
                empty = if (people.isLoading) null else "No people found",
                onPick = {
                    setPerson(it.id)
                    picking = false
                },
            )
        }
    }
}
