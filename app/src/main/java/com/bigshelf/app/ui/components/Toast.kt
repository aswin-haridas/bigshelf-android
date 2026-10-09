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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.delay
import com.bigshelf.app.ui.theme.BsMotion

/**
 * Port of the web's sonner `<Toaster position="top-center">` with its compact overrides
 * (280px wide, 8x12 padding, 12px text, 6px gap). Sonner's default light theme: white card,
 * #ededed border, near-black text. Call `toast.success("…")` from anywhere.
 */
object toast {
    internal enum class Kind { Success, Error, Info, Message }
    internal data class Item(val id: Long, val kind: Kind, val text: String)

    internal val items = mutableStateListOf<Item>()
    private var nextId = 0L

    private fun show(kind: Kind, text: String) {
        items += Item(nextId++, kind, text)
        // Sonner shows at most 3 at a time
        while (items.size > 3) items.removeAt(0)
    }

    fun success(text: String) = show(Kind.Success, text)
    fun error(text: String) = show(Kind.Error, text)
    fun info(text: String) = show(Kind.Info, text)
    fun message(text: String) = show(Kind.Message, text)

    internal fun dismiss(id: Long) {
        items.removeAll { it.id == id }
    }
}

private val ToastBg = Color(0xFFFFFFFF)
private val ToastBorder = Color(0xFFEDEDED)
private val ToastText = Color(0xFF171717)

@Composable
fun Toaster(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        toast.items.forEach { item ->
            key(item.id) {
                LaunchedEffect(Unit) {
                    delay(4000) // sonner default duration
                    toast.dismiss(item.id)
                }
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visible, enter = fadeIn(BsMotion.spec()) + slideInVertically(BsMotion.spec()) { -it }, exit = fadeOut(BsMotion.fast())) {
                    Row(
                        Modifier
                            .width(280.dp)
                            .shadow(6.dp, BsShapes.Lg, ambientColor = Color.Black.copy(alpha = 0.1f))
                            .clip(BsShapes.Lg)
                            .background(ToastBg)
                            .border(1.dp, ToastBorder, BsShapes.Lg)
                            .bsClickable(scale = 1f) { toast.dismiss(item.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val icon = when (item.kind) {
                            toast.Kind.Success -> BsIcons.CheckCircleFill
                            toast.Kind.Error -> BsIcons.WarningCircleFill
                            toast.Kind.Info -> BsIcons.InfoFill
                            toast.Kind.Message -> null
                        }
                        if (icon != null) BsIcon(icon, size = 16.dp, tint = ToastText)
                        Text(
                            item.text,
                            color = ToastText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = BsWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
