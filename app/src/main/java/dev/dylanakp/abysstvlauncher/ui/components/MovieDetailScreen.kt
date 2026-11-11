package dev.dylanakp.abysstvlauncher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.MediaVersion
import dev.dylanakp.abysstvlauncher.model.MovieDetails
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlassElevated
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import kotlinx.coroutines.delay

@Composable
fun MovieDetailScreen(
    movieItem: MediaItem,
    movieDetails: MovieDetails?,
    isLoading: Boolean,
    playbackHistory: Map<String, PlaybackHistoryEntry>,
    onBack: () -> Unit,
    onPlayMovie: (MediaItem, String?) -> Unit,
    onPlayTrailer: (String) -> Unit,
    onSelectVersion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showVersionMenu by remember { mutableStateOf(false) }

    BackHandler {
        if (showVersionMenu) {
            showVersionMenu = false
        } else {
            onBack()
        }
    }

    val playButtonRequester = remember { FocusRequester() }
    val versions = movieDetails?.versions.orEmpty()
    val selectedVersion = versions.firstOrNull { it.id == movieDetails?.selectedVersionId }
        ?: versions.firstOrNull()

    val historyEntry = playbackHistory[movieItem.id]
    val hasSavedPos = historyEntry != null && historyEntry.progressPercent < 0.95f && historyEntry.positionMs > 15_000L
    val resumeTimeStr = if (hasSavedPos && historyEntry != null) {
        val totalSec = historyEntry.positionMs / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        String.format(java.util.Locale.US, "%02d:%02d", min, sec)
    } else ""

    LaunchedEffect(movieItem.id) {
        delay(150L)
        try {
            playButtonRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AbyssVoid)
    ) {
        // 1. FULL-BLEED BANNER IN THE BACKGROUND
        HeroBackdropArtwork(
            item = movieItem,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to AbyssVoid,
                        0.42f to AbyssVoid.copy(alpha = 0.93f),
                        0.72f to AbyssVoid.copy(alpha = 0.50f),
                        1.0f to Color.Transparent
                    )
                )
        )

        // 2. MAIN DETAILS CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Back button & Category Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MovieActionButton(
                    label = stringResource(R.string.details_close),
                    icon = AbyssIconType.ARROW_LEFT,
                    isPrimary = false,
                    onClick = onBack
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.badge_movie),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = AbyssTextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (movieDetails?.genres?.isNotEmpty() == true) {
                    Text(
                        text = movieDetails.genres.take(3).joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = AbyssTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Center Column: Title, Metadata, Action Buttons, Hint & Synopsis
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Movie Title
                Text(
                    text = movieItem.title(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp
                    ),
                    color = AbyssTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )


                // Metadata Badges (Rating, Year, Duration, Quality, Audio, Version Specs)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movieItem.rating.isNotBlank() && movieItem.rating != "0" && movieItem.rating != "0.0") {
                        MovieMetaBadge(text = "★ ${movieItem.rating}", textColor = AbyssGold, bgColor = Color(0x33F59E0B))
                    }
                    if (movieItem.year.isNotBlank()) {
                        MovieMetaBadge(text = movieItem.year)
                    }
                    if (movieItem.duration().isNotBlank()) {
                        MovieMetaBadge(text = movieItem.duration())
                    }

                    val qualityBadge = selectedVersion?.resolution?.ifBlank { movieItem.qualityBadge } ?: movieItem.qualityBadge
                    if (qualityBadge.isNotBlank()) {
                        MovieMetaBadge(text = qualityBadge)
                    }

                    val audioBadge = selectedVersion?.audioCodec?.ifBlank { movieItem.audioBadge } ?: movieItem.audioBadge
                    if (audioBadge.isNotBlank()) {
                        MovieMetaBadge(text = audioBadge)
                    }

                    if (selectedVersion?.displaySize?.isNotBlank() == true) {
                        MovieMetaBadge(text = selectedVersion.displaySize, bgColor = Color(0x2210B981), textColor = Color(0xFF6EE7B7))
                    }

                    if (versions.size > 1) {
                        MovieMetaBadge(
                            text = stringResource(R.string.movie_version_count, versions.size),
                            bgColor = Color(0x22FFFFFF),
                            textColor = AbyssTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Action Buttons Row: Play/Resume (Long click for version) & Trailer
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Play / Resume Button (Long-press to pick version if multiple exist)
                    val playLabel = if (hasSavedPos) {
                        stringResource(R.string.series_resume_at, resumeTimeStr)
                    } else {
                        stringResource(R.string.cta_play_stream)
                    }

                    MovieActionButton(
                        label = playLabel,
                        icon = AbyssIconType.PLAY,
                        isPrimary = true,
                        focusRequester = playButtonRequester,
                        onClick = {
                            onPlayMovie(movieItem, selectedVersion?.id)
                        },
                        onLongClick = if (versions.size > 1) {
                            { showVersionMenu = true }
                        } else null
                    )

                    // 2. Play Trailer Button (Concise label, strictly R.string.cta_trailer)
                    if (movieDetails?.trailerUrl?.isNotBlank() == true) {
                        MovieActionButton(
                            label = stringResource(R.string.cta_trailer),
                            icon = AbyssIconType.PLAY,
                            isPrimary = false,
                            onClick = {
                                onPlayTrailer(movieDetails.trailerUrl)
                            }
                        )
                    }
                }

                // Hint for multiple versions
                if (versions.size > 1) {
                    Text(
                        text = stringResource(R.string.movie_long_press_hint),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = AbyssTextSecondary.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }

                // Synopsis
                Text(
                    text = movieItem.synopsis().ifBlank {
                        stringResource(R.string.no_synopsis_available)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    color = AbyssTextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. VERSION PICKER MODAL (Triggered by long-press on Play button)
        if (showVersionMenu && versions.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9000000))
                    .clickable { showVersionMenu = false },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AbyssSurfaceGlassElevated)
                        .border(1.dp, AbyssBorderHairline, RoundedCornerShape(20.dp))
                        .clickable(enabled = false) {}
                        .padding(24.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.movie_versions_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = AbyssTextPrimary
                            )
                            Text(
                                text = stringResource(R.string.movie_version_count, versions.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = AbyssTextSecondary
                            )
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(versions) { ver ->
                                val isSelected = ver.id == selectedVersion?.id
                                VersionSelectionCard(
                                    version = ver,
                                    isSelected = isSelected,
                                    onClick = {
                                        onSelectVersion(ver.id)
                                        showVersionMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieMetaBadge(
    text: String,
    textColor: Color = AbyssTextPrimary,
    bgColor: Color = Color(0x22FFFFFF)
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun VersionSelectionCard(
    version: MediaVersion,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.02f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0x38FFFFFF)
                    else -> Color(0x18FFFFFF)
                }
            )
            .border(
                1.dp,
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0x66FFFFFF)
                    else -> Color.Transparent
                },
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSelected) {
                        AbyssIcon(
                            type = AbyssIconType.CHECK,
                            color = if (isFocused) Color.Black else Color.White,
                            size = 14.dp
                        )
                    }
                    Text(
                        text = version.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isFocused) Color.Black else AbyssTextPrimary
                    )
                }
                val subInfo = listOfNotNull(
                    version.resolution.ifBlank { null },
                    version.videoCodec.ifBlank { null },
                    version.audioCodec.ifBlank { null },
                    version.container.uppercase().ifBlank { null }
                ).joinToString(" • ")

                if (subInfo.isNotBlank()) {
                    Text(
                        text = subInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) Color(0xFF333333) else AbyssTextSecondary
                    )
                }
            }

            if (version.displaySize.isNotBlank()) {
                Text(
                    text = version.displaySize,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isFocused) Color.Black else Color(0xFF6EE7B7)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieActionButton(
    label: String,
    icon: AbyssIconType,
    isPrimary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var longPressConsumed by remember { mutableStateOf(false) }

    val (bgColor, textColor) = when {
        isFocused -> Pair(Color.White, Color.Black)
        isPrimary -> Pair(Color(0x38FFFFFF), Color.White)
        else -> Pair(Color(0x18FFFFFF), AbyssTextPrimary)
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(if (isFocused) 1.05f else 1f)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { keyEvent ->
                if (onLongClick == null) return@onPreviewKeyEvent false
                val native = keyEvent.nativeKeyEvent
                val isSelectKey = native.keyCode in listOf(
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                )
                if (isSelectKey && native.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (native.repeatCount > 0) {
                        if (!longPressConsumed) {
                            longPressConsumed = true
                            onLongClick()
                        }
                        true
                    } else {
                        longPressConsumed = false
                        false
                    }
                } else if (isSelectKey && native.action == android.view.KeyEvent.ACTION_UP) {
                    if (longPressConsumed) {
                        longPressConsumed = false
                        true
                    } else false
                } else false
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AbyssIcon(
                type = icon,
                color = textColor,
                size = 14.dp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
