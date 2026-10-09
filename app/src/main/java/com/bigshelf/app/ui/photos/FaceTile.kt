package com.bigshelf.app.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Person
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight

/** Web `personImageSrc`: face crop first, then the whole cover photo. */
fun personImageSrc(person: Person): String? {
    val cover = person.cover ?: return null
    return cover.faceThumbnail
        ?: cover.box?.thumbnailUrl
        ?: cover.box?.faceId?.let { "/api/v1/faces/$it/thumbnail" }
        ?: cover.thumbnailUrl
}

fun photoCountLabel(count: Int) = "$count photo${if (count == 1) "" else "s"}"

/**
 * Port of face-tile.tsx / face-card.tsx: square `rounded-md border border-border` face with a
 * black gradient caption. Touch adaptation: the caption the web reveals on hover is always shown.
 */
@Composable
fun FaceTile(
    imageSrc: String?,
    name: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .bsClickable(scale = 0.97f, onClick = onClick)
            .clip(BsShapes.Md)
            .background(BsColors.Background)
            .border(1.dp, BsColors.Border, BsShapes.Md),
    ) {
        if (imageSrc != null) {
            AsyncImage(
                model = BigShelfApp.api.mediaUrl(imageSrc),
                contentDescription = name ?: "Face",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BsIcon(BsIcons.Users, Modifier.align(Alignment.Center), size = 24.dp, tint = BsColors.Muted)
        }
        if (name != null || subtitle != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Black.copy(alpha = 0.9f),
                        ),
                    )
                    .padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (name != null) {
                    Text(
                        name,
                        style = BsText.Xs.copy(fontWeight = BsWeight.Medium, lineHeight = BsText.Xs.fontSize * 1.25),
                        color = BsColors.Foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        Modifier.padding(top = 2.dp),
                        style = BsText.Xs2.copy(lineHeight = BsText.Xs2.fontSize * 1.25),
                        color = BsColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
