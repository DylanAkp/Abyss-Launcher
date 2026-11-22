package dev.dylanakp.abysstvlauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dylanakp.abysstvlauncher.ui.focus.tvClickable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvFocusableWidgetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tvClickable_busyRow_keepsFocusAndSuppressesClick() {
        val clicks = mutableStateOf(0)
        val requester = FocusRequester()

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .focusRequester(requester)
                    .tvClickable(isBusy = true) { clicks.value++ }
            ) {
                Text("Busy action")
            }
        }

        composeTestRule.runOnIdle { requester.requestFocus() }
        composeTestRule.onNodeWithText("Busy action").assertIsFocused()
        composeTestRule.onNodeWithText("Busy action").performClick()
        assertEquals(0, clicks.value)
    }

    @Test
    fun settingsNavTab_switchesOnlyOnClick_notOnFocus() {
        val clicks = mutableStateOf(0)
        val requester = FocusRequester()

        composeTestRule.setContent {
            SettingsNavTab(
                label = "Seer",
                subLabel = "Requests",
                icon = AbyssIconType.GLOBE,
                isSelected = false,
                focusRequester = requester,
                onClick = { clicks.value++ }
            )
        }

        composeTestRule.runOnIdle { requester.requestFocus() }
        assertEquals(0, clicks.value)

        composeTestRule.onNodeWithText("Seer").performClick()
        assertEquals(1, clicks.value)
    }
}
