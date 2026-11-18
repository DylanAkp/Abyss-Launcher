package dev.dylanakp.abysstvlauncher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

@Composable
fun AbyssModalOverlay(
    scrimColor: Color,
    onDismiss: () -> Unit,
    dismissOnBack: Boolean = true,
    dismissOnScrimClick: Boolean = true,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    BackHandler(enabled = true) {
        if (dismissOnBack) onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor)
            .pointerInput(dismissOnScrimClick) {
                detectTapGestures {
                    if (dismissOnScrimClick) onDismiss()
                }
            }
            .focusProperties {
                onExit = { cancelFocusChange() }
            }
            .focusGroup(),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun RequestModalInitialFocus(
    focusRequester: FocusRequester,
    vararg keys: Any?,
    enabled: Boolean = true,
    fallbackRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val view = LocalView.current

    LaunchedEffect(enabled, *keys) {
        if (!enabled) return@LaunchedEffect
        if (!view.hasFocus()) {
            view.requestFocus()
        }
        repeat(6) { attempt ->
            withFrameNanos { }
            val requested = runCatching {
                focusRequester.requestFocus()
            }.getOrDefault(false) || (fallbackRequester != null && runCatching {
                fallbackRequester.requestFocus()
            }.getOrDefault(false))
            if (requested) {
                onFocused?.invoke()
                return@LaunchedEffect
            }
            delay(32L * (attempt + 1))
        }
    }
}
