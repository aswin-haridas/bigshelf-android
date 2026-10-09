package com.bigshelf.app.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsInput
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.BsTabs
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.TabItem
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.FaceTile
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.photos.personImageSrc
import com.bigshelf.app.ui.photos.photoCountLabel
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch
import com.bigshelf.app.data.Downloads
import androidx.compose.ui.platform.LocalContext

enum class PeopleTab { People, Faces, Untagged }

// Shared by every People-section page so the tab bar stays identical (web PEOPLE_TABS)
private val PEOPLE_TABS = listOf(
    TabItem(PeopleTab.People, "People"),
    TabItem(PeopleTab.Faces, "Detected Faces"),
    TabItem(PeopleTab.Untagged, "Untagged Photos"),
)

/** Square face grid: `grid-cols-2 gap-2` on phones, like people-grid.tsx. */
@Composable
private fun FaceGrid(content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Port of people-grid.tsx: tabs, people search, named people with photos. */
@Composable
fun PeopleScreen(onPerson: (String) -> Unit, onTab: (PeopleTab) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val query = rememberQuery("people", invalidatedBy = setOf("people")) { BigShelfApp.api.people() }
    // Hide people with no photos left (e.g. all their photos were binned)
    val people = query.data?.items.orEmpty().filter {
        it.photoCount > 0 && it.name.lowercase().contains(search.trim().lowercase())
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BsTabs(PEOPLE_TABS, PeopleTab.People, onTab)
            BsSearchBar(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Search people")
        }
        when {
            query.isLoading -> LoadingText()
            people.isEmpty() -> EmptyState(
                if (search.isNotEmpty()) "No people matching \"$search\"" else "No people yet",
                description = "Tag faces in your photos and they will show up here.",
            )
            else -> FaceGrid {
                items(people, key = { it.id }) { person ->
                    FaceTile(personImageSrc(person), person.name, photoCountLabel(person.photoCount), onClick = { onPerson(person.id) })
                }
            }
        }
    }
}

/** Detected Faces tab: face groups that aren't named yet. */
@Composable
fun FacesScreen(onFace: (String) -> Unit, onTab: (PeopleTab) -> Unit) {
    val query = rememberQuery("faces", invalidatedBy = setOf("faces")) { BigShelfApp.api.faces() }
    val faces = query.data.orEmpty().filterNot { it.isIgnored }
    Column(Modifier.fillMaxSize()) {
        BsTabs(PEOPLE_TABS, PeopleTab.Faces, onTab, Modifier.padding(bottom = 16.dp))
        when {
            query.isLoading -> LoadingText()
            faces.isEmpty() -> EmptyState("No detected faces", description = "Faces found in new photos will show up here.")
            else -> FaceGrid {
                items(faces, key = { it.id }) { face ->
                    FaceTile(
                        imageSrc = face.faceThumbnail ?: "/api/v1/faces/${face.id}/thumbnail",
                        name = face.faceName,
                        subtitle = face.faceCount?.let(::photoCountLabel),
                        onClick = { onFace(face.id) },
                    )
                }
            }
        }
    }
}

/** Untagged Photos tab: photos nobody is tagged in. */
@Composable
fun UntaggedScreen(onOpenPhoto: (String) -> Unit, onTab: (PeopleTab) -> Unit) {
    val query = rememberQuery("untagged", invalidatedBy = setOf("people")) { BigShelfApp.api.untaggedPhotos() }
    Column(Modifier.fillMaxSize()) {
        BsTabs(PEOPLE_TABS, PeopleTab.Untagged, onTab, Modifier.padding(bottom = 16.dp))
        if (query.isLoading) LoadingText()
        else ImagesGrid(
            photos = query.data?.items.orEmpty(),
            onOpen = { onOpenPhoto(it.id) },
            totalCount = query.data?.total,
            groupByDate = false,
        )
    }
}

/**
 * Port of person-photos-view.tsx: back, Download all, tap-to-rename title, photo grid in
 * the server's similarity order (no month headers).
 */
@Composable
fun PersonScreen(personId: String, onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val query = rememberQuery("person", personId, invalidatedBy = setOf("people")) { api.personPhotos(personId) }
    val data = query.data
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }

    fun save() {
        val clean = name.trim()
        if (clean.isEmpty() || clean == data?.person?.name) {
            editing = false
            return
        }
        saving = true
        scope.launch {
            try {
                api.renamePerson(personId, clean)
                PhotoEvents.invalidate("people")
                toast.success("Person renamed")
                editing = false
            } catch (_: Exception) {
                toast.error("Failed to rename person")
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BsButton(
                    onClick = {
                        val items = data?.items.orEmpty()
                        downloading = true
                        scope.launch {
                            try {
                                Downloads.save(context, items)
                                toast.success("Downloading ${items.size} photos")
                            } catch (_: Exception) {
                                toast.error("Download failed")
                            } finally {
                                downloading = false
                            }
                        }
                    },
                    variant = ButtonVariant.Secondary,
                    enabled = !downloading && !data?.items.isNullOrEmpty(),
                ) {
                    if (downloading) Spinner(size = 14.dp) else BsIcon(BsIcons.DownloadSimple, size = 14.dp, contentDescription = "Download all")
                }
                if (editing) {
                    BsInput(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.width(160.dp),
                        textStyle = BsText.Lg.copy(fontWeight = BsWeight.Semibold),
                        autoFocus = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                    )
                    BsButton(onClick = ::save, variant = ButtonVariant.Secondary, enabled = !saving && name.isNotBlank(), textStyle = BsText.Xs) {
                        Text("Save")
                    }
                } else {
                    Text(
                        data?.person?.name ?: "…",
                        Modifier.bsClickable(scale = 1f) {
                            name = data?.person?.name.orEmpty()
                            editing = true
                        },
                        style = BsText.Lg.copy(fontWeight = BsWeight.Semibold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            query.isLoading -> LoadingText()
            data == null || data.items.isEmpty() -> EmptyState("No photos found", description = "No photos found for this person.")
            else -> ImagesGrid(
                photos = data.items,
                onOpen = { onOpenPhoto(it.id) },
                totalCount = data.total,
                groupByDate = false,
            )
        }
    }
}

/** Photos of one detected face group. */
@Composable
fun FacePhotosScreen(faceId: String, onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val query = rememberQuery("face", faceId, invalidatedBy = setOf("faces")) { BigShelfApp.api.facePhotos(faceId) }
    val data = query.data
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground)
            Text(
                data?.face?.faceName ?: "Unnamed face",
                style = BsText.Lg.copy(fontWeight = BsWeight.Semibold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            query.isLoading -> LoadingText()
            data == null || data.items.isEmpty() -> EmptyState("No photos found")
            else -> ImagesGrid(photos = data.items, onOpen = { onOpenPhoto(it.id) }, totalCount = data.total, groupByDate = false)
        }
    }
}
