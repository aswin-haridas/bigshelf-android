package com.bigshelf.app

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentActivity
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.state.UploadStore
import com.bigshelf.app.ui.BigShelfRoot
import com.bigshelf.app.ui.theme.BigShelfTheme

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(true)
    private var unlockError by mutableStateOf<String?>(null)
    private var unlockInProgress = false
    private lateinit var biometricPrompt: BiometricPrompt
    private val pendingShares = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-only app: light system-bar icons on the background colour
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(false)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlockInProgress = false
                locked = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                unlockInProgress = false
                unlockError = errString.toString()
            }
        })
        preferHighRefreshRate()
        if (savedInstanceState == null) {
            queueShared(intent)
        } else {
            pendingShares.addAll(BundleCompat.getParcelableArrayList(savedInstanceState, "pendingShares", Uri::class.java).orEmpty())
        }
        setContent {
            BigShelfTheme {
                BigShelfRoot(locked, unlockError, ::requestUnlock, ::uploadPendingShares)
            }
        }
    }

    private fun requestUnlock() {
        if (unlockInProgress) return
        if (!getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            unlockError = "Set up a screen lock in Android settings to unlock BigShelf."
            return
        }
        unlockError = null
        val biometric = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG else BIOMETRIC_WEAK
        unlockInProgress = true
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock BigShelf")
                .setAllowedAuthenticators(biometric or DEVICE_CREDENTIAL)
                .build(),
        )
    }

    override fun onStop() {
        locked = true
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList("pendingShares", ArrayList(pendingShares))
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
        queueShared(intent)
        if (!locked && AuthStore.status == AuthStore.Status.Authenticated) uploadPendingShares()
    }

    private fun queueShared(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        pendingShares.addAll(uris)
    }

    private fun uploadPendingShares() {
        if (pendingShares.isEmpty()) return
        UploadStore.start(pendingShares.toList())
        pendingShares.clear()
    }
}
