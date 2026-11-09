package dev.dylanakp.abysstvlauncher.ui.components

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlassElevated
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import kotlinx.coroutines.delay

@Composable
fun MediaDetailModal(
    item: MediaItem?,
    isSeerConfigured: Boolean = false,
    onDismiss: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onRequestOnSeer: ((MediaItem) -> Unit)? = null
) {
    AnimatedVisibility(
        visible = item != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (item == null) return@AnimatedVisibility
        val primaryButtonRequester = remember { FocusRequester() }

        LaunchedEffect(item) {
            delay(100)
            try {
                primaryButtonRequester.requestFocus()
            } catch (_: Exception) {}
        }

        val isAvailable = item.streamUrl.isNotBlank() || item.jellyfinId.isNotBlank() || (isSeerConfigured && item.seerStatus == SeerMediaStatus.AVAILABLE)
        val canRequest = isSeerConfigured && !isAvailable && item.tmdbId != null && item.seerStatus != SeerMediaStatus.PROCESSING && item.seerStatus != SeerMediaStatus.PENDING_APPROVAL

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .fillMaxHeight(0.72f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AbyssSurfaceGlassElevated)
                    .clickable(enabled = false) {}
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Left: Poster
                    Box(
                        modifier = Modifier
                            .width(170.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                    ) {
                        AbyssMediaArtwork(
                            imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Right: Information & Actions
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Category & Seer Status Tag
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val catLabel = when (item.category) {
                                    MediaCategory.SERIES -> stringResource(R.string.badge_series)
                                    else -> stringResource(R.string.badge_movie)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x337C5CFF))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = catLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = Color(0xFFC4B5FD)
                                    )
                                }

                                if (isSeerConfigured && item.seerStatus != SeerMediaStatus.UNKNOWN) {
                                    val (statusBg, statusFg) = when (item.seerStatus) {
                                        SeerMediaStatus.AVAILABLE -> Pair(Color(0x3310B981), Color(0xFF6EE7B7))
                                        SeerMediaStatus.PROCESSING -> Pair(Color(0x3306B6D4), Color(0xFF67E8F9))
                                        SeerMediaStatus.PENDING_APPROVAL -> Pair(Color(0x33F59E0B), Color(0xFFFCD34D))
                                        else -> Pair(Color(0x22FFFFFF), AbyssTextSecondary)
                                    }
                                    val statusText = item.seerStatus.label()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusBg)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = statusFg
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = item.title(),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = AbyssTextPrimary,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Metadata Badges (Rating, Year, Duration)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (item.rating.isNotBlank() && item.rating != "0" && item.rating != "0.0") {
                                    Text(
                                        text = "★ ${item.rating}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AbyssGold
                                    )
                                }
                                if (item.year.isNotBlank()) {
                                    Text(
                                        text = item.year,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AbyssTextSecondary
                                    )
                                }
                                if (item.duration().isNotBlank()) {
                                    Text(
                                        text = "•  ${item.duration()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AbyssTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = item.synopsis().ifBlank {
                                    stringResource(R.string.no_synopsis_available)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                ),
                                color = AbyssTextSecondary,
                                maxLines = if (isSeerConfigured && (item.seerStatus == SeerMediaStatus.PROCESSING || item.downloadProgress != null)) 3 else 5
                            )

                            // Active Seer Download Telemetry
                            if (isSeerConfigured && (item.seerStatus == SeerMediaStatus.PROCESSING || item.downloadProgress != null)) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x2206B6D4))
                                        .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    AbyssIcon(
                                                        type = AbyssIconType.DOWNLOAD,
                                                        color = Color(0xFF67E8F9),
                                                        size = 13.dp
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.downloading_in_progress),
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = Color(0xFF67E8F9)
                                                    )
                                                }

                                                if (item.downloadProgress != null) {
                                                    Text(
                                                        text = "${(item.downloadProgress * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        ),
                                                        color = Color(0xFFE0F2FE)
                                                    )
                                                }
                                            }

                                            // Progress Bar
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color(0x33FFFFFF))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(item.downloadProgress?.coerceIn(0.02f, 1f) ?: 0.5f)
                                                        .background(Color(0xFF06B6D4))
                                                )
                                            }

                                            // Subtitle info: size and ETA
                                            val details = listOfNotNull(
                                                item.downloadSizeStr,
                                                item.downloadEta?.let { stringResource(R.string.download_eta, it) }
                                            )
                                            if (details.isNotEmpty()) {
                                                Text(
                                                    text = details.joinToString(" • "),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = AbyssTextSecondary
                                                )
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.download_processing),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = AbyssTextSecondary
                                                )
                                            }
                                        }
                                    }
                            }
                        }

                        // Action Buttons: Play or Request on Seer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isAvailable) {
                                ModalActionButton(
                                    label = stringResource(R.string.details_play),
                                    icon = AbyssIconType.PLAY,
                                    isPrimary = true,
                                    focusRequester = primaryButtonRequester,
                                    onClick = { onPlay(item) }
                                )
                            } else if (canRequest && onRequestOnSeer != null) {
                                ModalActionButton(
                                    label = stringResource(R.string.cta_request_on_seer),
                                    icon = AbyssIconType.PLAY,
                                    isPrimary = true,
                                    focusRequester = primaryButtonRequester,
                                    onClick = { onRequestOnSeer(item) }
                                )
                            }

                            ModalActionButton(
                                label = stringResource(R.string.details_close),
                                icon = AbyssIconType.ARROW_LEFT,
                                isPrimary = !isAvailable && !canRequest,
                                focusRequester = if (!isAvailable && !canRequest) primaryButtonRequester else null,
                                onClick = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalActionButton(
    label: String,
    icon: AbyssIconType,
    isPrimary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(if (isFocused) 1.05f else 1f)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    isFocused -> Color.White
                    isPrimary -> Color(0x337C5CFF)
                    else -> Color(0x18FFFFFF)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AbyssIcon(
                type = icon,
                color = when {
                    isFocused -> Color.Black
                    isPrimary -> Color(0xFFC4B5FD)
                    else -> AbyssTextPrimary
                },
                size = 12.dp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = when {
                    isFocused -> Color.Black
                    isPrimary -> Color(0xFFEDE9FE)
                    else -> AbyssTextPrimary
                }
            )
        }
    }
}
