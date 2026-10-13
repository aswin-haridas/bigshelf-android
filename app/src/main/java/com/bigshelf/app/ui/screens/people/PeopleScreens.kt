package com.bigshelf.app.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
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
import com.bigshelf.app.data.Photo
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsInput
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.BsSearchBar
import com.bigshelf.app.ui.components.BsTabs
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.TabItem
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.Avatar
import com.bigshelf.app.ui.photos.AvatarTile
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
    TabItem(PeopleTab.Faces, "Faces"),
    TabItem(PeopleTab.Untagged, "Untagged"),
)

/** Round avatar grid, three across with 16dp gutters. */
@Composable
private fun FaceGrid(content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** "People" title over the shared tab bar, with 16dp gutters. */
@Composable
private fun PeopleHeader(active: PeopleTab, onTab: (PeopleTab) -> Unit, below: @Composable () -> Unit = {}) {
    PageHeader("People")
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BsTabs(PEOPLE_TABS, active, onTab)
        below()
    }
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
        PeopleHeader(PeopleTab.People, onTab) {
            BsSearchBar(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Search people")
            FaceReviewBanner()
        }
        when {
            query.isLoading -> LoadingText()
            people.isEmpty() -> EmptyState(
                if (search.isNotEmpty()) "No people matching \"$search\"" else "No people yet",
            )
            else -> FaceGrid {
                items(people, key = { it.id }) { person ->
                    AvatarTile(personImageSrc(person), person.name, photoCountLabel(person.photoCount), onClick = { onPerson(person.id) })
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
        PeopleHeader(PeopleTab.Faces, onTab) { FaceReviewBanner() }
        when {
            query.isLoading -> LoadingText()
            faces.isEmpty() -> EmptyState("No faces")
            else -> FaceGrid {
                items(faces, key = { it.id }) { face ->
                    AvatarTile(
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
        PeopleHeader(PeopleTab.Untagged, onTab)
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
 * A person laid out like an Instagram profile: avatar beside Photos / Videos / Years, the name,
 * Rename and Download buttons, then their photos in the server's similarity order.
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
        PageHeader(
            data?.person?.name ?: "…",
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        )
        when {
            query.isLoading -> LoadingText()
            data == null -> EmptyState("No photos")
            else -> ImagesGrid(
                photos = data.items,
                onOpen = { onOpenPhoto(it.id) },
                totalCount = data.total,
                groupByDate = false,
                header = {
                    // This endpoint's person has no cover, so fall back to their face in the first photo
                    val avatar = personImageSrc(data.person)
                        ?: data.items.firstNotNullOfOrNull { p ->
                            p.photoPerson?.let { it.thumbnailUrl ?: it.faceId?.let { id -> "/api/v1/faces/$id/thumbnail" } }
                        }
                        ?: data.items.firstOrNull()?.thumbnailUrl
                    ProfileHeader(avatar, data.person.name, data.items, data.total) {
                        BsButton(
                            onClick = {
                                name = data.person.name
                                editing = true
                            },
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Secondary,
                        ) { Text("Rename") }
                        BsButton(
                            onClick = {
                                downloading = true
                                scope.launch {
                                    try {
                                        Downloads.save(context, data.items)
                                        toast.success("Downloading ${data.items.size} photos")
                                    } catch (_: Exception) {
                                        toast.error("Download failed")
                                    } finally {
                                        downloading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Secondary,
                            enabled = data.items.isNotEmpty(),
                            loading = downloading,
                        ) { Text("Download all") }
                    }
                },
            )
        }
    }

    if (editing) {
        BsModal(
            title = "Rename person",
            onClose = { editing = false },
            footer = {
                BsButton(onClick = { editing = false }, variant = ButtonVariant.Ghost) { Text("Cancel") }
                BsButton(onClick = ::save, enabled = !saving && name.isNotBlank(), loading = saving) { Text("Save") }
            },
        ) {
            BsInput(
                value = name,
                onValueChange = { name = it },
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
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
        PageHeader(
            data?.face?.faceName ?: "Unnamed face",
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        )
        when {
            query.isLoading -> LoadingText()
            data == null -> EmptyState("No photos found")
            else -> ImagesGrid(
                photos = data.items,
                onOpen = { onOpenPhoto(it.id) },
                totalCount = data.total,
                groupByDate = false,
                header = {
                    ProfileHeader(
                        data.face.faceThumbnail ?: "/api/v1/faces/${data.face.id}/thumbnail",
                        data.face.faceName ?: "Unnamed face",
                        data.items,
                        data.total,
                    )
                },
            )
        }
    }
}

/**
 * Instagram profile header: 88dp avatar beside Photos / Videos / Years, the name with the
 * first year under it, then an optional row of equal-width buttons.
 */
@Composable
private fun ProfileHeader(
    imageSrc: String?,
    name: String,
    photos: List<Photo>,
    total: Int,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val years = remember(photos) { photos.mapNotNull { (it.takenAt ?: it.createdAt)?.take(4)?.toIntOrNull() }.toSet() }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(imageSrc, name, Modifier.size(88.dp))
            Row(Modifier.weight(1f).padding(start = 16.dp)) {
                Stat(total, if (total == 1) "Photo" else "Photos")
                Stat(photos.count { it.isVideo }, "Videos")
                Stat(years.size, if (years.size == 1) "Year" else "Years")
            }
        }
        Text(name, Modifier.padding(top = 12.dp), style = BsText.Sm.copy(fontWeight = BsWeight.Semibold))
        years.minOrNull()?.let { Text("Since $it", style = BsText.Xs, color = BsColors.Muted) }
        if (actions != null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
private fun RowScope.Stat(value: Int, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = BsText.Base.copy(fontWeight = BsWeight.Semibold))
        Text(label, style = BsText.Xs, color = BsColors.Muted)
    }
}
