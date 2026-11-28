package com.bigshelf.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

/** Traced circle for buttons and other compact loading states. */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color = LocalContentColor.current) =
    TracedPath(CIRCLE, modifier, size, tint, durationMillis = 1000)

@Composable
fun Pulse(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    durationMillis: Int = 1500,
) {
    val transition = rememberInfiniteTransition(label = "loading pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween((durationMillis / 2).coerceAtLeast(1), easing = PulseEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse progress",
    )
    Canvas(
        modifier.size(size).semantics {
            contentDescription = "Loading"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        val strokeWidth = 2.dp.toPx()
        scale(0.95f + 0.1f * progress) {
            drawCircle(
                color = tint,
                radius = (this.size.minDimension - strokeWidth).coerceAtLeast(0f) / 2f,
                alpha = 0.8f - 0.4f * progress,
                style = Stroke(strokeWidth),
            )
        }
    }
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

@Composable
fun SquareLoader(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = BsColors.Muted) =
    TracedPath(SQUARE, modifier, size, tint)

@Composable
fun TriangleLoader(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = BsColors.Muted) =
    TracedPath(TRIANGLE, modifier, size, tint)

/** Matches the web loaders' 24-unit paths, faint track and 30% tracing dash. */
@Composable
private fun TracedPath(d: String, modifier: Modifier, iconSize: Dp, tint: Color, durationMillis: Int = 1600) {
    val transition = rememberInfiniteTransition(label = "trace")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "progress",
    )
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    val segment = remember { Path() }
    Canvas(modifier.size(iconSize).semantics { contentDescription = "Loading" }) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = if (iconSize <= 16.dp) 2.5f else 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, tint.copy(alpha = tint.alpha * 0.25f), style = stroke)
            val len = measure.length
            val start = (progress % 1f) * len
            val end = start + 0.3f * len
            segment.reset()
            measure.getSegment(start, minOf(end, len), segment, true)
            if (end > len) measure.getSegment(0f, end - len, segment, false)
            drawPath(segment, tint, style = stroke)
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

private const val CIRCLE = "M12 3a9 9 0 1 1 0 18 9 9 0 1 1 0-18Z"
private const val TRIANGLE = "M12 3 22 20H2Z"
private const val SQUARE = "M4 4h16v16H4Z"
private val PulseEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
