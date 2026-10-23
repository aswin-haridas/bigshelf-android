package com.bigshelf.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.bigshelf.app.state.UploadStore
import com.bigshelf.app.ui.BigShelfRoot
import com.bigshelf.app.ui.theme.BigShelfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-only app: light system-bar icons on the background colour
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        preferHighRefreshRate()
        // Only on first create, so a rotation doesn't re-upload the share
        if (savedInstanceState == null) uploadShared(intent)
        setContent {
            BigShelfTheme { BigShelfRoot() }
        }
    }

    /** Ask for the panel's fastest mode at the current resolution (120 Hz on S23); the OS still drops it when idle. */
    private fun preferHighRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uploadShared(intent)
    }

    /** Images shared from another app's share sheet go straight into the upload queue. */
    private fun uploadShared(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        UploadStore.start(uris)
    }
}
