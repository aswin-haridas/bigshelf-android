package com.bigshelf.app.ui.screens.explore

import androidx.compose.foundation.background
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.AlbumListItem
import com.bigshelf.app.data.NamedPlace
import com.bigshelf.app.data.PhotoFilter
import com.bigshelf.app.data.PlaceNames
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsInput
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.ConfirmModal
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.photos.photoCount
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.Neutral
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.ui.screens.AlbumSharingModal
import com.bigshelf.app.ui.shell.rememberUploadPicker

fun albumSubtitle(album: AlbumListItem) = buildString {
    append(photoCount(album.photoCount))
    if (album.childCount > 0) append(" · ${countLabel(album.childCount, "album")}")
}

/** "Trips / Goa / Beach" for every album, from the flat list's parent links. */
fun albumPaths(albums: List<AlbumListItem>): Map<String, String> {
    val byId = albums.associateBy { it.id }
    fun path(album: AlbumListItem, depth: Int): String {
        val parent = album.parentId?.let(byId::get)
        return if (parent == null || depth > 16) album.title else "${path(parent, depth + 1)} / ${album.title}"
    }
    return albums.associate { it.id to path(it, 0) }
}

/** [id] and every album inside it, at any depth: where it can't be moved to. */
private fun withDescendants(albums: List<AlbumListItem>, id: String): Set<String> {
    val out = mutableSetOf(id)
    var grew = true
    while (grew) grew = out.addAll(albums.filter { it.parentId in out }.map { it.id })
    return out
}

/** All top-level albums as a grid of covers, with New album. */
@Composable
fun AlbumsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val albums = rememberQuery("albums-top", invalidatedBy = setOf("albums", "photos")) { BigShelfApp.api.albums(topLevel = true) }
    var creating by remember { mutableStateOf(false) }
    val list = albums.data.orEmpty()
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Albums",
            count = countLabel(list.size, "album"),
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            BsIconButton(BsIcons.Plus, "New album", onClick = { creating = true }, tint = BsColors.Foreground)
        }
        when {
            albums.isLoading && albums.data == null -> LoadingText()
            list.isEmpty() -> EmptyState("No albums yet")
            else -> AlbumGrid(list, onOpen = { onNavigate(Routes.album(it.id)) })
        }
    }
    if (creating) AlbumFormModal("New album", onClose = { creating = false }, onCreated = { onNavigate(Routes.album(it.id)) })
}

@Composable
private fun AlbumGrid(albums: List<AlbumListItem>, onOpen: (AlbumListItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            CoverCard(album.title, albumSubtitle(album), album.coverPhoto, onClick = { onOpen(album) }, icon = BsIcons.Stack)
        }
    }
}

/**
 * One album: its sub-albums in a row above its photos. The header adds a sub-album, renames,
 * moves the album into another one, or deletes it (its sub-albums move up a level).
 * Selected photos get Move / Copy / Remove from the selection bar.
 */
