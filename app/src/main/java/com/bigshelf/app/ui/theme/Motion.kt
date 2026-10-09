package com.bigshelf.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Motion tokens: short and snappy. Everything that moves uses one of these so the app
 * feels consistent - state changes in [FAST_MS], things entering or leaving in [MS].
 */
object BsMotion {
    const val FAST_MS = 100
    const val MS = 150

    /** Quick start, soft landing (Material "emphasized decelerate"). */
    val Ease = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST_MS, easing = Ease)
    fun <T> spec(): FiniteAnimationSpec<T> = tween(MS, easing = Ease)
}
