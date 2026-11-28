package com.bigshelf.app.ui.auth

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import com.bigshelf.app.ui.components.toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsMotion
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bigshelf.app.R
import com.bigshelf.app.state.AuthStore
import com.bigshelf.app.ui.components.BsButton
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
    // Hero takes whatever the form leaves (it shrinks away under the keyboard) and fades into
    // the page, so the form at the bottom, in thumb reach, sits on the same background.
    Column(
        Modifier
            .fillMaxSize()
            .background(BsColors.Background)
            .imePadding(),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(BsColors.PrimaryDeep),
        ) {
            Image(
                painterResource(R.drawable.bigshelf_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to BsColors.Background),
                ),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 384.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = content,
            )
        }
    }
}

/** `AuthHeader`: `text-xl font-semibold tracking-tight` title, no subtitle. */
@Composable
private fun AuthHeader(title: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            Modifier.semantics { heading() },
            style = BsText.Xl.copy(fontWeight = BsWeight.Semibold, letterSpacing = BsText.TrackingTight),
            color = BsColors.Foreground,
        )
    }
}

/** `AuthFooter`: muted prompt with a semibold foreground link. */
@Composable
private fun AuthFooter(prompt: String, linkText: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            buildAnnotatedString {
                append("$prompt ")
                // Non-breaking: on narrow screens the link wraps as a whole, never "Sign / Up"
                withStyle(SpanStyle(color = BsColors.Foreground, fontWeight = BsWeight.Semibold)) { append(linkText.replace(' ', '\u00A0')) }
            },
            Modifier.bsClickable(scale = 1f, onClick = onClick),
            style = BsText.Xs,
            color = BsColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthError(error: String?) {
    AnimatedVisibility(
        visible = !error.isNullOrEmpty(),
        enter = expandVertically(animationSpec = BsMotion.spec(), expandFrom = Alignment.Top),
        exit = shrinkVertically(animationSpec = BsMotion.spec(), shrinkTowards = Alignment.Top),
    ) {
        Text(
            error.orEmpty(),
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = BsText.Xs,
            color = BsColors.Danger,
        )
    }
}

/** Tall bold pill, the form's one main action. */
@Composable
private fun AuthButton(
    loading: Boolean,
    onClick: () -> Unit,
    error: String? = null,
    content: @Composable RowScope.() -> Unit,
) = Column {
    AuthError(error)
    BsButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        loading = loading,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        textStyle = BsText.Base.copy(fontWeight = BsWeight.Semibold),
        shape = BsShapes.Full,
        content = content,
    )
}

/**
 * Borderless filled pill with the label as its placeholder (still read out once filled);
 * focus lifts the fill. Passwords get the eye in a joined segment of its own.
 */
@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    autofill: ContentType? = null,
    validationError: String? = null,
) {
    var show by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(if (focused) FieldFocused else FieldFill, BsMotion.fast(), label = "authField")
    val outline by animateColorAsState(
        if (validationError != null) BsColors.Danger else Color.Transparent,
        BsMotion.fast(),
        label = "authFieldOutline",
    )
    val start = if (password) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) else BsShapes.Full
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicTextField(
                value = value,
                // An error about the old value goes as soon as it's edited
                onValueChange = { AuthStore.clearError(); onValueChange(it) },
                modifier = Modifier.weight(1f).fillMaxHeight().semantics {
                    contentDescription = listOfNotNull(label, hint).joinToString(". ")
                    if (validationError != null) error(validationError)
                    if (autofill != null) contentType = autofill // password managers fill these
                },
                singleLine = true,
                textStyle = BsText.Base.copy(color = BsColors.Foreground),
                cursorBrush = SolidColor(BsColors.Foreground),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = keyboardActions,
                visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                interactionSource = interaction,
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().clip(start).background(fill)
                            .border(1.dp, outline, start).padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) Text(label, style = BsText.Base, color = BsColors.MutedFaint, maxLines = 1)
                        inner()
                    }
                },
            )
            if (password) {
                Box(
                    Modifier
                        .width(64.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50))
                        .background(FieldFill),
                    contentAlignment = Alignment.Center,
                ) {
                    BsIconButton(
                        icon = if (show) BsIcons.EyeSlash else BsIcons.Eye,
                        onClick = { show = !show },
                        tint = BsColors.MutedSoft,
                        contentDescription = if (show) "Hide password" else "Show password",
                    )
                }
            }
        }
        if (hint != null) {
            Text(
                hint,
                Modifier.padding(start = 24.dp).clearAndSetSemantics {},
                style = BsText.Xs2.copy(fontWeight = BsWeight.Light),
                color = BsColors.MutedFaint,
            )
        }
    }
}

