package dev.dylanakp.abysstvlauncher.ui.focus

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.pressDpadUp() = performKeyInput { pressKey(Key.DirectionUp) }

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.pressDpadDown() = performKeyInput { pressKey(Key.DirectionDown) }

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.pressDpadLeft() = performKeyInput { pressKey(Key.DirectionLeft) }

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.pressDpadRight() = performKeyInput { pressKey(Key.DirectionRight) }

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.pressOk() = performKeyInput { pressKey(Key.Enter) }
