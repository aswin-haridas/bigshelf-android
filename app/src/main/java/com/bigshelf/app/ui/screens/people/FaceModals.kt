package com.bigshelf.app.ui.screens.people

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import com.bigshelf.app.ui.components.TriangleLoader
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.FaceItem
import com.bigshelf.app.data.Person
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsInput
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.FaceTile
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A face group's crop, or the on-demand endpoint that regenerates a missing one. */
fun faceImageSrc(face: FaceItem) = face.faceThumbnail ?: "/api/v1/faces/${face.id}/thumbnail"

// Every face mutation can change people, face groups and the photos showing them
private fun invalidateFaces() = PhotoEvents.invalidate("faces", "people", "photos", "photo")

/**
 * Port of tag-face-modal.tsx: name a detected face group with a new name or an existing
 * person (filtered as you type, lookalikes first). [onDone] runs once it's tagged.
 */
@Composable
fun TagFaceModal(face: FaceItem, onClose: () -> Unit, onDone: () -> Unit = {}) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<Person>?>(null) }
    // Typed name filters server-side; the face id ranks lookalikes first. Debounced 250ms.
    LaunchedEffect(name) {
        delay(250)
        people = runCatching { api.people(name.trim(), faceId = face.id).items }.getOrNull() ?: people
    }

    fun tag(personId: String?, label: String) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val result = api.tagFace(face.id, name = label, personId = personId)
                invalidateFaces()
                val tagged = result.personName.ifEmpty { label }
                toast.success(
                    if (result.autoTaggedCount > 0) "Tagged as $tagged & linked ${result.autoTaggedCount} matching faces"
                    else "Tagged as $tagged",
                )
                onClose()
                onDone()
            } catch (e: Exception) {
                toast.error(e.userMessage("Failed to tag face"))
            } finally {
                saving = false
            }
        }
    }

    val clean = name.trim()
    BsModal(
        title = "Tag face",
        onClose = onClose,
        footer = {
            BsButton(onClick = { tag(null, clean) }, enabled = clean.isNotEmpty() && !saving, loading = saving, textStyle = BsText.Xs) {
                Text("Save tag")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FaceTile(imageSrc = faceImageSrc(face), name = null, subtitle = null, onClick = {}, modifier = Modifier.size(64.dp))
                BsInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Enter person name",
                    autoFocus = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (clean.isNotEmpty()) tag(null, clean) }),
                )
            }
            Text("Or select existing person", style = BsText.Xs, color = BsColors.Muted)
            PersonList(
                people = people.orEmpty(),
                enabled = !saving,
                empty = if (people == null) null else "No people found",
                onPick = { tag(it.id, it.name) },
            )
        }
    }
}

/**
 * Port of merge-face-group-modal.tsx: move every face of [source]'s group into another
 * unnamed group - for one person clustering split in two. One card per group.
 */
@Composable
fun MergeFaceGroupModal(source: FaceItem, onClose: () -> Unit, onMerged: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val faces = rememberQuery("faces", invalidatedBy = setOf("faces")) { api.faces() }
    var target by remember { mutableStateOf<String?>(null) }
    var merging by remember { mutableStateOf(false) }
    val candidates = faces.data.orEmpty()
        .filter { it.faceName == null && !it.isIgnored && it.groupKey != source.groupKey }
        .distinctBy { it.groupKey }

    BsModal(
        title = "Merge into another face",
        onClose = onClose,
        footer = {
            BsButton(onClick = onClose, variant = ButtonVariant.Ghost, enabled = !merging, textStyle = BsText.Xs) { Text("Cancel") }
            BsButton(
                onClick = {
                    val to = target ?: return@BsButton
                    merging = true
                    scope.launch {
                        try {
                            val res = api.mergeFaceGroup(source.id, to)
                            // Whatever else the backend reports as stale, plus the groups themselves
                            PhotoEvents.invalidate(*(res.invalidate + "faces").toTypedArray())
                            toast.success("Merged into the selected face group")
                            onClose()
                            onMerged()
                        } catch (_: Exception) {
                            toast.error("Failed to merge face groups")
                        } finally {
                            merging = false
                        }
                    }
                },
                enabled = target != null && !merging,
                loading = merging,
                textStyle = BsText.Xs,
            ) { Text("Merge groups") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "All faces in this group move into the group you pick below.",
                style = BsText.Xs,
                color = BsColors.Muted,
            )
            when {
                faces.isLoading -> TriangleLoader(Modifier.fillMaxWidth().wrapContentWidth().padding(vertical = 16.dp))
                candidates.isEmpty() -> Text("No other detected face groups available.", style = BsText.Xs, color = BsColors.Muted)
                else -> Column(
                    Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    candidates.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { face ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .border(2.dp, if (face.id == target) BsColors.Foreground else Color.Transparent, BsShapes.Md),
                                ) {
                                    FaceTile(imageSrc = faceImageSrc(face), name = null, subtitle = null, onClick = { target = face.id })
                                }
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Port of merge-person-modal.tsx: [source]'s photos move to the person picked, then
 * [source] is removed. Faces in the list tell apart people with similar names.
 */
@Composable
fun MergePersonModal(source: Person, onClose: () -> Unit, onMerged: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val people = rememberQuery("people-all", invalidatedBy = setOf("people")) { api.people().items }
    var search by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Person?>(null) }
    var merging by remember { mutableStateOf(false) }
    val candidates = people.data.orEmpty()
        .filter { it.id != source.id && it.name.contains(search.trim(), ignoreCase = true) }

    BsModal(
        title = "Merge into…",
        onClose = onClose,
        footer = {
            BsButton(onClick = onClose, variant = ButtonVariant.Ghost, enabled = !merging, textStyle = BsText.Xs) { Text("Cancel") }
            BsButton(
                onClick = {
                    val to = target ?: return@BsButton
                    merging = true
                    scope.launch {
                        try {
                            api.mergePeople(source.id, to.id)
                            invalidateFaces()
                            toast.success("Merged \"${source.name}\" into \"${to.name}\"")
                            onClose()
                            onMerged()
                        } catch (_: Exception) {
                            toast.error("Failed to merge people")
                        } finally {
                            merging = false
                        }
                    }
                },
                enabled = target != null && !merging,
                loading = merging,
                textStyle = BsText.Xs,
            ) { Text("Merge into ${target?.name ?: "person"}") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Photos of ${source.name} move to the person you pick, then this entry is removed.", style = BsText.Xs, color = BsColors.Muted)
            BsSearchBar(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Search people")
            PersonList(
                people = candidates,
                selectedId = target?.id,
                enabled = !merging,
                empty = when {
                    people.isLoading -> null
                    search.isNotBlank() -> "No matching people found."
                    else -> "No other people available."
                },
                onPick = { target = it },
            )
        }
    }
}
