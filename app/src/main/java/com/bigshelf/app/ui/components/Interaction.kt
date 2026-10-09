package com.bigshelf.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
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

/** Clickable with the web press feedback (scale) and no ripple. */
fun Modifier.bsClickable(enabled: Boolean = true, scale: Float = 0.96f, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .pressScale(interaction, if (enabled) scale else 1f)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}
