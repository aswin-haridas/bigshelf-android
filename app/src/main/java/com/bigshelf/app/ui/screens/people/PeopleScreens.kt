package com.bigshelf.app.ui.screens.people

import androidx.compose.foundation.background
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.bigshelf.app.ui.theme.BsShapes
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
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
    // Hide people with no photos left (e.g. all their photos were binned). Hottest first;
    // the stable sort keeps unrated people in the server's name order at the end.
    val people = query.data?.items.orEmpty().filter {
        it.photoCount > 0 && it.name.lowercase().contains(search.trim().lowercase())
    }.sortedByDescending { it.heat ?: -1.0 }

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

/**
 * Detected Faces tab (web faces-grid.tsx): face groups that aren't named yet. Cluster matches
 * them to existing people; Select picks groups to merge (into the first picked) or forget.
 */
@Composable
fun FacesScreen(onFace: (String) -> Unit, onTab: (PeopleTab) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val query = rememberQuery("faces", invalidatedBy = setOf("faces")) { api.faces() }
    val faces = query.data.orEmpty().filter { !it.isIgnored && it.faceName == null }
    var clustering by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    // Ordered: the first pick is the group the rest are merged into
    val selected = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf<String?>(null) }

    fun cluster() {
        clustering = true
        scope.launch {
            try {
                val matched = api.clusterFaces().autoAssignedToPeople
                PhotoEvents.invalidate("faces", "people", "photos")
                if (matched > 0) toast.success("Matched $matched faces to existing people")
                else toast.info("No faces matched existing people")
            } catch (_: Exception) {
                toast.error("Failed to match faces")
            } finally {
                clustering = false
            }
        }
    }

    fun merge() {
        val (target, sources) = selected.first() to selected.drop(1)
        busy = "merge"
        scope.launch {
            // Keys the backend reports stale; refreshed even if a later merge fails
            val stale = mutableSetOf("faces")
            try {
                sources.forEach { stale += api.mergeFaceGroup(it, target).invalidate }
                toast.success("Merged ${sources.size + 1} groups")
                selected.clear()
            } catch (_: Exception) {
                toast.error("Failed to merge groups")
            } finally {
                PhotoEvents.invalidate(*stale.toTypedArray())
                busy = null
            }
        }
    }

    // Forget every selected group; failed ones stay selected so they can be retried
    fun forget() {
        busy = "forget"
        scope.launch {
            val failed = selected.filter { id -> runCatching { api.ignoreFace(id) }.isFailure }
            val done = selected.size - failed.size
            selected.clear()
            selected.addAll(failed)
            PhotoEvents.invalidate("faces", "people", "photos")
            if (failed.isNotEmpty()) toast.error("Failed to forget ${failed.size} face(s)") else toast.success("$done face(s) forgotten")
            busy = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        PeopleHeader(PeopleTab.Faces, onTab) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BsButton(
                    onClick = ::cluster,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Secondary,
                    enabled = !clustering && faces.isNotEmpty(),
                    loading = clustering,
                    textStyle = BsText.Xs,
                ) { Text(if (clustering) "Clustering…" else "Cluster faces") }
                BsButton(
                    onClick = {
                        selecting = !selecting
                        selected.clear()
                    },
                    modifier = Modifier.weight(1f),
                    variant = if (selecting) ButtonVariant.Primary else ButtonVariant.Secondary,
                    enabled = faces.isNotEmpty(),
                    textStyle = BsText.Xs,
                ) { Text(if (selecting) "Done" else "Select") }
            }
            FaceReviewBanner()
        }
        Box(Modifier.weight(1f)) {
            when {
                query.isLoading -> LoadingText()
                faces.isEmpty() -> EmptyState("No untagged faces", description = "Detected faces without a name will appear here.")
                else -> FaceGrid {
                    items(faces, key = { it.id }) { face ->
                        val picked = face.id in selected
                        Box {
                            AvatarTile(
                                imageSrc = faceImageSrc(face),
                                name = null,
                                // Each card is one detected group; show its size once merged/clustered
                                subtitle = face.faceCount?.takeIf { it > 1 }?.let { "$it faces" },
                                onClick = {
                                    if (!selecting) onFace(face.id)
                                    else if (!selected.remove(face.id)) selected.add(face.id)
                                },
                            )
                            if (selecting) {
                                Box(
                                    Modifier
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(BsShapes.Full)
                                        .background(if (picked) BsColors.Primary else Color.Black.copy(alpha = 0.4f))
                                        .border(1.dp, BsColors.Muted.copy(alpha = 0.7f), BsShapes.Full),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (picked) BsIcon(BsIcons.CheckBold, size = 14.dp, tint = BsColors.Foreground)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(BsColors.Surface).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selected.size} selected", Modifier.weight(1f), style = BsText.Xs, color = BsColors.Muted)
                BsButton(
                    onClick = ::merge,
                    variant = ButtonVariant.Secondary,
                    enabled = busy == null && selected.size >= 2,
                    loading = busy == "merge",
                    textStyle = BsText.Xs,
                ) {
                    BsIcon(BsIcons.GitMerge, size = 14.dp)
                    Text("Merge")
                }
                BsButton(
                    onClick = ::forget,
                    variant = ButtonVariant.Secondary,
                    contentColor = BsColors.Danger,
                    enabled = busy == null,
                    loading = busy == "forget",
                    textStyle = BsText.Xs,
                ) { Text("Forget") }
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
 * Rename, Download and Merge buttons, then their photos in the server's similarity order.
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
    var merging by remember { mutableStateOf(false) }

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
                        BsButton(onClick = { merging = true }, modifier = Modifier.weight(1f), variant = ButtonVariant.Secondary) {
                            Text("Merge")
                        }
                    }
                },
            )
        }
    }

    if (merging && data != null) {
        MergePersonModal(data.person, onClose = { merging = false }, onMerged = onBack)
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

/**
 * Photos of one detected face group (web face-photos-view.tsx), with Tag, Merge (into
 * another group), Forget and Download all.
 */
@Composable
fun FacePhotosScreen(faceId: String, onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val query = rememberQuery("face", faceId, invalidatedBy = setOf("faces")) { api.facePhotos(faceId) }
    val data = query.data
    var modal by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    fun forget() {
        busy = "forget"
        scope.launch {
            try {
                api.ignoreFace(faceId)
                PhotoEvents.invalidate("faces", "people", "photos")
                toast.success("Face forgotten")
                onBack()
            } catch (_: Exception) {
                toast.error("Failed to forget face")
            } finally {
                busy = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            data?.face?.faceName ?: "Untagged face",
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            BsIconButton(BsIcons.Trash, "Forget face", onClick = ::forget, loading = busy == "forget", enabled = data != null, tint = BsColors.Danger)
        }
        when {
            query.isLoading -> LoadingText()
            data == null -> EmptyState("No photos found")
            else -> ImagesGrid(
                photos = data.items,
                onOpen = { onOpenPhoto(it.id) },
                totalCount = data.total,
                groupByDate = false,
                header = {
                    ProfileHeader(faceImageSrc(data.face), data.face.faceName ?: "Untagged face", data.items, data.total) {
                        BsButton(onClick = { modal = "tag" }, modifier = Modifier.weight(1f)) { Text("Tag") }
                        BsButton(onClick = { modal = "merge" }, modifier = Modifier.weight(1f), variant = ButtonVariant.Secondary) { Text("Merge") }
                        BsButton(
                            onClick = {
                                busy = "download"
                                scope.launch {
                                    try {
                                        Downloads.save(context, data.items)
                                        toast.success("Downloading ${data.items.size} photos")
                                    } catch (_: Exception) {
                                        toast.error("Download failed")
                                    } finally {
                                        busy = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Secondary,
                            enabled = data.items.isNotEmpty(),
                            loading = busy == "download",
                        ) { Text("Download") }
                    }
                },
            )
        }
    }

    data?.face?.let { face ->
        when (modal) {
            "tag" -> TagFaceModal(face, onClose = { modal = null }, onDone = onBack)
            "merge" -> MergeFaceGroupModal(face, onClose = { modal = null }, onMerged = onBack)
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
