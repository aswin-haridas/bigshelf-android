package com.bigshelf.app.ui.screens

import com.bigshelf.app.ui.components.LoadError
import com.bigshelf.app.ui.components.TriangleLoader
import com.bigshelf.app.ui.components.ShinyText
import com.bigshelf.app.ui.shell.LocalBottomBarInset
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.AlbumListItem
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
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.screens.explore.CoverCard
import com.bigshelf.app.ui.screens.explore.albumSubtitle
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch

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

/** Library > Sharing: albums people shared with you. */
@Composable
fun SharingScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val api = BigShelfApp.api
    val shared = rememberQuery("albums-shared", invalidatedBy = setOf("albums")) { api.sharedAlbums() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Sharing", leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Shared with you") {
                val albums = shared.data.orEmpty()
                if (shared.isError && shared.data == null) {
                    LoadError(shared::refetch, what = "shared albums")
                } else if (albums.isEmpty()) {
                    if (shared.isLoading) ShinyText("Loading…", Modifier.padding(horizontal = 20.dp)) else Hint("Nothing yet")
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
        }
    }
}

/**
 * An album's sharing sheet: uploading photos into it and the people it's shared with. The owner
 * adds people by username or email and removes them; a member can leave. [onUpload] opens the
 * photo picker, registered by the screen so it outlives this sheet.
 */
@Composable
fun AlbumSharingModal(album: AlbumListItem, onUpload: () -> Unit, onLeft: () -> Unit, onClose: () -> Unit) {
    val api = BigShelfApp.api
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
            BsButton(onClick = onUpload, variant = ButtonVariant.Secondary) {
                BsIcon(BsIcons.UploadSimple, size = 16.dp)
                Text("Upload photos")
            }

            Text("People", style = BsText.Xs.copy(fontWeight = BsWeight.Medium), color = BsColors.Muted)
            val list = people.data
            if (list == null) {
                TriangleLoader(Modifier.align(Alignment.CenterHorizontally))
            } else {
                list.forEach { person ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(person.username, Modifier.weight(1f), style = BsText.Sm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            person.role == "owner" -> Text("Owner", style = BsText.Xs, color = BsColors.Muted)
                            owner -> BsIconButton(BsIcons.X, "Remove ${person.username}", onClick = {
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
                    variant = ButtonVariant.DangerGhost,
                    loading = busy == "leave",
                ) {
                    BsIcon(BsIcons.SignOut, size = 16.dp, tint = BsColors.Danger)
                    Text("Leave album")
                }
            }
        }
    }
}
