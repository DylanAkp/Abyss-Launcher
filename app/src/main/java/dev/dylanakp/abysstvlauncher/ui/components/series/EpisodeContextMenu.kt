package dev.dylanakp.abysstvlauncher.ui.components.series

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import dev.dylanakp.abysstvlauncher.ui.components.AbyssModalOverlay
import dev.dylanakp.abysstvlauncher.ui.components.RequestModalInitialFocus
import kotlinx.coroutines.delay

@Composable
fun EpisodeContextMenu(
    episode: SeriesEpisode?,
    seriesItem: MediaItem,
    isWatched: Boolean,
    onDismiss: () -> Unit,
    onSelectVersion: (() -> Unit)? = null,
    onToggleSingleWatched: () -> Unit,
    onMarkAllPreviousWatched: () -> Unit,
    onMarkAllFollowingUnwatched: () -> Unit
) {
    if (episode == null) return

    val initialFocusRequester = remember { FocusRequester() }
    var hasSeenFreshDown by remember(episode.id) { mutableStateOf(false) }

    RequestModalInitialFocus(
        focusRequester = initialFocusRequester,
        episode.id
    )

    AbyssModalOverlay(
        scrimColor = Color(0xA6000000),
        onDismiss = onDismiss,
        modifier = Modifier.onPreviewKeyEvent { event ->
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
                    KeyEvent.ACTION_UP -> !hasSeenFreshDown
                    else -> false
                }
            } else false
        }
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF191C24))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                val epCode = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x1FFFFFFF))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${seriesItem.title()} • $epCode",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            fontSize = 11.sp
                        ),
                        color = AbyssTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = episode.title.ifBlank { epCode },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = AbyssTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (onSelectVersion != null && episode.versions.size > 1) {
                    val activeVer = episode.versions.firstOrNull { it.id == episode.selectedVersionId }
                        ?: episode.versions.firstOrNull()
                    EpisodeContextMenuItem(
                        label = "Source (${episode.versions.size})${if (activeVer != null) " • ${activeVer.name}" else ""}",
                        icon = AbyssIconType.CHEVRON_DOWN,
                        onClick = onSelectVersion
                    )
                }

                val watchedLabel = if (isWatched) {
                    stringResource(R.string.episode_action_mark_unwatched)
                } else {
                    stringResource(R.string.episode_action_mark_watched)
                }
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

                EpisodeContextMenuItem(
                    label = stringResource(R.string.episode_action_mark_all_following_unwatched),
                    icon = AbyssIconType.CLOSE,
                    onClick = onMarkAllFollowingUnwatched
                )

                Spacer(modifier = Modifier.height(2.dp))

                EpisodeContextMenuItem(
                    label = stringResource(R.string.episode_action_cancel),
                    icon = AbyssIconType.CLOSE,
                    isTonal = false,
                    onClick = onDismiss
                )
            }
        }
    }

@Composable
private fun EpisodeContextMenuItem(
    label: String,
    icon: AbyssIconType? = null,
    isTonal: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(110),
        label = "epMenuScale"
    )
    val bgColor = when {
        isFocused -> Color.White
        isTonal -> Color(0xFF252A36)
        else -> Color(0x12FFFFFF)
    }
    val contentColor = if (isFocused) Color(0xFF14171F) else AbyssTextPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
