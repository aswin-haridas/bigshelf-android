package com.bigshelf.app.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Material pieces we still lean on (drawer, ripples) get the same palette, so nothing
// renders in Material's default purple-grey.
private val scheme = darkColorScheme(
    primary = BsColors.Primary,
    onPrimary = BsColors.Foreground,
    background = BsColors.Background,
    onBackground = BsColors.Foreground,
    surface = BsColors.Background,
    onSurface = BsColors.Foreground,
    surfaceVariant = BsColors.Surface,
    onSurfaceVariant = BsColors.Muted,
    outline = BsColors.Border,
    error = BsColors.Danger,
    errorContainer = BsColors.DangerBg,
)

/** Dark-only, like the web app. Body text defaults to white `text-sm`. */
@Composable
fun BigShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalContentColor provides BsColors.Foreground,
            LocalTextStyle provides BsText.Sm,
            // Web uses `selection:bg-white selection:text-black` on the auth pages
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = BsColors.Foreground,
                backgroundColor = BsColors.Foreground.copy(alpha = 0.3f),
            ),
            content = content,
        )
    }
}
