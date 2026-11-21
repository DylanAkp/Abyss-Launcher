package dev.dylanakp.abysstvlauncher.ui.main

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.SystemAppHelper
import dev.dylanakp.abysstvlauncher.model.AbyssNavTab
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import dev.dylanakp.abysstvlauncher.ui.LauncherUiState
import dev.dylanakp.abysstvlauncher.ui.LauncherViewModel
import dev.dylanakp.abysstvlauncher.ui.components.HeroBackdropArtwork
import dev.dylanakp.abysstvlauncher.ui.components.HeroSpotlightContent
import dev.dylanakp.abysstvlauncher.ui.components.MediaRow
import dev.dylanakp.abysstvlauncher.ui.focus.safeRequestFocus
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
private val tvVerticalBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        val bottomMargin = 32f
        return when {
            offset < 0f -> offset
            offset + size > containerSize - bottomMargin -> (offset + size) - (containerSize - bottomMargin)
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainCatalogContent(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    context: Context,
    mainListState: LazyListState,
    topBarFocusRequester: FocusRequester,
    heroPlayFocusRequester: FocusRequester,
    contentFirstFocusRequester: FocusRequester,
    navigateToTopBar: () -> Boolean,
    navigateToTopBarFromCatalog: () -> Boolean,
    onTopRowFocused: (MediaItem) -> Unit,
    playMediaItem: (MediaItem) -> Unit,
    handleMediaContextMenu: (MediaItem) -> Unit,
    handleNoOpFocus: (MediaItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val continueWatchingFiltered = state.continueWatchingItems
    val continueWatchingMoviesFiltered = state.continueWatchingMovies
    val continueWatchingSeriesFiltered = state.continueWatchingSeries

    val topHomeRowKey = remember(
        state.pinnedApps.isNotEmpty(),
        continueWatchingFiltered.isNotEmpty(),
        state.seerConfig.isConfigured,
        state.seerRequests.isNotEmpty(),
        state.homeMovies.isNotEmpty(),
        state.homeSeries.isNotEmpty(),
        state.seerTrending.isNotEmpty()
    ) {
        when {
            state.pinnedApps.isNotEmpty() -> "favoriteAppsRow"
            continueWatchingFiltered.isNotEmpty() -> "continueWatchingRow"
            state.seerConfig.isConfigured && state.seerRequests.isNotEmpty() -> "homeSeerRequestsRow"
            state.homeMovies.isNotEmpty() -> "homeMoviesRow"
            state.homeSeries.isNotEmpty() -> "homeSeriesRow"
            state.seerConfig.isConfigured && state.seerTrending.isNotEmpty() -> "homeSeerTrendingRow"
            else -> ""
        }
    }

    val topDiscoverRowKey = remember(
        state.seerRequests.isNotEmpty(),
        state.seerTrending.isNotEmpty(),
        state.seerPopularMovies.isNotEmpty(),
        state.seerPopularTv.isNotEmpty()
    ) {
        when {
            state.seerRequests.isNotEmpty() -> "seerRequestsRow"
            state.seerTrending.isNotEmpty() -> "seerTrendingRow"
            state.seerPopularMovies.isNotEmpty() -> "seerPopMoviesRow"
            state.seerPopularTv.isNotEmpty() -> "seerPopTvRow"
            else -> ""
        }
    }

    val topMovieRowKey = remember(
        continueWatchingMoviesFiltered.isNotEmpty(),
        state.moviesTop100.isNotEmpty(),
        state.moviesNouveautes.isNotEmpty(),
        state.movies.isNotEmpty()
    ) {
        when {
            continueWatchingMoviesFiltered.isNotEmpty() -> "moviesContinueRow"
            state.moviesTop100.isNotEmpty() -> "moviesTop100Row"
            state.moviesNouveautes.isNotEmpty() -> "moviesNouveautesRow"
            state.movies.isNotEmpty() -> "moviesFullRow"
            else -> ""
        }
    }

    val topSeriesRowKey = remember(
        continueWatchingSeriesFiltered.isNotEmpty(),
        state.seriesTop100.isNotEmpty(),
        state.seriesNouveautes.isNotEmpty(),
        state.series.isNotEmpty()
    ) {
        when {
            continueWatchingSeriesFiltered.isNotEmpty() -> "seriesContinueRow"
            state.seriesTop100.isNotEmpty() -> "seriesTopRow"
            state.seriesNouveautes.isNotEmpty() -> "seriesNouvRow"
            state.series.isNotEmpty() -> "seriesFullRow"
            else -> ""
        }
    }

    val homeAppsRowState = rememberLazyListState()
    val homeContinueRowState = rememberLazyListState()
    val homeSeerRequestsRowState = rememberLazyListState()
    val homeMoviesRowState = rememberLazyListState()
    val homeSeriesRowState = rememberLazyListState()
    val homeSeerRowState = rememberLazyListState()

    val discoverRequestsRowState = rememberLazyListState()
    val discoverTrendingRowState = rememberLazyListState()
    val discoverPopMoviesRowState = rememberLazyListState()
    val discoverPopTvRowState = rememberLazyListState()

    val moviesContinueRowState = rememberLazyListState()
    val moviesTop100RowState = rememberLazyListState()
    val moviesNouveautesRowState = rememberLazyListState()
    val moviesFullRowState = rememberLazyListState()

    val seriesContinueRowState = rememberLazyListState()
    val seriesTopRowState = rememberLazyListState()
    val seriesNouvRowState = rememberLazyListState()
    val seriesFullRowState = rememberLazyListState()

    val homeHeroOrTopBarNavigateUp: () -> Boolean = {
        if (mainListState.firstVisibleItemIndex > 0 || mainListState.firstVisibleItemScrollOffset > 0) {
            coroutineScope.launch {
                mainListState.animateScrollToItem(0, 0)
                if (state.activeHeroItem != null) {
                    androidx.compose.runtime.withFrameNanos { }
                    heroPlayFocusRequester.safeRequestFocus()
                }
            }
            true
        } else if (state.activeHeroItem != null && heroPlayFocusRequester.safeRequestFocus()) {
            true
        } else {
            topBarFocusRequester.safeRequestFocus()
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides tvVerticalBringIntoViewSpec) {
    LazyColumn(
        state = mainListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.selectedTab == AbyssNavTab.HOME) {
            item(key = "homeUnifiedHeader", contentType = "header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(370.dp)
                        .background(AbyssVoid)
                ) {
                    state.activeHeroItem?.let { activeHero ->
                        HeroBackdropArtwork(
                            item = activeHero,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    state.activeHeroItem?.let { activeHero ->
                        HeroSpotlightContent(
                            item = activeHero,
                            isInWatchlist = state.watchlistIds.contains(activeHero.id),
                            playButtonFocusRequester = heroPlayFocusRequester,
                            onNavigateUp = navigateToTopBar,
                            onNavigateDown = { contentFirstFocusRequester.safeRequestFocus() },
                            onPlayClick = { item -> playMediaItem(item) },
                            onPlayFocused = {
                                if (mainListState.firstVisibleItemIndex > 0 || mainListState.firstVisibleItemScrollOffset > 0) {
                                    coroutineScope.launch {
                                        mainListState.animateScrollToItem(0, 0)
                                    }
                                }
                            },
                            onDetailsClick = { item -> playMediaItem(item) },
                            onToggleWatchlist = { item ->
                                viewModel.toggleWatchlist(item)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(bottom = 12.dp)
                        )
                    }
                }
            }
        } else {
            item(key = "topBarSpacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(65.dp))
            }
        }

        when (state.selectedTab) {
            AbyssNavTab.HOME -> {
                if (state.pinnedApps.isNotEmpty()) {
                    item(key = "favoriteAppsRow", contentType = "app_row") {
                        val isTopRow = topHomeRowKey == "favoriteAppsRow"
                        AppHorizontalRow(
                            title = stringResource(R.string.row_pinned_apps),
                            apps = state.pinnedApps,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) homeHeroOrTopBarNavigateUp else null,
                            rowState = homeAppsRowState,
                            onAppClick = { app ->
                                SystemAppHelper.launchApp(context, app.packageName)
                            },
                            onAppLongClick = { app ->
                                viewModel.openAppContextMenu(app)
                            }
                        )
                    }
                }

                if (continueWatchingFiltered.isNotEmpty()) {
                    item(key = "continueWatchingRow", contentType = "media_poster_row") {
                        val isTopRow = topHomeRowKey == "continueWatchingRow"
                        MediaRow(
                            title = stringResource(R.string.row_continue_watching),
                            items = continueWatchingFiltered,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) homeHeroOrTopBarNavigateUp else null,
                            rowState = homeContinueRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = handleNoOpFocus
                        )
                    }
                }

                if (state.seerConfig.isConfigured && state.seerRequests.isNotEmpty()) {
                    item(key = "homeSeerRequestsRow", contentType = "media_poster_row") {
                        val isTopRow = topHomeRowKey == "homeSeerRequestsRow"
                        MediaRow(
                            title = stringResource(R.string.row_requests_downloads),
                            items = state.seerRequests,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) homeHeroOrTopBarNavigateUp else null,
                            rowState = homeSeerRequestsRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = handleNoOpFocus
                        )
                    }
                }

                if (state.homeMovies.isNotEmpty()) {
                    item(key = "homeMoviesRow", contentType = "media_poster_row") {
                        val isTopRow = topHomeRowKey == "homeMoviesRow"
                        val seeAllMoviesTitle = stringResource(R.string.row_all_movies)
                        MediaRow(
                            title = stringResource(R.string.row_recent_movies),
                            items = state.homeMovies,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) homeHeroOrTopBarNavigateUp else null,
                            rowState = homeMoviesRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openCatalog(seeAllMoviesTitle, state.movies) }
                        )
                    }
                }

                if (state.homeSeries.isNotEmpty()) {
                    item(key = "homeSeriesRow", contentType = "media_poster_row") {
                        val isTopRow = topHomeRowKey == "homeSeriesRow"
                        val seeAllSeriesTitle = stringResource(R.string.row_all_series)
                        MediaRow(
                            title = stringResource(R.string.row_recent_shows),
                            items = state.homeSeries,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) homeHeroOrTopBarNavigateUp else null,
                            rowState = homeSeriesRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openCatalog(seeAllSeriesTitle, state.series) }
                        )
                    }
                }

                if (state.seerConfig.isConfigured && state.seerTrending.isNotEmpty()) {
                    item(key = "homeSeerTrendingRow", contentType = "media_poster_row") {
                        val isTopRow = topHomeRowKey == "homeSeerTrendingRow"
                        val title = stringResource(R.string.row_seer_trending)
                        MediaRow(
                            title = title,
                            items = state.seerTrending,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) homeHeroOrTopBarNavigateUp else null,
                            rowState = homeSeerRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openCatalog(title, state.seerTrending) }
                        )
                    }
                }
            }

            AbyssNavTab.DISCOVER -> {
                if (state.seerRequests.isNotEmpty()) {
                    item(key = "seerRequestsRow", contentType = "media_poster_row") {
                        val isTopRow = topDiscoverRowKey == "seerRequestsRow"
                        MediaRow(
                            title = stringResource(R.string.row_seer_requests),
                            items = state.seerRequests,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = discoverRequestsRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus
                        )
                    }
                }

                if (state.seerTrending.isNotEmpty()) {
                    item(key = "seerTrendingRow", contentType = "media_poster_row") {
                        val isTopRow = topDiscoverRowKey == "seerTrendingRow"
                        val title = stringResource(R.string.row_seer_trending)
                        MediaRow(
                            title = title,
                            items = state.seerTrending,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = discoverTrendingRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openFullCategory(title, "seer_trending") }
                        )
                    }
                }

                if (state.seerPopularMovies.isNotEmpty()) {
                    item(key = "seerPopMoviesRow", contentType = "media_poster_row") {
                        val isTopRow = topDiscoverRowKey == "seerPopMoviesRow"
                        val title = stringResource(R.string.row_seer_popular_movies)
                        MediaRow(
                            title = title,
                            items = state.seerPopularMovies,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = discoverPopMoviesRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openFullCategory(title, "seer_movies") }
                        )
                    }
                }

                if (state.seerPopularTv.isNotEmpty()) {
                    item(key = "seerPopTvRow", contentType = "media_poster_row") {
                        val isTopRow = topDiscoverRowKey == "seerPopTvRow"
                        val title = stringResource(R.string.row_seer_popular_tv)
                        MediaRow(
                            title = title,
                            items = state.seerPopularTv,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = discoverPopTvRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openFullCategory(title, "seer_tv") }
                        )
                    }
                }
            }

            AbyssNavTab.MOVIES -> {
                if (continueWatchingMoviesFiltered.isNotEmpty()) {
                    item(key = "moviesContinueRow", contentType = "media_poster_row") {
                        val isTopRow = topMovieRowKey == "moviesContinueRow"
                        MediaRow(
                            title = stringResource(R.string.row_continue_watching),
                            items = continueWatchingMoviesFiltered,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = moviesContinueRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus
                        )
                    }
                }

                if (state.moviesTop100.isNotEmpty()) {
                    item(key = "moviesTop100Row", contentType = "media_poster_row") {
                        val isTopRow = topMovieRowKey == "moviesTop100Row"
                        val title = stringResource(R.string.row_top_rated_movies)
                        MediaRow(
                            title = title,
                            items = state.moviesTop100,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = moviesTop100RowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openFullCategory(title, "movies_top") }
                        )
                    }
                }

                if (state.moviesNouveautes.isNotEmpty()) {
                    item(key = "moviesNouveautesRow", contentType = "media_poster_row") {
                        val isTopRow = topMovieRowKey == "moviesNouveautesRow"
                        val title = stringResource(R.string.row_recently_added_movies)
                        MediaRow(
                            title = title,
                            items = state.moviesNouveautes,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = moviesNouveautesRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openFullCategory(title, "movies_nouv") }
                        )
                    }
                }

                if (state.movies.isNotEmpty()) {
                    item(key = "moviesFullRow", contentType = "media_poster_row") {
                        val isTopRow = topMovieRowKey == "moviesFullRow"
                        val title = stringResource(R.string.row_all_jf_movies)
                        val previewMovies = remember(state.movies) { state.movies.take(30) }
                        MediaRow(
                            title = title,
                            items = previewMovies,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = moviesFullRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openCatalog(title, state.movies) }
                        )
                    }
                }
            }

            AbyssNavTab.SERIES -> {
                if (continueWatchingSeriesFiltered.isNotEmpty()) {
                    item(key = "seriesContinueRow", contentType = "media_poster_row") {
                        val isTopRow = topSeriesRowKey == "seriesContinueRow"
                        MediaRow(
                            title = stringResource(R.string.row_continue_watching),
                            items = continueWatchingSeriesFiltered,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = seriesContinueRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus
                        )
                    }
                }

                if (state.seriesTop100.isNotEmpty()) {
                    item(key = "seriesTopRow", contentType = "media_poster_row") {
                        val isTopRow = topSeriesRowKey == "seriesTopRow"
                        val title = stringResource(R.string.row_top_rated_shows)
                        MediaRow(
                            title = title,
                            items = state.seriesTop100,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = seriesTopRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openFullCategory(title, "series_top") }
                        )
                    }
                }

                if (state.seriesNouveautes.isNotEmpty()) {
                    item(key = "seriesNouvRow", contentType = "media_poster_row") {
                        val isTopRow = topSeriesRowKey == "seriesNouvRow"
                        val title = stringResource(R.string.row_recently_added_shows)
                        MediaRow(
                            title = title,
                            items = state.seriesNouveautes,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = seriesNouvRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openFullCategory(title, "series_nouv") }
                        )
                    }
                }

                if (state.series.isNotEmpty()) {
                    item(key = "seriesFullRow", contentType = "media_poster_row") {
                        val isTopRow = topSeriesRowKey == "seriesFullRow"
                        val title = stringResource(R.string.row_all_jf_series)
                        val previewSeries = remember(state.series) { state.series.take(30) }
                        MediaRow(
                            title = title,
                            items = previewSeries,
                            firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                            onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                            rowState = seriesFullRowState,
                            onItemClick = playMediaItem,
                            onItemLongClick = handleMediaContextMenu,
                            onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                            onSeeMoreClick = { viewModel.openCatalog(title, state.series) }
                        )
                    }
                }
            }

            AbyssNavTab.APPS -> Unit
        }
    }
    }
}
