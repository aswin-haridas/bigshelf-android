package com.bigshelf.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale from index.css (`--text-*`) plus the Tailwind defaults the web app uses
 * (`text-base`, `text-lg`). 1rem = 16sp; line heights follow Tailwind's pairing.
 */
object BsText {
    val Xs2 = TextStyle(fontSize = 11.sp, lineHeight = 16.sp) // text-2xs
    val Xs = TextStyle(fontSize = 12.sp, lineHeight = 16.sp) // text-xs
    val Sm = TextStyle(fontSize = 14.sp, lineHeight = 20.sp) // text-sm
    val Base = TextStyle(fontSize = 16.sp, lineHeight = 24.sp) // text-base
    val Lg = TextStyle(fontSize = 18.sp, lineHeight = 28.sp) // text-lg (page titles)
    val Xl = TextStyle(fontSize = 20.sp, lineHeight = 28.sp) // text-xl
    val Xl2 = TextStyle(fontSize = 24.sp, lineHeight = 32.sp) // text-2xl
    val Xl4 = TextStyle(fontSize = 36.sp, lineHeight = 40.sp) // text-4xl

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
