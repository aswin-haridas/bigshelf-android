package com.bigshelf.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsMotion

/**
 * The web's global `button:active { transform: scale(0.96) }` (index.css) - every tappable
 * shrinks slightly while pressed instead of showing a Material ripple.
 */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = BsMotion.fast(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Inset border for keyboard and directional-pad focus. */
fun Modifier.bsFocusIndicator(interaction: MutableInteractionSource, shape: Shape = BsShapes.Md): Modifier = composed {
    val focused by interaction.collectIsFocusedAsState()
    if (focused) border(2.dp, BsColors.FocusRing, shape) else this
}

/** Clickable with scale on press and an outline on focus. */
fun Modifier.bsClickable(
    enabled: Boolean = true,
    scale: Float = 0.96f,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .pressScale(interaction, if (enabled) scale else 1f)
        .bsFocusIndicator(interaction)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
