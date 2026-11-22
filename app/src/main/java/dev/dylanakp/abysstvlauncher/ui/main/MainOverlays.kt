package dev.dylanakp.abysstvlauncher.ui.main

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import dev.dylanakp.abysstvlauncher.data.SystemAppHelper
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.ui.LauncherUiState
import dev.dylanakp.abysstvlauncher.ui.LauncherViewModel
import dev.dylanakp.abysstvlauncher.ui.components.AppContextMenu
import dev.dylanakp.abysstvlauncher.ui.components.AppUpdateModal
import dev.dylanakp.abysstvlauncher.ui.components.MediaContextMenu
import dev.dylanakp.abysstvlauncher.ui.components.MediaDetailModal
import dev.dylanakp.abysstvlauncher.ui.components.SettingsDrawer

@Composable
fun MainOverlays(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    context: Context,
    playMediaItem: (MediaItem) -> Unit,
    settingsNavFocusRequester: FocusRequester
) {
    AppContextMenu(
        app = state.selectedAppForMenu,
        onDismiss = { viewModel.closeAppContextMenu() },
        onLaunch = { app ->
            SystemAppHelper.launchApp(context, app.packageName)
            viewModel.closeAppContextMenu()
        },
        onTogglePin = { app -> viewModel.togglePinApp(context, app) },
        onMoveLeft = { app -> viewModel.movePinnedApp(context, app, -1) },
        onMoveRight = { app -> viewModel.movePinnedApp(context, app, 1) },
        onToggleHide = { app -> viewModel.toggleHideApp(context, app) },
        onOpenSystemInfo = { app ->
            SystemAppHelper.openAppDetails(context, app.packageName)
            viewModel.closeAppContextMenu()
        }
    )

    MediaContextMenu(
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
        onToggleWatchlist = { media -> viewModel.toggleWatchlist(media) },
        onToggleWatched = { media -> viewModel.toggleMediaWatchedStatus(media) },
        onRemoveFromContinue = { media -> viewModel.removeFromContinueWatching(media) }
    )

    MediaDetailModal(
        item = state.selectedMediaForDetails,
        isSeerConfigured = state.seerConfig.isConfigured,
        onDismiss = { viewModel.closeMediaDetails() },
        onPlay = { item ->
            viewModel.closeMediaDetails()
            playMediaItem(item)
        },
        onRequestOnSeer = { item -> viewModel.requestMediaOnSeer(item) }
    )

    SettingsDrawer(
        isOpen = state.isSettingsDrawerOpen,
        navInitialFocusRequester = settingsNavFocusRequester,
        jellyfinConfig = state.jellyfinConfig,
        seerConfig = state.seerConfig,
        trackPreference = state.trackPreference,
        isSyncing = state.isSyncingJellyfin,
        isTestingSeer = state.isTestingSeer,
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
        onSelectTrackPreference = { viewModel.setTrackPreference(it) },
        onSelectLanguage = { viewModel.setLanguage(it) },
        onToggleShowHiddenApps = { viewModel.setShowHiddenApps(it) },
        onUpdateWeatherCity = { viewModel.setWeatherCity(it) },
        onSelectTemperatureUnit = { viewModel.setTemperatureUnit(it) },
        isCheckingUpdates = state.isCheckingUpdates,
        onCheckForUpdates = { viewModel.checkForUpdates(silent = false) }
    )

    AppUpdateModal(
        update = state.pendingUpdate,
        isDownloading = state.isDownloadingUpdate,
        downloadProgress = state.updateDownloadProgress,
        errorMessage = state.updateError,
        onDismiss = { viewModel.dismissUpdate() },
        onUpdate = { viewModel.downloadAndInstallUpdate(context) }
    )
}
