package com.bigshelf.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii: soft and generous, rounder than the web app. */
object BsShapes {
    val Sm = RoundedCornerShape(6.dp)
    val Md = RoundedCornerShape(10.dp)
    val Lg = RoundedCornerShape(14.dp)
    val Xl = RoundedCornerShape(20.dp)
    val Full = CircleShape
}
