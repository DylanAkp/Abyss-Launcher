package dev.dylanakp.abysstvlauncher.ui.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.player.PlayerPositionState
import dev.dylanakp.abysstvlauncher.player.TrackOption
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dylanakp.abysstvlauncher.theme.AbyssColorAccent
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import dev.dylanakp.abysstvlauncher.ui.focus.tvClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlayerOsdOverlay(
    session: PlayerSession,
    videoResolutionBadge: String,
    currentWallClockMs: Long,
    seekPreviewTargetMs: Long,
    positionStateFlow: StateFlow<PlayerPositionState>,
    durationMs: Long,
    isPlaying: Boolean,
    showResumePrompt: Boolean,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    resumeButtonFocusRequester: FocusRequester,
    playPauseFocusRequester: FocusRequester,
    onResumeConfirmed: () -> Unit,
    onRestartFromBeginning: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNextEpisode: (SeriesEpisode) -> Unit,
    onOpenAudioTracks: () -> Unit,
    onOpenSubtitleTracks: () -> Unit,
    onExit: () -> Unit
) {
    val positionState by positionStateFlow.collectAsStateWithLifecycle()
    val currentPositionMs = positionState.currentPositionMs
    val bufferedPositionMs = positionState.bufferedPositionMs

    Box(modifier = Modifier.fillMaxSize()) {
        // Top Vignette & Metadata Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AbyssVoid.copy(alpha = 0.90f),
                            AbyssVoid.copy(alpha = 0.50f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 36.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (videoResolutionBadge.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x33FFFFFF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = videoResolutionBadge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    color = AbyssTextPrimary
                                )
                            }
                        }
                        if (session.subtitle.isNotBlank()) {
                            Text(
                                text = session.subtitle,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = AbyssTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = AbyssTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Top-right current wall-clock time & Ends At
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66101420))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val nowStr = formatOsdTime(currentWallClockMs)
                    Text(
                        text = nowStr,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = AbyssTextPrimary
                    )

                    val displayPos = if (seekPreviewTargetMs >= 0L) seekPreviewTargetMs else currentPositionMs
                    val remainingMs = (durationMs - displayPos).coerceAtLeast(0L)
                    if (durationMs > 0L && remainingMs > 0L) {
                        val endsAtStr = formatOsdTime(currentWallClockMs + remainingMs)
                        Text(
                            text = "•",
                            color = AbyssTextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(R.string.ends_at_fmt, endsAtStr),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = AbyssColorAccent
                        )
                    }
                }
            }
        }

        // Bottom Vignette, Resume Banner, Seekbar & Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            AbyssVoid.copy(alpha = 0.70f),
                            AbyssVoid.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(horizontal = 36.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showResumePrompt) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD181D2A))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.series_resume_at, formatDurationMs(session.savedResumePositionMs)),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = AbyssTextPrimary
                    )
                    OsdPillButton(
                        label = stringResource(R.string.cta_resume),
                        icon = AbyssIconType.PLAY,
                        isPrimary = true,
                        focusRequester = resumeButtonFocusRequester,
                        onClick = onResumeConfirmed
                    )
                    OsdPillButton(
                        label = stringResource(R.string.restart_from_beginning),
                        icon = AbyssIconType.RESTART,
                        onClick = onRestartFromBeginning
                    )
                }
            }

            if (durationMs > 0L) {
                val effectivePos = if (seekPreviewTargetMs >= 0L) seekPreviewTargetMs else currentPositionMs
                val progressFraction = (effectivePos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val bufferedFraction = (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val remainingMs = (durationMs - effectivePos).coerceAtLeast(0L)
                val endsAtStr = formatOsdTime(currentWallClockMs + remainingMs)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDurationMs(effectivePos)}  /  ${formatDurationMs(durationMs)}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = AbyssTextPrimary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "-${formatDurationMs(remainingMs)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = AbyssTextSecondary
                            )
                            Text(
                                text = stringResource(R.string.ends_at_fmt, endsAtStr),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    var seekBarFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (seekBarFocused) 10.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x33FFFFFF))
                            .tvClickable(onFocused = { seekBarFocused = it }) { }
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            onSeekDelta(-10_000L)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            onSeekDelta(10_000L)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bufferedFraction)
                                .background(Color(0x55FFFFFF))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(if (seekBarFocused) AbyssColorAccent else Color.White)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OsdPillButton(
                    label = if (isPlaying) {
                        stringResource(R.string.player_pause)
                    } else {
                        stringResource(R.string.cta_play_stream)
                    },
                    icon = if (isPlaying) AbyssIconType.PAUSE else AbyssIconType.PLAY,
                    isPrimary = true,
                    focusRequester = playPauseFocusRequester,
                    onClick = onTogglePlayPause
                )

                if (session.nextEpisode != null) {
                    val nextEp = session.nextEpisode
                    val isNearEnd = durationMs > 0L && (durationMs - currentPositionMs) < 180_000L
                    val epCode = "S${nextEp.seasonNumber.toString().padStart(2, '0')}E${nextEp.episodeNumber.toString().padStart(2, '0')}"
                    OsdPillButton(
                        label = stringResource(R.string.player_next, epCode),
                        icon = AbyssIconType.SKIP_NEXT,
                        isPrimary = isNearEnd,
                        onClick = { onPlayNextEpisode(nextEp) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                val activeAudioLabel = audioTracks.firstOrNull { it.isSelected }?.label
                val audioTitle = stringResource(R.string.audio_tracks_title)
                OsdPillButton(
                    label = activeAudioLabel?.ifBlank { audioTitle } ?: audioTitle,
                    icon = AbyssIconType.AUDIO_SPEAKER,
                    onClick = onOpenAudioTracks
                )

                val activeSub = subtitleTracks.firstOrNull { it.isSelected && !it.isOffOption }
                val subTitle = stringResource(R.string.subtitles_title)
                val subLabel = activeSub?.label?.ifBlank { subTitle } ?: subTitle
                OsdPillButton(
                    label = subLabel,
                    icon = AbyssIconType.SUBTITLES_CC,
                    onClick = onOpenSubtitleTracks
                )

                OsdPillButton(
                    label = stringResource(R.string.btn_exit),
                    icon = AbyssIconType.CLOSE,
                    onClick = onExit
                )
            }
        }
    }
}

private val OSD_TIME_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm")

private fun formatOsdTime(epochMs: Long): String =
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(OSD_TIME_FORMATTER)
