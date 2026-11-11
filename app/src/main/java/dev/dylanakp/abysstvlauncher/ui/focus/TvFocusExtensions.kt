package dev.dylanakp.abysstvlauncher.ui.focus

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent

fun Modifier.dpadNavigation(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null
): Modifier = this
    .then(
        if (up != null || down != null || left != null || right != null) {
            Modifier.focusProperties {
                up?.let { this.up = it }
                down?.let { this.down = it }
                left?.let { this.left = it }
                right?.let { this.right = it }
            }
        } else Modifier
    )
    .then(
        if (onUp != null || onDown != null || onLeft != null || onRight != null) {
            Modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> onUp?.invoke() ?: false
                        KeyEvent.KEYCODE_DPAD_DOWN -> onDown?.invoke() ?: false
                        KeyEvent.KEYCODE_DPAD_LEFT -> onLeft?.invoke() ?: false
                        KeyEvent.KEYCODE_DPAD_RIGHT -> onRight?.invoke() ?: false
                        else -> false
                    }
                } else false
            }
        } else Modifier
    )
