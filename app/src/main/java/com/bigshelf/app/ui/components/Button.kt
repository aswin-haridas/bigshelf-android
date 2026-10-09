package com.bigshelf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
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
import com.bigshelf.app.ui.theme.Neutral

enum class ButtonVariant { Primary, Secondary, Ghost }

/**
 * Port of components/Button.tsx: `px-3 py-1.5 text-sm font-medium rounded-md`.
 * - primary: bg-primary, pressed bg-primary-deep
 * - secondary: bg-neutral-800, pressed bg-neutral-600
 * - ghost: transparent, pressed bg-neutral-800
 * Loading swaps to a dark surface + muted text with a 14dp spinner, like the web.
 */
@Composable
fun BsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentColor: Color = BsColors.Foreground,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    textStyle: TextStyle = BsText.Sm,
    gap: Dp = 6.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = when {
        loading -> BsColors.Surface
        variant == ButtonVariant.Primary -> if (pressed) BsColors.PrimaryDeep else BsColors.Primary
        variant == ButtonVariant.Secondary -> if (pressed) Neutral.N600 else Neutral.N800
        else -> if (pressed) Neutral.N800 else Color.Transparent
    }
    // `transition-all duration-300`
    val bg by animateColorAsState(target, tween(300), label = "buttonBg")
    val clickable = enabled && !loading

    Row(
        modifier = modifier
            .pressScale(interaction, if (clickable) 0.97f else 1f)
            .clip(BsShapes.Md)
            .background(bg)
            .clickable(interaction, indication = null, enabled = clickable, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (loading) BsColors.Muted else contentColor,
            LocalTextStyle provides textStyle.copy(fontWeight = BsWeight.Medium),
        ) {
            if (loading) Spinner(size = 14.dp)
            content()
        }
    }
}

/**
 * The square icon buttons from the photo / person headers:
 * `p-2 bg-surface border border-border rounded-lg text-muted`, foreground when [active].
 */
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
            .clip(BsShapes.Lg)
            .background(BsColors.Surface)
            .border(1.dp, if (active) BsColors.BorderFocus else BsColors.Border, BsShapes.Lg)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val color = tint ?: if (active) BsColors.Foreground else BsColors.Muted
        if (loading) Spinner(size = 16.dp, tint = color)
        else BsIcon(icon, size = 16.dp, tint = color, contentDescription = contentDescription)
    }
}
