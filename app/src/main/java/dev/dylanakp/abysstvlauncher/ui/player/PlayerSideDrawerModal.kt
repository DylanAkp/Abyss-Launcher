package dev.dylanakp.abysstvlauncher.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.player.TrackOption
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import kotlinx.coroutines.delay

@Composable
fun PlayerSideDrawerModal(
    modalType: PlayerModalType,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectSubtitle: (TrackOption) -> Unit,
    onClose: () -> Unit
) {
    val firstItemRequester = remember { FocusRequester() }
    val closeBtnRequester = remember { FocusRequester() }

    LaunchedEffect(modalType) {
        delay(80L)
        try {
            firstItemRequester.requestFocus()
        } catch (_: Exception) {
            try { closeBtnRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val headerTitle = when (modalType) {
        PlayerModalType.AUDIO_TRACKS -> stringResource(R.string.audio_tracks_title)
        PlayerModalType.SUBTITLE_TRACKS -> stringResource(R.string.subtitles_title)
        PlayerModalType.NONE -> ""
    }

    Box(
        modifier = Modifier
            .width(380.dp)
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
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = AbyssTextPrimary
                )
                OsdPillButton(
                    label = "",
                    icon = AbyssIconType.CLOSE,
                    focusRequester = closeBtnRequester,
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (modalType) {
                PlayerModalType.AUDIO_TRACKS -> {
                    if (audioTracks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_audio_tracks),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AbyssTextSecondary
                        )
                    } else {
                        val selectedIdx = audioTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(audioTracks) { idx, opt ->
                                TrackOptionRowItem(
                                    title = opt.label,
                                    subtitle = opt.subLabel,
                                    isSelected = opt.isSelected,
                                    focusRequester = if (idx == selectedIdx) firstItemRequester else null,
                                    onClick = { onSelectAudio(opt) }
                                )
                            }
                        }
                    }
                }
                PlayerModalType.SUBTITLE_TRACKS -> {
                    val selectedIdx = subtitleTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(subtitleTracks) { idx, opt ->
                            TrackOptionRowItem(
                                title = opt.label,
                                subtitle = opt.subLabel,
                                isSelected = opt.isSelected,
                                focusRequester = if (idx == selectedIdx) firstItemRequester else null,
                                onClick = { onSelectSubtitle(opt) }
                            )
                        }
                    }
                }
                PlayerModalType.NONE -> Unit
            }
        }
    }
}

@Composable
fun TrackOptionRowItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(110),
        label = "trackRowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0x38FFFFFF)
                    else -> Color(0x14FFFFFF)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = if (isFocused) Color(0xFF1E222B) else AbyssTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isFocused) Color(0xFF555555) else AbyssTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                AbyssIcon(
                    type = AbyssIconType.CHECK,
                    color = if (isFocused) Color(0xFF1E222B) else Color.White,
                    size = 14.dp
                )
            }
        }
    }
}
