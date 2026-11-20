package com.bigshelf.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.delay
import com.bigshelf.app.ui.theme.BsMotion

/**
 * Transient feedback at the top of the window, with optional actions such as Undo.
 */
object toast {
    internal enum class Kind { Success, Error, Info, Message }
    internal data class Item(val id: Long, val kind: Kind, val text: String, val action: Pair<String, () -> Unit>? = null)

    internal val items = mutableStateListOf<Item>()
    private var nextId = 0L

    private fun show(kind: Kind, text: String, action: Pair<String, () -> Unit>? = null) {
        // A repeat of a toast already showing (e.g. every failed request in a row) isn't stacked again
        if (items.any { it.kind == kind && it.text == text && action == null }) return
        items += Item(nextId++, kind, text, action)
        // Sonner shows at most 3 at a time
        while (items.size > 3) items.removeAt(0)
    }

    fun success(text: String) = show(Kind.Success, text)

    /** A success toast with a button, e.g. "Undo" after moving photos to the Bin. */
    fun success(text: String, actionLabel: String, onAction: () -> Unit) = show(Kind.Success, text, actionLabel to onAction)
    fun error(text: String) = show(Kind.Error, text)
    fun info(text: String) = show(Kind.Info, text)
    fun message(text: String) = show(Kind.Message, text)

    internal fun dismiss(id: Long) {
        items.removeAll { it.id == id }
    }
}

@Composable
fun Toaster(modifier: Modifier = Modifier) {
    val accessibility = LocalAccessibilityManager.current
    Column(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, top = 16.dp, end = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        toast.items.forEach { item ->
            key(item.id) {
                LaunchedEffect(accessibility) {
                    val timeout = if (item.action != null) 6000L else 4000L
                    delay(accessibility?.calculateRecommendedTimeoutMillis(
                        timeout, containsIcons = true, containsText = true, containsControls = true,
                    ) ?: timeout)
                    toast.dismiss(item.id)
                }
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visible, enter = fadeIn(BsMotion.spec()) + slideInVertically(BsMotion.spec()) { -it }, exit = fadeOut(BsMotion.fast())) {
                    Row(
                        Modifier
                            .widthIn(max = 320.dp)
                            .shadow(6.dp, BsShapes.Lg, ambientColor = Color.Black.copy(alpha = 0.1f))
                            .clip(BsShapes.Lg)
                            .background(BsColors.Surface)
                            .border(1.dp, BsColors.ControlBorder, BsShapes.Lg)
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val icon = when (item.kind) {
                            toast.Kind.Success -> BsIcons.CheckCircleFill
                            toast.Kind.Error -> BsIcons.WarningCircleFill
                            toast.Kind.Info -> BsIcons.InfoFill
                            toast.Kind.Message -> null
                        }
                        if (icon != null) BsIcon(icon, size = 16.dp, tint = BsColors.Foreground)
                        Text(
                            item.text,
                            Modifier.weight(1f, fill = item.action != null),
                            color = BsColors.Foreground,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = BsWeight.Medium,
                        )
                        item.action?.let { (label, onAction) ->
                            BsButton(
                                onClick = {
                                    toast.dismiss(item.id)
                                    onAction()
                                },
                                variant = ButtonVariant.Ghost,
                            ) { Text(label) }
                        }
                        BsIconButton(BsIcons.X, "Dismiss notification", onClick = { toast.dismiss(item.id) })
                    }
                }
            }
        }
    }
}
