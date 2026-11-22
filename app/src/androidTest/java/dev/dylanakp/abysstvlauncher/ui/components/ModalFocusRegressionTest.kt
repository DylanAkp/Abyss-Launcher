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
import androidx.test.platform.app.InstrumentationRegistry
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.AppUpdate
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.MovieDetails
import dev.dylanakp.abysstvlauncher.model.MediaVersion
import dev.dylanakp.abysstvlauncher.ui.components.settings.SettingsTrackPicker
import dev.dylanakp.abysstvlauncher.ui.components.settings.TrackPickerKind
import dev.dylanakp.abysstvlauncher.ui.focus.pressOk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModalFocusRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun updateModal_focusesPrimaryAction_okInvokesOnce() {
        val updates = mutableStateOf(0)
        val update = AppUpdate("99.0.0", 990000, "notes", "https://example.invalid/app.apk", 1L)
        val label = string(R.string.update_action_now)

        composeTestRule.setContent {
            AppUpdateModal(
                update = update,
                isDownloading = false,
                downloadProgress = 0f,
                errorMessage = null,
                onDismiss = {},
                onUpdate = { updates.value++ }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(label).assertIsFocused()
        composeTestRule.onNodeWithText(label).pressOk()
        assertEquals(1, updates.value)
    }

    @Test
    fun trackPicker_focusesSelectedOption_restoresToOriginRow() {
        val trigger = FocusRequester()
        val visible = mutableStateOf(true)

        composeTestRule.setContent {
            Column {
                Text("Origin", modifier = Modifier.focusRequester(trigger).clickable { })
                if (visible.value) {
                    SettingsTrackPicker(
                        kind = TrackPickerKind.AUDIO,
                        options = listOf("eng", "fra"),
                        selected = "eng",
                        onSelect = {},
                        onDismiss = { visible.value = false },
                        restoreFocusTo = trigger
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("English").assertIsFocused()
        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Origin").assertIsFocused()
    }

    @Test
    fun movieDetail_initialFocus_landsOnPlay() {
        val item = MediaItem(id = "m1", title = "Movie", category = MediaCategory.MOVIE)
        val details = MovieDetails(
            movieItem = item,
            versions = listOf(MediaVersion(id = "v1", name = "Default")),
            selectedVersionId = "v1"
        )

        composeTestRule.setContent {
            MovieDetailScreen(
                movieItem = item,
                movieDetails = details,
                isLoading = false,
                playbackHistory = emptyMap(),
                onBack = {},
                onPlayMovie = { _, _ -> },
                onPlayTrailer = {},
                onSelectVersion = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.cta_play_stream)).assertIsFocused()
    }
}