// Filled input per bigshelf/DESIGN.md: surface-hover, lifted ~6% toward white while focused
private val FieldFill = BsColors.SurfaceHover
private val FieldFocused = lerp(BsColors.SurfaceHover, Color.White, 0.06f)

private enum class LoginField(val missingMessage: String) {
    Identifier("Enter your username or email."),
    Password("Enter your password."),
}

@Composable
fun LoginScreen(onRegister: () -> Unit) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var missingField by rememberSaveable { mutableStateOf<LoginField?>(null) }
    val scope = rememberCoroutineScope()
    val loading = AuthStore.status == AuthStore.Status.Loading
    // Length rules are for new passwords; the server judges this one
    val submit = {
        if (!loading) {
            val missing = when {
                identifier.isBlank() -> LoginField.Identifier
                password.isEmpty() -> LoginField.Password
                else -> null
            }
            missingField = missing
            if (missing != null) {
                toast.error(missing.missingMessage)
            } else {
                scope.launch {
                    if (!AuthStore.login(identifier.trim(), password)) {
                        AuthStore.error?.let(toast::error)
                    }
                }
            }
        }
    }

    AuthHeader("Sign in")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthField(
            label = "Username or Email",
            value = identifier,
            onValueChange = {
                identifier = it
                if (missingField == LoginField.Identifier && it.isNotBlank()) missingField = null
            },
            keyboardType = KeyboardType.Email,
            autofill = ContentType.Username,
            validationError = LoginField.Identifier.missingMessage.takeIf { missingField == LoginField.Identifier },
        )
        AuthField(
            label = "Password",
            value = password,
            onValueChange = {
                password = it
                if (missingField == LoginField.Password && it.isNotEmpty()) missingField = null
            },
            password = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
            autofill = ContentType.Password,
            validationError = LoginField.Password.missingMessage.takeIf { missingField == LoginField.Password },
        )
        // No reset flow on the server yet, so say who can help instead of leaving a dead end
        Text(
            "Forgot password?",
            Modifier
                .align(Alignment.End)
                .bsClickable(scale = 1f) { toast.info("Ask your BigShelf admin to reset your password.") }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = BsText.Xs,
            color = BsColors.MutedSoft,
        )
        AuthButton(loading, submit) {
            Text(if (loading) "Signing in..." else "Sign in")
        }
    }
    AuthFooter("Don't have an account?", "Sign up", onRegister)
}

@Composable
fun RegisterScreen(onLogin: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val loading = AuthStore.status == AuthStore.Status.Loading
    // Sign up, then sign straight in - same as the web Register page
    val submit = {
        if (loading) Unit
        else if (listOf(email, username).any { it.isBlank() }) AuthStore.error = "Fill in all fields."
        else if (password.length < 8) AuthStore.error = "Password must be at least 8 characters."
        else scope.launch {
            if (AuthStore.register(email.trim(), username.trim(), password)) AuthStore.login(email.trim(), password)
        }
    }

    AuthHeader("Sign up")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthField("Email", email, { email = it }, keyboardType = KeyboardType.Email, autofill = ContentType.EmailAddress)
        AuthField("Username", username, { username = it }, autofill = ContentType.NewUsername)
        AuthField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            hint = "Must be at least 8 characters.",
            password = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
            autofill = ContentType.NewPassword,
        )
        AuthButton(loading, { submit() }, AuthStore.error) {
            Text(if (loading) "Signing up..." else "Sign up")
        }
    }
    AuthFooter("Already have an account?", "Sign in", onLogin)
}
