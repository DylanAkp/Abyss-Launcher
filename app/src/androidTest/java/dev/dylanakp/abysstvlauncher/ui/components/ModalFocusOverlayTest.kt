package dev.dylanakp.abysstvlauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dylanakp.abysstvlauncher.ui.focus.pressDpadDown
import org.junit.Rule
import org.junit.Test

class ModalFocusOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun modalHost_requestsInitialFocus_restoresTriggerAndTrapsFocus() {
        val visible = mutableStateOf(false)
        val trigger = FocusRequester()

        composeTestRule.setContent {
            Column {
                Text(
                    text = "Trigger",
                    modifier = Modifier
                        .focusRequester(trigger)
                        .clickable { visible.value = true }
                )
                if (visible.value) {
                    val first = FocusRequester()
                    val second = FocusRequester()
                    AbyssModalHost(
                        onDismiss = { visible.value = false },
                        initialFocusRequester = first,
                        restoreFocusTo = trigger
                    ) {
                        Column {
                            Text(text = "First", modifier = Modifier.focusRequester(first).clickable {})
                            Text(text = "Second", modifier = Modifier.focusRequester(second).clickable {})
                            Text(text = "Close", modifier = Modifier.clickable { visible.value = false })
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Trigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("First").assertIsFocused()

        composeTestRule.onNodeWithText("First").pressDpadDown()
        composeTestRule.onNodeWithText("Second").assertIsFocused()

        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Trigger").assertIsFocused()
    }
}
