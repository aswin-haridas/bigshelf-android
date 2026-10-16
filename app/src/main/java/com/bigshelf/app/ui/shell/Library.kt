package com.bigshelf.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Decimal units, like Google Photos ("23.8 GB") - web sidebar formatBytes
fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val i = if (bytes > 0) minOf(floor(log10(bytes.toDouble()) / 3).toInt(), units.lastIndex) else 0
    val value = bytes / 1000.0.pow(i)
    val text = String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")
    return "$text ${units[i]}"
}

/** Borderless surface card holding a group of rows. */
@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(title, Modifier.padding(start = 4.dp), style = BsText.Xs.copy(fontWeight = BsWeight.Medium), color = BsColors.Muted)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(BsShapes.Xl)
                .background(BsColors.Surface),
        ) { content() }
    }
}

/** 56dp row: icon, label, optional trailing hint, caret. Hairline above all but the first. */
@Composable
private fun LibraryRow(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    divider: Boolean = false,
    trailing: String? = null,
    color: Color = BsColors.Foreground,
    caret: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { if (divider) drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .bsClickable(scale = 1f, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) BsIcon(icon, size = 20.dp, tint = if (color == BsColors.Foreground) BsColors.Muted else color)
        Text(label, Modifier.weight(1f), style = BsText.Sm, color = color)
        if (trailing != null) Text(trailing, style = BsText.Xs, color = BsColors.MutedFaint)
        if (caret) BsIcon(BsIcons.CaretRight, size = 16.dp, tint = BsColors.MutedFaint)
    }
}

/**
 * Library tab: everything the web sidebar holds beyond the bottom-bar sections - upload,
 * Archive, Bin, the tools, Settings, Log out and storage used.
 */
@Composable
fun LibraryScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val storage = rememberQuery("storage", invalidatedBy = setOf("photos")) { BigShelfApp.api.storage() }
    // System photo picker stands in for the web dropzone / file dialog
    val upload = rememberUploadPicker()

    Column(Modifier.fillMaxSize()) {
        PageHeader("Library", count = storage.data?.let { "${formatBytes(it.usedBytes)} used" })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Group {
                LibraryRow("Upload photos", BsIcons.UploadSimple, caret = false, onClick = {
                    upload()
                })
            }
            Group {
                LibraryRow("Favourites", BsIcons.Heart, onClick = { onNavigate(Routes.FAVOURITES) })
                LibraryRow("Backup & storage", BsIcons.CloudArrowUp, divider = true, onClick = { onNavigate(Routes.BACKUP) })
                LibraryRow("Sharing", BsIcons.ShareNetwork, divider = true, onClick = { onNavigate(Routes.SHARING) })
                LibraryRow("Archive", BsIcons.Archive, divider = true, onClick = { onNavigate(Routes.ARCHIVE) })
                LibraryRow("Bin", BsIcons.Trash, divider = true, onClick = { onNavigate(Routes.BIN) })
            }
            // Same tools, same order as the web sidebar
            Group("Tools") {
                LibraryRow("Framer", BsIcons.FilmStrip, onClick = { onNavigate(Routes.framer()) })
                LibraryRow("Triptych", BsIcons.SquareSplitVertical, divider = true, onClick = { onNavigate(Routes.TRIPTYCH) })
                LibraryRow("Triplets", BsIcons.Users, divider = true, onClick = { onNavigate(Routes.TRIPLETS) })
                LibraryRow("Composer", BsIcons.Sparkle, divider = true, onClick = { onNavigate(Routes.COMPOSER) })
                LibraryRow("Duplicates", BsIcons.Copy, divider = true, onClick = { onNavigate(Routes.DUPLICATES) })
                LibraryRow("Pipelines", BsIcons.FlowArrow, divider = true, onClick = { onNavigate(Routes.PIPELINES) })
                LibraryRow("Organize", BsIcons.Funnel, divider = true, onClick = { onNavigate(Routes.ORGANIZE) })
            }
            Group {
                LibraryRow("Settings", BsIcons.Gear, onClick = { onNavigate(Routes.SETTINGS) })
                LibraryRow("Log out", BsIcons.SignOut, divider = true, color = BsColors.Danger, caret = false, onClick = {
                    scope.launch { AuthStore.logout() }
                })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
