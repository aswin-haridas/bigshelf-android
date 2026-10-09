package com.bigshelf.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bigshelf.app.R
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsField
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch

/**
 * Port of modules/auth/components/auth-layout.tsx (mobile stacking): the hero image card
 * (`min-h-[380px] bg-primary-deep rounded-xl`) above a `max-w-sm space-y-6` form column.
 */
@Composable
fun AuthLayout(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BsColors.Background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(BsShapes.Xl)
                .background(BsColors.PrimaryDeep),
        ) {
            Image(
                painterResource(R.drawable.bigshelf_hero),
                contentDescription = "Hero background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 384.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = content,
            )
        }
    }
}

/** `AuthHeader`: `text-xl font-semibold tracking-tight` title, `text-xs text-muted` subtitle. */
@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = BsText.Xl.copy(fontWeight = BsWeight.Semibold, letterSpacing = BsText.TrackingTight),
            color = BsColors.Foreground,
        )
        Text(subtitle, Modifier.padding(top = 4.dp), style = BsText.Xs, color = BsColors.Muted, textAlign = TextAlign.Center)
    }
}

/** `AuthFooter`: muted prompt with a semibold foreground link. */
@Composable
private fun AuthFooter(prompt: String, linkText: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            buildAnnotatedString {
                append("$prompt ")
                withStyle(SpanStyle(color = BsColors.Foreground, fontWeight = BsWeight.Semibold)) { append(linkText) }
            },
            Modifier.bsClickable(scale = 1f, onClick = onClick),
            style = BsText.Xs,
            color = BsColors.Muted,
        )
    }
}

/** `AuthError`: `rounded-lg bg-danger-bg/40 border border-danger-border px-3 py-2 text-xs text-danger`. */
@Composable
private fun AuthError(error: String?) {
    if (error == null) return
    Text(
        error,
        Modifier
            .fillMaxWidth()
            .clip(BsShapes.Lg)
            .background(BsColors.DangerBg.copy(alpha = 0.4f))
            .border(1.dp, BsColors.DangerBorder, BsShapes.Lg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = BsText.Xs,
        color = BsColors.Danger,
    )
}

@Composable
fun LoginScreen(onRegister: () -> Unit) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val loading = AuthStore.status == AuthStore.Status.Loading
    // `required` + `minLength={8}` from the web form
    val submit = {
        if (identifier.isNotBlank() && password.length >= 8 && !loading) {
            scope.launch { AuthStore.login(identifier.trim(), password) }
        }
    }

    AuthHeader("Sign In Account", "Enter your credentials to access your account.")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BsField(
            label = "Username or Email",
            value = identifier,
            onValueChange = { identifier = it },
            placeholder = "johnfrans or john@example.com",
            keyboardType = KeyboardType.Email,
        )
        BsField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Enter your password",
            password = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        AuthError(AuthStore.error)
        BsButton(onClick = { submit() }, enabled = !loading) {
            Text(if (loading) "Logging in..." else "Log in")
        }
    }
    AuthFooter("Don't have an account?", "Sign Up", onRegister)
}

@Composable
fun RegisterScreen(onLogin: () -> Unit) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val loading = AuthStore.status == AuthStore.Status.Loading
    val valid = listOf(firstName, lastName, email, username).all { it.isNotBlank() } && password.length >= 8
    // Sign up, then sign straight in - same as the web Register page
    val submit = {
        if (valid && !loading) {
            scope.launch {
                if (AuthStore.register(email.trim(), username.trim(), password)) AuthStore.login(email.trim(), password)
            }
        }
    }

    AuthHeader("Sign Up Account", "Enter your personal data to create your account.")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BsField("First Name", firstName, { firstName = it }, Modifier.weight(1f), placeholder = "eg. John")
            BsField("Last Name", lastName, { lastName = it }, Modifier.weight(1f), placeholder = "eg. Francisco")
        }
        BsField("Email", email, { email = it }, placeholder = "eg. johnfrans@gmail.com", keyboardType = KeyboardType.Email)
        BsField("Username", username, { username = it }, placeholder = "eg. johnfrans")
        BsField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Enter your password",
            hint = "Must be at least 8 characters.",
            password = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        AuthError(AuthStore.error)
        BsButton(onClick = { submit() }, enabled = !loading) {
            Text(if (loading) "Signing up..." else "Sign Up")
        }
    }
    AuthFooter("Already have an account?", "Log in", onLogin)
}