@Composable
fun AlbumScreen(albumId: String, onBack: () -> Unit, onNavigate: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val album = rememberQuery("album", albumId, invalidatedBy = setOf("albums")) { api.album(albumId) }
    val children = rememberQuery("album-children", albumId, invalidatedBy = setOf("albums")) { api.albums(parentId = albumId) }
    val feed = rememberPagedPhotos("album", albumId, invalidatedBy = setOf("albums", "photos")) { api.albumPhotos(albumId, it) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val title = album.data?.title ?: "Album"
    // Shared with you: you can add and upload photos, but the album itself is the owner's
    val owned = album.data?.ownerId.let { it == null || it == AuthStore.user?.id }
    val upload = rememberUploadPicker(albumId)

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title,
            count = album.data?.let(::albumSubtitle),
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            BsIconButton(BsIcons.ShareNetwork, "Share", onClick = { dialog = "share" })
            if (owned) {
                BsIconButton(BsIcons.FolderSimplePlus, "New album inside", onClick = { dialog = "sub" })
                // Rename / move / delete sit behind "more" so the title keeps its room
                Box {
                    BsIconButton(BsIcons.DotsThreeVertical, "More", onClick = { menu = true })
                    DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = BsColors.Surface) {
                        listOf(
                            Triple("rename", "Rename", BsIcons.PencilSimple),
                            Triple("move", "Move into album", BsIcons.ArrowBendUpRight),
                            Triple("delete", "Delete album", BsIcons.Trash),
                        ).forEach { (id, label, icon) ->
                            val tint = if (id == "delete") BsColors.Danger else BsColors.Foreground
                            DropdownMenuItem(
                                text = { Text(label, style = BsText.Sm, color = tint) },
                                leadingIcon = { BsIcon(icon, size = 20.dp, tint = tint) },
                                onClick = {
                                    menu = false
                                    dialog = id
                                },
                            )
                        }
                    }
                }
            }
        }
        val subAlbums = children.data.orEmpty()
        ImagesGrid(
            photos = feed.photos,
            onOpen = { onOpenPhoto(it.id) },
            fetchNextPage = feed::fetchNextPage,
            hasNextPage = feed.hasNextPage,
            isFetchingNextPage = feed.isFetching,
            totalCount = feed.total,
            // Albums keep the backend's order (similar shots together), not a timeline
            groupByDate = false,
            header = if (subAlbums.isEmpty()) null else {
                {
                    LazyRow(
                        Modifier.padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(subAlbums, key = { it.id }) { sub ->
                            CoverCard(sub.title, albumSubtitle(sub), sub.coverPhoto, onClick = { onNavigate(Routes.album(sub.id)) }, Modifier.width(120.dp), icon = BsIcons.Stack)
                        }
                    }
                }
            },
        )
    }

    when (dialog) {
        "share" -> album.data?.let {
            AlbumSharingModal(
                it,
                onUpload = { dialog = null; upload() },
                onLeft = onBack,
                onClose = { dialog = null },
            )
        }
        "sub" -> AlbumFormModal(
            "New album in $title",
            parentId = albumId,
            onClose = { dialog = null },
            onCreated = { onNavigate(Routes.album(it.id)) },
        )
        "rename" -> TextPromptModal(
            title = "Rename album",
            initial = title,
            confirmLabel = "Save",
            onClose = { dialog = null },
        ) { name ->
            api.renameAlbum(albumId, name)
            PhotoEvents.invalidate("albums")
        }
        "move" -> AlbumPickerModal(
            title = "Move \"$title\" to",
            exclude = albumId,
            excludeTree = true,
            topLevel = true,
            onClose = { dialog = null },
        ) { target ->
            api.moveAlbum(albumId, target?.id)
            PhotoEvents.invalidate("albums")
            toast.success(if (target == null) "Moved to the top level" else "Moved into ${target.title}")
        }
        "delete" -> ConfirmModal(
            title = "Delete album",
            message = "Delete \"$title\"? Its photos stay in your library and any albums inside it move up a level.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    try {
                        api.deleteAlbum(albumId)
                        PhotoEvents.invalidate("albums")
                        toast.success("Album deleted")
                        onBack()
                    } catch (_: Exception) {
                        toast.error("Failed to delete album")
                    }
                }
            },
            onClose = { dialog = null },
        )
    }
}

