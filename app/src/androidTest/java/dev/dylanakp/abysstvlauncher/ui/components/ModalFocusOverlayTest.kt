package dev.dylanakp.abysstvlauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ModalFocusOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun abyssModalOverlay_requestsInitialFocusOnTarget() {
        composeTestRule.setContent {
            val requester = remember { FocusRequester() }
            AbyssModalOverlay(
                scrimColor = Color.Black.copy(alpha = 0.7f),
                onDismiss = {}
            ) {
                RequestModalInitialFocus(requester)
                Box(
                    modifier = Modifier
                        .focusRequester(requester)
                        .clickable {}
                ) {
                    Text("Action")
                }
            }
        }

        composeTestRule.onNodeWithText("Action").assertIsFocused()
    }
}
