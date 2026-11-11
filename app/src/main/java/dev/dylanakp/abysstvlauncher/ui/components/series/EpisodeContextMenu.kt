package dev.dylanakp.abysstvlauncher.ui.components.series

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlassElevated
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import kotlinx.coroutines.delay

@Composable
fun EpisodeContextMenu(
    episode: SeriesEpisode?,
    seriesItem: MediaItem,
    isWatched: Boolean,
    onDismiss: () -> Unit,
    onToggleSingleWatched: () -> Unit,
    onMarkAllPreviousWatched: () -> Unit
) {
    AnimatedVisibility(
        visible = episode != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (episode == null) return@AnimatedVisibility
        val initialFocusRequester = remember { FocusRequester() }
        var hasSeenFreshDown by remember(episode.id) { mutableStateOf(false) }

        LaunchedEffect(episode.id) {
            delay(60L)
            try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val isSelectKey = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

                    if (isSelectKey) {
                        when (native.action) {
                            KeyEvent.ACTION_DOWN -> {
                                if (native.repeatCount == 0) {
                                    hasSeenFreshDown = true
                                    false
                                } else {
                                    !hasSeenFreshDown
                                }
                            }
                            KeyEvent.ACTION_UP -> {
                                !hasSeenFreshDown
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AbyssSurfaceGlassElevated)
                    .clickable(enabled = false) {}
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = seriesItem.title(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = AbyssTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val epCode = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}"
                    Text(
                        text = if (episode.title.isNotBlank()) "$epCode — ${episode.title}" else epCode,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = AbyssTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val watchedLabel = if (isWatched) stringResource(R.string.episode_action_mark_unwatched) else stringResource(R.string.episode_action_mark_watched)
                    EpisodeContextMenuItem(
                        label = watchedLabel,
                        icon = if (isWatched) AbyssIconType.CLOSE else AbyssIconType.CHECK,
                        focusRequester = initialFocusRequester,
                        onClick = onToggleSingleWatched
                    )

                    EpisodeContextMenuItem(
                        label = stringResource(R.string.episode_action_mark_all_previous_watched),
                        icon = AbyssIconType.CHECK,
                        onClick = onMarkAllPreviousWatched
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    EpisodeContextMenuItem(
                        label = stringResource(R.string.episode_action_cancel),
                        icon = AbyssIconType.CLOSE,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeContextMenuItem(
    label: String,
    icon: AbyssIconType? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val contentColor = if (isFocused) Color.Black else AbyssTextPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White else Color(0x14FFFFFF))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                AbyssIcon(
                    type = icon,
                    color = contentColor,
                    size = 16.dp
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
