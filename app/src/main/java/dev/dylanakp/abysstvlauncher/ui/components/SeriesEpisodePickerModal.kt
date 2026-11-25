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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.MediaVersion
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SeriesSeason
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderFocus
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssColorAccent
import dev.dylanakp.abysstvlauncher.theme.AbyssColorRating
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlassElevated
import dev.dylanakp.abysstvlauncher.theme.AbyssTextMuted
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import dev.dylanakp.abysstvlauncher.ui.player.formatDurationMs
import dev.dylanakp.abysstvlauncher.ui.focus.FocusSlotHolder
import dev.dylanakp.abysstvlauncher.ui.focus.LocalFocusSlots
import dev.dylanakp.abysstvlauncher.ui.focus.safeRequestFocus
import dev.dylanakp.abysstvlauncher.ui.focus.tvClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun resolveEpisodeProgress(
    ep: SeriesEpisode,
    playbackHistory: Map<String, PlaybackHistoryEntry>
): Float {
    val hist = playbackHistory[ep.id]
    return when {
        hist != null -> hist.progressPercent
        playbackHistory.containsKey(ep.id) -> 0f
        ep.progressPercent > 0f -> ep.progressPercent
        else -> 0f
    }
}

internal fun resolveEpisodeSavedPosition(
    ep: SeriesEpisode,
    playbackHistory: Map<String, PlaybackHistoryEntry>
): Long {
    val hist = playbackHistory[ep.id]
    val prog = resolveEpisodeProgress(ep, playbackHistory)
    return when {
        hist != null && prog < 0.95f -> hist.positionMs
        ep.savedPositionMs > 1_000L && prog < 0.95f -> ep.savedPositionMs
        else -> 0L
    }
}

internal fun resolveSeriesResumeTarget(
    seasons: List<SeriesSeason>,
    seriesResumeEntry: PlaybackHistoryEntry?,
    playbackHistory: Map<String, PlaybackHistoryEntry>
): Triple<SeriesEpisode, Long, Boolean>? {
    if (seasons.isEmpty()) return null
    val allEps = seasons.flatMap { it.episodes }
    if (allEps.isEmpty()) return null

    if (seriesResumeEntry != null) {
        val matched = allEps.firstOrNull {
            it.seasonNumber == seriesResumeEntry.seasonNumber &&
                it.episodeNumber == seriesResumeEntry.episodeNumber
        }
        if (matched != null) {
            val progress = resolveEpisodeProgress(matched, playbackHistory)
            val pos = resolveEpisodeSavedPosition(matched, playbackHistory)
            return if (progress >= 0.95f) {
                val idx = allEps.indexOfFirst { it.id == matched.id }
                val nextEp = allEps.drop(idx + 1).firstOrNull { resolveEpisodeProgress(it, playbackHistory) < 0.95f }
                    ?: allEps.lastOrNull()
                    ?: matched
                Triple(nextEp, resolveEpisodeSavedPosition(nextEp, playbackHistory), true)
            } else {
                Triple(matched, pos, true)
            }
        }
    }

    val inProgressEp = allEps.firstOrNull { resolveEpisodeProgress(it, playbackHistory) in 0.01f..0.94f }
    val target = inProgressEp
        ?: allEps.firstOrNull { resolveEpisodeProgress(it, playbackHistory) < 0.95f }
        ?: allEps.first()
    return Triple(target, resolveEpisodeSavedPosition(target, playbackHistory), inProgressEp != null)
}

