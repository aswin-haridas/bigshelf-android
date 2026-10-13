package com.bigshelf.app.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Photo
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsMotion

/**
 * Port of modules/common/components/image-card.tsx: sharp-cornered 3:4 tile with the
 * selection circle (top-left), AI tulip (top-right), video play badge (bottom-left),
 * processing ring + spinner (info) and a primary/30 wash when selected.
 *
 * Touch adaptation: the web reveals the circle on hover; here it shows while a selection
 * is active, and a long press starts one (the web's checkmark click).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCard(
    photo: Photo,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    // null: the caller sizes the tile (the tall tiles of the explore mosaic)
    aspectRatio: Float? = 3f / 4f,
) {
    val processing = photo.isProcessing
    Box(
        modifier
            .then(if (aspectRatio != null) Modifier.fillMaxWidth().aspectRatio(aspectRatio) else Modifier)
            .background(BsColors.Surface.copy(alpha = 0.3f))
            .then(if (processing) Modifier.border(2.dp, BsColors.Info) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onToggle),
    ) {
        val src = photo.thumbnailUrl ?: photo.imageSrc
        if (src != null) {
            // Grid tiles use the 720x960 (3:4) thumbnail, like the web
            AsyncImage(
                model = BigShelfApp.api.mediaUrl(src),
                contentDescription = photo.title ?: photo.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (photo.isAiImage) {
            BsIcon(BsIcons.FlowerTulip, Modifier.align(Alignment.TopEnd).padding(8.dp), size = 16.dp, tint = BsColors.Foreground)
        }
        if (photo.isVideo) {
            BsIcon(
                BsIcons.PlayFill,
                Modifier.align(Alignment.BottomStart).padding(8.dp),
                size = 20.dp,
                tint = Color.White.copy(alpha = 0.9f),
            )
        }
        // Selection tint: primary (purple) wash instead of an outline, under the checkmark
        if (selected) Box(Modifier.fillMaxSize().background(BsColors.Primary.copy(alpha = 0.3f)))

        AnimatedVisibility(
            visible = selected || selectionActive,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            enter = fadeIn(BsMotion.fast()) + scaleIn(BsMotion.fast(), initialScale = 0.9f),
            exit = fadeOut(BsMotion.fast()) + scaleOut(BsMotion.fast(), targetScale = 0.9f),
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(BsShapes.Full)
                    .background(if (selected) BsColors.Primary else Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, BsColors.Muted.copy(alpha = 0.7f), BsShapes.Full)
                    .bsClickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) BsIcon(BsIcons.CheckBold, size = 14.dp, tint = BsColors.Foreground)
            }
        }

        if (processing) {
            Spinner(Modifier.align(Alignment.BottomEnd).padding(6.dp), size = 16.dp, tint = BsColors.Info)
        }
    }
}