/** One-field dialog: rename, "Save as album" title. [onConfirm] runs with the trimmed text. */
@Composable
fun TextPromptModal(
    title: String,
    initial: String,
    confirmLabel: String,
    onClose: () -> Unit,
    onConfirm: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    val submit = {
        val clean = text.trim()
        if (clean.isNotEmpty() && !saving) {
            saving = true
            scope.launch {
                try {
                    onConfirm(clean)
                    onClose()
                } catch (e: Exception) {
                    toast.error(e.userMessage("Failed to save"))
                } finally {
                    saving = false
                }
            }
        }
    }
    BsModal(
        title = title,
        onClose = onClose,
        footer = {
            BsButton(onClick = submit, enabled = text.isNotBlank(), loading = saving, textStyle = BsText.Xs) { Text(confirmLabel) }
        },
    ) {
        BsInput(
            value = text,
            onValueChange = { text = it },
            autoFocus = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
    }
}

// Date range picks are UTC midnights; the filter wants the local day's start and end
private fun dayStart(utcMillis: Long): String =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString()

private fun dayEnd(utcMillis: Long): String =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()

private fun shortDate(utcMillis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        .format(Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate())

/**
 * New album, optionally inside [parentId]. Besides a name it can fill itself: pick a date
 * range and/or a place, and every photo taken then / there is added (a smart album).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumFormModal(
    title: String,
    onClose: () -> Unit,
    onCreated: (AlbumListItem) -> Unit,
    parentId: String? = null,
) {
    val api = BigShelfApp.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf<NamedPlace?>(null) }
    val range = rememberDateRangePickerState()
    var dates by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
    val places = rememberQuery("places", invalidatedBy = setOf("photos")) { PlaceNames.places(context) }

    val create = {
        val clean = name.trim()
        if (clean.isNotEmpty() && !saving) {
            saving = true
            scope.launch {
                try {
                    val filter = PhotoFilter(
                        takenFrom = dates?.first?.let(::dayStart),
                        takenTo = dates?.let { (start, end) -> dayEnd(end ?: start) },
                        bbox = place?.bbox,
                    )
                    val album = api.createAlbum(clean, parentId = parentId, filter = filter)
                    PhotoEvents.invalidate("albums")
                    toast.success(if (album.photoCount > 0) "Album created with ${photoCount(album.photoCount)}" else "Album created")
                    onClose()
                    onCreated(album)
                } catch (e: Exception) {
                    toast.error(e.userMessage("Failed to create album"))
                } finally {
                    saving = false
                }
            }
        }
    }

    BsModal(
        title = title,
        onClose = onClose,
        footer = {
            BsButton(onClick = create, enabled = name.isNotBlank(), loading = saving, textStyle = BsText.Xs) { Text("Create album") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BsInput(
                value = name,
                onValueChange = { name = it },
                placeholder = "Album title",
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
            )
            Text("Add photos automatically (optional)", style = BsText.Xs, color = BsColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    dates?.let { (s, e) -> if (e == null || e == s) shortDate(s) else "${shortDate(s)} – ${shortDate(e)}" } ?: "Any dates",
                    onClick = { picking = true },
                    icon = BsIcons.CalendarBlank,
                )
                if (dates != null) Chip("Clear", onClick = { dates = null; range.setSelection(null, null) })
            }
            val list = places.data.orEmpty()
            if (list.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SelectChip("Any place", place == null) { place = null } }
                    items(list.take(20), key = { "${it.name}-${it.latitude}" }) { p ->
                        SelectChip(p.name, place == p) { place = if (place == p) null else p }
                    }
                }
            }
        }
    }

    if (picking) {
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                BsButton(
                    onClick = {
                        range.selectedStartDateMillis?.let { dates = it to range.selectedEndDateMillis }
                        picking = false
                    },
                    variant = ButtonVariant.Ghost,
                    enabled = range.selectedStartDateMillis != null,
                ) { Text("Done") }
            },
            dismissButton = { BsButton(onClick = { picking = false }, variant = ButtonVariant.Ghost) { Text("Cancel") } },
        ) {
            DateRangePicker(range, Modifier.heightIn(max = 520.dp), title = null, showModeToggle = false)
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(BsShapes.Full)
            .background(if (selected) Neutral.N800 else BsColors.Surface)
            .border(1.dp, if (selected) BsColors.BorderFocus else BsColors.Border, BsShapes.Full)
            .bsClickable(scale = 0.96f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = BsText.Xs.copy(fontWeight = BsWeight.Medium),
        color = if (selected) BsColors.Foreground else BsColors.Muted,
        maxLines = 1,
    )
}

/**
 * Pick an album by its full path ("Trips / Goa"). [exclude] hides an album (the one photos are
 * in), plus everything inside it with [excludeTree] (moving an album into itself),
 * [topLevel] offers "Top level" (picked as null), and
 * [onCreate] adds a "new album" field on top (the add-to-album flow).
 */
@Composable
fun AlbumPickerModal(
    title: String,
    onClose: () -> Unit,
    exclude: String? = null,
    excludeTree: Boolean = false,
    topLevel: Boolean = false,
    onCreate: (suspend (String) -> Unit)? = null,
    onPick: suspend (AlbumListItem?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val albums = rememberQuery("albums-all", invalidatedBy = setOf("albums")) { BigShelfApp.api.albums() }
    var saving by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    fun submit(block: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                block()
                onClose()
            } catch (e: Exception) {
                toast.error(e.userMessage("Something went wrong"))
            } finally {
                saving = false
            }
        }
    }

    val all = albums.data.orEmpty()
    val hidden = exclude?.let { if (excludeTree) withDescendants(all, it) else setOf(it) }.orEmpty()
    val paths = albumPaths(all)
    val rows = all.filterNot { it.id in hidden }.sortedBy { paths[it.id]?.lowercase() }
    val create = { newTitle.trim().takeIf { it.isNotEmpty() }?.let { t -> onCreate?.let { c -> submit { c(t) } } } }

    BsModal(
        title = title,
        onClose = onClose,
        footer = if (onCreate == null) null else {
            {
                BsButton(onClick = { create() }, enabled = !saving && newTitle.isNotBlank(), textStyle = BsText.Xs) {
                    Text(if (saving) "Saving…" else "Create album")
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onCreate != null) {
                BsInput(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = "New album title",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
            }
            Column(
                Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (topLevel) PickRow("Top level", null, enabled = !saving) { submit { onPick(null) } }
                rows.forEach { album ->
                    PickRow(paths[album.id] ?: album.title, "${album.photoCount}", enabled = !saving) { submit { onPick(album) } }
                }
                if (rows.isEmpty() && !albums.isLoading && !topLevel) {
                    Text("No albums yet — create one above.", style = BsText.Xs, color = BsColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun PickRow(label: String, trailing: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bsClickable(enabled = enabled, scale = 0.98f, onClick = onClick)
            .clip(BsShapes.Sm)
            .background(BsColors.Surface)
            .border(1.dp, BsColors.Border, BsShapes.Sm)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = BsText.Xs.copy(fontWeight = BsWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, Modifier.padding(start = 8.dp), style = BsText.Xs, color = BsColors.Muted)
    }
}
