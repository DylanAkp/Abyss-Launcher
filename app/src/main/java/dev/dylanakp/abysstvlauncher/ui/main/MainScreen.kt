package dev.dylanakp.abysstvlauncher.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.SystemAppHelper
import dev.dylanakp.abysstvlauncher.model.AbyssNavTab
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import dev.dylanakp.abysstvlauncher.ui.LauncherViewModel
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import dev.dylanakp.abysstvlauncher.ui.components.AbyssSearchScreen
import dev.dylanakp.abysstvlauncher.ui.components.AbyssTopBar
import dev.dylanakp.abysstvlauncher.ui.components.AppCard
import dev.dylanakp.abysstvlauncher.ui.components.AppContextMenu
import dev.dylanakp.abysstvlauncher.ui.components.AppUpdateModal
import dev.dylanakp.abysstvlauncher.ui.components.CategoryCatalogScreen
import dev.dylanakp.abysstvlauncher.ui.components.HeroBackdropArtwork
import dev.dylanakp.abysstvlauncher.ui.components.HeroSpotlightContent
import dev.dylanakp.abysstvlauncher.ui.components.MediaDetailModal
import dev.dylanakp.abysstvlauncher.ui.components.MediaRow
import dev.dylanakp.abysstvlauncher.ui.components.MovieDetailScreen
import dev.dylanakp.abysstvlauncher.ui.components.SeriesDetailScreen
import dev.dylanakp.abysstvlauncher.ui.components.SettingsDrawer
import dev.dylanakp.abysstvlauncher.ui.player.AbyssPlayerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: LauncherViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = state.selectedTab != AbyssNavTab.HOME) {
        viewModel.selectTab(AbyssNavTab.HOME)
    }
    BackHandler(enabled = state.isSearchActive) {
        viewModel.setSearchActive(false)
        viewModel.selectTab(AbyssNavTab.HOME)
    }
    BackHandler(enabled = state.selectedMediaForDetails != null) {
        viewModel.closeMediaDetails()
    }
    BackHandler(enabled = state.selectedAppForMenu != null) {
        viewModel.closeAppContextMenu()
    }
    BackHandler(enabled = state.selectedMediaForMenu != null) {
        viewModel.closeMediaContextMenu()
    }
    BackHandler(enabled = state.isSettingsDrawerOpen) {
        viewModel.setSettingsDrawerOpen(false)
    }

    LaunchedEffect(state.seerConfig.isConfigured, state.selectedTab) {
        if (!state.seerConfig.isConfigured && state.selectedTab == AbyssNavTab.DISCOVER) {
            viewModel.selectTab(AbyssNavTab.HOME)
        }
    }

    val playMediaItem: (MediaItem) -> Unit = remember(viewModel, context) {
        { item -> viewModel.onMediaItemClicked(context, item) }
    }
    val handleMediaContextMenu: (MediaItem) -> Unit = remember(viewModel) {
        { item -> viewModel.openMediaContextMenu(item) }
    }
    val handleNoOpFocus: (MediaItem) -> Unit = remember { {} }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    LaunchedEffect(state.spotlightCandidates.size, state.selectedTab) {
        if (state.selectedTab == AbyssNavTab.HOME) {
            while (true) {
                delay(16_000L)
                viewModel.advanceHeroSpotlight()
            }
        }
    }

    // Full-screen AbyssPlayerScreen when playback session is active
    val activeSession = state.activePlayerSession
    if (activeSession != null) {
        AbyssPlayerScreen(
            session = activeSession,
            onBack = { posMs, durMs ->
                viewModel.closePlayer(posMs, durMs)
            },
            onProgressUpdate = { posMs, durMs ->
                viewModel.updatePlaybackProgress(posMs, durMs)
            },
            onPlayNextEpisode = { nextEp ->
                viewModel.playNextEpisodeInPlayer(nextEp)
            },
            onLaunchExternalFallback = { streamUrl, title ->
                val tempItem = activeSession.mediaItem.copy(streamUrl = streamUrl, title = title)
                SystemAppHelper.playMediaExternal(context, tempItem, PlayerPreference.EXTERNAL_PLAYER)
            }
        )
        return
    }

    // Movie Detail Screen
    val selectedMovie = state.selectedMovieForDetails
    if (selectedMovie != null) {
        MovieDetailScreen(
            movieItem = selectedMovie,
            movieDetails = state.activeMovieDetails,
            isLoading = state.isLoadingMovieDetails,
            playbackHistory = state.playbackHistory,
            onBack = { viewModel.closeMovieDetails() },
            onPlayMovie = { item, versionId ->
                viewModel.playMovie(context, item, versionId)
            },
            onPlayTrailer = { trailerUrl ->
                viewModel.playTrailer(context, trailerUrl, selectedMovie.title())
            },
            onSelectVersion = { versionId ->
                viewModel.selectMovieVersion(versionId)
            }
        )
        return
    }

    // Series Detail Screen
    val selectedSeries = state.selectedSeriesForEpisodes
    if (selectedSeries != null) {
        SeriesDetailScreen(
            seriesItem = selectedSeries,
            seriesDetails = state.activeSeriesDetails,
            isLoading = state.isLoadingSeriesDetails,
            playbackHistory = state.playbackHistory,
            onBack = { viewModel.closeSeriesEpisodePicker() },
            onPlayEpisode = { episode, nextEpisode, savedPositionMs ->
                viewModel.playSeriesEpisode(selectedSeries, episode, nextEpisode, savedPositionMs)
            },
            onMarkEpisodesWatched = { allEpisodes, targetEpisode ->
                viewModel.markEpisodesUpToWatched(selectedSeries, allEpisodes, targetEpisode)
            },
            onToggleSingleEpisodeWatched = { allEpisodes, targetEpisode ->
                viewModel.toggleSingleEpisodeWatched(selectedSeries, allEpisodes, targetEpisode)
            }
        )
        return
    }

    // Category Catalog Screen ("Voir tout" / "See all")
    val activeCatalog = state.activeCatalogSelection
    if (activeCatalog != null) {
        CategoryCatalogScreen(
            selection = activeCatalog,
            onBack = { viewModel.closeCatalog() },
            onItemClick = { playMediaItem(it) }
        )
        return
    }

    // Focus requesters for D-pad navigation
    val topBarFocusRequester = remember { FocusRequester() }
    val heroPlayFocusRequester = remember { FocusRequester() }
    val contentFirstFocusRequester = remember { FocusRequester() }
    val searchFirstKeyFocusRequester = remember { FocusRequester() }
    val mainListState = rememberLazyListState()
    val appsGridState = rememberLazyGridState()

    // Ensure initial focus targets Home Tab on launcher startup
    LaunchedEffect(Unit) {
        delay(150L)
        try {
            topBarFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(state.selectedTab) {
        mainListState.scrollToItem(0)
        appsGridState.scrollToItem(0)
    }

    val navigateToTopBar: () -> Unit = {
        coroutineScope.launch {
            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
        }
        Unit
    }

    val navigateToTopBarFromCatalog: () -> Unit = {
        coroutineScope.launch {
            if (mainListState.firstVisibleItemIndex > 0 || mainListState.firstVisibleItemScrollOffset > 0) {
                mainListState.animateScrollToItem(0, 0)
            } else {
                try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
        Unit
    }

    val onTopRowFocused: (MediaItem) -> Unit = {
        coroutineScope.launch {
            if (mainListState.firstVisibleItemIndex > 0 || mainListState.firstVisibleItemScrollOffset > 0) {
                mainListState.animateScrollToItem(0, 0)
            }
        }
    }

    val navigateToTopBarFromApps: () -> Unit = {
        coroutineScope.launch {
            if (appsGridState.firstVisibleItemIndex > 0 || appsGridState.firstVisibleItemScrollOffset > 0) {
                appsGridState.animateScrollToItem(0, 0)
            } else {
                try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
        Unit
    }

    val onTopRowAppFocused: () -> Unit = {
        coroutineScope.launch {
            if (appsGridState.firstVisibleItemIndex > 0 || appsGridState.firstVisibleItemScrollOffset > 0) {
                appsGridState.animateScrollToItem(0, 0)
            }
        }
    }

    val onNavigateDownFromTopBar: () -> Unit = {
        coroutineScope.launch {
            if (state.isSearchActive) {
                try {
                    searchFirstKeyFocusRequester.requestFocus()
                } catch (_: Exception) {}
            } else if (state.selectedTab == AbyssNavTab.HOME && state.activeHeroItem != null) {
                try {
                    heroPlayFocusRequester.requestFocus()
                } catch (_: Exception) {
                    try { contentFirstFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            } else {
                try {
                    contentFirstFocusRequester.requestFocus()
                } catch (_: Exception) {
                    try {
                        mainListState.scrollToItem(0)
                        delay(50)
                        contentFirstFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }
        }
        Unit
    }

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
        state.homeMovies.isNotEmpty()
    ) {
        when {
            continueWatchingMoviesFiltered.isNotEmpty() -> "moviesContinueRow"
            state.moviesTop100.isNotEmpty() -> "moviesTop100Row"
            state.moviesNouveautes.isNotEmpty() -> "moviesNouveautesRow"
            state.homeMovies.isNotEmpty() -> "moviesFullRow"
            else -> ""
        }
    }

    val topSeriesRowKey = remember(
        continueWatchingSeriesFiltered.isNotEmpty(),
        state.seriesTop100.isNotEmpty(),
        state.seriesNouveautes.isNotEmpty(),
        state.homeSeries.isNotEmpty()
    ) {
        when {
            continueWatchingSeriesFiltered.isNotEmpty() -> "seriesContinueRow"
            state.seriesTop100.isNotEmpty() -> "seriesTopRow"
            state.seriesNouveautes.isNotEmpty() -> "seriesNouvRow"
            state.homeSeries.isNotEmpty() -> "seriesFullRow"
            else -> ""
        }
    }

    val homeAppsRowState = rememberLazyListState()
    val homeContinueRowState = rememberLazyListState()
    val homeMoviesRowState = rememberLazyListState()
    val homeSeriesRowState = rememberLazyListState()
    val homeSeerRowState = rememberLazyListState()

    val homeHeroOrTopBarNavigateUp: () -> Unit = {
        coroutineScope.launch {
            mainListState.animateScrollToItem(0, 0)
            if (state.activeHeroItem != null) {
                try {
                    heroPlayFocusRequester.requestFocus()
                } catch (_: Exception) {
                    try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            } else {
                try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
        Unit
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AbyssVoid)
    ) {
        if (state.isSearchActive) {
            AbyssSearchScreen(
                query = state.searchQuery,
                movies = state.movies,
                series = state.series,
                tmdbRecommendations = state.tmdbTrending,
                apps = state.visibleApps,
                searchResults = state.searchResults,
                isSearching = state.isSearching,
                searchHistory = state.searchHistory,
                searchInputFocusRequester = searchFirstKeyFocusRequester,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onSubmitQuery = { viewModel.addSearchHistory(it) },
                onSelectSearchHistory = { viewModel.selectSearchHistory(it) },
                onClearSearchHistory = { viewModel.clearSearchHistory() },
                onRemoveSearchHistoryItem = { viewModel.removeSearchHistoryItem(it) },
                onNavigateUpToTopBar = navigateToTopBar,
                onMediaClick = {
                    viewModel.addSearchHistory(state.searchQuery)
                    playMediaItem(it)
                },
                onAppClick = { app ->
                    viewModel.addSearchHistory(state.searchQuery)
                    if (!SystemAppHelper.launchApp(context, app.packageName)) {
                        Toast.makeText(context, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
                    }
                },
                onAppLongClick = { app ->
                    viewModel.openAppContextMenu(app)
                }
            )
        } else if (state.selectedTab == AbyssNavTab.APPS) {
            AppsGridView(
                allApps = state.visibleApps,
                hiddenApps = state.hiddenAppsList,
                showHiddenApps = state.showHiddenApps,
                gridState = appsGridState,
                firstItemFocusRequester = contentFirstFocusRequester,
                onNavigateUp = navigateToTopBarFromApps,
                onTopRowFocused = onTopRowAppFocused,
                onAppClick = { app ->
                    if (!SystemAppHelper.launchApp(context, app.packageName)) {
                        Toast.makeText(context, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
                    }
                },
                onAppLongClick = { app ->
                    viewModel.openAppContextMenu(app)
                }
            )
        } else {
            LazyColumn(
                state = mainListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Cinema Hero Banner on Home
                if (state.selectedTab == AbyssNavTab.HOME) {
                    item(key = "homeUnifiedHeader", contentType = "header") {
                        val heroBringIntoViewResponder = remember {
                            object : BringIntoViewResponder {
                                @OptIn(ExperimentalFoundationApi::class)
                                override fun calculateRectForParent(localRect: Rect): Rect {
                                    return Rect(0f, 0f, localRect.right, 0f)
                                }
                                @OptIn(ExperimentalFoundationApi::class)
                                override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                                    mainListState.animateScrollToItem(0, 0)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(370.dp)
                                .background(AbyssVoid)
                                .bringIntoViewResponder(heroBringIntoViewResponder)
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
                                    onNavigateDown = {
                                        coroutineScope.launch {
                                            try {
                                                contentFirstFocusRequester.requestFocus()
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    onPlayClick = { item -> playMediaItem(item) },
                                    onPlayFocused = {
                                        coroutineScope.launch {
                                            mainListState.animateScrollToItem(0, 0)
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
                        // Favorite Pinned Apps Row
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

                        // Continue Watching Row (Powered directly by Jellyfin Resume & Next Up)
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

                        // Seer Active Requests & Downloads Row (if any)
                        if (state.seerConfig.isConfigured && state.seerRequests.isNotEmpty()) {
                            item(key = "homeSeerRequestsRow", contentType = "media_poster_row") {
                                val isTopRow = topHomeRowKey == "homeSeerRequestsRow"
                                MediaRow(
                                    title = stringResource(R.string.row_requests_downloads),
                                    items = state.seerRequests,
                                    firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                                    onNavigateUp = if (isTopRow) homeHeroOrTopBarNavigateUp else null,
                                    onItemClick = playMediaItem,
                                    onItemLongClick = handleMediaContextMenu,
                                    onItemFocused = handleNoOpFocus
                                )
                            }
                        }

                        // Jellyfin Movies Row
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

                        // Jellyfin Series Row
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

                        // Seer Trending / Discover Row
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
                        // Seer Requests Row
                        if (state.seerRequests.isNotEmpty()) {
                            item(key = "seerRequestsRow", contentType = "media_poster_row") {
                                val isTopRow = topDiscoverRowKey == "seerRequestsRow"
                                MediaRow(
                                    title = stringResource(R.string.row_seer_requests),
                                    items = state.seerRequests,
                                    firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                                    onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                                    onItemClick = playMediaItem,
                                    onItemLongClick = handleMediaContextMenu,
                                    onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus
                                )
                            }
                        }

                        // Seer Trending Row
                        if (state.seerTrending.isNotEmpty()) {
                            item(key = "seerTrendingRow", contentType = "media_poster_row") {
                                val isTopRow = topDiscoverRowKey == "seerTrendingRow"
                                val title = stringResource(R.string.row_seer_trending)
                                MediaRow(
                                    title = title,
                                    items = state.seerTrending,
                                    firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                                    onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                                    onItemClick = playMediaItem,
                                    onItemLongClick = handleMediaContextMenu,
                                    onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                                    onSeeMoreClick = { viewModel.openFullCategory(title, "seer_trending") }
                                )
                            }
                        }

                        // Seer Popular Movies
                        if (state.seerPopularMovies.isNotEmpty()) {
                            item(key = "seerPopMoviesRow", contentType = "media_poster_row") {
                                val isTopRow = topDiscoverRowKey == "seerPopMoviesRow"
                                val title = stringResource(R.string.row_seer_popular_movies)
                                MediaRow(
                                    title = title,
                                    items = state.seerPopularMovies,
                                    firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                                    onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
                                    onItemClick = playMediaItem,
                                    onItemLongClick = handleMediaContextMenu,
                                    onItemFocused = if (isTopRow) onTopRowFocused else handleNoOpFocus,
                                    onSeeMoreClick = { viewModel.openFullCategory(title, "seer_movies") }
                                )
                            }
                        }

                        // Seer Popular TV Shows
                        if (state.seerPopularTv.isNotEmpty()) {
                            item(key = "seerPopTvRow", contentType = "media_poster_row") {
                                val isTopRow = topDiscoverRowKey == "seerPopTvRow"
                                val title = stringResource(R.string.row_seer_popular_tv)
                                MediaRow(
                                    title = title,
                                    items = state.seerPopularTv,
                                    firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                                    onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
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
                                MediaRow(
                                    title = title,
                                    items = state.movies.take(30),
                                    firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                                    onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
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
                                MediaRow(
                                    title = title,
                                    items = state.series.take(30),
                                    firstItemFocusRequester = if (isTopRow) contentFirstFocusRequester else null,
                                    onNavigateUp = if (isTopRow) navigateToTopBarFromCatalog else null,
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

        // Floating Top Bar with frosted button groups
        val isRemoteLocatorInstalled = remember(state.allApps) {
            SystemAppHelper.isPackageInstalled(context.packageManager, "com.nvidia.remotelocator") ||
                state.allApps.any { it.packageName == "com.nvidia.remotelocator" }
        }

        AbyssTopBar(
            selectedTab = state.selectedTab,
            isSeerConfigured = state.seerConfig.isConfigured,
            isSearchActive = state.isSearchActive,
            weatherText = state.weatherInfo?.displayString(state.temperatureUnit),
            activeTabRequester = topBarFocusRequester,
            onNavigateDown = onNavigateDownFromTopBar,
            onSelectTab = { viewModel.selectTab(it) },
            onFindRemote = if (isRemoteLocatorInstalled) {
                {
                    val launched = SystemAppHelper.launchApp(context, "com.nvidia.remotelocator")
                    if (!launched) {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.nvidia.remotelocator")
                            if (intent != null) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else null,
            onOpenSearch = { viewModel.setSearchActive(true) },
            onToggleLanguageQuick = {
                viewModel.setLanguage(if (state.language == AppLanguage.FR) AppLanguage.EN else AppLanguage.FR)
            },
            onOpenAndroidSettings = { SystemAppHelper.openAndroidSettings(context) },
            onOpenLauncherSettings = { viewModel.setSettingsDrawerOpen(true) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Ephemeral Toast Banner
        AnimatedVisibility(
            visible = state.statusBannerMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            state.statusBannerMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE6141722))
                        .clickable { viewModel.clearStatusMessage() }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AbyssTextPrimary
                    )
                }
            }
        }

        // App Context Menu for Pinned / Hidden apps
        AppContextMenu(
            app = state.selectedAppForMenu,
            onDismiss = { viewModel.closeAppContextMenu() },
            onLaunch = { app ->
                SystemAppHelper.launchApp(context, app.packageName)
                viewModel.closeAppContextMenu()
            },
            onTogglePin = { app ->
                viewModel.togglePinApp(context, app)
            },
            onMoveLeft = { app ->
                viewModel.movePinnedApp(context, app, -1)
            },
            onMoveRight = { app ->
                viewModel.movePinnedApp(context, app, 1)
            },
            onToggleHide = { app ->
                viewModel.toggleHideApp(context, app)
            },
            onOpenSystemInfo = { app ->
                SystemAppHelper.openAppDetails(context, app.packageName)
                viewModel.closeAppContextMenu()
            }
        )

        // Media Context Menu for Movies / Series (Mark as watched, remove from continue, watchlist)
        dev.dylanakp.abysstvlauncher.ui.components.MediaContextMenu(
            media = state.selectedMediaForMenu,
            isInWatchlist = state.selectedMediaForMenu?.let { state.watchlistIds.contains(it.id) } ?: false,
            playbackHistory = state.playbackHistory,
            onDismiss = { viewModel.closeMediaContextMenu() },
            onPlay = { media ->
                viewModel.closeMediaContextMenu()
                playMediaItem(media)
            },
            onOpenDetails = { media ->
                viewModel.closeMediaContextMenu()
                playMediaItem(media)
            },
            onToggleWatchlist = { media ->
                viewModel.toggleWatchlist(media)
            },
            onToggleWatched = { media ->
                viewModel.toggleMediaWatchedStatus(media)
            },
            onRemoveFromContinue = { media ->
                viewModel.removeFromContinueWatching(media)
            }
        )

        // Media Detail Modal with Play and Seer Request support
        MediaDetailModal(
            item = state.selectedMediaForDetails,
            isSeerConfigured = state.seerConfig.isConfigured,
            onDismiss = { viewModel.closeMediaDetails() },
            onPlay = { item ->
                viewModel.closeMediaDetails()
                playMediaItem(item)
            },
            onRequestOnSeer = { item ->
                viewModel.requestMediaOnSeer(item)
            }
        )

        // Settings Drawer
        SettingsDrawer(
            isOpen = state.isSettingsDrawerOpen,
            jellyfinConfig = state.jellyfinConfig,
            seerConfig = state.seerConfig,
            isSyncing = state.isSyncingJellyfin,
            currentLanguage = state.language,
            showHiddenApps = state.showHiddenApps,
            weatherCity = state.weatherCity,
            temperatureUnit = state.temperatureUnit,
            weatherInfo = state.weatherInfo,
            onClose = { viewModel.setSettingsDrawerOpen(false) },
            onOpenAndroidSettings = { SystemAppHelper.openAndroidSettings(context) },
            onSaveAndConnectJellyfin = { srv, user, pass ->
                viewModel.saveAndConnectJellyfin(srv, user, pass)
            },
            onSaveAndTestSeer = { srv, key ->
                viewModel.saveAndTestSeer(srv, key)
            },
            onSelectPlayerPreference = { viewModel.setPlayerPreference(it) },
            onSelectLanguage = { viewModel.setLanguage(it) },
            onToggleShowHiddenApps = { viewModel.setShowHiddenApps(it) },
            onUpdateWeatherCity = { viewModel.setWeatherCity(it) },
            onSelectTemperatureUnit = { viewModel.setTemperatureUnit(it) },
            isCheckingUpdates = state.isCheckingUpdates,
            onCheckForUpdates = { viewModel.checkForUpdates(silent = false) }
        )

        // App Update Prompt Modal
        AppUpdateModal(
            update = state.pendingUpdate,
            isDownloading = state.isDownloadingUpdate,
            downloadProgress = state.updateDownloadProgress,
            errorMessage = state.updateError,
            onDismiss = { viewModel.dismissUpdate() },
            onUpdate = { viewModel.downloadAndInstallUpdate(context) }
        )
    }
}

@Composable
private fun AppHorizontalRow(
    title: String,
    apps: List<TvAppInfo>,
    onAppClick: (TvAppInfo) -> Unit,
    onAppLongClick: (TvAppInfo) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    rowState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = AbyssTextPrimary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
        )
        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(apps, key = { _, it -> it.packageName }) { index, app ->
                AppCard(
                    app = app,
                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                    onNavigateUp = onNavigateUp,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}

@Composable
private fun AppsGridView(
    allApps: List<TvAppInfo>,
    hiddenApps: List<TvAppInfo>,
    showHiddenApps: Boolean,
    gridState: LazyGridState,
    onAppClick: (TvAppInfo) -> Unit,
    onAppLongClick: (TvAppInfo) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onTopRowFocused: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalAvailableWidth = maxWidth - 64.dp
        val minCardWidth = 170.dp
        val spacing = 14.dp
        val columnsCount = maxOf(1, ((totalAvailableWidth + spacing) / (minCardWidth + spacing)).toInt())

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = minCardWidth),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 72.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            val list = if (showHiddenApps) (allApps + hiddenApps) else allApps
            itemsIndexed(list, key = { _, it -> it.packageName }) { index, app ->
                val isTopRow = index < columnsCount
                AppCard(
                    app = app,
                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                    onNavigateUp = if (isTopRow) onNavigateUp else null,
                    onFocus = if (isTopRow) onTopRowFocused else null,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}
