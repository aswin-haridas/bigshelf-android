package com.bigshelf.app.ui.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.state.UploadStore
import com.bigshelf.app.ui.components.BsProgress
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.Neutral

/**
 * Port of components/UploadProgressModal.tsx: `fixed bottom-4 left-4 w-72 bg-neutral-800
 * border border-neutral-700 p-4 rounded-md` with an h-2 primary progress track.
 */
@Composable
fun UploadProgressModal(modifier: Modifier = Modifier) {
    val upload = UploadStore.upload ?: return
    val done = upload.progress >= 100
    // Linear easing keeps the steps between updates fluid, like the web's ease-linear
    val shown by animateFloatAsState(upload.progress.toFloat(), tween(500, easing = LinearEasing), label = "upload")
    Column(
        modifier
            .padding(16.dp)
            .width(288.dp)
            .shadow(10.dp, BsShapes.Md)
            .clip(BsShapes.Md)
            .background(Neutral.N800)
            .border(1.dp, Neutral.N700, BsShapes.Md)
            .padding(16.dp),
    ) {
        Text(
            if (done) "Upload complete" else "Uploading ${upload.fileCount} file(s)...",
            Modifier.padding(bottom = 8.dp),
            style = BsText.Sm.copy(fontWeight = BsWeight.Medium),
        )
        BsProgress(shown, height = 8.dp, track = Neutral.N700)
        Text(
            "${upload.progress}% · ${upload.processed} of ${upload.fileCount} processed",
            Modifier.padding(top = 8.dp),
            style = BsText.Xs,
            color = BsColors.Muted,
        )
    }
}
