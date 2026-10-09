package com.bigshelf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.Neutral

/**
 * Port of components/FloatingActionBar.tsx (mobile layout: `inset-x-3 bottom-3`):
 * `rounded-xl bg-neutral-900/90 shadow-lg ring-1 ring-white/10`, one scrollable row `gap-2 p-1.5`.
 * The caller places it at the bottom of the screen.
 */
@Composable
fun FloatingActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(10.dp, BsShapes.Xl)
            .clip(BsShapes.Xl)
            .background(Neutral.N900.copy(alpha = 0.9f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), BsShapes.Xl),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

data class TabItem<T>(val id: T, val label: String)

/**
 * Port of components/ui/Tabs.tsx - segmented control:
 * `h-8 bg-surface border border-border rounded-md p-0.5 gap-1`, active tab `bg-white/10 text-white`.
 */
@Composable
fun <T> BsTabs(tabs: List<TabItem<T>>, active: T, onChange: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(32.dp)
            .clip(BsShapes.Md)
            .background(BsColors.Surface)
            .border(1.dp, BsColors.Border, BsShapes.Md)
            .padding(2.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == active
            val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent, label = "tabBg")
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(BsShapes.Sm)
                    .background(bg)
                    .bsClickable(scale = 1f) { onChange(tab.id) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    style = BsText.Sm.copy(fontWeight = BsWeight.Medium),
                    color = if (selected) BsColors.Foreground else BsColors.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Port of components/ui/Switch.tsx (shadcn base-nova): 32x18.4dp track, primary when on,
 * `border-focus/80` when off, 16dp white thumb.
 */
@Composable
fun BsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) BsColors.Primary else BsColors.BorderFocus.copy(alpha = 0.8f), label = "track")
    val x by animateDpAsState(if (checked) 14.dp else 0.dp, label = "thumb")
    Box(
        modifier
            .size(width = 32.dp, height = 18.4.dp)
            .clip(BsShapes.Full)
            .background(track)
            .bsClickable(enabled = enabled, scale = 1f) { onCheckedChange(!checked) }
            .padding(1.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(16.dp).clip(BsShapes.Full).background(BsColors.Foreground))
    }
}

/** Port of components/ui/Progress.tsx track: `h-1 rounded-full bg-border`, primary fill. */
@Composable
fun BsProgress(percent: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 4.dp, track: Color = BsColors.Border) {
    Box(modifier.fillMaxWidth().height(height).clip(BsShapes.Full).background(track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent.coerceIn(0f, 100f) / 100f)
                .clip(BsShapes.Full)
                .background(BsColors.Primary),
        )
    }
}

/** Port of components/ui/EmptyState.tsx: centred `text-lg font-semibold` title, `text-sm text-muted` line. */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = BsText.Lg.copy(fontWeight = BsWeight.Semibold), textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                description,
                Modifier.padding(top = 4.dp),
                style = BsText.Sm,
                color = BsColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The web's inline `<p className="text-center text-sm text-muted py-16">Loading…</p>`. */
@Composable
fun LoadingText(modifier: Modifier = Modifier, text: String = "Loading…") {
    Text(
        text,
        modifier.fillMaxWidth().padding(vertical = 64.dp),
        style = BsText.Sm,
        color = BsColors.Muted,
        textAlign = TextAlign.Center,
    )
}

/** Page header: `text-lg font-semibold` title + `text-xs text-muted` count, actions on the right. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(title, style = BsText.Lg.copy(fontWeight = BsWeight.Semibold), maxLines = 1)
        if (count != null) Text(count, style = BsText.Xs, color = BsColors.Muted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = actions)
    }
}

