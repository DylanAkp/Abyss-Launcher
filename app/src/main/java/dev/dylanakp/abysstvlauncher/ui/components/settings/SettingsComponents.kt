package dev.dylanakp.abysstvlauncher.ui.components.settings

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.TRACK_LANGUAGE_CODES
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import dev.dylanakp.abysstvlauncher.ui.focus.safeRequestFocus
import java.util.Locale

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            ),
            color = Color(0xFF8E918F),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2025))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun SettingsGlassInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isEditing) {
        if (isEditing && !textFieldFocusRequester.safeRequestFocus()) isEditing = false
    }

    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = if (isFocused || isEditing) Color(0xFF8AB4F8) else Color(0xFF8E918F)
            )
            if (isFocused && !isEditing) {
                Text(
                    text = stringResource(R.string.input_hint_ok_edit),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = Color(0xFF1A73E8)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused || isEditing) Color(0xFF313A47) else Color(0xFF16181D))
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(enabled = !isEditing) { isEditing = true }
                .onPreviewKeyEvent { event ->
                    if (isEditing || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_DPAD_CENTER -> {
                            isEditing = true
                            true
                        }
                        else -> false
                    }
                }
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = isEditing,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFieldFocusRequester)
                    .onFocusChanged { state ->
                        isEditing = state.isFocused
                        if (state.isFocused) isFocused = false
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(FocusDirection.Down)
                                KeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(FocusDirection.Up)
                                else -> false
                            }
                        } else false
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                color = if (isFocused || isEditing) Color(0xFF9AA0A6) else Color(0xFF5E6368),
                                fontSize = 13.sp
                            )
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
fun SettingsPrimaryActionButton(
    label: String,
    isLoading: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var restoreFocusAfterLoading by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(100),
        label = "primaryBtnScale"
    )

    LaunchedEffect(isLoading) {
        if (isLoading) {
            restoreFocusAfterLoading = isFocused
        } else {
            if (restoreFocusAfterLoading) focusRequester?.safeRequestFocus()
            restoreFocusAfterLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color(0xFF1A73E8))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { if (!isLoading) onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = if (isFocused) Color(0xFF1A73E8) else Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = if (isFocused) Color(0xFF1A73E8) else Color.White
            )
        }
    }
}

@Composable
fun GoogleTvRadioRow(
    title: String,
    subtitle: String,
    icon: AbyssIconType? = null,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1.0f,
        animationSpec = tween(100),
        label = "radioRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFFE8EAED)
                    isSelected -> Color(0xFF252932)
                    else -> Color(0xFF181A1F)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isFocused -> Color(0xFFCBD5E1)
                            isSelected -> Color(0xFF1E2430)
                            else -> Color(0xFF1E2025)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AbyssIcon(
                    type = icon,
                    color = when {
                        isFocused -> Color(0xFF131314)
                        isSelected -> Color(0xFF8AB4F8)
                        else -> Color(0xFF8E918F)
                    },
                    size = 18.dp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = if (isFocused) Color(0xFF131314) else Color.White
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = if (isFocused) Color(0xFF444746) else Color(0xFF8E918F)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    when {
                        isFocused && isSelected -> Color(0xFF1A73E8)
                        isFocused -> Color(0xFF8E918F)
                        isSelected -> Color(0xFF8AB4F8)
                        else -> Color(0xFF5E6368)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) Color(0xFF1A73E8) else Color(0xFF8AB4F8))
                )
            }
        }
    }
}

@Composable
fun GoogleTvSwitchRow(
    title: String,
    subtitle: String,
    icon: AbyssIconType? = null,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1.0f,
        animationSpec = tween(100),
        label = "switchRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFFE8EAED)
                    isChecked -> Color(0xFF252932)
                    else -> Color(0xFF181A1F)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isFocused -> Color(0xFFCBD5E1)
                            isChecked -> Color(0xFF1E2430)
                            else -> Color(0xFF1E2025)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AbyssIcon(
                    type = icon,
                    color = when {
                        isFocused -> Color(0xFF131314)
                        isChecked -> Color(0xFF8AB4F8)
                        else -> Color(0xFF8E918F)
                    },
                    size = 18.dp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = if (isFocused) Color(0xFF131314) else Color.White
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = if (isFocused) Color(0xFF444746) else Color(0xFF8E918F)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        isFocused && isChecked -> Color(0xFF1A73E8)
                        isChecked -> Color(0xFF8AB4F8)
                        else -> Color(0xFF3C4043)
                    }
                )
                .padding(2.dp),
            contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isFocused && !isChecked) Color(0xFF131314) else Color.White)
            )
        }
    }
}

val TRACK_AUDIO_OPTIONS = listOf("") + TRACK_LANGUAGE_CODES
val TRACK_SUBTITLE_OPTIONS = listOf("", "off") + TRACK_LANGUAGE_CODES.flatMap { listOf(it, "$it!") }

@Composable
fun trackLanguageLabel(code: String): String = when (code) {
    "" -> stringResource(R.string.track_lang_auto)
    "off" -> stringResource(R.string.track_lang_off)
    else -> {
        val base = code.removeSuffix("!")
        val name = Locale.forLanguageTag(base).getDisplayLanguage(Locale.getDefault())
            .ifBlank { base }
            .replaceFirstChar { it.uppercase() }
        if (code.endsWith("!")) stringResource(R.string.track_lang_forced_format, name) else name
    }
}

