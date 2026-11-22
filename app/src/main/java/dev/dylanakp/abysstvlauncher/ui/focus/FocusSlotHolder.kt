package dev.dylanakp.abysstvlauncher.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/** Owns the app's logical focus slots so screens stop threading FocusRequesters through widgets. */
class FocusSlotHolder {
    private val requesters = mutableMapOf<String, FocusRequester>()

    fun requester(slot: String): FocusRequester = requesters.getOrPut(slot) { FocusRequester() }

    fun request(slot: String): Boolean = requester(slot).safeRequestFocus()

    companion object {
        const val TOP_BAR = "top_bar"
        const val FIRST_CONTENT = "first_content"
        const val HERO_PLAY = "hero_play"
        const val SEARCH_INPUT = "search_input"
        const val SETTINGS_NAV = "settings_nav"
        const val TRACK_AUDIO_ROW = "track_audio_row"
        const val TRACK_SUBTITLE_ROW = "track_subtitle_row"
        const val PLAYER_OSD = "player_osd"
    }
}

@Composable
fun rememberFocusSlotHolder(): FocusSlotHolder = remember { FocusSlotHolder() }

val LocalFocusSlots = staticCompositionLocalOf<FocusSlotHolder> {
    error("LocalFocusSlots not provided")
}
