package com.bigshelf.app.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch

/**
 * Port of components/CustomModal.tsx as a bottom sheet: slides up from the bottom over a
 * `bg-black/50` scrim, drag down or tap outside to close. Title with an X button, scrolling
 * body, optional bordered footer that stays pinned under it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BsModal(
    title: String,
    onClose: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The X slides the sheet down before it leaves, like a drag or a tap outside
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onClose() } }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        containerColor = BsColors.Background,
        contentColor = BsColors.Foreground,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = BsShapes.Xl.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        dragHandle = { BottomSheetDefaults.DragHandle(color = BsColors.BorderFocus) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Titles carry names ("Share \"…\"", "Edit: …"): never push the X off the sheet
                Text(
                    title,
                    Modifier.weight(1f).padding(end = 8.dp),
                    style = BsText.Base.copy(fontWeight = BsWeight.Semibold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .bsClickable(onClick = close)
                        .clip(BsShapes.Md)
                        .padding(8.dp),
                ) {
                    BsIcon(BsIcons.XBold, size = 16.dp, tint = BsColors.Muted, contentDescription = "Close")
                }
            }
            // Tall content scrolls between a fixed header and footer, so the buttons stay reachable
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) { content() }
            if (footer != null) {
                // Wraps onto a second line when the buttons don't fit (long names, big text)
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind { drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { footer() }
            }
        }
    }
}

/** Confirm step for irreversible actions (the web uses `window.confirm`). */
@Composable
fun ConfirmModal(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    BsModal(
        title = title,
        onClose = onClose,
        footer = {
            BsButton(onClick = onClose, variant = ButtonVariant.Ghost) { Text("Cancel") }
            BsButton(onClick = { onClose(); onConfirm() }, variant = ButtonVariant.Secondary, contentColor = BsColors.Danger) {
                Text(confirmLabel)
            }
        },
    ) {
        Text(message, color = BsColors.Muted)
    }
}

