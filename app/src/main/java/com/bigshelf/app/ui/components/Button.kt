package com.bigshelf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.BsMotion

enum class ButtonVariant { Primary, Secondary, Ghost }

/**
 * Minimal buttons, rounded and flat:
 * - primary: white with black text, dimming while pressed
 * - secondary: surface grey, lighter while pressed
 * - ghost: transparent, surface grey while pressed
 * Loading swaps to the surface + muted text with a 14dp spinner.
 */
@Composable
fun BsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    textStyle: TextStyle = BsText.Sm,
    gap: Dp = 6.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = when {
        loading -> BsColors.Surface
        variant == ButtonVariant.Primary -> if (pressed) BsColors.MutedSoft else BsColors.Foreground
        variant == ButtonVariant.Secondary -> if (pressed) BsColors.SurfaceHover else BsColors.Surface
        else -> if (pressed) BsColors.Surface else Color.Transparent
    }
    val bg by animateColorAsState(target, BsMotion.fast(), label = "buttonBg")
    val clickable = enabled && !loading

    Row(
        modifier = modifier
            .pressScale(interaction, if (clickable) 0.97f else 1f)
            .clip(BsShapes.Lg)
            .background(bg)
            .clickable(interaction, indication = null, enabled = clickable, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides when {
                loading -> BsColors.Muted
                contentColor != null -> contentColor
                variant == ButtonVariant.Primary -> BsColors.Background
                else -> BsColors.Foreground
            },
            LocalTextStyle provides textStyle.copy(fontWeight = BsWeight.Medium),
        ) {
            if (loading) Spinner(size = 14.dp)
            content()
        }
    }
}

/** Borderless 44dp icon button: a bare glyph, on a surface circle when [active]. */
@Composable
fun BsIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    tint: Color? = null,
) {
    Box(
        modifier = modifier
            .bsClickable(enabled = enabled && !loading, onClick = onClick)
            .clip(BsShapes.Full)
            .background(if (active) BsColors.Surface else Color.Transparent)
            .size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        val color = tint ?: BsColors.Foreground
        if (loading) Spinner(size = 20.dp, tint = color)
        else BsIcon(icon, size = 20.dp, tint = color, contentDescription = contentDescription)
    }
}
