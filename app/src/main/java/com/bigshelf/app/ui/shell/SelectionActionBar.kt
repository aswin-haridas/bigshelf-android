package com.bigshelf.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Downloads
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsInput
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.ConfirmModal
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.Neutral
import kotlinx.coroutines.launch

private fun plural(n: Int) = "$n photo${if (n > 1) "s" else ""}"

/**
 * Port of components/SelectionActionBar.tsx: one bulk bar for the whole app, docked in place
 * of the bottom nav while anything is selected.
 * Bin and Archive get restore / unarchive instead of the library actions; a person's page
 * adds Untag. Back clears the selection (the web's Escape).
 */
@Composable
fun SelectionActionBar(path: String, onPersonGone: () -> Unit, modifier: Modifier = Modifier) {
    val count = SelectionStore.count
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    var albumOpen by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf(false) }
    val busy = pending != null
    val api = BigShelfApp.api

    BackHandler(enabled = count > 0 && !albumOpen) { SelectionStore.clear() }

    val mode = when {
        path.startsWith("bin") -> "bin"
        path.startsWith("archive") -> "archive"
        else -> "library"
    }
    val personId = Regex("""^people/([^/]+)$""").find(path)?.groupValues?.get(1)?.takeIf { it != "untagged" }
    val unselected = SelectionStore.visible.filterNot { SelectionStore.isSelected(it.id) }

    // Every action moves photos out of the view they're in, so all photo views refresh after
    fun runBulk(name: String, success: (Int) -> String, failure: String, request: suspend (Set<String>) -> Unit) {
        if (count == 0 || busy) return
        val ids = SelectionStore.ids
        pending = name
        scope.launch {
            try {
                request(ids)
                toast.success(success(ids.size))
                SelectionStore.clear()
            } catch (_: Exception) {
                toast.error(failure)
            } finally {
                PhotoEvents.invalidateAll()
                pending = null
            }
        }
    }

    @Composable
    fun RowScope.Action(name: String, icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
        val tint = if (danger) BsColors.Danger else BsColors.Foreground
        Column(
            Modifier.weight(1f).fillMaxHeight().bsClickable(enabled = !busy, scale = 0.94f, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (pending == name) Spinner(size = 24.dp, tint = tint) else BsIcon(icon, size = 24.dp, tint = tint)
            Text(
                label,
                Modifier.padding(top = 4.dp),
                style = BsText.Xs2.copy(fontWeight = BsWeight.Medium),
                color = if (danger) BsColors.Danger else BsColors.MutedSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // Docked where the bottom nav sits: count row, then the actions laid out like nav tabs
    Column(
        modifier
            .fillMaxWidth()
            .background(BsColors.Background)
            .drawBehind { drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(BsShapes.Full).bsClickable(enabled = !busy, onClick = SelectionStore::clear), contentAlignment = Alignment.Center) {
                BsIcon(BsIcons.XBold, size = 20.dp, tint = BsColors.Foreground, contentDescription = "Clear selection")
            }
            Text("$count selected", Modifier.weight(1f), style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
            if (unselected.isNotEmpty()) {
                Text(
                    "Select all",
                    Modifier
                        .clip(BsShapes.Md)
                        .bsClickable(enabled = !busy, scale = 0.96f) { SelectionStore.selectMany(unselected) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = BsText.Sm.copy(fontWeight = BsWeight.Medium),
                    color = Neutral.N300,
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            if (mode == "bin") {
                Action("restore", BsIcons.ArrowCounterClockwise, "Restore") {
                    runBulk("restore", { "${plural(it)} restored" }, "Failed to restore photos") { api.flagDeleted(it, false) }
                }
                Action("permanent-delete", BsIcons.Trash, "Delete forever", danger = true) { confirmPurge = true }
            } else {
                Action("album", BsIcons.Stack, "Album") { albumOpen = true }
                if (personId != null) {
                    Action("untag", BsIcons.Tag, "Untag") {
                        runBulk("untag", { "Untagged ${plural(it)}" }, "Failed to untag photos") {
                            if (api.untagPhotos(personId, it).personDeleted) onPersonGone()
                        }
                    }
                }
                Action("download", BsIcons.DownloadSimple, "Download") {
                    val selected = SelectionStore.photos.values.toList()
                    pending = "download"
                    scope.launch {
                        try {
                            Downloads.save(context, selected)
                            toast.success("Downloading ${plural(selected.size)}")
                            // Settings > Archive on download: downloaded photos leave the library
                            if (BigShelfApp.settings.archiveOnDownload) {
                                api.flagArchived(selected.map { it.id }, true)
                                PhotoEvents.invalidateAll()
                            }
                            SelectionStore.clear()
                        } catch (_: Exception) {
                            toast.error("Failed to download")
                        } finally {
                            pending = null
                        }
                    }
                }
                if (mode == "archive") {
                    Action("unarchive", BsIcons.Archive, "Unarchive") {
                        runBulk("unarchive", { "${plural(it)} unarchived" }, "Failed to unarchive photos") { api.flagArchived(it, false) }
                    }
                } else {
                    Action("archive", BsIcons.Archive, "Archive") {
                        runBulk("archive", { "${plural(it)} archived" }, "Failed to archive photos") { api.flagArchived(it, true) }
                    }
                }
                Action("delete", BsIcons.Trash, "Bin", danger = true) {
                    runBulk("delete", { "${plural(it)} moved to Bin" }, "Failed to move photos to Bin") { api.flagDeleted(it, true) }
                }
            }
        }
    }

    if (confirmPurge) {
        ConfirmModal(
            title = "Delete permanently",
            message = "Permanently delete ${plural(count)}? This cannot be undone.",
            confirmLabel = "Delete permanently",
            onConfirm = {
                runBulk("permanent-delete", { "${plural(it)} permanently deleted" }, "Failed to permanently delete photos") {
                    api.permanentDelete(it)
                }
            },
            onClose = { confirmPurge = false },
        )
    }
    if (albumOpen) AddToAlbumModal(onClose = { albumOpen = false })
}

/** Add-to-album picker: create a new album or add to an existing one (web AddToAlbumModal). */
@Composable
private fun AddToAlbumModal(onClose: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val albums = rememberQuery("albums", invalidatedBy = setOf("albums")) { api.albums() }

    fun save(request: suspend () -> Unit, success: String, failure: String) {
        saving = true
        scope.launch {
            try {
                request()
                PhotoEvents.invalidate("albums")
                toast.success(success)
                SelectionStore.clear()
                onClose()
            } catch (_: Exception) {
                toast.error(failure)
            } finally {
                saving = false
            }
        }
    }

    val create = {
        val clean = title.trim()
        if (clean.isNotEmpty()) save({ api.createAlbum(clean, SelectionStore.ids) }, "Album created", "Failed to create album")
    }

    BsModal(
        title = "Add to album",
        onClose = onClose,
        footer = {
            BsButton(onClick = create, enabled = !saving && title.isNotBlank(), textStyle = BsText.Xs) {
                Text(if (saving) "Saving…" else "Create album")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BsInput(
                value = title,
                onValueChange = { title = it },
                placeholder = "New album title",
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
            )
            Column(
                Modifier.heightIn(max = 224.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val items = albums.data.orEmpty()
                items.forEach { album ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bsClickable(enabled = !saving, scale = 0.98f) {
                                save({ api.addToAlbum(album.id, SelectionStore.ids) }, "Added to album", "Failed to add to album")
                            }
                            .clip(BsShapes.Sm)
                            .background(BsColors.Surface)
                            .border(1.dp, BsColors.Border, BsShapes.Sm)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            album.title,
                            Modifier.weight(1f),
                            style = BsText.Xs.copy(fontWeight = BsWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${album.photoCount}", Modifier.padding(start = 8.dp), style = BsText.Xs, color = BsColors.Muted)
                    }
                }
                if (items.isEmpty() && !albums.isLoading) {
                    Text("No albums yet — create one above.", style = BsText.Xs, color = BsColors.Muted)
                }
            }
        }
    }
}

