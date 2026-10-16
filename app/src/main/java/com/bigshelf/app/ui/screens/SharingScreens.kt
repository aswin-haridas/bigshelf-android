package com.bigshelf.app.ui.screens

import android.content.Context
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.AlbumListItem
import com.bigshelf.app.data.ShareLink
import com.bigshelf.app.data.userMessage
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsInput
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.ConfirmModal
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.screens.explore.CoverCard
import com.bigshelf.app.ui.screens.explore.albumSubtitle
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch

/** Opens the system share sheet with a link's web address. */
fun sendLink(context: Context, link: ShareLink) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, link.title)
        .putExtra(Intent.EXTRA_TEXT, BigShelfApp.api.shareUrl(link.token))
    context.startActivity(Intent.createChooser(send, link.title))
}

private fun itemCount(n: Int) = "$n ${if (n == 1) "item" else "items"}"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            Modifier.padding(horizontal = 20.dp),
            style = BsText.Xs.copy(fontWeight = BsWeight.Medium),
            color = BsColors.Muted,
        )
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = 20.dp), style = BsText.Xs, color = BsColors.MutedFaint)
}

/** Library > Sharing: albums people shared with you, and your links (share again or revoke). */
@Composable
fun SharingScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val api = BigShelfApp.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shared = rememberQuery("albums-shared", invalidatedBy = setOf("albums")) { api.sharedAlbums() }
    val links = rememberQuery("shares", invalidatedBy = setOf("shares", "albums")) { api.shares() }
    var revoking by remember { mutableStateOf<ShareLink?>(null) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Sharing", leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Shared with you") {
                val albums = shared.data.orEmpty()
                if (albums.isEmpty()) {
                    Hint(if (shared.isLoading) "Loading…" else "Nothing yet")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(albums, key = { it.id }) { album ->
                            CoverCard(
                                album.title,
                                albumSubtitle(album),
                                album.coverPhoto,
                                onClick = { onNavigate(Routes.album(album.id)) },
                                Modifier.width(140.dp),
                            )
                        }
                    }
                }
            }
            Section("Your links") {
                val list = links.data.orEmpty()
                if (list.isEmpty()) {
                    Hint(if (links.isLoading) "Loading…" else "No links")
                } else {
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(BsShapes.Xl)
                            .background(BsColors.Surface.copy(alpha = 0.3f))
                            .border(1.dp, BsColors.Border, BsShapes.Xl),
                    ) {
                        list.forEachIndexed { i, link ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .drawBehind { if (i > 0) drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                                    .heightIn(min = 64.dp)
                                    .padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(link.title, style = BsText.Sm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (link.albumId != null) "Album · ${itemCount(link.photoCount)}" else itemCount(link.photoCount),
                                        style = BsText.Xs,
                                        color = BsColors.Muted,
                                    )
                                }
                                BsIconButton(BsIcons.ShareNetwork, "Share again", onClick = { sendLink(context, link) })
                                BsIconButton(BsIcons.Trash, "Revoke", onClick = { revoking = link }, tint = BsColors.Danger)
                            }
                        }
                    }
                }
            }
        }
    }

    revoking?.let { link ->
        ConfirmModal(
            title = "Revoke link",
            message = "\"${link.title}\" stops working for everyone who has it.",
            confirmLabel = "Revoke",
            onConfirm = {
                scope.launch {
                    try {
                        api.deleteShare(link.id)
                        PhotoEvents.invalidate("shares")
                        toast.success("Link revoked")
                    } catch (e: Exception) {
                        toast.error(e.userMessage("Couldn't revoke the link"))
                    }
                }
            },
            onClose = { revoking = null },
        )
    }
}

/**
 * An album's sharing sheet: a public link (owner only), uploading photos into it, and the people
 * it's shared with. The owner adds people by username or email and removes them; a member can
 * leave. [onUpload] opens the photo picker, registered by the screen so it outlives this sheet.
 */
@Composable
fun AlbumSharingModal(album: AlbumListItem, onUpload: () -> Unit, onLeft: () -> Unit, onClose: () -> Unit) {
    val api = BigShelfApp.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = AuthStore.user?.id
    val owner = album.ownerId == null || album.ownerId == me
    val people = rememberQuery("album-members", album.id, invalidatedBy = setOf("albums")) { api.albumMembers(album.id) }
    var identifier by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }

    fun run(name: String, failure: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = name
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                toast.error(e.userMessage(failure))
            } finally {
                busy = null
            }
        }
    }

    val add = {
        val who = identifier.trim()
        if (who.isNotEmpty()) run("add", "Couldn't share the album") {
            people.data = api.addAlbumMember(album.id, who)
            identifier = ""
            toast.success("Shared with $who")
        }
    }

    BsModal(title = "Share \"${album.title}\"", onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (owner) {
                    BsButton(
                        onClick = {
                            run("link", "Couldn't create a link") {
                                // One link per album: reuse it rather than minting another
                                val link = api.shares().firstOrNull { it.albumId == album.id } ?: api.createShare(albumId = album.id)
                                PhotoEvents.invalidate("shares")
                                sendLink(context, link)
                            }
                        },
                        variant = ButtonVariant.Secondary,
                        loading = busy == "link",
                    ) {
                        BsIcon(BsIcons.ShareNetwork, size = 16.dp)
                        Text("Share link")
                    }
                }
                BsButton(onClick = onUpload, variant = ButtonVariant.Secondary) {
                    BsIcon(BsIcons.UploadSimple, size = 16.dp)
                    Text("Upload photos")
                }
            }

            Text("People", style = BsText.Xs.copy(fontWeight = BsWeight.Medium), color = BsColors.Muted)
            val list = people.data
            if (list == null) {
                Spinner(size = 16.dp, tint = BsColors.Muted)
            } else {
                list.forEach { person ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(person.username, Modifier.weight(1f), style = BsText.Sm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            person.role == "owner" -> Text("Owner", style = BsText.Xs, color = BsColors.Muted)
                            owner -> BsIconButton(BsIcons.XBold, "Remove ${person.username}", onClick = {
                                run(person.id, "Couldn't remove ${person.username}") {
                                    api.removeAlbumMember(album.id, person.id)
                                    people.data = list.filter { it.id != person.id }
                                    PhotoEvents.invalidate("albums")
                                }
                            })
                            person.id == me -> Text("You", style = BsText.Xs, color = BsColors.Muted)
                        }
                    }
                }
            }

            if (owner) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BsInput(
                        value = identifier,
                        onValueChange = { identifier = it },
                        modifier = Modifier.weight(1f),
                        placeholder = "Username or email",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                    )
                    BsButton(onClick = add, enabled = identifier.isNotBlank(), loading = busy == "add") {
                        BsIcon(BsIcons.UserPlus, size = 16.dp)
                        Text("Add")
                    }
                }
            } else if (me != null) {
                BsButton(
                    onClick = {
                        run("leave", "Couldn't leave the album") {
                            api.removeAlbumMember(album.id, me)
                            PhotoEvents.invalidate("albums")
                            toast.success("You left \"${album.title}\"")
                            onClose()
                            onLeft()
                        }
                    },
                    variant = ButtonVariant.Ghost,
                    contentColor = BsColors.Danger,
                    loading = busy == "leave",
                ) {
                    BsIcon(BsIcons.SignOut, size = 16.dp, tint = BsColors.Danger)
                    Text("Leave album")
                }
            }
        }
    }
}
