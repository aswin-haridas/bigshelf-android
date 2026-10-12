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
import com.bigshelf.app.ui.screens.explore.AlbumPickerModal
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
import com.bigshelf.app.ui.screens.sendLink
import com.bigshelf.app.data.userMessage

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
    // "copy" (add to album) or "move" (out of this album into another)
    var albumPicker by remember { mutableStateOf<String?>(null) }
    var confirmPurge by remember { mutableStateOf(false) }
    val busy = pending != null
    val api = BigShelfApp.api

    BackHandler(enabled = count > 0 && albumPicker == null) { SelectionStore.clear() }

    // Inside an album, photos can be moved to another album or taken out of this one
    val albumId = Regex("""^albums/([^/]+)$""").find(path)?.groupValues?.get(1)
    val mode = when {
        path.startsWith("bin") -> "bin"
        path.startsWith("archive") -> "archive"
        albumId != null -> "album"
        else -> "library"
    }
    val personId = Regex("""^people/([^/]+)$""").find(path)?.groupValues?.get(1)?.takeIf { it != "untagged" }
    val unselected = SelectionStore.visible.filterNot { SelectionStore.isSelected(it.id) }

    // Every action moves photos out of the view they're in, so all photo views refresh after
    // [undo] puts the photos back; it's offered on the success toast
    fun runBulk(
        name: String,
        success: (Int) -> String,
        failure: String,
        undo: (suspend (Set<String>) -> Unit)? = null,
        request: suspend (Set<String>) -> Unit,
    ) {
        if (count == 0 || busy) return
        val ids = SelectionStore.ids
        pending = name
        scope.launch {
            try {
                request(ids)
                if (undo == null) toast.success(success(ids.size))
                else toast.success(success(ids.size), "Undo") {
                    scope.launch {
                        try {
                            undo(ids)
                        } catch (_: Exception) {
                            toast.error("Couldn't undo")
                        } finally {
                            PhotoEvents.invalidateAll()
                            PhotoEvents.invalidate("albums")
                        }
                    }
                }
                SelectionStore.clear()
            } catch (_: Exception) {
                toast.error(failure)
            } finally {
                PhotoEvents.invalidateAll()
                PhotoEvents.invalidate("albums")
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
                if (albumId != null) {
                    Action("move", BsIcons.ArrowBendUpRight, "Move") { albumPicker = "move" }
                    Action("copy", BsIcons.Copy, "Copy") { albumPicker = "copy" }
                    Action("remove", BsIcons.MinusCircle, "Remove") {
                        runBulk(
                            "remove", { "Removed ${plural(it)} from album" }, "Failed to remove from album",
                            undo = { api.addToAlbum(albumId, it) },
                        ) { api.removeFromAlbum(albumId, it) }
                    }
                } else {
                    Action("album", BsIcons.Stack, "Album") { albumPicker = "copy" }
                }
                if (personId != null) {
                    Action("untag", BsIcons.Tag, "Untag") {
                        runBulk("untag", { "Untagged ${plural(it)}" }, "Failed to untag photos") {
                            if (api.untagPhotos(personId, it).personDeleted) onPersonGone()
                        }
                    }
                }
                Action("share", BsIcons.ShareNetwork, "Share") {
                    val ids = SelectionStore.ids
                    pending = "share"
                    scope.launch {
                        try {
                            sendLink(context, api.createShare(photoIds = ids))
                            PhotoEvents.invalidate("shares")
                            SelectionStore.clear()
                        } catch (e: Exception) {
                            toast.error(e.userMessage("Couldn't create a link"))
                        } finally {
                            pending = null
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
                        runBulk(
                            "archive", { "${plural(it)} archived" }, "Failed to archive photos",
                            undo = { api.flagArchived(it, false) },
                        ) { api.flagArchived(it, true) }
                    }
                }
                Action("delete", BsIcons.Trash, "Bin", danger = true) {
                    runBulk(
                        "delete", { "${plural(it)} moved to Bin" }, "Failed to move photos to Bin",
                        undo = { api.flagDeleted(it, false) },
                    ) { api.flagDeleted(it, true) }
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
    albumPicker?.let { kind ->
        val moving = kind == "move"
        // Copy adds to the picked album; Move also takes the photos out of this one
        suspend fun into(targetId: String, label: String) {
            val ids = SelectionStore.ids
            api.addToAlbum(targetId, ids)
            if (moving && albumId != null) api.removeFromAlbum(albumId, ids)
            PhotoEvents.invalidate("albums")
            toast.success("${if (moving) "Moved" else "Added"} ${plural(ids.size)} to $label")
            SelectionStore.clear()
        }
        AlbumPickerModal(
            title = if (moving) "Move to album" else "Add to album",
            onClose = { albumPicker = null },
            exclude = albumId,
            onCreate = { title ->
                val album = api.createAlbum(title)
                into(album.id, album.title)
            },
        ) { album -> if (album != null) into(album.id, album.title) }
    }
}
