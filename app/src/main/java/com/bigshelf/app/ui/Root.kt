package com.bigshelf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.ui.auth.AuthLayout
import com.bigshelf.app.ui.auth.LoginScreen
import com.bigshelf.app.ui.auth.RegisterScreen
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.Toaster
import com.bigshelf.app.ui.shell.AppShell
import com.bigshelf.app.ui.theme.BsColors

/**
 * Root: restores the session (web `_app` beforeLoad -> fetchMe), then shows either the
 * auth pages or the app shell. Toasts sit above everything, like sonner's Toaster.
 */
@Composable
fun BigShelfRoot() {
    var checked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!checked) {
            AuthStore.fetchMe()
            checked = true
        }
    }
    // A refresh that fails mid-session drops back to Login (web: redirect to /login)
    LaunchedEffect(Unit) { BigShelfApp.api.sessionExpired.collect { AuthStore.onSessionEnded() } }

    Box(Modifier.fillMaxSize().background(BsColors.Background)) {
        when {
            !checked -> LoadingText(Modifier.align(Alignment.Center))
            AuthStore.status == AuthStore.Status.Authenticated -> AppShell()
            else -> AuthFlow()
        }
        Toaster(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun AuthFlow() {
    var register by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = register) { register = false }
    AuthLayout {
        if (register) {
            RegisterScreen(onLogin = { AuthStore.clearError(); register = false })
        } else {
            LoginScreen(onRegister = { AuthStore.clearError(); register = true })
        }
    }
}
