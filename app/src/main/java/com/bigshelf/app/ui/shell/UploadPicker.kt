package com.bigshelf.app.ui.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bigshelf.app.state.UploadStore

/**
 * Opens the system photo picker and uploads what's picked. First asks once for media
 * location access: without it Android strips GPS from the files, and the photos never reach
 * the map or Places. The picker opens whatever the answer.
 */
@Composable
fun rememberUploadPicker(albumId: String? = null): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        UploadStore.start(uris, albumId)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pick() }
    return {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) permission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) else pick()
    }
}
