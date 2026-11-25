package dev.dylanakp.abysstvlauncher.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.dylanakp.abysstvlauncher.ui.focus.FocusSlotHolder
import dev.dylanakp.abysstvlauncher.ui.focus.LocalFocusSlots
import dev.dylanakp.abysstvlauncher.ui.focus.safeRequestFocus
import dev.dylanakp.abysstvlauncher.ui.focus.tvClickable
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
            onProgressUpdate = { posMs, durMs, isPaused, updatedSession ->
                viewModel.updatePlaybackProgress(posMs, durMs, isPaused, updatedSession)
            },
            onPlayNextEpisode = { nextEp ->
                viewModel.playNextEpisodeInPlayer(nextEp)
            },
            onLaunchExternalFallback = { streamUrl, title ->
                val tempItem = activeSession.mediaItem.copy(streamUrl = streamUrl, title = title)
                SystemAppHelper.playMediaExternal(
                    context,
                    tempItem,
                    PlayerPreference.EXTERNAL_PLAYER,
                    apiKey = state.jellyfinConfig.accessToken
                )
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
            onSelectEpisodeVersion = { episodeId, versionId ->
                viewModel.selectEpisodeVersion(episodeId, versionId)
            },
            onMarkEpisodesWatched = { allEpisodes, targetEpisode ->
                viewModel.markEpisodesUpToWatched(selectedSeries, allEpisodes, targetEpisode)
            },
            onToggleSingleEpisodeWatched = { allEpisodes, targetEpisode ->
                viewModel.toggleSingleEpisodeWatched(selectedSeries, allEpisodes, targetEpisode)
            },
            onMarkEpisodesUnwatchedFrom = { allEpisodes, targetEpisode ->
                viewModel.unmarkEpisodesFrom(selectedSeries, allEpisodes, targetEpisode)
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

    // Focus slots are owned by a single holder; widgets never receive requesters as parameters.
    val focusSlots = LocalFocusSlots.current
    val topBarFocusRequester = focusSlots.requester(FocusSlotHolder.TOP_BAR)
    val heroPlayFocusRequester = focusSlots.requester(FocusSlotHolder.HERO_PLAY)
    val contentFirstFocusRequester = focusSlots.requester(FocusSlotHolder.FIRST_CONTENT)
    val searchFirstKeyFocusRequester = focusSlots.requester(FocusSlotHolder.SEARCH_INPUT)
    val mainListState = rememberLazyListState()
    val appsGridState = rememberLazyGridState()

    // One-shot initial focus. Modal hosts now own their own focus + restore.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        topBarFocusRequester.safeRequestFocus()
    }

    LaunchedEffect(state.selectedTab) {
        mainListState.scrollToItem(0)
        appsGridState.scrollToItem(0)
    }

    val navigateToTopBar: () -> Boolean = {
        topBarFocusRequester.safeRequestFocus()
    }

    val isCatalogScrolled by remember {
        derivedStateOf { mainListState.firstVisibleItemIndex > 0 || mainListState.firstVisibleItemScrollOffset > 0 }
    }
    val isAppsGridScrolled by remember {
        derivedStateOf { appsGridState.firstVisibleItemIndex > 0 || appsGridState.firstVisibleItemScrollOffset > 0 }
    }

    val navigateToTopBarFromCatalog: () -> Boolean = {
        if (isCatalogScrolled) {
            coroutineScope.launch { mainListState.animateScrollToItem(0, 0) }
        }
        topBarFocusRequester.safeRequestFocus()
    }

    val onTopRowFocused: (MediaItem) -> Unit = handleNoOpFocus

    val navigateToTopBarFromApps: () -> Boolean = {
        if (isAppsGridScrolled) {
            coroutineScope.launch { appsGridState.animateScrollToItem(0, 0) }
        }
        topBarFocusRequester.safeRequestFocus()
    }

    val onTopRowAppFocused: () -> Unit = {}

    val onNavigateDownFromTopBar: () -> Boolean = {
        when {
            state.isSettingsDrawerOpen -> focusSlots.requester(FocusSlotHolder.SETTINGS_NAV).safeRequestFocus()
            state.isSearchActive -> searchFirstKeyFocusRequester.safeRequestFocus()
            state.selectedTab == AbyssNavTab.HOME && state.activeHeroItem != null -> {
                heroPlayFocusRequester.safeRequestFocus() || contentFirstFocusRequester.safeRequestFocus()
            }
            contentFirstFocusRequester.safeRequestFocus() -> true
            else -> {
                coroutineScope.launch {
                    mainListState.scrollToItem(0)
                    contentFirstFocusRequester.safeRequestFocus()
                }
                true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AbyssVoid)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        Toast.makeText(context, context.getString(dev.dylanakp.abysstvlauncher.R.string.err_cannot_launch_app, app.label), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, context.getString(dev.dylanakp.abysstvlauncher.R.string.err_cannot_launch_app, app.label), Toast.LENGTH_SHORT).show()
                    }
                },
                onAppLongClick = { app ->
                    viewModel.openAppContextMenu(app)
                }
            )
        } else {
            MainCatalogContent(
                state = state,
                viewModel = viewModel,
                context = context,
                mainListState = mainListState,
                topBarFocusRequester = topBarFocusRequester,
                heroPlayFocusRequester = heroPlayFocusRequester,
                contentFirstFocusRequester = contentFirstFocusRequester,
                navigateToTopBar = navigateToTopBar,
                navigateToTopBarFromCatalog = navigateToTopBarFromCatalog,
                onTopRowFocused = onTopRowFocused,
                playMediaItem = playMediaItem,
                handleMediaContextMenu = handleMediaContextMenu,
                handleNoOpFocus = handleNoOpFocus
            )
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
            weatherInfo = state.weatherInfo,
            temperatureUnit = state.temperatureUnit,
            activeTabRequester = topBarFocusRequester,
            onNavigateDown = onNavigateDownFromTopBar,
            onSelectTab = { viewModel.selectTab(it) },
            onFindRemote = if (isRemoteLocatorInstalled) {
                {
                    SystemAppHelper.launchApp(context, "com.nvidia.remotelocator")
                    Unit
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
                val bannerText = when (msg) {
                    is dev.dylanakp.abysstvlauncher.ui.UiMessage.Resource -> androidx.compose.ui.res.stringResource(msg.resId, *msg.args.toTypedArray())
                    is dev.dylanakp.abysstvlauncher.ui.UiMessage.Text -> msg.text
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE6141722))
                        .clickable { viewModel.clearStatusMessage() }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AbyssTextPrimary
                    )
                }
            }
        }

        MainOverlays(
            state = state,
            viewModel = viewModel,
            context = context,
            playMediaItem = playMediaItem
        )
        }
    }
}

@Composable
fun PlaybackPreparingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xD905060A))
            .focusGroup()
            .tvClickable(isBusy = true, onFocused = {}) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = Color(0xFF7C5CFF))
            Text(
                text = stringResource(R.string.player_preparing),
                style = MaterialTheme.typography.bodyMedium,
                color = AbyssTextPrimary
            )
        }
    }
}
