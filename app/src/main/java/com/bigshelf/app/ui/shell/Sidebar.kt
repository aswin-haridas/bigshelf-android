package com.bigshelf.app.ui.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.state.UploadStore
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.Neutral
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Decimal units, like Google Photos ("23.8 GB") - web sidebar formatBytes
private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val i = if (bytes > 0) minOf(floor(log10(bytes.toDouble()) / 3).toInt(), units.lastIndex) else 0
    val value = bytes / 1000.0.pow(i)
    val text = String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")
    return "$text ${units[i]}"
}

/** Does [path] belong to the sidebar item at [route]? (TanStack `activeProps` matching.) */
private fun isActive(path: String, route: String) = when (route) {
    Routes.HOME -> path == Routes.HOME
    Routes.PEOPLE -> path.startsWith("people") || path.startsWith("faces")
    else -> path == route || path.startsWith("$route/")
}

/**
 * Port of modules/home/components/sidebar.tsx as the mobile drawer (`w-64 bg-background p-3`):
 * dashed upload zone, nav items (`text-base p-2 rounded-md`, active `bg-neutral-800`),
 * Log out in danger, and storage used.
 */
@Composable
fun Sidebar(currentPath: String, onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val storage = rememberQuery("storage", invalidatedBy = setOf("photos")) { BigShelfApp.api.storage() }
    // System photo picker stands in for the web dropzone / file dialog
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        UploadStore.start(uris)
    }

    Column(
        Modifier
            .width(256.dp)
            .fillMaxHeight()
            .background(BsColors.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .bsClickable(scale = 0.98f) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
                .drawBehind {
                    drawRoundRect(
                        color = BsColors.Border,
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                    )
                }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BsIcon(BsIcons.UploadSimple, size = 16.dp, tint = BsColors.Muted)
            Text("Tap to upload images", style = BsText.Xs, color = BsColors.Muted, textAlign = TextAlign.Center)
        }

        Routes.sidebar.forEach { (label, route) ->
            val active = isActive(currentPath, route)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(BsShapes.Md)
                    .background(if (active) Neutral.N800 else BsColors.Background)
                    .bsClickable(scale = 0.98f) { onNavigate(route) }
                    .padding(8.dp),
            ) {
                Text(label, style = BsText.Base)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(BsShapes.Md)
                .bsClickable(scale = 0.98f) { scope.launch { AuthStore.logout() } }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BsIcon(BsIcons.SignOut, size = 16.dp, tint = BsColors.Danger)
            Text("Log out", style = BsText.Base, color = BsColors.Danger)
        }

        storage.data?.let {
            Text("${formatBytes(it.usedBytes)} used", Modifier.padding(8.dp), style = BsText.Xs, color = BsColors.Muted)
        }
        Spacer(Modifier.height(8.dp))
    }
}
