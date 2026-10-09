package com.bigshelf.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight

/**
 * Port of components/CustomModal.tsx: `bg-black/50` backdrop, `bg-background rounded-xl
 * max-w-md` dialog, `text-sm font-semibold` title with an X button, optional bordered footer.
 * Fades/scales in over 200ms like the web's `starting:opacity-0 starting:scale-95`.
 */
@Composable
fun BsModal(
    title: String,
    onClose: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The window's own dim would stack on our bg-black/50 scrim
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(200)) }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 448.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = 0.95f + 0.05f * enter.value
                        scaleX = s
                        scaleY = s
                    }
                    .shadow(24.dp, BsShapes.Xl)
                    .clip(BsShapes.Xl)
                    .background(BsColors.Background)
                    // Swallow taps so they don't reach the backdrop
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, style = BsText.Sm.copy(fontWeight = BsWeight.Semibold))
                    Box(
                        Modifier
                            .bsClickable(onClick = onClose)
                            .clip(BsShapes.Md)
                            .padding(8.dp),
                    ) {
                        BsIcon(BsIcons.XBold, size = 16.dp, tint = BsColors.Muted, contentDescription = "Close")
                    }
                }
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
                if (footer != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind { drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = footer,
                    )
                }
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

