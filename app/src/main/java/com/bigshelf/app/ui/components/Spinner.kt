package com.bigshelf.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.ui.theme.BsText
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
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

/** Web's `<Loader />`: a dash chasing around an infinity sign, over a faint copy of the sign. */
@Composable
fun InfinityLoader(modifier: Modifier = Modifier, width: Dp = 48.dp, tint: Color = LocalContentColor.current) =
    TracedPath(INFINITY, 24f, modifier.size(width, width / 2), tint, tint)

/** Web's `<TriangleLoader />` for modals: a white line running along a triangle's edges. */
@Composable
fun TriangleLoader(modifier: Modifier = Modifier, size: Dp = 32.dp, tint: Color = BsColors.Muted) =
    TracedPath(TRIANGLE, 24f, modifier.size(size), tint, BsColors.Foreground)

/** Draws [d] (an SVG path in a [viewBoxWidth]-wide box) faintly, with 30% of it traced in [dash] on a loop. */
@Composable
private fun TracedPath(d: String, viewBoxWidth: Float, modifier: Modifier, tint: Color, dash: Color) {
    val transition = rememberInfiniteTransition(label = "trace")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "progress",
    )
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    val segment = remember { Path() }
    Canvas(modifier.semantics { contentDescription = "Loading" }) {
        scale(size.width / viewBoxWidth, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, tint.copy(alpha = 0.2f), style = stroke)
            val len = measure.length
            val start = progress * len
            val end = start + 0.3f * len
            segment.reset()
            measure.getSegment(start, minOf(end, len), segment, true)
            if (end > len) measure.getSegment(0f, end - len, segment, true)
            drawPath(segment, dash, style = stroke)
        }
    }
}

/** Web's `.shiny-text` (reactbits ShinyText): a white highlight sweeping across the text. */
@Composable
fun ShinyText(text: String, modifier: Modifier = Modifier, style: TextStyle = BsText.Xs, color: Color = BsColors.Muted, textAlign: TextAlign? = null) {
    val transition = rememberInfiniteTransition(label = "shine")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    var width by remember { mutableFloatStateOf(0f) }
    // The band's centre travels from past the right edge to past the left edge, like background-position 100% -> -100%
    val centre = width * (1.5f - 2f * t)
    val brush = Brush.linearGradient(
        0f to color, 0.4f to color, 0.5f to Color.White.copy(alpha = 0.9f), 0.6f to color, 1f to color,
        start = Offset(centre - width, 0f),
        end = Offset(centre + width, 0f),
    )
    Text(text, modifier.onSizeChanged { width = it.width.toFloat() }, style = style.copy(brush = brush), textAlign = textAlign)
}

private const val INFINITY = "M12 6C10 3.33 8 2 6 2a4 4 0 1 0 0 8c2 0 4-1.33 6-4zm0 0c2 2.67 4 4 6 4a4 4 0 0 0 0-8c-2 0-4 1.33-6 4z"
private const val TRIANGLE = "M12 3L21.5 20H2.5Z"
