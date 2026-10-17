package com.bigshelf.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons

/** Phosphor icon at one of the web sizes (12 / 14 / 16 / 20 / 24 / 32). */
@Composable
fun BsIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}

/** `<SpinnerIcon className="animate-spin" />` - the only loading indicator the web app uses. */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color = LocalContentColor.current) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    BsIcon(BsIcons.Spinner, modifier.rotate(angle), size = size, tint = tint, contentDescription = "Loading")
}

/** `<div className="animate-pulse bg-surface" />` - stands in for an image until it loads. */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    // Tailwind's animate-pulse: opacity 1 -> 0.5 -> 1 over 2s
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier.background(BsColors.Surface.copy(alpha = alpha)))
}
