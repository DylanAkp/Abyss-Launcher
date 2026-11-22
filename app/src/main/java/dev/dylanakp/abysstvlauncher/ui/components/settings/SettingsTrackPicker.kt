package dev.dylanakp.abysstvlauncher.ui.components.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.TrackPreference
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import dev.dylanakp.abysstvlauncher.ui.components.AbyssModalHost

enum class TrackPickerKind { AUDIO, SUBTITLE }

fun TrackPreference.audioChoice(): String = audioLanguage.orEmpty()

fun TrackPreference.subtitleChoice(): String = when {
    subtitleOff == true -> "off"
    subtitleLanguage == null -> ""
    subtitleForced == true -> "$subtitleLanguage!"
    else -> subtitleLanguage.orEmpty()
}

fun TrackPreference.withAudioChoice(choice: String) =
    copy(audioLanguage = choice.ifBlank { null })

fun TrackPreference.withSubtitleChoice(choice: String) = when (choice) {
    "off" -> copy(subtitleLanguage = null, subtitleOff = true)
    else -> copy(
        subtitleLanguage = choice.removeSuffix("!").ifBlank { null },
        subtitleOff = false,
        subtitleForced = choice.endsWith("!")
    )
}

@Composable
fun SettingsPickerRow(
    title: String,
    subtitle: String,
    valueLabel: String,
    icon: AbyssIconType? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1.0f,
        animationSpec = tween(100),
        label = "pickerRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color(0xFF181A1F))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isFocused) Color(0xFFCBD5E1) else Color(0xFF1E2025)),
                contentAlignment = Alignment.Center
            ) {
                AbyssIcon(
                    type = icon,
                    color = if (isFocused) Color(0xFF131314) else Color(0xFF8AB4F8),
                    size = 18.dp
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = if (isFocused) Color(0xFF131314) else Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = if (isFocused) Color(0xFF444746) else Color(0xFF8E918F)
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFocused) Color(0xFF1A73E8) else Color(0xFF252932))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                color = if (isFocused) Color.White else Color(0xFF8AB4F8)
            )
        }
    }
}

@Composable
fun SettingsTrackPicker(
    kind: TrackPickerKind,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    restoreFocusTo: FocusRequester? = null
) {
    val firstItemRequester = remember { FocusRequester() }
    val closeRequester = remember { FocusRequester() }
    val title = stringResource(
        if (kind == TrackPickerKind.AUDIO) R.string.pref_audio_language_title else R.string.pref_subtitle_language_title
    )

    AbyssModalHost(
        onDismiss = onDismiss,
        initialFocusRequester = firstItemRequester,
        scrimColor = Color(0xD905060A),
        restoreFocusTo = restoreFocusTo
    ) {
        Box(
            modifier = Modifier
                .width(540.dp)
                .fillMaxHeight()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0141824))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E2025))
                            .focusRequester(closeRequester)
                            .onFocusChanged {}
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        AbyssIcon(type = AbyssIconType.CLOSE, color = Color.White, size = 16.dp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(options) { _, option ->
                        GoogleTvRadioRow(
                            title = trackLanguageLabel(option),
                            subtitle = "",
                            isSelected = option == selected,
                            focusRequester = if (option == selected) firstItemRequester else null,
                            onClick = {
                                onSelect(option)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}
