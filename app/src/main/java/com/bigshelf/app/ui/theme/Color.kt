package com.bigshelf.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens: true-black, near-monochrome, with purple kept as the one accent (selection,
 * switches, progress). Use these for every colour; status colours are success / warning / info / danger.
 */
object BsColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceHover = Color(0xFF1C1C1C)
    val Border = Color(0xFF1F1F1F)
    val BorderFocus = Color(0xFF3F3F3F)

    val Foreground = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8E8E8E)
    val MutedSoft = Color(0xFFD4D4D4)
    val MutedFaint = Color(0xFF5C5C5C)

    val Primary = Color(0xFF7C3AED)
    val PrimaryDeep = Color(0xFF3B0764)

    val Danger = Color(0xFFF87171)
    val DangerBg = Color(0xFF450A0A)
    val DangerBorder = Color(0x80991B1B) // rgb(153 27 27 / 0.5)

    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Info = Color(0xFF60A5FA)
}

/** Greys a few older components use directly, tuned to the black palette. New UI should use [BsColors]. */
internal object Neutral {
    val N300 = Color(0xFFD4D4D4)
    val N400 = Color(0xFFA3A3A3)
    val N600 = Color(0xFF3A3A3A)
    val N700 = Color(0xFF2A2A2A)
    val N800 = Color(0xFF1C1C1C)
    val N900 = Color(0xFF121212)
    val Rose400 = Color(0xFFFB7185)
}
