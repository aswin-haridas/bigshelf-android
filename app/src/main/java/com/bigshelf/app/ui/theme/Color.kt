package com.bigshelf.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens, one-to-one with the `@theme` block in frontend/src/index.css.
 * Use these for every colour; status colours are success / warning / info / danger.
 */
object BsColors {
    val Background = Color(0xFF0B0C10)
    val Surface = Color(0xFF16171B)
    val SurfaceHover = Color(0xFF232429)
    val Border = Color(0xFF26262B)
    val BorderFocus = Color(0xFF52525B)

    val Foreground = Color(0xFFFFFFFF)
    val Muted = Color(0xFFA3A3A3)
    val MutedSoft = Color(0xFFD4D4D4)
    val MutedFaint = Color(0xFF737373)

    val Primary = Color(0xFF7C3AED)
    val PrimaryDeep = Color(0xFF3B0764)

    val Danger = Color(0xFFF87171)
    val DangerBg = Color(0xFF450A0A)
    val DangerBorder = Color(0x80991B1B) // rgb(153 27 27 / 0.5)

    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Info = Color(0xFF60A5FA)
}

/**
 * Tailwind neutrals that shared web components still use directly
 * (Button `secondary`, SearchBar, FloatingActionBar, sidebar active row, upload modal).
 * Only for porting those components exactly - new UI should use [BsColors].
 */
internal object Neutral {
    val N300 = Color(0xFFD4D4D4)
    val N400 = Color(0xFFA3A3A3)
    val N600 = Color(0xFF525252)
    val N700 = Color(0xFF404040)
    val N800 = Color(0xFF262626)
    val N900 = Color(0xFF171717)
    val Rose400 = Color(0xFFFB7185)
}
