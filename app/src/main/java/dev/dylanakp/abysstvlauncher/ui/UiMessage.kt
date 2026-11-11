package dev.dylanakp.abysstvlauncher.ui

import androidx.annotation.StringRes

sealed interface UiMessage {
    data class Resource(
        @param:StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiMessage

    data class Text(val text: String) : UiMessage
}
