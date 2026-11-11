package dev.dylanakp.abysstvlauncher.ui.components.series

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import dev.dylanakp.abysstvlauncher.ui.components.AbyssMediaArtwork
import dev.dylanakp.abysstvlauncher.ui.player.formatDurationMs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailedEpisodeCard(
    episode: SeriesEpisode,
    seriesBackdropFallback: String,
    progressPercent: Float,
    savedPositionMs: Long,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    var longClickHandled by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "epCardScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else AbyssSurfaceGlass,
        animationSpec = tween(120),
        label = "epCardBg"
    )

    val primaryTextColor = if (isFocused) Color.Black else AbyssTextPrimary
    val secondaryTextColor = if (isFocused) Color(0xFF444444) else AbyssTextSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    onNavigateUp != null
                ) {
                    onNavigateUp.invoke()
                    true
                } else if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (event.nativeKeyEvent.repeatCount >= 1 && !longClickHandled) {
                            longClickHandled = true
                            onLongClick()
                            true
                        } else {
                            false
                        }
                    } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                        if (longClickHandled) {
                            longClickHandled = false
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick() }
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(168.dp)
                    .height(94.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AbyssVoid)
            ) {
                AbyssMediaArtwork(
                    imageUrl = episode.posterUrl.ifBlank { seriesBackdropFallback },
                    primaryColor = Color(0xFF1E293B),
                    secondaryColor = Color(0xFF0F172A),
                    darkColor = Color(0xFF08090C),
                    seedKey = episode.id,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = Color.White
                    )
                }

                if (progressPercent >= 0.95f) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xDD000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            AbyssIcon(
                                type = AbyssIconType.CHECK,
                                color = AbyssGold,
                                size = 9.dp
                            )
                            Text(
                                text = stringResource(R.string.badge_watched_caps),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = AbyssGold
                            )
                        }
                    }
                }

                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        AbyssIcon(
                            type = AbyssIconType.PLAY,
                            color = Color.White,
                            size = 13.dp
                        )
                    }
                }

                if (progressPercent > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color(0x88000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent.coerceIn(0f, 1f))
                                .background(AbyssGold)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${episode.episodeNumber}. ${episode.title}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (savedPositionMs > 15_000L) {
                            Text(
                                text = stringResource(R.string.series_resume_at, formatDurationMs(savedPositionMs)),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (isFocused) Color.Black else AbyssGold
                            )
                        } else if (progressPercent >= 0.95f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AbyssIcon(
                                    type = AbyssIconType.CHECK,
                                    color = if (isFocused) Color.Black else AbyssGold,
                                    size = 11.dp
                                )
                                Text(
                                    text = stringResource(R.string.badge_watched),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isFocused) Color.Black else AbyssGold
                                )
                            }
                        }

                        if (episode.duration.isNotBlank()) {
                            Text(
                                text = episode.duration,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                color = secondaryTextColor
                            )
                        }
                    }
                }

                if (episode.plot.isNotBlank()) {
                    Text(
                        text = episode.plot,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = secondaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stringResource(R.string.series_season_episode_fmt, episode.seasonNumber, episode.episodeNumber),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesMetaBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x22FFFFFF))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = AbyssTextSecondary
        )
    }
}

@Composable
fun SeriesActionPill(
    label: String,
    icon: AbyssIconType? = null,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(110),
        label = "seriesActionScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isPrimary -> Color(0xE6FFFFFF)
            else -> AbyssPillGlass
        },
        animationSpec = tween(110),
        label = "seriesActionBg"
    )
    val contentColor = when {
        isFocused || isPrimary -> Color.Black
        else -> AbyssTextPrimary
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onNavigateUp != null) {
                                onNavigateUp.invoke()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onNavigateDown != null) {
                                onNavigateDown.invoke()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                AbyssIcon(
                    type = icon,
                    color = contentColor,
                    size = 13.dp
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
