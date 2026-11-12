package dev.dylanakp.abysstvlauncher.ui.components

import dev.dylanakp.abysstvlauncher.ui.components.series.DetailedEpisodeCard
import dev.dylanakp.abysstvlauncher.ui.components.series.EpisodeContextMenu
import dev.dylanakp.abysstvlauncher.ui.components.series.SeriesActionPill
import dev.dylanakp.abysstvlauncher.ui.components.series.SeriesMetaBadge
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderFocus
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlassElevated
import dev.dylanakp.abysstvlauncher.theme.AbyssTextMuted
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import dev.dylanakp.abysstvlauncher.ui.player.formatDurationMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailScreen(
    seriesItem: MediaItem,
    seriesDetails: SeriesDetails?,
    isLoading: Boolean,
    playbackHistory: Map<String, PlaybackHistoryEntry>,
    onBack: () -> Unit,
    onPlayEpisode: (SeriesEpisode, SeriesEpisode?, Long) -> Unit,
    onMarkEpisodesWatched: ((List<SeriesEpisode>, SeriesEpisode) -> Unit)? = null,
    onToggleSingleEpisodeWatched: ((List<SeriesEpisode>, SeriesEpisode) -> Unit)? = null,
    onMarkEpisodesUnwatchedFrom: ((List<SeriesEpisode>, SeriesEpisode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var episodeForMenu by remember { mutableStateOf<SeriesEpisode?>(null) }

    BackHandler {
        if (episodeForMenu != null) {
            episodeForMenu = null
        } else {
            onBack()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val pageListState = rememberLazyListState()
    val seasons = seriesDetails?.seasons.orEmpty()
    val seriesResumeEntry = playbackHistory[seriesItem.id]

    var selectedSeasonIndex by remember(seriesItem.id) {
        val initialIdx = if (seriesResumeEntry != null && seasons.isNotEmpty()) {
            seasons.indexOfFirst { it.seasonNumber == seriesResumeEntry.seasonNumber }.coerceAtLeast(0)
        } else 0
        mutableIntStateOf(initialIdx)
    }

    val currentSeason = seasons.getOrNull(selectedSeasonIndex.coerceIn(0, (seasons.size - 1).coerceAtLeast(0)))
    val episodes = currentSeason?.episodes.orEmpty()

    // Helper to get unified progress combining local database history and server (Jellyfin) progress
    fun getEpisodeProgress(ep: SeriesEpisode): Float {
        val hist = playbackHistory[ep.id]
        return when {
            hist != null -> hist.progressPercent
            playbackHistory.containsKey(ep.id) -> 0f
            ep.progressPercent > 0f -> ep.progressPercent
            else -> 0f
        }
    }

    fun getEpisodeSavedPosition(ep: SeriesEpisode): Long {
        val hist = playbackHistory[ep.id]
        val prog = getEpisodeProgress(ep)
        return when {
            hist != null && prog < 0.95f -> hist.positionMs
            ep.savedPositionMs > 1_000L && prog < 0.95f -> ep.savedPositionMs
            else -> 0L
        }
    }

    // Determine target episode to Continue or Start from S01E01
    val resumeTargetEpisode: Triple<SeriesEpisode, Long, Boolean>? = remember(seriesDetails, playbackHistory) {
        if (seasons.isEmpty()) null
        else {
            val allEps = seasons.flatMap { it.episodes }
            if (seriesResumeEntry != null) {
                val matched = allEps.firstOrNull {
                    it.seasonNumber == seriesResumeEntry.seasonNumber &&
                        it.episodeNumber == seriesResumeEntry.episodeNumber
                }
                if (matched != null) {
                    val progress = getEpisodeProgress(matched)
                    val pos = getEpisodeSavedPosition(matched)
                    if (progress >= 0.95f) {
                        // Episode was finished -> offer the next unwatched episode!
                        val idx = allEps.indexOfFirst { it.id == matched.id }
                        val nextEp = allEps.drop(idx + 1).firstOrNull { getEpisodeProgress(it) < 0.95f }
                            ?: allEps.firstOrNull { getEpisodeProgress(it) < 0.95f }
                            ?: matched
                        val nextPos = getEpisodeSavedPosition(nextEp)
                        Triple(nextEp, nextPos, true)
                    } else {
                        Triple(matched, pos, true)
                    }
                } else {
                    val inProgressEp = allEps.firstOrNull { getEpisodeProgress(it) in 0.01f..0.94f }
                    val target = inProgressEp ?: allEps.firstOrNull { getEpisodeProgress(it) < 0.95f } ?: allEps.first()
                    Triple(target, getEpisodeSavedPosition(target), inProgressEp != null)
                }
            } else {
                // If no local history entry for the series, check if Jellyfin server returned in-progress episode
                val inProgressEp = allEps.firstOrNull { getEpisodeProgress(it) in 0.01f..0.94f }
                val target = inProgressEp ?: allEps.firstOrNull { getEpisodeProgress(it) < 0.95f } ?: allEps.first()
                Triple(target, getEpisodeSavedPosition(target), inProgressEp != null)
            }
        }
    }

    fun findNextEpisode(currentEp: SeriesEpisode): SeriesEpisode? {
        val allEps = seasons.flatMap { it.episodes }
        val idx = allEps.indexOfFirst { it.id == currentEp.id }
        return if (idx != -1 && idx + 1 < allEps.size) allEps[idx + 1] else null
    }

    val continueButtonRequester = remember { FocusRequester() }
    val firstSeasonTabRequester = remember { FocusRequester() }
    val firstEpisodeRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading, seriesDetails) {
        if (!isLoading) {
            delay(150L) // Increased delay to ensure UI is ready
            try {
                continueButtonRequester.requestFocus()
            } catch (e: Exception) {
                try { firstEpisodeRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AbyssVoid)
    ) {
        LazyColumn(
            state = pageListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // 1. FULL-BLEED 16:9 CINEMA HERO HEADER FOR THE SERIES
            item(key = "seriesHeroHeader") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(AbyssVoid)
                ) {
                    HeroBackdropArtwork(
                        item = seriesItem,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Series Metadata + Prominent Continue Button
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 36.dp, end = 220.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Eyebrow
                        Text(
                            text = stringResource(R.string.series_seasons_episodes_tag),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontSize = 10.sp
                            ),
                            color = AbyssTextSecondary
                        )

                        // Series Title
                        Text(
                            text = seriesItem.title(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                lineHeight = 34.sp
                            ),
                            color = AbyssTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Metadata Pills Row (Year, Seasons Count, Episodes Count, Quality, Rating)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (seriesItem.year.isNotBlank()) {
                                SeriesMetaBadge(text = seriesItem.year)
                            }
                            if (seasons.isNotEmpty()) {
                                val totalEps = seasons.sumOf { it.episodes.size }
                                SeriesMetaBadge(
                                    text = stringResource(R.string.series_seasons_episodes_count, seasons.size, totalEps)
                                )
                            }
                            if (seriesItem.qualityBadge.isNotBlank()) {
                                SeriesMetaBadge(text = seriesItem.qualityBadge)
                            }
                            if (seriesItem.rating.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x22FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AbyssIcon(
                                        type = AbyssIconType.STAR_FILLED,
                                        color = AbyssGold,
                                        size = 10.dp
                                    )
                                    Text(
                                        text = seriesItem.rating,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = AbyssGold
                                    )
                                }
                            }
                        }

                        // Synopsis
                        if (seriesItem.synopsis().isNotBlank()) {
                            Text(
                                text = seriesItem.synopsis(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                ),
                                color = AbyssTextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Prominent Primary CTA Row: Continue where we left off / Start S01E01 + Restart
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (resumeTargetEpisode != null) {
                                val (ep, savedPos, hasHistory) = resumeTargetEpisode
                                val epCode = "S${ep.seasonNumber.toString().padStart(2, '0')}E${ep.episodeNumber.toString().padStart(2, '0')}"
                                val continueLabel = when {
                                    savedPos > 15_000L -> stringResource(R.string.series_continue_pos, epCode, formatDurationMs(savedPos))
                                    hasHistory -> stringResource(R.string.series_continue_title, epCode, ep.title)
                                    else -> stringResource(R.string.series_play_title, epCode, ep.title)
                                }

                                SeriesActionPill(
                                    label = continueLabel,
                                    icon = AbyssIconType.PLAY,
                                    isPrimary = true,
                                    focusRequester = continueButtonRequester,
                                    onNavigateDown = {
                                        try {
                                            if (seasons.size > 1) firstSeasonTabRequester.requestFocus()
                                            else firstEpisodeRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    },
                                    onClick = {
                                        onPlayEpisode(ep, findNextEpisode(ep), savedPos)
                                    }
                                )

                                if (savedPos > 15_000L) {
                                    SeriesActionPill(
                                        label = stringResource(R.string.series_restart_ep, epCode),
                                        icon = AbyssIconType.RESTART,
                                        onNavigateDown = {
                                            try {
                                                if (seasons.size > 1) firstSeasonTabRequester.requestFocus()
                                                else firstEpisodeRequester.requestFocus()
                                            } catch (_: Exception) {}
                                        },
                                        onClick = {
                                            onPlayEpisode(ep, findNextEpisode(ep), 0L)
                                        }
                                    )
                                }
                            } else if (!isLoading) {
                                SeriesActionPill(
                                    label = stringResource(R.string.btn_back),
                                    icon = AbyssIconType.ARROW_LEFT,
                                    isPrimary = true,
                                    focusRequester = continueButtonRequester,
                                    onClick = onBack
                                )
                            }
                        }
                    }
                }
            }

            // 2. LOADING OR SEASON SELECTOR PILLS ROW
            if (isLoading) {
                item(key = "seriesLoadingState") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = stringResource(R.string.series_loading_episodes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AbyssTextSecondary
                            )
                        }
                    }
                }
            } else if (seasons.isEmpty()) {
                item(key = "seriesEmptyState") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 36.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = stringResource(R.string.series_no_episodes),
                            style = MaterialTheme.typography.titleMedium,
                            color = AbyssTextSecondary
                        )
                    }
                }
            } else {
                // Season Tabs Row (Saison 01, Saison 02...)
                item(key = "seriesSeasonSelectorRow") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 36.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.series_seasons_label),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = 11.sp
                                ),
                                color = AbyssTextSecondary
                            )
                            Text(
                                text = stringResource(R.string.series_hold_ok_hint),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = AbyssTextMuted
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(seasons, key = { _, s -> s.seasonNumber }) { idx, season ->
                                val isSelected = idx == selectedSeasonIndex
                                SeriesActionPill(
                                    label = "${season.name}  (${season.episodes.size})",
                                    isPrimary = isSelected,
                                    focusRequester = if (idx == selectedSeasonIndex) firstSeasonTabRequester else null,
                                    onFocused = { selectedSeasonIndex = idx },
                                    onNavigateUp = {
                                        coroutineScope.launch {
                                            pageListState.animateScrollToItem(0)
                                            try { continueButtonRequester.requestFocus() } catch (_: Exception) {}
                                        }
                                    },
                                    onNavigateDown = {
                                        try { firstEpisodeRequester.requestFocus() } catch (_: Exception) {}
                                    },
                                    onClick = { selectedSeasonIndex = idx }
                                )
                            }
                        }
                    }
                }

                // 3. DETAILED EPISODE CARDS LIST
                itemsIndexed(episodes, key = { _, ep -> ep.id }) { idx, ep ->
                    val progress = getEpisodeProgress(ep)
                    val savedPos = getEpisodeSavedPosition(ep)

                    DetailedEpisodeCard(
                        episode = ep,
                        seriesBackdropFallback = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                        progressPercent = progress,
                        savedPositionMs = savedPos,
                        focusRequester = if (idx == 0) firstEpisodeRequester else null,
                        onNavigateUp = if (idx == 0) {
                            {
                                if (seasons.size > 1) {
                                    try { firstSeasonTabRequester.requestFocus() } catch (_: Exception) {}
                                } else {
                                    coroutineScope.launch {
                                        pageListState.animateScrollToItem(0)
                                        try { continueButtonRequester.requestFocus() } catch (_: Exception) {}
                                    }
                                }
                            }
                        } else null,
                        onClick = {
                            onPlayEpisode(ep, findNextEpisode(ep), savedPos)
                        },
                        onLongClick = {
                            episodeForMenu = ep
                        },
                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 6.dp)
                    )
                }
            }
        }

        EpisodeContextMenu(
            episode = episodeForMenu,
            seriesItem = seriesItem,
            isWatched = (episodeForMenu?.let { getEpisodeProgress(it) } ?: 0f) >= 0.95f,
            onDismiss = { episodeForMenu = null },
            onToggleSingleWatched = {
                val targetEp = episodeForMenu ?: return@EpisodeContextMenu
                episodeForMenu = null
                val allEps = seasons.flatMap { it.episodes }
                onToggleSingleEpisodeWatched?.invoke(allEps, targetEp)
            },
            onMarkAllPreviousWatched = {
                val targetEp = episodeForMenu ?: return@EpisodeContextMenu
                episodeForMenu = null
                val allEps = seasons.flatMap { it.episodes }
                onMarkEpisodesWatched?.invoke(allEps, targetEp)
            },
            onMarkAllFollowingUnwatched = {
                val targetEp = episodeForMenu ?: return@EpisodeContextMenu
                episodeForMenu = null
                val allEps = seasons.flatMap { it.episodes }
                onMarkEpisodesUnwatchedFrom?.invoke(allEps, targetEp)
            }
        )
    }
}

