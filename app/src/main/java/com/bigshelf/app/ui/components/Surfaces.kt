package com.bigshelf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.MonoFamily
import com.bigshelf.app.ui.theme.BsMotion

data class TabItem<T>(val id: T, val label: String)

/** Segmented control: a borderless surface pill, the active tab a lighter pill inside it. */
@Composable
fun <T> BsTabs(tabs: List<TabItem<T>>, active: T, onChange: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(BsShapes.Full)
            .background(BsColors.Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == active
            val bg by animateColorAsState(if (selected) BsColors.SurfaceHover else Color.Transparent, BsMotion.fast(), label = "tabBg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(BsShapes.Full)
                    .background(bg)
                    .bsClickable(scale = 1f) { onChange(tab.id) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    style = BsText.Xs.copy(fontWeight = BsWeight.Medium),
                    color = if (selected) BsColors.Foreground else BsColors.Muted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
    val track by animateColorAsState(if (checked) BsColors.Primary else BsColors.BorderFocus.copy(alpha = 0.8f), BsMotion.fast(), label = "track")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, BsMotion.spec(), label = "thumb")
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(BsShapes.Full)
            .background(track)
            .bsClickable(enabled = enabled, scale = 1f) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(BsShapes.Full).background(BsColors.Foreground))
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

/** Screen loader: the web's `<Loader />` centred with `py-16`, plus an optional `text-xs text-muted` line. */
@Composable
fun LoadingText(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfinityLoader(tint = BsColors.Muted)
        if (text != null) ShinyText(text, textAlign = TextAlign.Center)
    }
}

/**
 * Mobile top bar: optional back button, `text-lg font-semibold` title with the count under it,
 * actions on the right. Carries its own 16dp gutters so grids below can run edge to edge.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = BsText.Lg.copy(fontWeight = BsWeight.Semibold),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (count != null) Text(count, Modifier.padding(top = 2.dp), style = BsText.Xs.copy(fontFamily = MonoFamily), color = BsColors.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
