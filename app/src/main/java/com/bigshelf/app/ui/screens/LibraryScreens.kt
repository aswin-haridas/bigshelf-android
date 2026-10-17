package com.bigshelf.app.ui.screens

import androidx.compose.foundation.background
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.ArchiveFolder
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.rememberPagedPhotos
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.ConfirmModal
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.photos.ImagesGrid
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun countLabel(n: Int, noun: String) = "$n ${if (n == 1) noun else "${noun}s"}"

/** Port of modules/bin/bin.tsx. Restore / delete permanently live in the selection bar. */
@Composable
fun BinScreen(onOpenPhoto: (String) -> Unit, onBack: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val feed = rememberPagedPhotos("bin", invalidatedBy = setOf("bin-photos")) { api.bin(it) }
    var emptying by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val total = feed.total ?: feed.photos.size

    Column(Modifier.fillMaxSize()) {
        PageHeader("Bin", count = countLabel(total, "photo"), leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) }) {
            if (total > 0) {
                BsButton(onClick = { confirm = true }, variant = ButtonVariant.Secondary, enabled = !emptying, contentColor = BsColors.Danger) {
                    if (emptying) Spinner(size = 14.dp) else BsIcon(BsIcons.Trash, size = 14.dp)
                    Text("Empty")
                }
            }
        }
        ImagesGrid(
            photos = feed.photos,
            onOpen = { onOpenPhoto(it.id) },
            fetchNextPage = feed::fetchNextPage,
            hasNextPage = feed.hasNextPage,
            isFetchingNextPage = feed.isFetching,
        )
    }

    if (confirm) {
        ConfirmModal(
            title = "Empty Bin",
            message = "Permanently delete every photo in the Bin? This cannot be undone.",
            confirmLabel = "Empty Bin",
            onConfirm = {
                emptying = true
                scope.launch {
                    try {
                        val n = api.emptyBin().deletedCount ?: 0
                        toast.success("${countLabel(n, "photo")} permanently deleted")
                        PhotoEvents.invalidateAll()
                    } catch (_: Exception) {
                        toast.error("Failed to empty Bin")
                    } finally {
                        emptying = false
                    }
                }
            },
            onClose = { confirm = false },
        )
    }
}

// "Sep 24, 2026, 3:12 PM" - the moment the batch was downloaded (web folderLabel)
fun folderLabel(id: String): String = runCatching {
    val instant = runCatching { OffsetDateTime.parse(id).toInstant() }
        .getOrElse { LocalDateTime.parse(id).atZone(ZoneId.of("UTC")).toInstant() }
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(id)

/** Port of modules/archive/archive.tsx (folder list): one folder per download batch. */
@Composable
fun ArchiveScreen(onFolder: (String) -> Unit, onBack: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val folders = rememberQuery("archive-folders", invalidatedBy = setOf("archive-photos")) { api.archiveFolders() }
    var busy by remember { mutableStateOf<Pair<String, String>?>(null) }
    var emptying by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val list = folders.data.orEmpty()

    fun folderAction(folder: ArchiveFolder, delete: Boolean) {
        busy = folder.id to if (delete) "delete" else "unarchive"
        scope.launch {
            try {
                val n = api.folderAction(folder.id, delete).flaggedCount ?: 0
                toast.success("${countLabel(n, "photo")} ${if (delete) "moved to Bin" else "unarchived"}")
                PhotoEvents.invalidate("archive-photos", "photos", "bin-photos", "organize")
            } catch (_: Exception) {
                toast.error("Failed to ${if (delete) "delete" else "unarchive"} folder")
            } finally {
                busy = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Archive", count = countLabel(list.size, "folder"), leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) }) {
            if (list.isNotEmpty()) {
                BsButton(
                    onClick = { confirmEmpty = true },
                    variant = ButtonVariant.Secondary,
                    enabled = !emptying && busy == null,
                    contentColor = BsColors.Danger,
                ) {
                    if (emptying) Spinner(size = 14.dp) else BsIcon(BsIcons.Trash, size = 14.dp)
                    Text("Empty")
                }
            }
        }
        when {
            folders.isLoading -> LoadingText()
            list.isEmpty() -> EmptyState("Archive is empty")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { it.id }) { folder ->
                    FolderCard(folder, busy?.takeIf { it.first == folder.id }?.second, onOpen = { onFolder(folder.id) }) { delete ->
                        folderAction(folder, delete)
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmModal(
            title = "Empty archive",
            message = "Move every archived photo to the Bin?",
            confirmLabel = "Empty archive",
            onConfirm = {
                emptying = true
                scope.launch {
                    try {
                        api.emptyArchive()
                        toast.success("Archive emptied")
                        PhotoEvents.invalidate("archive-photos", "photos", "bin-photos", "organize")
                    } catch (_: Exception) {
                        toast.error("Failed to empty archive")
                    } finally {
                        emptying = false
                    }
                }
            },
            onClose = { confirmEmpty = false },
        )
    }
}

/** Port of archive/components/folder-card.tsx: square cover, count pill, unarchive / bin buttons. */
@Composable
private fun FolderCard(folder: ArchiveFolder, busyAction: String?, onOpen: () -> Unit, onAction: (delete: Boolean) -> Unit) {
    Box {
        Column(Modifier.bsClickable(scale = 0.98f, onClick = onOpen)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(BsShapes.Xl)
                    .background(BsColors.Surface.copy(alpha = 0.4f))
                    .border(1.dp, BsColors.Border.copy(alpha = 0.3f), BsShapes.Xl),
            ) {
                folder.cover?.thumbnailUrl?.let {
                    AsyncImage(
                        model = BigShelfApp.api.mediaUrl(it),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    "${folder.count}",
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(BsShapes.Full)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = BsText.Xs2,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BsIcon(BsIcons.Folder, size = 14.dp, tint = Color.White.copy(alpha = 0.8f))
                Text(
                    folderLabel(folder.id),
                    style = BsText.Xs,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BsIconButton(
                BsIcons.ArrowCounterClockwise,
                "Unarchive",
                onClick = { onAction(false) },
                enabled = busyAction == null,
                loading = busyAction == "unarchive",
                tint = BsColors.Foreground,
            )
            BsIconButton(
                BsIcons.Trash,
                "Move folder to Bin",
                onClick = { onAction(true) },
                enabled = busyAction == null,
                loading = busyAction == "delete",
                tint = BsColors.Danger,
            )
        }
    }
}

/** Port of the archive folder view: that batch's photos, no date headers. */
@Composable
fun ArchiveFolderScreen(folder: String, onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val api = BigShelfApp.api
    val feed = rememberPagedPhotos("archive", folder, invalidatedBy = setOf("archive-photos")) { api.archivePhotos(folder, it) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            folderLabel(folder),
            count = countLabel(feed.total ?: feed.photos.size, "photo"),
            leading = { BsIconButton(BsIcons.ArrowLeft, "All folders", onClick = onBack, tint = BsColors.Foreground) },
        )
        ImagesGrid(
            photos = feed.photos,
            onOpen = { onOpenPhoto(it.id) },
            fetchNextPage = feed::fetchNextPage,
            hasNextPage = feed.hasNextPage,
            isFetchingNextPage = feed.isFetching,
            groupByDate = false,
        )
    }
}
