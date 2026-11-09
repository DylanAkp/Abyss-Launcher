package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.ui.player.formatDurationMs

@Composable
fun MediaContextMenu(
    media: MediaItem?,
    isInWatchlist: Boolean,
    playbackHistory: Map<String, PlaybackHistoryEntry>,
    onDismiss: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onRemoveFromContinue: (MediaItem) -> Unit
) {
    AnimatedVisibility(
        visible = media != null,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(120))
    ) {
        if (media == null) return@AnimatedVisibility
        val initialFocusRequester = remember { FocusRequester() }
        var hasSeenFreshDown by remember(media.id) { mutableStateOf(false) }

        val historyEntry = playbackHistory[media.id]
        val isWatched = (historyEntry?.progressPercent ?: 0f) >= 0.95f
        val hasContinueProgress = historyEntry != null && historyEntry.positionMs > 0L

        LaunchedEffect(media.id) {
            kotlinx.coroutines.delay(60L)
            try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB305070C))
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
            // Modal Sheet (Zero Outline)
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF0F1420))
                    .clickable(enabled = false) {}
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF161B28))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Poster thumbnail
                        if (media.posterUrl.isNotBlank()) {
                            val context = LocalContext.current
                            val thumbRequest = remember(media.posterUrl) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(media.posterUrl)
                                    .size(96, 144)
                                    .precision(coil.size.Precision.INEXACT)
                                    .allowHardware(true)
                                    .build()
                            }
                            AsyncImage(
                                model = thumbRequest,
                                contentDescription = media.title(),
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x22FFFFFF)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = media.title(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = AbyssTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val catLabel = if (media.category == MediaCategory.SERIES) stringResource(R.string.badge_series) else stringResource(R.string.badge_movie)
                            val episodeTag = historyEntry?.let {
                                if (it.seasonNumber > 0 && it.episodeNumber > 0) {
                                    "S${it.seasonNumber.toString().padStart(2, '0')}E${it.episodeNumber.toString().padStart(2, '0')}"
                                } else null
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x28FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = catLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = AbyssTextPrimary
                                    )
                                }

                                if (media.year.isNotBlank()) {
                                    Text(
                                        text = media.year,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = AbyssTextSecondary
                                    )
                                }

                                if (episodeTag != null) {
                                    Text(
                                        text = "•  $episodeTag",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = AbyssGold
                                    )
                                }
                            }
                        }
                    }

                    // Grouped actions container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF161B28))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Play / Resume Action
                        val playLabel = when {
                            historyEntry != null && historyEntry.positionMs > 15_000L && !isWatched -> {
                                stringResource(R.string.series_resume_at, formatDurationMs(historyEntry.positionMs))
                            }
                            media.category == MediaCategory.SERIES -> {
                                stringResource(R.string.series_episodes_and_seasons)
                            }
                            else -> {
                                stringResource(R.string.cta_play_stream)
                            }
                        }
                        MediaContextMenuItem(
                            label = playLabel,
                            icon = AbyssIconType.PLAY,
                            focusRequester = initialFocusRequester,
                            isPrimary = true,
                            onClick = {
                                onDismiss()
                                onPlay(media)
                            }
                        )

                        // 2. Mark as Watched / Unwatched
                        val watchedLabel = when {
                            isWatched -> stringResource(R.string.episode_action_mark_unwatched)
                            media.category == MediaCategory.SERIES -> stringResource(R.string.mark_series_watched)
                            else -> stringResource(R.string.episode_action_mark_watched)
                        }
                        MediaContextMenuItem(
                            label = watchedLabel,
                            icon = if (isWatched) AbyssIconType.CLOSE else AbyssIconType.CHECK,
                            onClick = {
                                onDismiss()
                                onToggleWatched(media)
                            }
                        )

                        // 3. Remove from Continue Watching (if present)
                        if (hasContinueProgress || isWatched) {
                            MediaContextMenuItem(
                                label = stringResource(R.string.remove_from_continue),
                                icon = AbyssIconType.CLOSE,
                                onClick = {
                                    onDismiss()
                                    onRemoveFromContinue(media)
                                }
                            )
                        }

                        // 4. Watchlist Toggle
                        MediaContextMenuItem(
                            label = stringResource(if (isInWatchlist) R.string.remove_from_watchlist else R.string.cta_pin_to_watchlist),
                            icon = if (isInWatchlist) AbyssIconType.STAR_FILLED else AbyssIconType.STAR_OUTLINE,
                            onClick = {
                                onDismiss()
                                onToggleWatchlist(media)
                            }
                        )

                        // 5. Open Details / Episodes (for Series)
                        if (media.category == MediaCategory.SERIES) {
                            MediaContextMenuItem(
                                label = stringResource(R.string.series_episodes_and_seasons),
                                icon = AbyssIconType.TV_CHANNELS,
                                onClick = {
                                    onDismiss()
                                    onOpenDetails(media)
                                }
                            )
                        }
                    }

                    // 6. Close button
                    MediaContextMenuItem(
                        label = stringResource(R.string.app_action_close),
                        icon = AbyssIconType.CLOSE,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaContextMenuItem(
    label: String,
    icon: AbyssIconType? = null,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(120),
        label = "mediaItemScale"
    )

    val contentColor = if (isFocused) Color(0xFF1E222B) else Color(0xFFF1F5F9)
    val badgeBg = if (isFocused) Color(0x1F000000) else Color(0x1FFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isPrimary -> Color(0x223B82F6)
                    else -> Color(0x0EFFFFFF)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    AbyssIcon(
                        type = icon,
                        color = contentColor,
                        size = 16.dp
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp
                ),
                color = contentColor
            )
        }
    }
}
