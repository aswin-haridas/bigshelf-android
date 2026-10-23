package com.bigshelf.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bigshelf.app.R

/** Inter, bundled (Latin subset; other scripts fall back to the system font). */
val Sans = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
)

/**
 * Type scale in Inter, sized for compact phones (~384dp wide, e.g. Galaxy S23): nothing under
 * 12sp, 14sp secondary text, 15sp body, 22sp page titles. Names follow the web's `--text-*` tokens.
 */
object BsText {
    val Xs2 = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp) // text-2xs  -> M3 labelMedium
    val Xs = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp) // text-xs   -> M3 bodyMedium
    val Sm = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp) // text-sm   -> M3 bodyLarge
    val Base = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp) // text-base -> M3 bodyLarge
    val Lg = TextStyle(fontFamily = Sans, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em) // text-lg (page titles) -> M3 titleLarge
    val Xl = TextStyle(fontFamily = Sans, fontSize = 22.sp, lineHeight = 28.sp) // text-xl   -> M3 headlineSmall
    val Xl2 = TextStyle(fontFamily = Sans, fontSize = 26.sp, lineHeight = 32.sp) // text-2xl  -> M3 headlineMedium
    val Xl4 = TextStyle(fontFamily = Sans, fontSize = 32.sp, lineHeight = 40.sp) // text-4xl  -> M3 displaySmall

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

/** Counts, timestamps, scores, dimensions: Inter too, so numbers don't break the minimal look. */
val MonoFamily: FontFamily = Sans
