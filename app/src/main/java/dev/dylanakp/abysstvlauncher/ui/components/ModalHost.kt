package dev.dylanakp.abysstvlauncher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import dev.dylanakp.abysstvlauncher.ui.focus.safeRequestFocus

/**
 * Single modal scaffold: dismissal, focus trap, one-shot initial focus and trigger restore.
 * The trap owns onExit exclusively; callers must not add their own cancel gates.
 */
@Composable
fun AbyssModalHost(
    onDismiss: () -> Unit,
    initialFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    restoreFocusTo: FocusRequester? = null,
    scrimColor: Color = Color.Black.copy(alpha = 0.7f),
    dismissOnBack: Boolean = true,
    dismissOnScrimClick: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    BackHandler(enabled = dismissOnBack) { onDismiss() }

    val view = LocalView.current
    LaunchedEffect(Unit) {
        if (!view.hasFocus()) view.requestFocus()
        for (attempt in 0 until 3) {
            withFrameNanos { }
            if (initialFocusRequester.safeRequestFocus()) break
        }
    }

    DisposableEffect(restoreFocusTo) {
        onDispose { restoreFocusTo?.safeRequestFocus() }
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
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        contentAlignment = contentAlignment,
        content = content
    )
}
