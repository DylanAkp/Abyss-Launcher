package dev.dylanakp.abysstvlauncher.remote

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RemoteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun performGlobalKey(key: String): Boolean {
        val name = key.uppercase()
        val globalAction = when (name) {
            "BACK" -> GLOBAL_ACTION_BACK
            "HOME" -> GLOBAL_ACTION_HOME
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS", "SHADE" -> GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            "POWER" -> GLOBAL_ACTION_POWER_DIALOG
            "LOCK_SCREEN" -> GLOBAL_ACTION_LOCK_SCREEN.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.P }
            "SPLIT_SCREEN" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.N }
            else -> null
        }
        if (globalAction != null) return performGlobalAction(globalAction)

        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: rootInActiveWindow ?: return false
        return when (name) {
            "DPAD_CENTER", "OK", "ENTER" -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            "DPAD_UP", "PAGE_UP" -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "DPAD_DOWN", "PAGE_DOWN" -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "DEL", "DELETE" -> node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
            else -> false
        }
    }

    companion object {
        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set
    }
}
