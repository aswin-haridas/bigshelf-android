package com.bigshelf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.Neutral
import androidx.compose.runtime.LaunchedEffect

/**
 * Port of components/Input.tsx:
 * `bg-surface border border-border focus:border-border-focus rounded-xl px-3.5 py-2.5 text-xs`
 * (`text-xs` is what the web shows below the `sm` breakpoint, i.e. on phones).
 */
@Composable
fun BsInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textStyle: TextStyle = BsText.Xs,
    singleLine: Boolean = true,
    autoFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) BsColors.BorderFocus else BsColors.Border, label = "inputBorder")
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        singleLine = singleLine,
        textStyle = textStyle.copy(color = BsColors.Foreground),
        cursorBrush = SolidColor(BsColors.Foreground),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .clip(BsShapes.Xl)
                    .background(BsColors.Surface)
                    .border(1.dp, border, BsShapes.Xl)
                    .padding(start = 14.dp, end = if (trailing != null) 12.dp else 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = BsColors.MutedFaint, maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * Port of components/Field.tsx: `text-xs text-muted-soft` label over an [BsInput], an eye
 * toggle for passwords, and an optional `text-2xs text-muted-faint font-light` hint.
 */
@Composable
fun BsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var show by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = BsText.Xs.copy(fontWeight = BsWeight.Normal), color = BsColors.MutedSoft)
        BsInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = keyboardActions,
            visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
            trailing = if (password) {
                {
                    BsIcon(
                        if (show) BsIcons.EyeSlash else BsIcons.Eye,
                        Modifier.bsClickable { show = !show },
                        size = 16.dp,
                        tint = BsColors.MutedFaint,
                        contentDescription = if (show) "Hide password" else "Show password",
                    )
                }
            } else null,
        )
        if (hint != null) {
            Text(
                hint,
                Modifier.padding(top = 4.dp),
                style = BsText.Xs2.copy(fontWeight = BsWeight.Light),
                color = BsColors.MutedFaint,
            )
        }
    }
}

/**
 * Port of components/SearchBar.tsx: `h-8 bg-neutral-800 rounded-md pl-9 text-sm font-medium`
 * with a 16dp magnifying glass; turns neutral-700 while focused.
 */
@Composable
fun BsSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg by animateColorAsState(if (focused) Neutral.N700 else Neutral.N800, label = "searchBg")
    val style = BsText.Sm.copy(fontWeight = BsWeight.Medium)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = style.copy(color = BsColors.Foreground),
        cursorBrush = SolidColor(BsColors.Foreground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(32.dp)
                    .clip(BsShapes.Md)
                    .background(bg)
                    .padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BsIcon(BsIcons.MagnifyingGlass, Modifier.size(16.dp), tint = Neutral.N400)
                Box(Modifier.padding(start = 8.dp).weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = Neutral.N400, maxLines = 1)
                    inner()
                }
            }
        },
    )
}