@Composable
fun SeriesDetailScreen(
    seriesItem: MediaItem,
    seriesDetails: SeriesDetails?,
    isLoading: Boolean,
    playbackHistory: Map<String, PlaybackHistoryEntry>,
    onBack: () -> Unit,
    onPlayEpisode: (SeriesEpisode, SeriesEpisode?, Long) -> Unit,
    onSelectEpisodeVersion: ((String, String) -> Unit)? = null,
    onMarkEpisodesWatched: ((List<SeriesEpisode>, SeriesEpisode) -> Unit)? = null,
    onToggleSingleEpisodeWatched: ((List<SeriesEpisode>, SeriesEpisode) -> Unit)? = null,
    onMarkEpisodesUnwatchedFrom: ((List<SeriesEpisode>, SeriesEpisode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var episodeForMenu by remember { mutableStateOf<SeriesEpisode?>(null) }
    var episodeForVersionPicker by remember { mutableStateOf<SeriesEpisode?>(null) }

    BackHandler {
        if (episodeForVersionPicker != null) {
            episodeForVersionPicker = null
        } else if (episodeForMenu != null) {
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
    fun getEpisodeProgress(ep: SeriesEpisode): Float =
        resolveEpisodeProgress(ep, playbackHistory)

    fun getEpisodeSavedPosition(ep: SeriesEpisode): Long =
        resolveEpisodeSavedPosition(ep, playbackHistory)

    // Determine target episode to Continue or Start from S01E01
    val resumeTargetEpisode: Triple<SeriesEpisode, Long, Boolean>? = remember(seriesDetails, playbackHistory) {
        resolveSeriesResumeTarget(seasons, seriesResumeEntry, playbackHistory)
    }

    val allEpisodes = remember(seasons) { seasons.flatMap { it.episodes } }

    fun findNextEpisode(currentEp: SeriesEpisode): SeriesEpisode? {
        val idx = allEpisodes.indexOfFirst { it.id == currentEp.id }
        return if (idx != -1 && idx + 1 < allEpisodes.size) allEpisodes[idx + 1] else null
    }

    val continueButtonRequester = remember { FocusRequester() }
    val selectedSeasonFocusRequester = remember { FocusRequester() }
    val heroBringIntoViewRequester = remember { BringIntoViewRequester() }
    val scrollHeroToTop: () -> Unit = {
        coroutineScope.launch {
            withFrameNanos { }
            heroBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(seriesItem.id, isLoading) {
        if (isLoading) return@LaunchedEffect
        withFrameNanos { }
        continueButtonRequester.safeRequestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AbyssVoid)
    ) {
        LazyColumn(
            state = pageListState,
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    onEnter = {
                        if (episodeForMenu != null || episodeForVersionPicker != null) {
                            cancelFocusChange()
                        }
                    }
                },
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // 1. FULL-BLEED 16:9 CINEMA HERO HEADER FOR THE SERIES
            item(key = "seriesHeroHeader") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .bringIntoViewRequester(heroBringIntoViewRequester)
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
                                        color = AbyssColorRating,
                                        size = 10.dp
                                    )
                                    Text(
                                        text = seriesItem.rating,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = AbyssColorRating
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
                                    onFocused = scrollHeroToTop,
                                    onClick = {
                                        onPlayEpisode(ep, findNextEpisode(ep), savedPos)
                                    }
                                )

                                if (savedPos > 15_000L) {
                                    SeriesActionPill(
                                        label = stringResource(R.string.series_restart_ep, epCode),
                                        icon = AbyssIconType.RESTART,
                                        onFocused = scrollHeroToTop,
                                        onClick = {
                                            onPlayEpisode(ep, findNextEpisode(ep), 0L)
                                        }
                                    )
                                }

                                if (ep.versions.size > 1) {
                                    val activeVer = ep.versions.firstOrNull { it.id == ep.selectedVersionId }
                                        ?: ep.versions.firstOrNull()
                                    SeriesActionPill(
                                        label = activeVer?.name ?: stringResource(R.string.movie_versions_title),
                                        icon = AbyssIconType.CHEVRON_DOWN,
                                        onFocused = scrollHeroToTop,
                                        onClick = {
                                            episodeForVersionPicker = ep
                                        }
                                    )
                                }
                            } else if (!isLoading) {
                                SeriesActionPill(
                                    label = stringResource(R.string.btn_back),
                                    icon = AbyssIconType.ARROW_LEFT,
                                    isPrimary = true,
                                    focusRequester = continueButtonRequester,
                                    onFocused = scrollHeroToTop,
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
                        val watchedInSeason = episodes.count { getEpisodeProgress(it) >= 0.95f }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 36.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                if (episodes.isNotEmpty()) {
                                    val seasonFraction = (watchedInSeason.toFloat() / episodes.size.toFloat()).coerceIn(0f, 1f)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(56.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0x33FFFFFF))
                                        ) {
                                            if (seasonFraction > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(seasonFraction)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(AbyssColorAccent)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "$watchedInSeason/${episodes.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            ),
                                            color = if (watchedInSeason > 0) AbyssColorAccent else AbyssTextMuted
                                        )
                                    }
                                }
                            }
                            Text(
                                text = stringResource(R.string.series_hold_ok_hint),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = AbyssTextMuted
                            )
                        }
                        var pendingSeasonFocus by remember { mutableStateOf<Int?>(null) }
                        val visualSelectedSeasonIndex = pendingSeasonFocus ?: selectedSeasonIndex
                        LaunchedEffect(pendingSeasonFocus) {
                            val target = pendingSeasonFocus ?: return@LaunchedEffect
                            kotlinx.coroutines.delay(120L)
                            selectedSeasonIndex = target
                            pendingSeasonFocus = null
                        }
                        LazyRow(
                            modifier = Modifier
                                .focusProperties {
                                    onEnter = {
                                        selectedSeasonFocusRequester.safeRequestFocus()
                                    }
                                }
                                .focusGroup(),
                            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = seasons,
                                key = { _, s -> s.seasonNumber },
                                contentType = { _, _ -> "season_pill" }
                            ) { idx, season ->
                                val isSelected = idx == visualSelectedSeasonIndex
                                SeriesActionPill(
                                    label = "${season.name}  (${season.episodes.size})",
                                    isPrimary = isSelected,
                                    focusRequester = if (idx == selectedSeasonIndex) selectedSeasonFocusRequester else null,
                                    onFocused = { pendingSeasonFocus = if (idx != selectedSeasonIndex) idx else null },
                                    onClick = {
                                        pendingSeasonFocus = null
                                        selectedSeasonIndex = idx
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. DETAILED EPISODE CARDS LIST
                itemsIndexed(
                    items = episodes,
                    key = { _, ep -> ep.id },
                    contentType = { _, _ -> "episode_card" }
                ) { _, ep ->
                    val progress = getEpisodeProgress(ep)
                    val savedPos = getEpisodeSavedPosition(ep)

                    DetailedEpisodeCard(
                        episode = ep,
                        seriesBackdropFallback = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                        progressPercent = progress,
                        savedPositionMs = savedPos,
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
            onSelectVersion = if ((episodeForMenu?.versions?.size ?: 0) > 1) {
                {
                    val target = episodeForMenu
                    episodeForMenu = null
                    episodeForVersionPicker = target
                }
            } else null,
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

        val versionTarget = episodeForVersionPicker
        if (versionTarget != null && versionTarget.versions.size > 1) {
            EpisodeVersionPickerDialog(
                episode = versionTarget,
                onDismiss = { episodeForVersionPicker = null },
                onSelectVersion = { versionId ->
                    onSelectEpisodeVersion?.invoke(versionTarget.id, versionId)
                    episodeForVersionPicker = null
                }
            )
        }
    }
}

@Composable
private fun EpisodeVersionPickerDialog(
    episode: SeriesEpisode,
    onDismiss: () -> Unit,
    onSelectVersion: (String) -> Unit
) {
    val firstRequester = remember { FocusRequester() }
    val selectedId = episode.selectedVersionId ?: episode.versions.firstOrNull()?.id

    LaunchedEffect(episode.id) {
        withFrameNanos { }
        firstRequester.safeRequestFocus()
    }

    AbyssModalHost(
        onDismiss = onDismiss,
        initialFocusRequester = firstRequester,
        restoreFocusTo = LocalFocusSlots.current.requester(FocusSlotHolder.FIRST_CONTENT),
        scrimColor = Color(0xA6000000)
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF191C24))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                val epCode = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}"
                Text(
                    text = "${stringResource(R.string.movie_versions_title)} • $epCode",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AbyssTextPrimary
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = episode.versions,
                        key = { _, ver -> ver.id },
                        contentType = { _, _ -> "version_card" }
                    ) { index, ver ->
                        EpisodeVersionRow(
                            version = ver,
                            isSelected = ver.id == selectedId,
                            focusRequester = if (index == 0) firstRequester else null,
                            onClick = { onSelectVersion(ver.id) }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun EpisodeVersionRow(
    version: MediaVersion,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isFocused -> Color.White
        isSelected -> Color(0x38FFFFFF)
        else -> Color(0xFF252A36)
    }
    val textColor = if (isFocused) Color(0xFF14171F) else AbyssTextPrimary
    val subColor = if (isFocused) Color(0xFF475569) else AbyssTextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .tvClickable(onFocused = { isFocused = it }, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = version.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOf(version.resolution, version.videoCodec, version.audioCodec)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isSelected) {
            AbyssIcon(
                type = AbyssIconType.CHECK,
                color = textColor,
                size = 18.dp
            )
        }
    }
}

