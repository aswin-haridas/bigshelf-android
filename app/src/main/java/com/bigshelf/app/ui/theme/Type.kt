package com.bigshelf.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale named after index.css (`--text-*`), but sized to the Material 3 scale for
 * phones: nothing under 12sp, 14sp secondary text, 16sp body. Each token sits one step
 * above its web value, so the web's hierarchy holds at a readable mobile size.
 */
object BsText {
    val Xs2 = TextStyle(fontSize = 12.sp, lineHeight = 16.sp) // text-2xs  -> M3 labelMedium
    val Xs = TextStyle(fontSize = 14.sp, lineHeight = 20.sp) // text-xs   -> M3 bodyMedium
    val Sm = TextStyle(fontSize = 16.sp, lineHeight = 24.sp) // text-sm   -> M3 bodyLarge
    val Base = TextStyle(fontSize = 16.sp, lineHeight = 24.sp) // text-base -> M3 bodyLarge
    val Lg = TextStyle(fontSize = 22.sp, lineHeight = 28.sp) // text-lg (page titles) -> M3 titleLarge
    val Xl = TextStyle(fontSize = 24.sp, lineHeight = 32.sp) // text-xl   -> M3 headlineSmall
    val Xl2 = TextStyle(fontSize = 28.sp, lineHeight = 36.sp) // text-2xl  -> M3 headlineMedium
    val Xl4 = TextStyle(fontSize = 36.sp, lineHeight = 44.sp) // text-4xl  -> M3 displaySmall

    /** `tracking-tight` */
    val TrackingTight = (-0.025).em

    /** `tracking-wider` */
    val TrackingWider = 0.05.em
}

/** `--font-weight-*` tokens. No bold: the web rules forbid `font-bold`. */
object BsWeight {
    val Light = FontWeight(300)
    val Normal = FontWeight(400)
    val Medium = FontWeight(500)
    val Semibold = FontWeight(600)
}

/** `font-mono`: counts, timestamps, scores, dimensions. */
val MonoFamily: FontFamily = FontFamily.Monospace
