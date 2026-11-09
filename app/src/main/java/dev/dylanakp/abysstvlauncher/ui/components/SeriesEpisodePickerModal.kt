package dev.dylanakp.abysstvlauncher.ui.components

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

    var selectedSeasonIndex by remember(seriesDetails) {
        val initialIdx = if (seriesResumeEntry != null && seasons.isNotEmpty()) {
            seasons.indexOfFirst { it.seasonNumber == seriesResumeEntry.seasonNumber }.coerceAtLeast(0)
        } else 0
        mutableIntStateOf(initialIdx)
    }

    val currentSeason = seasons.getOrNull(selectedSeasonIndex)
    val episodes = currentSeason?.episodes.orEmpty()

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
                    val epHist = playbackHistory[matched.id] ?: seriesResumeEntry
                    if (epHist.progressPercent >= 0.95f) {
                        // Episode was finished -> offer the next unwatched episode!
                        val idx = allEps.indexOfFirst { it.id == matched.id }
                        val nextEp = allEps.drop(idx + 1).firstOrNull { (playbackHistory[it.id]?.progressPercent ?: 0f) < 0.95f }
                            ?: allEps.firstOrNull { (playbackHistory[it.id]?.progressPercent ?: 0f) < 0.95f }
                            ?: matched
                        Triple(nextEp, 0L, true)
                    } else {
                        val pos = if (epHist.positionMs <= 1000L) 0L else epHist.positionMs
                        Triple(matched, pos, true)
                    }
                } else {
                    val firstUnwatched = allEps.firstOrNull { (playbackHistory[it.id]?.progressPercent ?: 0f) < 0.95f } ?: allEps.first()
                    Triple(firstUnwatched, 0L, false)
                }
            } else {
                val firstUnwatched = allEps.firstOrNull { (playbackHistory[it.id]?.progressPercent ?: 0f) < 0.95f } ?: allEps.first()
                Triple(firstUnwatched, 0L, false)
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
                    val hist = playbackHistory[ep.id]
                    val progress = hist?.progressPercent ?: 0f
                    val savedPos = if (hist != null && progress < 0.95f) hist.positionMs else 0L

                    DetailedEpisodeCard(
                        episode = ep,
                        seriesBackdropFallback = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                        progressPercent = progress,
                        savedPositionMs = savedPos,
                        focusRequester = if (idx == 0) firstEpisodeRequester else null,
                        onNavigateUp = if (idx == 0) {
                            {
                                coroutineScope.launch {
                                    pageListState.animateScrollToItem(0)
                                    try {
                                        if (seasons.size > 1) firstSeasonTabRequester.requestFocus()
                                        else continueButtonRequester.requestFocus()
                                    } catch (_: Exception) {}
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
            isWatched = (playbackHistory[episodeForMenu?.id]?.progressPercent ?: 0f) >= 0.95f,
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
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailedEpisodeCard(
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
            // 16:9 Episode Still Thumbnail with Episode Number Badge & Progress Bar
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

                // Top-left Episode Number Badge
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

                // Top-right Vu / Watched checkmark badge
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

                // Center Play Icon when focused
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

                // Bottom Progress Bar
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

            // Episode Title, Plot Synopsis, Duration, and Resume Status
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
private fun SeriesMetaBadge(text: String) {
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
private fun SeriesActionPill(
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
                    // Header: Series Title
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

                    // Episode Code & Title
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

                    // 1. Mark as watched / Mark as unwatched
                    val watchedLabel = if (isWatched) stringResource(R.string.episode_action_mark_unwatched) else stringResource(R.string.episode_action_mark_watched)
                    EpisodeContextMenuItem(
                        label = watchedLabel,
                        icon = if (isWatched) AbyssIconType.CLOSE else AbyssIconType.CHECK,
                        focusRequester = initialFocusRequester,
                        onClick = onToggleSingleWatched
                    )

                    // 2. Mark all previous as watched
                    EpisodeContextMenuItem(
                        label = stringResource(R.string.episode_action_mark_all_previous_watched),
                        icon = AbyssIconType.CHECK,
                        onClick = onMarkAllPreviousWatched
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3. Cancel
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
