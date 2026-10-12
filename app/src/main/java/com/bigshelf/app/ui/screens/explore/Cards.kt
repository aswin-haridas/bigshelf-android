package com.bigshelf.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.bigshelf.app.ui.theme.BsIcons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.LibraryEvent
import com.bigshelf.app.data.Photo
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Square cover over a title and a muted subtitle: the album, place and event cards. Without a
 * cover photo the square shows [icon] (e.g. a "New album" card).
 */
@Composable
fun CoverCard(
    title: String,
    subtitle: String?,
    cover: Photo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(modifier.bsClickable(scale = 0.98f, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(BsShapes.Xl)
                .background(BsColors.Surface.copy(alpha = 0.4f))
                .border(1.dp, BsColors.Border.copy(alpha = 0.3f), BsShapes.Xl),
            contentAlignment = Alignment.Center,
        ) {
            val src = cover?.thumbnailUrl ?: cover?.imageSrc
            if (src != null) {
                AsyncImage(
                    model = BigShelfApp.api.mediaUrl(src),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (icon != null) {
                BsIcon(icon, size = 32.dp, tint = BsColors.Muted)
            }
        }
        Text(
            title,
            Modifier.padding(top = 6.dp),
            style = BsText.Xs.copy(fontWeight = BsWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(subtitle, style = BsText.Xs2, color = BsColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Panel header (`text-sm font-medium`) with optional text actions on the right. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actions: List<Pair<String, () -> Unit>> = emptyList()) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
        actions.forEach { (label, onClick) ->
            Text(
                label,
                Modifier.clip(BsShapes.Md).bsClickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
                style = BsText.Xs.copy(fontWeight = BsWeight.Medium),
                color = BsColors.Muted,
            )
        }
    }
}

/**
 * Bordered group of 56dp rows (icon, label, trailing hint, caret), like the Library tab's.
 * Each row is (label, icon, hint) to its click.
 */
@Composable
fun RowGroup(modifier: Modifier, rows: List<Pair<Triple<String, ImageVector, String?>, () -> Unit>>) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(BsShapes.Xl)
            .background(BsColors.Surface.copy(alpha = 0.3f))
            .border(1.dp, BsColors.Border, BsShapes.Xl),
    ) {
        rows.forEachIndexed { i, (row, onClick) ->
            val (label, icon, hint) = row
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { if (i > 0) drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                    .bsClickable(scale = 1f, onClick = onClick)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BsIcon(icon, size = 20.dp, tint = BsColors.Muted)
                Text(label, Modifier.weight(1f), style = BsText.Sm)
                if (hint != null) Text(hint, style = BsText.Xs, color = BsColors.MutedFaint)
                BsIcon(BsIcons.CaretRight, size = 16.dp, tint = BsColors.MutedFaint)
            }
        }
    }
}

/** Pill with an icon or colour dot: search matches and colour chips. */
@Composable
fun Chip(label: String, onClick: () -> Unit, icon: ImageVector? = null, swatch: Color? = null) {
    Row(
        Modifier
            .height(40.dp)
            .clip(BsShapes.Full)
            .background(BsColors.Surface)
            .border(1.dp, BsColors.Border, BsShapes.Full)
            .bsClickable(scale = 0.96f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (swatch != null) Box(Modifier.size(16.dp).clip(BsShapes.Full).background(swatch).border(1.dp, BsColors.BorderFocus, BsShapes.Full))
        if (icon != null) BsIcon(icon, size = 16.dp, tint = BsColors.Muted)
        Text(label, style = BsText.Xs.copy(fontWeight = BsWeight.Medium), maxLines = 1)
    }
}

/**
 * The colour chips (backend explore.COLORS). The swatches show photo content colours, so
 * they're the one place colours come from outside BsColors.
 */
val PHOTO_COLORS = listOf(
    Triple("red", "Red", Color(0xFFDC2626)),
    Triple("orange", "Orange", Color(0xFFF97316)),
    Triple("yellow", "Yellow", Color(0xFFFACC15)),
    Triple("green", "Green", Color(0xFF16A34A)),
    Triple("blue", "Blue", Color(0xFF2563EB)),
    Triple("purple", "Purple", Color(0xFF9333EA)),
    Triple("pink", "Pink", Color(0xFFF9A8D4)),
    Triple("monochrome", "Black & white", Color(0xFF9CA3AF)),
)

fun colorLabel(id: String) = PHOTO_COLORS.firstOrNull { it.first == id }?.second ?: id

// ---------- Dates ----------

/** An API timestamp as a local date, or null if it doesn't parse. */
fun localDate(iso: String?): LocalDate? = iso?.let {
    runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        // No offset (some backends drop it): the API stores UTC
        ?: runCatching { LocalDateTime.parse(it).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
}

/** "Mar 3, 2026", "Mar 1 – 3, 2026", "Dec 30, 2025 – Jan 2, 2026". */
fun dateRange(startIso: String, endIso: String): String {
    val start = localDate(startIso) ?: return ""
    val end = localDate(endIso) ?: start
    val day = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    return when {
        start == end -> day.format(start)
        start.year != end.year -> "${day.format(start)} – ${day.format(end)}"
        start.month != end.month ->
            "${DateTimeFormatter.ofPattern("MMM d", Locale.US).format(start)} – ${day.format(end)}"
        else -> "${DateTimeFormatter.ofPattern("MMM d", Locale.US).format(start)} – ${end.dayOfMonth}, ${end.year}"
    }
}

/** "Christmas", "Trip to Goa", "Goa", else the date. [place] is the geocoded centre, if any. */
fun eventTitle(event: LibraryEvent, place: String?): String = when {
    event.occasion != null -> event.occasion
    event.isTrip -> if (place != null) "Trip to $place" else "Trip"
    place != null -> place
    else -> localDate(event.start)?.let { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US).format(it) } ?: "Event"
}

fun countLabel(n: Int, noun: String) = "$n ${if (n == 1) noun else "${noun}s"}"
