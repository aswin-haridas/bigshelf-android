package com.bigshelf.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Tailwind v4 radii as the web app resolves them (`--radius-lg` / `--radius-xl` from index.css). */
object BsShapes {
    val Sm = RoundedCornerShape(4.dp) // rounded
    val Md = RoundedCornerShape(6.dp) // rounded-md
    val Lg = RoundedCornerShape(8.dp) // rounded-lg
    val Xl = RoundedCornerShape(12.dp) // rounded-xl
    val Full = CircleShape // rounded-full
}
