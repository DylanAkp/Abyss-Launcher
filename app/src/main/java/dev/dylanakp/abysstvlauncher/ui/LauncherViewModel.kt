package dev.dylanakp.abysstvlauncher.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.data.AppRepository
import dev.dylanakp.abysstvlauncher.data.AppUpdate
import dev.dylanakp.abysstvlauncher.data.AppUpdateRepository
import dev.dylanakp.abysstvlauncher.data.JellyfinLibraryFetchFailedException
import dev.dylanakp.abysstvlauncher.data.JellyfinPlaystateCommand
import dev.dylanakp.abysstvlauncher.data.JellyfinPlaystateEvent
import dev.dylanakp.abysstvlauncher.data.JellyfinRepository
import dev.dylanakp.abysstvlauncher.data.JellyfinWebSocketClient
import dev.dylanakp.abysstvlauncher.player.PlayerManager
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.data.LibrarySyncResult
import dev.dylanakp.abysstvlauncher.data.launcherDataStore
import dev.dylanakp.abysstvlauncher.data.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.data.PlaybackHistoryRepository
import dev.dylanakp.abysstvlauncher.data.SeerRepository
import dev.dylanakp.abysstvlauncher.data.SystemAppHelper
import dev.dylanakp.abysstvlauncher.data.TmdbRepository
import dev.dylanakp.abysstvlauncher.data.WeatherInfo
import dev.dylanakp.abysstvlauncher.data.WeatherRepository
import dev.dylanakp.abysstvlauncher.model.AbyssAccentPreset
import dev.dylanakp.abysstvlauncher.model.AbyssNavTab
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.JellyfinDisplayMessage
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus
import dev.dylanakp.abysstvlauncher.model.TrackPreference
import dev.dylanakp.abysstvlauncher.ui.viewmodels.LauncherSearchDelegate
import dev.dylanakp.abysstvlauncher.ui.viewmodels.LauncherUpdateDelegate
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.ui.player.PlayerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class LauncherUiState(
    val selectedTab: AbyssNavTab = AbyssNavTab.HOME,
    val language: AppLanguage = AppLanguage.AUTO,
    val showHiddenApps: Boolean = false,

    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val tmdbTrending: List<MediaItem> = emptyList(),
    val seerTrending: List<MediaItem> = emptyList(),
    val seerPopularMovies: List<MediaItem> = emptyList(),
    val seerPopularTv: List<MediaItem> = emptyList(),
    val seerRequests: List<MediaItem> = emptyList(),
    val watchlistIds: Set<String> = emptySet(),
    val playbackHistory: Map<String, PlaybackHistoryEntry> = emptyMap(),

    // Pre-computed lists ready for zero-CPU consumption in UI composables
    val continueWatchingItems: List<MediaItem> = emptyList(),
    val continueWatchingMovies: List<MediaItem> = emptyList(),
    val continueWatchingSeries: List<MediaItem> = emptyList(),
    val homeMovies: List<MediaItem> = emptyList(),
    val homeSeries: List<MediaItem> = emptyList(),

    val moviesTop100: List<MediaItem> = emptyList(),
    val moviesNouveautes: List<MediaItem> = emptyList(),
    val seriesTop100: List<MediaItem> = emptyList(),
    val seriesNouveautes: List<MediaItem> = emptyList(),

    val spotlightCandidates: List<MediaItem> = emptyList(),
    val heroIndex: Int = 0,
    val focusedMediaPreview: MediaItem? = null,
    val selectedMediaForDetails: MediaItem? = null,

    // Built-in Player, Movie & Series Detail state
    val activePlayerSession: PlayerSession? = null,
    val selectedMovieForDetails: MediaItem? = null,
    val activeMovieDetails: dev.dylanakp.abysstvlauncher.model.MovieDetails? = null,
    val isLoadingMovieDetails: Boolean = false,
    val selectedSeriesForEpisodes: MediaItem? = null,
    val activeSeriesDetails: SeriesDetails? = null,
    val isLoadingSeriesDetails: Boolean = false,

    val allApps: List<TvAppInfo> = emptyList(),
    val pinnedApps: List<TvAppInfo> = emptyList(),
    val visibleApps: List<TvAppInfo> = emptyList(),
    val hiddenAppsList: List<TvAppInfo> = emptyList(),
    val watchlistItems: List<MediaItem> = emptyList(),
    val selectedAppForMenu: TvAppInfo? = null,
    val selectedMediaForMenu: MediaItem? = null,

    val isSettingsDrawerOpen: Boolean = false,
    val weatherCity: String = "Paris",
    val temperatureUnit: dev.dylanakp.abysstvlauncher.model.TemperatureUnit = dev.dylanakp.abysstvlauncher.model.TemperatureUnit.CELSIUS,
    val weatherInfo: WeatherInfo? = null,
    val jellyfinConfig: JellyfinConfig = JellyfinConfig(),
    val seerConfig: SeerConfig = SeerConfig(),
    val trackPreference: TrackPreference = TrackPreference(),
    val isSyncingJellyfin: Boolean = false,
    val isTestingSeer: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val statusBannerMessage: UiMessage? = null,
    val jellyfinDisplayMessage: JellyfinDisplayMessage? = null,
    val activeCatalogSelection: dev.dylanakp.abysstvlauncher.ui.components.CatalogSelection? = null,
    val pendingUpdate: AppUpdate? = null,
    val isCheckingUpdates: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val updateError: String? = null
) {
    val activeHeroItem: MediaItem?
        get() = if (spotlightCandidates.isNotEmpty()) {
            spotlightCandidates.getOrElse(heroIndex % spotlightCandidates.size) {
                spotlightCandidates.first()
            }
        } else null
}

class LauncherViewModel(
    private val jellyfinWebSocketClient: JellyfinWebSocketClient,
    private val playerManager: PlayerManager
) : ViewModel() {

    private var prefs: LauncherPreferences? = null
    private var appContext: Context? = null
    private var isInitialized = false
    @Volatile
    private var hasSyncedJellyfinThisSession = false
    @Volatile
    private var hasSyncedSeerThisSession = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val libraryUpdateMutex = Mutex()
    private var rawJellyfinResumeItems: List<MediaItem> = emptyList()
    @Volatile
    private var preferredSeriesVersionIds: Map<String, String> = emptyMap()
    private var movieDetailsJob: Job? = null
    private var seriesDetailsJob: Job? = null
    private var progressSessionId = ""
    @Volatile
    private var lastProgressPositionMs = 0L

    private val searchDelegate: LauncherSearchDelegate by lazy {
        LauncherSearchDelegate(viewModelScope, _uiState) { prefs }
    }
    private val updateDelegate: LauncherUpdateDelegate by lazy {
        LauncherUpdateDelegate(viewModelScope, _uiState) { resId: Int, args: Array<out Any> ->
            showStatusMessage(resId, *args)
        }
    }

    companion object {
        private const val TRAILER_ID_PREFIX = "trailer_"
        private const val SERIES_VERSION_KEY_PREFIX = "series_version_"
        private const val WATCH_BATCH_SIZE = 8
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext
        registerNetworkSyncCallback(context.applicationContext)
        viewModelScope.launch {
            jellyfinWebSocketClient.displayMessages.collect { msg ->
                showJellyfinDisplayMessage(msg)
            }
        }
        viewModelScope.launch(Dispatchers.Main) {
            jellyfinWebSocketClient.playstateCommands.collect { event ->
                handleJellyfinPlaystate(event)
            }
        }
        val preferences = prefs ?: LauncherPreferences(context).also { prefs = it }

        // 1. Await DataStore load, then parallel load of disk cache, playback history, and installed apps on IO
        viewModelScope.launch(Dispatchers.IO) {
            preferences.awaitLoaded()

            val lang = preferences.language
            preferredSeriesVersionIds = loadSeriesVersionIds()

            val wCity = preferences.weatherCity
            val tUnit = preferences.temperatureUnit
            _uiState.update { it.copy(weatherCity = wCity, temperatureUnit = tUnit) }
            fetchWeather(wCity)
            startWeatherLoop()

            var jfConfig = preferences.jellyfinConfig
            val defaultUser = BuildConfig.DEFAULT_JELLYFIN_USER
            if (defaultUser.isNotBlank() && (jfConfig.username != defaultUser || jfConfig.accessToken.isBlank())) {
                jfConfig = jfConfig.copy(
                    serverUrl = BuildConfig.DEFAULT_JELLYFIN_SERVER.ifBlank { jfConfig.serverUrl },
                    username = defaultUser,
                    password = BuildConfig.DEFAULT_JELLYFIN_PASS.ifBlank { jfConfig.password },
                    accessToken = if (jfConfig.username != defaultUser) "" else jfConfig.accessToken
                )
                preferences.jellyfinConfig = jfConfig
            }

            var seerConfig = preferences.seerConfig
            val defaultSeerSrv = BuildConfig.DEFAULT_SEER_SERVER
            val defaultSeerKey = BuildConfig.DEFAULT_SEER_API_KEY
            if (defaultSeerSrv.isNotBlank() && (seerConfig.serverUrl.isBlank() || seerConfig.serverUrl != defaultSeerSrv || (defaultSeerKey.isNotBlank() && seerConfig.apiKey != defaultSeerKey))) {
                seerConfig = seerConfig.copy(
                    serverUrl = defaultSeerSrv,
                    apiKey = defaultSeerKey.ifBlank { seerConfig.apiKey }
                )
                preferences.seerConfig = seerConfig
            }

            val initialTab = if (!seerConfig.isConfigured && _uiState.value.selectedTab == AbyssNavTab.DISCOVER) AbyssNavTab.HOME else _uiState.value.selectedTab

            val cachedDeferred = async { MediaLibraryCache.load(context) }
            val historyDeferred = async { PlaybackHistoryRepository.getAllEntries(context) }
            val appsDeferred = async {
                AppRepository.loadInstalledApps(
                    context,
                    preferences.pinnedPackages,
                    preferences.hiddenPackages
                )
            }

            val cached = cachedDeferred.await()
            val history = historyDeferred.await()
            val apps = appsDeferred.await()

            val enrichedMovies = applyHistoryToItems(cached?.movies.orEmpty(), history)
            val enrichedSeries = applyHistoryToItems(cached?.series.orEmpty(), history)

            val baseState = _uiState.value.copy(
                selectedTab = initialTab,
                allApps = apps,
                language = lang,
                showHiddenApps = preferences.showHiddenApps,
                jellyfinConfig = jfConfig,
                seerConfig = seerConfig,
                trackPreference = preferences.trackPreference,
                watchlistIds = preferences.watchlistIds,
                playbackHistory = history,
                searchHistory = preferences.searchHistory,
                seerTrending = cached?.seerTrending.orEmpty(),
                seerPopularMovies = cached?.seerPopularMovies.orEmpty(),
                seerPopularTv = cached?.seerPopularTv.orEmpty(),
                seerRequests = cached?.seerRequests.orEmpty()
            )

            updateLibraryState {
                baseState.copy(
                    movies = enrichedMovies.ifEmpty { baseState.movies },
                    series = enrichedSeries.ifEmpty { baseState.series },
                    tmdbTrending = cached?.tmdbTrending ?: baseState.tmdbTrending,
                    playbackHistory = history
                )
            }

            // 2. Auto-sync Jellyfin library if configured
            if (jfConfig.isConfigured) {
                if (jfConfig.accessToken.isNotBlank()) {
                    jellyfinWebSocketClient.connect(jfConfig)
                }
                syncJellyfinLibrary(jfConfig, showToast = false)
            }

            // 3. Auto-sync Seer data if configured
            if (seerConfig.isConfigured) {
                syncSeerData(seerConfig)
            }

            // 4. Background refresh of TMDB Trending
            launch {
                try {
                    val trending = TmdbRepository.fetchTrending()
                    if (trending.isNotEmpty()) {
                        val currentMovies = _uiState.value.movies
                        val currentSeries = _uiState.value.series
                        val enrichedTrending = TmdbRepository.enrichWithJellyfinStreams(trending, currentMovies, currentSeries)
                        updateLibraryState { it.copy(tmdbTrending = enrichedTrending) }
                    }
                } catch (_: Exception) {}
            }

            // 5. Check for app updates on boot
            checkForUpdates(silent = true)
        }
    }

    private suspend fun updateLibraryState(transform: (LauncherUiState) -> LauncherUiState) {
        withContext(Dispatchers.Default) {
            libraryUpdateMutex.withLock {
                _uiState.update { current -> recomputeDerivedState(transform(current)) }
            }
        }
    }

    private suspend fun loadSeriesVersionIds(): Map<String, String> {
        val prefs = prefs ?: return emptyMap()
        return prefs.dataFlow.first().asMap()
            .filterKeys { it.name.startsWith(SERIES_VERSION_KEY_PREFIX) }
            .mapKeys { (key, _) -> key.name.removePrefix(SERIES_VERSION_KEY_PREFIX) }
            .mapValues { (_, value) -> value as? String ?: "" }
            .filterValues { it.isNotBlank() }
    }

    private fun persistSeriesVersion(seriesId: String, versionId: String) {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ctx.launcherDataStore.edit {
                    it[stringPreferencesKey(SERIES_VERSION_KEY_PREFIX + seriesId)] = versionId
                }
            }.onFailure { Log.w("AbyssSeriesVersion", "Failed to persist version for $seriesId", it) }
        }
    }

    private fun isTrailerSession(session: PlayerSession): Boolean =
        session.mediaItem.id.startsWith(TRAILER_ID_PREFIX)

    private fun needsJellyfinReAuth(result: LibrarySyncResult): Boolean = when (result) {
        is LibrarySyncResult.Success -> false
        is LibrarySyncResult.FetchFailed -> result.httpCode == 401
        is LibrarySyncResult.Failure ->
            result.error is SecurityException ||
                (result.error as? JellyfinLibraryFetchFailedException)?.httpCode == 401 ||
                result.error.message?.contains("401", ignoreCase = true) == true
    }

    private fun endActiveSession() {
        if (_uiState.value.activePlayerSession == null) return
        val posMs = playerManager.positionState.value.currentPositionMs
        val durMs = playerManager.state.value.durationMs
        closePlayer(posMs, durMs)
    }

    private fun resolveProgressSession(override: PlayerSession?): PlayerSession? {
        val active = _uiState.value.activePlayerSession ?: return null
        return if (override != null && override.mediaItem.id == active.mediaItem.id) override else active
    }

    private suspend fun setJellyfinWatched(config: JellyfinConfig, ids: List<String>, watched: Boolean) {
        ids.chunked(WATCH_BATCH_SIZE).forEach { batch ->
            coroutineScope {
                batch.map { id ->
                    async(Dispatchers.IO) {
                        runCatching {
                            if (watched) JellyfinRepository.markWatched(config, id)
                            else JellyfinRepository.markUnwatched(config, id)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun applyHistoryToItems(
        items: List<MediaItem>,
        history: Map<String, PlaybackHistoryEntry>
    ): List<MediaItem> {
        if (history.isEmpty() || items.isEmpty()) return items
        return items.map { item ->
            val entry = history[item.id]
            if (entry != null && entry.positionMs > 0L) {
                item.copy(progressPercent = entry.progressPercent)
            } else {
                item
            }
        }
    }

    private fun applyHistoryToSeriesDetails(
        details: dev.dylanakp.abysstvlauncher.model.SeriesDetails?,
        history: Map<String, PlaybackHistoryEntry>
    ): dev.dylanakp.abysstvlauncher.model.SeriesDetails? {
        if (details == null) return null
        return details.copy(
            seasons = details.seasons.map { season ->
                season.copy(
                    episodes = season.episodes.map { ep ->
                        val entry = history[ep.id]
                        if (entry != null) {
                            ep.copy(
                                progressPercent = entry.progressPercent,
                                savedPositionMs = if (entry.progressPercent < 0.95f) entry.positionMs else 0L
                            )
                        } else {
                            ep
                        }
                    }
                )
            }
        )
    }

    private fun recomputeDerivedState(current: LauncherUiState): LauncherUiState {
        val movies = current.movies
        val series = current.series
        val tmdbTrending = current.tmdbTrending
        val playbackHistory = current.playbackHistory
        val continueWatching = if (playbackHistory.isEmpty() && rawJellyfinResumeItems.isEmpty()) {
            emptyList()
        } else {
            val historyEntries = playbackHistory.values
                .filter { it.positionMs > 0L && it.progressPercent < 0.96f }
                .sortedByDescending { it.lastPlayedTimestamp }

            val neededIds = historyEntries.mapTo(HashSet()) { it.seriesId.ifBlank { it.itemId } }
            val foundItems = HashMap<String, MediaItem>(neededIds.size)

            for (m in movies) {
                if (neededIds.contains(m.id)) foundItems[m.id] = m
            }
            for (s in series) {
                if (neededIds.contains(s.id)) foundItems[s.id] = s
            }

            val fromHistory = historyEntries.mapNotNull { entry ->
                val targetId = entry.seriesId.ifBlank { entry.itemId }
                val item = foundItems[targetId] ?: return@mapNotNull null
                val epBadge = if (entry.seasonNumber > 0 && entry.episodeNumber > 0) {
                    "S${entry.seasonNumber.toString().padStart(2, '0')}E${entry.episodeNumber.toString().padStart(2, '0')}"
                } else item.qualityBadge

                item.copy(
                    progressPercent = entry.progressPercent,
                    qualityBadge = epBadge
                )
            }.distinctBy { it.id }

            (rawJellyfinResumeItems + fromHistory).distinctBy { it.id }
        }

        val continueMovies = continueWatching.filter { it.category == MediaCategory.MOVIE }
        val continueSeries = continueWatching.filter { it.category == MediaCategory.SERIES }

        val homeMov = movies.take(25)
        val homeSer = series.take(25)

        val movTop100 = movies.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(100)
        val movNouv = movies.take(25)
        val serTop100 = series.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(100)
        val serNouv = series.take(25)

        // Spotlight candidates: 1. Most recent continue TV show/movie, 2. Seer Trending & Popular, 3. Jellyfin & TMDB
        val candidates = run {
            val continueHero = continueSeries.firstOrNull { it.backdropUrl.isNotBlank() && it.backdropUrl != it.posterUrl }
                ?: continueWatching.firstOrNull { it.backdropUrl.isNotBlank() && it.backdropUrl != it.posterUrl }

            val validSeer = (current.seerTrending + current.seerPopularMovies + current.seerPopularTv)
                .filter { it.backdropUrl.isNotBlank() && it.backdropUrl != it.posterUrl }
                .take(12)

            val validMovies = movies.filter { it.backdropUrl.isNotBlank() && it.backdropUrl != it.posterUrl }.take(6)
            val validSeries = series.filter { it.backdropUrl.isNotBlank() && it.backdropUrl != it.posterUrl }.take(6)
            val validTrending = tmdbTrending.filter { it.backdropUrl.isNotBlank() }.take(6)

            val head = listOfNotNull(continueHero)
            val pool = (head + validSeer + validMovies + validSeries + validTrending).distinctBy { it.id }
            if (pool.isNotEmpty()) pool else (movies.take(4) + series.take(4))
        }

        val enrichedSeriesDetails = applyHistoryToSeriesDetails(current.activeSeriesDetails, playbackHistory)
        val pinned = current.allApps.filter { it.isPinned && !it.isHidden }.sortedBy { it.pinOrder }
        val visible = current.allApps.filter { !it.isHidden }
        val hidden = current.allApps.filter { it.isHidden }
        val watchlist = (movies + series).filter { current.watchlistIds.contains(it.id) }

        return current.copy(
            movies = movies,
            series = series,
            tmdbTrending = tmdbTrending,
            playbackHistory = playbackHistory,
            activeSeriesDetails = enrichedSeriesDetails,
            continueWatchingItems = continueWatching,
            continueWatchingMovies = continueMovies,
            continueWatchingSeries = continueSeries,
            homeMovies = homeMov,
            homeSeries = homeSer,
            moviesTop100 = movTop100,
            moviesNouveautes = movNouv,
            seriesTop100 = serTop100,
            seriesNouveautes = serNouv,
            spotlightCandidates = candidates,
            pinnedApps = pinned,
            visibleApps = visible,
            hiddenAppsList = hidden,
            watchlistItems = watchlist
        )
    }

    fun selectTab(tab: AbyssNavTab) {
        _uiState.update { it.copy(selectedTab = tab, isSearchActive = false) }
    }

    fun toggleSearch(active: Boolean? = null) {
        val target = active ?: !_uiState.value.isSearchActive
        _uiState.update { it.copy(isSearchActive = target) }
    }

    fun onMediaItemClicked(context: Context, item: MediaItem) {
        val state = _uiState.value

        // 1. Series -> If available in Jellyfin, open Episode Picker. Otherwise open Details modal
        if (item.category == MediaCategory.SERIES) {
            val matchedSeries = state.series.firstOrNull {
                it.id == item.id ||
                    (item.jellyfinId.isNotBlank() && it.jellyfinId == item.jellyfinId) ||
                    TmdbRepository.titlesMatchSafely(it.title, item.title)
            }
            val target = matchedSeries ?: if (state.jellyfinConfig.isConfigured && (item.jellyfinId.isNotBlank() || item.id.isNotBlank())) {
                item.copy(jellyfinId = item.jellyfinId.ifBlank { item.id })
            } else null

            if (target != null) {
                openSeriesEpisodePicker(target)
                return
            } else {
                openMediaDetails(item)
                return
            }
        }

        // 2. Playable Movie
        var playableMovieItem = item
        if (playableMovieItem.streamUrl.isBlank()) {
            val matched = state.movies.firstOrNull {
                it.id == item.id ||
                    (item.jellyfinId.isNotBlank() && it.jellyfinId == item.jellyfinId) ||
                    TmdbRepository.titlesMatchSafely(it.title, item.title)
            }
            if (matched != null && matched.streamUrl.isNotBlank()) {
                playableMovieItem = matched
            } else if (state.jellyfinConfig.isConfigured && (item.jellyfinId.isNotBlank() || item.id.isNotBlank())) {
                val jfId = item.jellyfinId.ifBlank { item.id }
                playableMovieItem = item.copy(
                    jellyfinId = jfId,
                    streamUrl = JellyfinRepository.getStreamUrl(state.jellyfinConfig, jfId)
                )
            }
        }

        openMovieDetails(playableMovieItem)
    }

    private fun launchMoviePlayback(context: Context, item: MediaItem) {
        val state = _uiState.value
        val safeStreamUrl = JellyfinRepository.sanitizeStreamUrl(item.streamUrl, state.jellyfinConfig)
        val safeItem = item.copy(streamUrl = safeStreamUrl)

        if (state.jellyfinConfig.playerPreference != PlayerPreference.ABYSS_INTERNAL) {
            val launched = SystemAppHelper.playMediaExternal(
                context,
                safeItem,
                state.jellyfinConfig.playerPreference,
                apiKey = state.jellyfinConfig.accessToken
            )
            if (launched) return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val savedEntry = PlaybackHistoryRepository.getEntry(context, safeItem.id)
            val savedPos = if (savedEntry != null && savedEntry.progressPercent < 0.95f) {
                savedEntry.positionMs
            } else 0L

            val session = PlayerSession(
                mediaItem = safeItem,
                streamUrl = safeItem.streamUrl,
                rawStreamUrl = item.streamUrl,
                title = safeItem.title(),
                subtitle = listOf(safeItem.year, safeItem.groupTitle()).filter { it.isNotBlank() }.joinToString(" • "),
                startPositionMs = 0L,
                savedResumePositionMs = savedPos
            )

            val jfId = safeItem.jellyfinId.ifBlank { safeItem.id }
            runCatching { JellyfinRepository.preparePlayback(state.jellyfinConfig, jfId) }
            runCatching { JellyfinRepository.reportPlaybackStart(state.jellyfinConfig, jfId, savedPos) }

            if (savedEntry == null) {
                PlaybackHistoryRepository.saveProgress(
                    context = context,
                    itemId = safeItem.id,
                    positionMs = 1_000L,
                    durationMs = 7_200_000L,
                    streamUrl = safeItem.streamUrl,
                    title = safeItem.title(),
                    posterUrl = safeItem.posterUrl,
                    backdropUrl = safeItem.backdropUrl.ifBlank { safeItem.posterUrl },
                    category = MediaCategory.MOVIE.name
                )
            }
            val updatedHistory = PlaybackHistoryRepository.getAllEntries(context)
            updateLibraryState { current ->
                current.copy(
                    activePlayerSession = session,
                    movies = applyHistoryToItems(current.movies, updatedHistory),
                    series = applyHistoryToItems(current.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
            }
        }
    }

    fun openMovieDetails(movieItem: MediaItem) {
        movieDetailsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedMovieForDetails = movieItem,
                activeMovieDetails = null,
                isLoadingMovieDetails = true
            )
        }
        movieDetailsJob = viewModelScope.launch(Dispatchers.IO) {
            val details = JellyfinRepository.fetchMovieDetails(_uiState.value.jellyfinConfig, movieItem)
            if (_uiState.value.selectedMovieForDetails?.id != movieItem.id) return@launch
            _uiState.update { current ->
                if (current.selectedMovieForDetails?.id == movieItem.id) {
                    current.copy(activeMovieDetails = details, isLoadingMovieDetails = false)
                } else current
            }
        }
    }

    fun closeMovieDetails() {
        movieDetailsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedMovieForDetails = null,
                activeMovieDetails = null,
                isLoadingMovieDetails = false
            )
        }
    }

    fun selectMovieVersion(versionId: String) {
        val current = _uiState.value.activeMovieDetails ?: return
        _uiState.update {
            it.copy(activeMovieDetails = current.copy(selectedVersionId = versionId))
        }
    }

    fun playMovie(context: Context, item: MediaItem, versionId: String? = null) {
        val config = _uiState.value.jellyfinConfig
        val jfId = item.jellyfinId.ifBlank { item.id }
        val streamUrl = if (config.isConfigured && jfId.isNotBlank()) {
            JellyfinRepository.getStreamUrl(config, jfId, versionId)
        } else {
            JellyfinRepository.sanitizeStreamUrl(item.streamUrl, config, versionId)
        }
        launchMoviePlayback(context, item.copy(streamUrl = streamUrl))
    }

    fun playTrailer(context: Context, trailerUrl: String, title: String) {
        if (trailerUrl.isBlank()) return
        if (trailerUrl.contains("/Videos/") && trailerUrl.contains("/stream")) {
            val session = PlayerSession(
                mediaItem = MediaItem(
                    id = "$TRAILER_ID_PREFIX${System.currentTimeMillis()}",
                    title = title,
                    category = MediaCategory.MOVIE,
                    streamUrl = trailerUrl
                ),
                streamUrl = trailerUrl,
                rawStreamUrl = trailerUrl,
                title = title,
                subtitle = context.getString(R.string.cta_trailer),
                startPositionMs = 0L
            )
            _uiState.update { it.copy(activePlayerSession = session) }
        } else {
            val key = if (trailerUrl.contains("v=")) {
                trailerUrl.substringAfter("v=").substringBefore("&")
            } else if (trailerUrl.contains("youtu.be/")) {
                trailerUrl.substringAfter("youtu.be/").substringBefore("?")
            } else null

            val launched = try {
                val intent = if (key != null) {
                    Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (!launched) {
                showStatusMessage(R.string.toast_trailer_unavailable, durationMs = 2500L)
            }
        }
    }

    fun openSeriesEpisodePicker(seriesItem: MediaItem) {
        seriesDetailsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSeriesForEpisodes = seriesItem,
                activeSeriesDetails = null,
                isLoadingSeriesDetails = true
            )
        }
        seriesDetailsJob = viewModelScope.launch(Dispatchers.IO) {
            val details = if (_uiState.value.jellyfinConfig.isConfigured) {
                JellyfinRepository.fetchSeriesDetails(_uiState.value.jellyfinConfig, seriesItem)
            } else null
            if (_uiState.value.selectedSeriesForEpisodes?.id != seriesItem.id) return@launch

            val resolvedDetails = details ?: run {
                seriesItem.tmdbId?.let { tmdbId ->
                    TmdbRepository.fetchSeriesDetailsFromTmdb(tmdbId, seriesItem)
                }
            }
            if (_uiState.value.selectedSeriesForEpisodes?.id != seriesItem.id) return@launch

            _uiState.update { current ->
                if (current.selectedSeriesForEpisodes?.id == seriesItem.id) {
                    current.copy(
                        activeSeriesDetails = applyHistoryToSeriesDetails(resolvedDetails, current.playbackHistory),
                        isLoadingSeriesDetails = false
                    )
                } else current
            }
        }
    }

    fun closeSeriesEpisodePicker() {
        seriesDetailsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSeriesForEpisodes = null,
                activeSeriesDetails = null,
                isLoadingSeriesDetails = false
            )
        }
    }

    fun selectEpisodeVersion(episodeId: String, versionId: String) {
        val details = _uiState.value.activeSeriesDetails ?: return
        val target = details.seasons.flatMap { it.episodes }.firstOrNull { it.id == episodeId }
        if (target == null || target.versions.none { it.id == versionId }) return
        preferredSeriesVersionIds = preferredSeriesVersionIds + (details.seriesItem.id to versionId)
        persistSeriesVersion(details.seriesItem.id, versionId)
        _uiState.update { state ->
            val currentDetails = state.activeSeriesDetails ?: return@update state
            state.copy(
                activeSeriesDetails = currentDetails.copy(
                    seasons = currentDetails.seasons.map { season ->
                        season.copy(
                            episodes = season.episodes.map { ep ->
                                if (ep.id == episodeId) ep.copy(selectedVersionId = versionId) else ep
                            }
                        )
                    }
                )
            )
        }
    }

    fun playSeriesEpisode(
        seriesItem: MediaItem,
        episode: SeriesEpisode,
        nextEpisode: SeriesEpisode?,
        startPositionMs: Long,
        versionId: String? = null
    ) {
        val ctx = appContext ?: return
        val config = _uiState.value.jellyfinConfig
        val chosenVersion = versionId?.let { id -> episode.versions.firstOrNull { it.id == id } }
            ?: episode.selectedVersionId?.let { id -> episode.versions.firstOrNull { it.id == id } }
            ?: preferredSeriesVersionIds[seriesItem.id]?.let { id -> episode.versions.firstOrNull { it.id == id } }
            ?: episode.versions.firstOrNull()
        if (chosenVersion != null) {
            preferredSeriesVersionIds = preferredSeriesVersionIds + (seriesItem.id to chosenVersion.id)
            persistSeriesVersion(seriesItem.id, chosenVersion.id)
        }
        val safeStreamUrl = JellyfinRepository.sanitizeStreamUrl(episode.streamUrl, config, chosenVersion?.id)
        val safeEpisode = episode.copy(streamUrl = safeStreamUrl)
        val epCode = "S${safeEpisode.seasonNumber.toString().padStart(2, '0')}E${safeEpisode.episodeNumber.toString().padStart(2, '0')}"

        if (config.playerPreference != PlayerPreference.ABYSS_INTERNAL) {
            val externalItem = seriesItem.copy(
                id = safeEpisode.id,
                jellyfinId = safeEpisode.id,
                streamUrl = safeStreamUrl,
                title = "${seriesItem.title()} — $epCode"
            )
            if (SystemAppHelper.playMediaExternal(
                    ctx,
                    externalItem,
                    config.playerPreference,
                    apiKey = config.accessToken
                )
            ) return
        }

        val session = PlayerSession(
            mediaItem = seriesItem.copy(id = safeEpisode.id, streamUrl = safeStreamUrl),
            streamUrl = safeStreamUrl,
            rawStreamUrl = episode.streamUrl,
            title = "${seriesItem.title()} — $epCode",
            subtitle = safeEpisode.title,
            startPositionMs = 0L,
            savedResumePositionMs = startPositionMs,
            seriesId = seriesItem.id,
            seasonNumber = safeEpisode.seasonNumber,
            episodeNumber = safeEpisode.episodeNumber,
            nextEpisode = nextEpisode
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { JellyfinRepository.preparePlayback(config, safeEpisode.id) }
            runCatching { JellyfinRepository.reportPlaybackStart(config, safeEpisode.id, startPositionMs) }

            if (startPositionMs <= 0L) {
                PlaybackHistoryRepository.saveProgress(
                    context = ctx,
                    itemId = episode.id,
                    positionMs = 1_000L,
                    durationMs = if (episode.savedDurationMs > 0L) episode.savedDurationMs else 1_800_000L,
                    seriesId = seriesItem.id,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.title,
                    streamUrl = episode.streamUrl,
                    title = seriesItem.title(),
                    posterUrl = seriesItem.posterUrl,
                    backdropUrl = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                    category = MediaCategory.SERIES.name
                )
            }
            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            updateLibraryState { current ->
                current.copy(
                    activePlayerSession = session,
                    movies = applyHistoryToItems(current.movies, updatedHistory),
                    series = applyHistoryToItems(current.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
            }
        }
    }

    fun playNextEpisodeInPlayer(nextEpisode: SeriesEpisode?) {
        val currentSession = _uiState.value.activePlayerSession ?: return
        val seriesId = currentSession.seriesId.ifBlank { currentSession.mediaItem.id }

        viewModelScope.launch(Dispatchers.IO) {
            val seriesDetails = _uiState.value.activeSeriesDetails ?: run {
                val config = _uiState.value.jellyfinConfig
                val dummyItem = currentSession.mediaItem.copy(id = seriesId, category = MediaCategory.SERIES)
                JellyfinRepository.fetchSeriesDetails(config, dummyItem)
            }
            val allEps = seriesDetails?.seasons?.flatMap { it.episodes }.orEmpty()
            val targetNext = nextEpisode ?: run {
                val curId = currentSession.mediaItem.id
                val curIdx = allEps.indexOfFirst {
                    it.id == curId ||
                        (it.seasonNumber == currentSession.seasonNumber && it.episodeNumber == currentSession.episodeNumber)
                }
                if (curIdx != -1 && curIdx + 1 < allEps.size) allEps[curIdx + 1] else null
            }

            if (targetNext == null) {
                withContext(Dispatchers.Main) {
                    showStatusMessage(R.string.msg_series_complete, durationMs = 3000L)
                }
                return@launch
            }

            val targetIdx = allEps.indexOfFirst { it.id == targetNext.id }
            val followingEp = if (targetIdx != -1 && targetIdx + 1 < allEps.size) allEps[targetIdx + 1] else null

            val resolvedSeriesItem = _uiState.value.selectedSeriesForEpisodes
                ?: _uiState.value.series.firstOrNull { it.id == seriesId }
                ?: currentSession.mediaItem.copy(id = seriesId, category = MediaCategory.SERIES)

            withContext(Dispatchers.Main) {
                val nextCode = "S${targetNext.seasonNumber.toString().padStart(2, '0')}E${targetNext.episodeNumber.toString().padStart(2, '0')}"
                showStatusMessage(R.string.msg_playing_next, nextCode, durationMs = 2500L)
                playSeriesEpisode(resolvedSeriesItem, targetNext, followingEp, 0L)
            }
        }
    }

    fun updatePlaybackProgress(
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean = false,
        sessionOverride: PlayerSession? = null
    ) {
        val ctx = appContext ?: return
        val session = resolveProgressSession(sessionOverride) ?: return
        if (durationMs <= 0L && positionMs <= 0L) return

        val itemId = session.mediaItem.id
        if (itemId == progressSessionId && positionMs <= lastProgressPositionMs) return
        progressSessionId = itemId
        lastProgressPositionMs = positionMs
        val isTrailer = isTrailerSession(session)
        val config = _uiState.value.jellyfinConfig

        viewModelScope.launch(Dispatchers.IO) {
            if (!isTrailer) {
                runCatching { JellyfinRepository.reportPlaybackProgress(config, itemId, positionMs, isPaused) }
                    .onFailure { Log.w("AbyssPlayback", "Progress report failed for $itemId", it) }
                runCatching {
                    PlaybackHistoryRepository.saveProgress(
                        context = ctx,
                        itemId = itemId,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        seriesId = session.seriesId,
                        seasonNumber = session.seasonNumber,
                        episodeNumber = session.episodeNumber,
                        episodeTitle = session.subtitle,
                        streamUrl = session.streamUrl,
                        title = session.mediaItem.title(),
                        posterUrl = session.mediaItem.posterUrl,
                        backdropUrl = session.mediaItem.backdropUrl.ifBlank { session.mediaItem.posterUrl },
                        category = if (session.seriesId.isNotBlank()) MediaCategory.SERIES.name else MediaCategory.MOVIE.name
                    )
                }.onFailure { Log.w("AbyssPlayback", "Progress history save failed for $itemId", it) }
            }
        }
    }

    fun closePlayer(positionMs: Long, durationMs: Long) {
        val ctx = appContext
        val session = _uiState.value.activePlayerSession ?: return
        _uiState.update { it.copy(activePlayerSession = null) }
        progressSessionId = ""
        if (ctx == null || (durationMs <= 0L && positionMs <= 0L)) return
        val isTrailer = isTrailerSession(session)
        val itemId = session.mediaItem.id
        val jfConfig = _uiState.value.jellyfinConfig

        viewModelScope.launch(Dispatchers.IO) {
            if (isTrailer) return@launch
            runCatching { JellyfinRepository.reportPlaybackStopped(jfConfig, itemId, positionMs) }
                .onFailure { Log.w("AbyssPlayback", "Stop report failed for $itemId", it) }
            runCatching {
                PlaybackHistoryRepository.saveProgress(
                    context = ctx,
                    itemId = itemId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    seriesId = session.seriesId,
                    seasonNumber = session.seasonNumber,
                    episodeNumber = session.episodeNumber,
                    episodeTitle = session.subtitle,
                    streamUrl = session.streamUrl,
                    title = session.mediaItem.title(),
                    posterUrl = session.mediaItem.posterUrl,
                    backdropUrl = session.mediaItem.backdropUrl.ifBlank { session.mediaItem.posterUrl },
                    category = if (session.seriesId.isNotBlank()) MediaCategory.SERIES.name else MediaCategory.MOVIE.name
                )
            }.onFailure { Log.w("AbyssPlayback", "Stop history save failed for $itemId", it) }
            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            updateLibraryState { current ->
                current.copy(
                    movies = applyHistoryToItems(current.movies, updatedHistory),
                    series = applyHistoryToItems(current.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
            }
        }
    }

    fun openMediaDetails(item: MediaItem) {
        val config = _uiState.value.jellyfinConfig
        val jfId = item.jellyfinId.ifBlank { item.id }
        val resolved = if (item.streamUrl.isBlank() && config.isConfigured && jfId.isNotBlank()) {
            item.copy(jellyfinId = jfId, streamUrl = JellyfinRepository.getStreamUrl(config, jfId))
        } else {
            item.copy(streamUrl = JellyfinRepository.sanitizeStreamUrl(item.streamUrl, config))
        }
        if (config.isConfigured && jfId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                JellyfinRepository.preparePlayback(config, jfId)
            }
        }
        _uiState.update { it.copy(selectedMediaForDetails = resolved) }
    }

    fun closeMediaDetails() {
        _uiState.update { it.copy(selectedMediaForDetails = null) }
    }

    fun openMediaContextMenu(item: MediaItem) {
        _uiState.update { it.copy(selectedMediaForMenu = item) }
    }

    fun closeMediaContextMenu() {
        _uiState.update { it.copy(selectedMediaForMenu = null) }
    }

    fun openSettingsDrawer() {
        _uiState.update { it.copy(isSettingsDrawerOpen = true) }
    }

    fun closeSettingsDrawer() {
        _uiState.update { it.copy(isSettingsDrawerOpen = false) }
    }

    fun setSettingsDrawerOpen(open: Boolean) {
        _uiState.update { it.copy(isSettingsDrawerOpen = open) }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
    }

    fun setSearchQuery(query: String) = updateSearchQuery(query)
    fun addSearchHistory(query: String) = commitSearchQuery(query)
    fun selectSearchHistory(query: String) = updateSearchQuery(query)

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun togglePinApp(context: Context, app: TvAppInfo) {
        if (app.isPinned) unpinApp(app.packageName) else pinApp(app.packageName)
    }

    fun toggleHideApp(context: Context, app: TvAppInfo) {
        if (app.isHidden) unhideApp(app.packageName) else hideApp(app.packageName)
    }

    fun movePinnedApp(context: Context, app: TvAppInfo, delta: Int) {
        val currentPinned = _uiState.value.allApps.filter { it.isPinned }.map { it.packageName }.toMutableList()
        val idx = currentPinned.indexOf(app.packageName)
        if (idx != -1) {
            val newIdx = (idx + delta).coerceIn(0, currentPinned.size - 1)
            if (newIdx != idx) {
                currentPinned.removeAt(idx)
                currentPinned.add(newIdx, app.packageName)
                prefs?.pinnedPackages = currentPinned
                refreshAppsList()
            }
        }
    }

    fun saveAndConnectJellyfin(serverUrl: String, user: String, pass: String) {
        val updated = _uiState.value.jellyfinConfig.copy(
            serverUrl = serverUrl,
            username = user,
            password = pass,
            accessToken = "" // Clear old token to force re-auth
        )
        prefs?.jellyfinConfig = updated
        hasSyncedJellyfinThisSession = false
        _uiState.update { it.copy(jellyfinConfig = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            prefs?.awaitWrites()
            syncJellyfinLibrary(updated, showToast = true)
        }
    }

    fun syncJellyfinLibrary(config: JellyfinConfig, showToast: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncingJellyfin = true) }

            // 1. Authenticate if token missing
            var activeConfig = if (config.accessToken.isBlank() && config.username.isNotBlank()) {
                val authRes = JellyfinRepository.authenticate(config)
                if (authRes.isSuccess) {
                    val authed = authRes.getOrThrow()
                    prefs?.jellyfinConfig = authed
                    prefs?.awaitWrites()
                    _uiState.update { it.copy(jellyfinConfig = authed) }
                    jellyfinWebSocketClient.connect(authed)
                    authed
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isSyncingJellyfin = false) }
                        if (showToast) {
                            val errMsg = authRes.exceptionOrNull()?.message ?: "Erreur d'authentification Jellyfin"
                            showStatusMessage(errMsg, 4000L)
                        }
                    }
                    return@launch
                }
            } else {
                config
            }

            var syncResult = JellyfinRepository.syncLibraryDetailed(activeConfig)

            if (needsJellyfinReAuth(syncResult)
                && activeConfig.username.isNotBlank() && activeConfig.password.isNotBlank()
            ) {
                Log.i("AbyssJellyfin", "Sync requires re-auth; re-authenticating with server...")
                val reAuthRes = JellyfinRepository.authenticate(activeConfig.copy(accessToken = ""))
                if (reAuthRes.isSuccess) {
                    activeConfig = reAuthRes.getOrThrow()
                    prefs?.jellyfinConfig = activeConfig
                    prefs?.awaitWrites()
                    _uiState.update { it.copy(jellyfinConfig = activeConfig) }
                    jellyfinWebSocketClient.connect(activeConfig)
                    syncResult = JellyfinRepository.syncLibraryDetailed(activeConfig)
                }
            }

            val resumeItems = runCatching { JellyfinRepository.fetchResumeAndNextUp(activeConfig) }
                .getOrDefault(emptyList())

            if (syncResult is LibrarySyncResult.Success) {
                val lib = syncResult.library
                hasSyncedJellyfinThisSession = true
                if (lib.movies.isEmpty() && lib.series.isEmpty() && (_uiState.value.movies.isNotEmpty() || _uiState.value.series.isNotEmpty())) {
                    Log.w("AbyssJellyfin", "Sync succeeded with an empty library; preserving cached items without re-auth")
                    _uiState.update { it.copy(isSyncingJellyfin = false) }
                    return@launch
                }

                val updatedConfig = activeConfig.copy(
                    lastSyncTimestamp = System.currentTimeMillis(),
                    syncedMoviesCount = lib.movies.size,
                    syncedSeriesCount = lib.series.size
                )
                prefs?.jellyfinConfig = updatedConfig

                appContext?.let { ctx ->
                    MediaLibraryCache.save(ctx, lib)
                }

                rawJellyfinResumeItems = resumeItems
                updateLibraryState { current ->
                    current.copy(
                        jellyfinConfig = updatedConfig,
                        isSyncingJellyfin = false,
                        movies = lib.movies,
                        series = lib.series
                    )
                }
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        showStatusMessage(R.string.msg_sync_success, durationMs = 3500L)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSyncingJellyfin = false) }
                    if (showToast) {
                        showStatusMessage(R.string.msg_sync_error, durationMs = 3500L)
                    }
                }
            }
        }
    }

    fun saveAndTestSeer(serverUrl: String, apiKey: String) {
        val updated = _uiState.value.seerConfig.copy(
            serverUrl = serverUrl,
            apiKey = apiKey
        )
        prefs?.seerConfig = updated
        hasSyncedSeerThisSession = false
        val safeTab = if (!updated.isConfigured && _uiState.value.selectedTab == AbyssNavTab.DISCOVER) AbyssNavTab.HOME else _uiState.value.selectedTab
        _uiState.update { it.copy(seerConfig = updated, isTestingSeer = true, selectedTab = safeTab) }

        viewModelScope.launch(Dispatchers.IO) {
            prefs?.awaitWrites()
            val testRes = SeerRepository.testConnection(updated)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isTestingSeer = false) }
                if (testRes.isSuccess) {
                    val ver = testRes.getOrThrow()
                    showStatusMessage(R.string.msg_seer_connected, ver, durationMs = 3000L)
                    syncSeerData(updated)
                } else {
                    showStatusMessage(R.string.msg_seer_failed, durationMs = 3500L)
                }
            }
        }
    }

    fun syncSeerData(config: SeerConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            val jf = _uiState.value.jellyfinConfig
            var activeConfig = if (config.apiKey.isBlank() && config.serverUrl.isNotBlank() && jf.isConfigured) {
                val authRes = SeerRepository.authenticateWithJellyfin(config.serverUrl, jf.username, jf.password)
                if (authRes.isSuccess) {
                    val key = authRes.getOrThrow()
                    val authed = config.copy(apiKey = key)
                    prefs?.seerConfig = authed
                    prefs?.awaitWrites()
                    _uiState.update { it.copy(seerConfig = authed) }
                    authed
                } else config
            } else config

            if (activeConfig.apiKey.isBlank()) {
                Log.w("AbyssSeer", "Seer apiKey is blank and auto-auth not completed")
                return@launch
            }

            Log.d("AbyssSeer", "Syncing Seer data from ${activeConfig.serverUrl}...")
            suspend fun fetchSeerBatch(cfg: SeerConfig) = coroutineScope {
                val t = async { runCatching { SeerRepository.fetchTrending(cfg) } }
                val m = async { runCatching { SeerRepository.fetchDiscoverMovies(cfg) } }
                val tv = async { runCatching { SeerRepository.fetchDiscoverTv(cfg) } }
                val r = async { runCatching { SeerRepository.fetchRequests(cfg) } }
                listOf(t.await(), m.await(), tv.await(), r.await())
            }

            var batch = fetchSeerBatch(activeConfig)
            var trendingRes = batch[0]
            var popMoviesRes = batch[1]
            var popTvRes = batch[2]
            var requestsRes = batch[3]

            val isAuthFailed = listOf(trendingRes, popMoviesRes, popTvRes, requestsRes).any { it.isFailure }

            if (isAuthFailed && jf.isConfigured && jf.username.isNotBlank()) {
                Log.i("AbyssSeer", "Seer sync failed (trending/movies/tv/requests); re-authenticating with Jellyfin credentials...")
                val reAuthRes = SeerRepository.authenticateWithJellyfin(activeConfig.serverUrl, jf.username, jf.password)
                if (reAuthRes.isSuccess) {
                    val newKey = reAuthRes.getOrThrow()
                    activeConfig = activeConfig.copy(apiKey = newKey)
                    prefs?.seerConfig = activeConfig
                    prefs?.awaitWrites()
                    _uiState.update { it.copy(seerConfig = activeConfig) }

                    batch = fetchSeerBatch(activeConfig)
                    trendingRes = batch[0]
                    popMoviesRes = batch[1]
                    popTvRes = batch[2]
                    requestsRes = batch[3]
                }
            }

            val trending = trendingRes.getOrDefault(emptyList())
            val popMovies = popMoviesRes.getOrDefault(emptyList())
            val popTv = popTvRes.getOrDefault(emptyList())
            val requests = requestsRes.getOrDefault(emptyList())
            val allSucceeded = listOf(trendingRes, popMoviesRes, popTvRes, requestsRes).all { it.isSuccess }
            hasSyncedSeerThisSession = allSucceeded

            Log.d("AbyssSeer", "Seer sync complete: ${trending.size} trending, ${popMovies.size} pop movies, ${popTv.size} pop tv, ${requests.size} requests")

            if (!allSucceeded && _uiState.value.seerTrending.isNotEmpty()) {
                Log.w("AbyssSeer", "Seer sync failed; preserving existing cached items")
                return@launch
            }

            val curMovies = _uiState.value.movies
            val curSeries = _uiState.value.series

            val enrichedTrending = TmdbRepository.enrichWithJellyfinStreams(trending, curMovies, curSeries)
            val enrichedPopMovies = TmdbRepository.enrichWithJellyfinStreams(popMovies, curMovies, curSeries)
            val enrichedPopTv = TmdbRepository.enrichWithJellyfinStreams(popTv, curMovies, curSeries)

            val updatedConfig = activeConfig.copy(
                lastSyncTimestamp = System.currentTimeMillis(),
                requestedCount = requests.size
            )
            prefs?.seerConfig = updatedConfig

            appContext?.let { ctx ->
                val currentLib = MediaLibraryCache.load(ctx)
                val updatedLib = (currentLib ?: SyncedLibrary()).copy(
                    seerTrending = enrichedTrending,
                    seerPopularMovies = enrichedPopMovies,
                    seerPopularTv = enrichedPopTv,
                    seerRequests = requests
                )
                MediaLibraryCache.save(ctx, updatedLib)
            }

            updateLibraryState { current ->
                current.copy(
                    seerConfig = updatedConfig,
                    seerTrending = enrichedTrending,
                    seerPopularMovies = enrichedPopMovies,
                    seerPopularTv = enrichedPopTv,
                    seerRequests = requests
                )
            }

            startSeerPolling(activeConfig)
        }
    }

    private var seerPollingJob: Job? = null

    private fun startSeerPolling(config: SeerConfig) {
        seerPollingJob?.cancel()
        seerPollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val hasActiveDownloads = _uiState.value.seerRequests.any {
                    it.seerStatus == SeerMediaStatus.PROCESSING || it.downloadProgress != null
                }
                // Poll every 30 seconds if actively downloading, otherwise every 2 minutes
                val delayMs = if (hasActiveDownloads) 30_000L else 120_000L
                delay(delayMs)

                val activeConfig = _uiState.value.seerConfig
                if (activeConfig.isConfigured && activeConfig.apiKey.isNotBlank()) {
                    val requests = SeerRepository.fetchRequests(activeConfig)
                    _uiState.update { it.copy(seerRequests = requests) }

                    // If a download finished (requests became available in Jellyfin), refresh Jellyfin library
                    val jfConfig = _uiState.value.jellyfinConfig
                    if (hasActiveDownloads && jfConfig.isConfigured) {
                        val stillDownloading = requests.any {
                            it.seerStatus == SeerMediaStatus.PROCESSING || it.downloadProgress != null
                        }
                        if (!stillDownloading) {
                            syncJellyfinLibrary(jfConfig, showToast = false)
                        }
                    }
                }
            }
        }
    }

    fun refreshInstalledApps(context: Context) {
        val preferences = prefs ?: LauncherPreferences(context).also { prefs = it }
        val pinned = preferences.pinnedPackages
        val hidden = preferences.hiddenPackages

        viewModelScope.launch(Dispatchers.IO) {
            val apps = AppRepository.loadInstalledApps(context, pinned, hidden)
            val pinned = apps.filter { it.isPinned && !it.isHidden }.sortedBy { it.pinOrder }
            val visible = apps.filter { !it.isHidden }
            val hiddenList = apps.filter { it.isHidden }
            _uiState.update {
                it.copy(
                    allApps = apps,
                    pinnedApps = pinned,
                    visibleApps = visible,
                    hiddenAppsList = hiddenList
                )
            }
        }
    }

    fun handleHomePressed() {
        endActiveSession()
        if (_uiState.value.isSearchActive) {
            setSearchActive(false)
        }
        if (_uiState.value.selectedMovieForDetails != null) {
            closeMovieDetails()
        }
        if (_uiState.value.selectedSeriesForEpisodes != null) {
            closeSeriesEpisodePicker()
        }
        if (_uiState.value.activeCatalogSelection != null) {
            closeCatalog()
        }
        if (_uiState.value.selectedTab != AbyssNavTab.HOME) {
            selectTab(AbyssNavTab.HOME)
        }
    }

    fun onActivityStopped() {
        endActiveSession()
    }

    fun onActivityResumed() {
        val jfConfig = _uiState.value.jellyfinConfig
        if (jfConfig.isConfigured) {
            if (jfConfig.accessToken.isNotBlank()) {
                jellyfinWebSocketClient.reconnectIfNeeded()
            }
            viewModelScope.launch(Dispatchers.IO) {
                val resumeItems = runCatching { JellyfinRepository.fetchResumeAndNextUp(jfConfig) }.getOrNull()
                if (resumeItems != null) {
                    rawJellyfinResumeItems = resumeItems
                    updateLibraryState { it }
                }
            }
        }
        val seerConfig = _uiState.value.seerConfig
        if (seerConfig.isConfigured) {
            val lastSync = seerConfig.lastSyncTimestamp
            if (System.currentTimeMillis() - lastSync > 600_000L) {
                syncSeerData(seerConfig)
            }
        }
    }

    fun requestMediaOnSeer(item: MediaItem) {
        val config = _uiState.value.seerConfig
        val tmdbId = item.tmdbId ?: return
        val isTv = item.category == MediaCategory.SERIES

        viewModelScope.launch(Dispatchers.IO) {
            val res = SeerRepository.requestMedia(config, tmdbId, isTv)
            withContext(Dispatchers.Main) {
                if (res.isSuccess) {
                    showStatusMessage(R.string.msg_seer_request_success, durationMs = 3500L)
                    closeMediaDetails()
                    syncSeerData(config)
                } else {
                    val errMsg = res.exceptionOrNull()?.message
                    if (errMsg != null) {
                        showStatusMessage(errMsg, 4000L)
                    } else {
                        showStatusMessage(R.string.msg_seer_request_error, durationMs = 4000L)
                    }
                }
            }
        }
    }

    fun removeFromContinueWatching(item: MediaItem) {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PlaybackHistoryRepository.clearEntry(ctx, item.id)
            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            rawJellyfinResumeItems = rawJellyfinResumeItems.filter { it.id != item.id }
            updateLibraryState { current ->
                current.copy(
                    selectedMediaForMenu = null,
                    playbackHistory = updatedHistory
                )
            }
            showStatusMessage(R.string.msg_removed_continue, durationMs = 2500L)
        }
    }

    fun toggleMediaWatchedStatus(item: MediaItem) {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val hist = PlaybackHistoryRepository.getEntry(ctx, item.id)
            val isWatched = (hist?.progressPercent ?: 0f) >= 0.95f
            val jfConfig = _uiState.value.jellyfinConfig

            val jfId = item.jellyfinId.ifBlank { item.id }
            PlaybackHistoryRepository.clearEntry(ctx, item.id)
            rawJellyfinResumeItems = rawJellyfinResumeItems.filter { it.id != jfId && it.jellyfinId != jfId }
            runCatching {
                if (isWatched) JellyfinRepository.markUnwatched(jfConfig, jfId)
                else JellyfinRepository.markWatched(jfConfig, jfId)
            }.onFailure { Log.w("AbyssWatched", "Jellyfin watched toggle failed for $jfId", it) }
            showStatusMessage(
                if (isWatched) R.string.msg_marked_single_unwatched else R.string.msg_marked_single_watched,
                durationMs = 2500L
            )

            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            val watched = !isWatched
            updateLibraryState { current ->
                current.copy(
                    selectedMediaForMenu = null,
                    playbackHistory = updatedHistory,
                    movies = current.movies.map {
                        if (it.id == item.id || it.jellyfinId == jfId) it.copy(progressPercent = if (watched) 1f else 0f) else it
                    },
                    series = current.series.map {
                        if (it.id == item.id || it.jellyfinId == jfId) it.copy(progressPercent = if (watched) 1f else 0f) else it
                    }
                )
            }
        }
    }

    fun toggleWatchlist(item: MediaItem) {
        val currentIds = _uiState.value.watchlistIds.toMutableSet()
        val isAdded = if (currentIds.contains(item.id)) {
            currentIds.remove(item.id)
            false
        } else {
            currentIds.add(item.id)
            true
        }
        prefs?.watchlistIds = currentIds
        _uiState.update { current ->
            current.copy(
                watchlistIds = currentIds,
                watchlistItems = (current.movies + current.series).filter { currentIds.contains(it.id) }
            )
        }
        showStatusMessage(if (isAdded) R.string.msg_added_watchlist else R.string.msg_removed_watchlist, durationMs = 2000L)
    }

    fun updateHeroIndex(newIndex: Int) {
        _uiState.update { it.copy(heroIndex = newIndex) }
    }

    fun advanceHeroSpotlight() {
        val total = _uiState.value.spotlightCandidates.size
        if (total > 1) {
            _uiState.update { it.copy(heroIndex = (it.heroIndex + 1) % total) }
        }
    }

    fun markEpisodesUpToWatched(
        seriesItem: MediaItem,
        allEpisodes: List<SeriesEpisode>,
        targetEpisode: SeriesEpisode
    ) {
        val ctx = appContext ?: return
        val jfConfig = _uiState.value.jellyfinConfig

        viewModelScope.launch(Dispatchers.IO) {
            PlaybackHistoryRepository.markEpisodesUpToWatched(
                context = ctx,
                seriesId = seriesItem.id,
                allEpisodes = allEpisodes,
                targetEpisode = targetEpisode,
                title = seriesItem.title(),
                posterUrl = seriesItem.posterUrl,
                backdropUrl = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                category = MediaCategory.SERIES.name
            )
            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            updateLibraryState { current ->
                current.copy(
                    movies = applyHistoryToItems(current.movies, updatedHistory),
                    series = applyHistoryToItems(current.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
            }

            showStatusMessage(R.string.msg_all_prev_watched, durationMs = 2500L)

            val targetIdx = allEpisodes.indexOfFirst { it.id == targetEpisode.id }
            if (targetIdx != -1) {
                setJellyfinWatched(jfConfig, allEpisodes.take(targetIdx + 1).map { it.id }, true)
            }
        }
    }

    fun toggleSingleEpisodeWatched(
        seriesItem: MediaItem,
        allEpisodes: List<SeriesEpisode>,
        targetEpisode: SeriesEpisode
    ) {
        val ctx = appContext ?: return
        val jfConfig = _uiState.value.jellyfinConfig

        viewModelScope.launch(Dispatchers.IO) {
            val targetEntry = PlaybackHistoryRepository.getEntry(ctx, targetEpisode.id)
            val isCurrentlyWatched = (targetEntry?.progressPercent ?: 0f) >= 0.95f ||
                    (targetEpisode.progressPercent >= 0.95f && targetEntry == null)
            if (isCurrentlyWatched) {
                PlaybackHistoryRepository.unmarkEpisodeWatched(
                    context = ctx,
                    seriesId = seriesItem.id,
                    allEpisodes = allEpisodes,
                    targetEpisode = targetEpisode,
                    title = seriesItem.title(),
                    posterUrl = seriesItem.posterUrl,
                    backdropUrl = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                    category = MediaCategory.SERIES.name
                )
            } else {
                PlaybackHistoryRepository.markSingleEpisodeWatched(
                    context = ctx,
                    seriesId = seriesItem.id,
                    allEpisodes = allEpisodes,
                    targetEpisode = targetEpisode,
                    title = seriesItem.title(),
                    posterUrl = seriesItem.posterUrl,
                    backdropUrl = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                    category = MediaCategory.SERIES.name
                )
            }

            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            updateLibraryState { current ->
                current.copy(
                    movies = applyHistoryToItems(current.movies, updatedHistory),
                    series = applyHistoryToItems(current.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
            }
            val epCode = "S${targetEpisode.seasonNumber.toString().padStart(2, '0')}E${targetEpisode.episodeNumber.toString().padStart(2, '0')}"
            val resId = if (isCurrentlyWatched) R.string.msg_marked_unwatched else R.string.msg_marked_watched
            showStatusMessage(resId, epCode, durationMs = 2500L)

            setJellyfinWatched(jfConfig, listOf(targetEpisode.id), !isCurrentlyWatched)
        }
    }

    fun unmarkEpisodesFrom(
        seriesItem: MediaItem,
        allEpisodes: List<SeriesEpisode>,
        targetEpisode: SeriesEpisode
    ) {
        val ctx = appContext ?: return
        val jfConfig = _uiState.value.jellyfinConfig

        viewModelScope.launch(Dispatchers.IO) {
            PlaybackHistoryRepository.unmarkEpisodesFrom(
                context = ctx,
                seriesId = seriesItem.id,
                allEpisodes = allEpisodes,
                targetEpisode = targetEpisode,
                title = seriesItem.title(),
                posterUrl = seriesItem.posterUrl,
                backdropUrl = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                category = MediaCategory.SERIES.name
            )

            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            updateLibraryState { current ->
                current.copy(
                    movies = applyHistoryToItems(current.movies, updatedHistory),
                    series = applyHistoryToItems(current.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
            }

            showStatusMessage(R.string.msg_all_following_unwatched, durationMs = 2500L)

            val targetIdx = allEpisodes.indexOfFirst { it.id == targetEpisode.id }
            if (targetIdx != -1) {
                setJellyfinWatched(jfConfig, allEpisodes.drop(targetIdx).map { it.id }, false)
            }
        }
    }

    fun setWeatherCity(newCity: String) {
        val city = newCity.trim().ifBlank { "Paris" }
        prefs?.weatherCity = city
        _uiState.update { it.copy(weatherCity = city) }
        WeatherRepository.clearCache()
        fetchWeather(city)
    }

    fun setTemperatureUnit(unit: dev.dylanakp.abysstvlauncher.model.TemperatureUnit) {
        prefs?.temperatureUnit = unit
        _uiState.update { it.copy(temperatureUnit = unit) }
    }

    private var weatherJob: Job? = null

    private fun fetchWeather(city: String) {
        viewModelScope.launch {
            val info = WeatherRepository.getWeather(city)
            _uiState.update { it.copy(weatherInfo = info) }
        }
    }

    private fun startWeatherLoop() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            while (isActive) {
                delay(30 * 60 * 1000L)
                val city = prefs?.weatherCity ?: "Paris"
                val info = WeatherRepository.getWeather(city)
                _uiState.update { it.copy(weatherInfo = info) }
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        prefs?.language = lang
        _uiState.update { it.copy(language = lang) }
        viewModelScope.launch {
            SeerRepository.clearCache()
            appContext?.let { MediaLibraryCache.clear(it) }
            syncJellyfinLibrary(_uiState.value.jellyfinConfig, showToast = false)
            syncSeerData(_uiState.value.seerConfig)
        }
    }

    fun setPlayerPreference(pref: PlayerPreference) {
        val updated = _uiState.value.jellyfinConfig.copy(playerPreference = pref)
        prefs?.jellyfinConfig = updated
        _uiState.update { it.copy(jellyfinConfig = updated) }
    }

    fun setTrackPreference(pref: TrackPreference) {
        prefs?.trackPreference = pref
        _uiState.update { it.copy(trackPreference = pref) }
    }

    fun setShowHiddenApps(show: Boolean) {
        prefs?.showHiddenApps = show
        _uiState.update { it.copy(showHiddenApps = show) }
    }

    fun pinApp(packageName: String) {
        val currentPinned = _uiState.value.allApps.filter { it.isPinned }.map { it.packageName }.toMutableList()
        if (!currentPinned.contains(packageName)) {
            currentPinned.add(packageName)
            prefs?.pinnedPackages = currentPinned
            refreshAppsList()
            showStatusMessage(R.string.msg_app_pinned, durationMs = 2000L)
        }
    }

    fun unpinApp(packageName: String) {
        val currentPinned = _uiState.value.allApps.filter { it.isPinned }.map { it.packageName }.toMutableList()
        if (currentPinned.remove(packageName)) {
            prefs?.pinnedPackages = currentPinned
            refreshAppsList()
            showStatusMessage(R.string.msg_app_unpinned, durationMs = 2000L)
        }
    }

    fun hideApp(packageName: String) {
        val currentHidden = _uiState.value.allApps.filter { it.isHidden }.map { it.packageName }.toMutableSet()
        currentHidden.add(packageName)
        prefs?.hiddenPackages = currentHidden
        refreshAppsList()
        showStatusMessage(R.string.msg_app_hidden, durationMs = 2000L)
    }

    fun unhideApp(packageName: String) {
        val currentHidden = _uiState.value.allApps.filter { it.isHidden }.map { it.packageName }.toMutableSet()
        if (currentHidden.remove(packageName)) {
            prefs?.hiddenPackages = currentHidden
            refreshAppsList()
            showStatusMessage(R.string.msg_app_unhidden, durationMs = 2000L)
        }
    }

    fun openAppContextMenu(app: TvAppInfo) {
        _uiState.update { it.copy(selectedAppForMenu = app) }
    }

    fun closeAppContextMenu() {
        _uiState.update { it.copy(selectedAppForMenu = null) }
    }

    fun openCatalog(selection: dev.dylanakp.abysstvlauncher.ui.components.CatalogSelection) {
        _uiState.update { it.copy(activeCatalogSelection = selection) }
    }

    fun openCatalog(title: String, items: List<MediaItem>, isLive: Boolean = false) {
        _uiState.update {
            it.copy(
                activeCatalogSelection = dev.dylanakp.abysstvlauncher.ui.components.CatalogSelection(
                    title,
                    items,
                    isLive
                )
            )
        }
    }

    fun openFullCategory(title: String, categoryKey: String, isLive: Boolean = false) {
        val items = when (categoryKey) {
            "movies_top" -> _uiState.value.moviesTop100
            "movies_nouv" -> _uiState.value.moviesNouveautes
            "series_top" -> _uiState.value.seriesTop100
            "series_nouv" -> _uiState.value.seriesNouveautes
            "seer_trending" -> _uiState.value.seerTrending
            "seer_movies" -> _uiState.value.seerPopularMovies
            "seer_tv" -> _uiState.value.seerPopularTv
            else -> _uiState.value.movies
        }
        openCatalog(title, items, isLive)
    }

    fun closeCatalog() {
        _uiState.update { it.copy(activeCatalogSelection = null) }
    }

    private fun refreshAppsList() {
        val ctx = appContext ?: return
        val preferences = prefs ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val apps = AppRepository.loadInstalledApps(
                ctx,
                preferences.pinnedPackages,
                preferences.hiddenPackages
            )
            val pinned = apps.filter { it.isPinned && !it.isHidden }.sortedBy { it.pinOrder }
            val visible = apps.filter { !it.isHidden }
            val hiddenList = apps.filter { it.isHidden }
            _uiState.update {
                it.copy(
                    allApps = apps,
                    pinnedApps = pinned,
                    visibleApps = visible,
                    hiddenAppsList = hiddenList
                )
            }
        }
    }

    fun updateSearchQuery(query: String) = searchDelegate.updateSearchQuery(query)

    fun commitSearchQuery(query: String) = searchDelegate.commitSearchQuery(query)

    fun clearSearchHistory() = searchDelegate.clearSearchHistory()

    fun removeSearchHistoryItem(item: String) = searchDelegate.removeSearchHistoryItem(item)

    fun showStatusMessage(resId: Int, vararg args: Any, durationMs: Long = 2500L) {
        showStatusMessage(UiMessage.Resource(resId, args.toList()), durationMs)
    }

    fun showStatusMessage(message: String, durationMs: Long = 2500L) {
        showStatusMessage(UiMessage.Text(message), durationMs)
    }

    private var statusMessageJob: Job? = null
    private var jellyfinDisplayMessageJob: Job? = null

    fun showJellyfinDisplayMessage(msg: JellyfinDisplayMessage) {
        jellyfinDisplayMessageJob?.cancel()
        jellyfinDisplayMessageJob = viewModelScope.launch {
            _uiState.update { it.copy(jellyfinDisplayMessage = msg) }
            delay(msg.timeoutMs)
            _uiState.update {
                if (it.jellyfinDisplayMessage?.id == msg.id) it.copy(jellyfinDisplayMessage = null) else it
            }
        }
    }

    fun dismissJellyfinDisplayMessage() {
        jellyfinDisplayMessageJob?.cancel()
        _uiState.update { it.copy(jellyfinDisplayMessage = null) }
    }

    private fun handleJellyfinPlaystate(event: JellyfinPlaystateEvent) {
        if (_uiState.value.activePlayerSession == null) return
        when (event.command) {
            JellyfinPlaystateCommand.PAUSE -> playerManager.pause()
            JellyfinPlaystateCommand.UNPAUSE -> playerManager.play()
            JellyfinPlaystateCommand.PLAY_PAUSE -> playerManager.togglePlayPause()
            JellyfinPlaystateCommand.SEEK -> event.seekPositionMs?.let { playerManager.seekTo(it) }
            JellyfinPlaystateCommand.FAST_FORWARD -> playerManager.seekBy(10_000L)
            JellyfinPlaystateCommand.REWIND -> playerManager.seekBy(-10_000L)
            JellyfinPlaystateCommand.STOP -> {
                val posMs = playerManager.positionState.value.currentPositionMs
                val durMs = playerManager.state.value.durationMs
                closePlayer(posMs, durMs)
            }
        }
    }

    fun showStatusMessage(msg: UiMessage, durationMs: Long = 2500L) {
        statusMessageJob?.cancel()
        statusMessageJob = viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = msg) }
            delay(durationMs)
            _uiState.update {
                if (it.statusBannerMessage == msg) it.copy(statusBannerMessage = null) else it
            }
        }
    }

    private fun registerNetworkSyncCallback(ctx: Context) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val state = _uiState.value
                if (state.jellyfinConfig.isConfigured) {
                    if (state.jellyfinConfig.accessToken.isNotBlank()) {
                        jellyfinWebSocketClient.reconnectIfNeeded()
                    }
                    if (!hasSyncedJellyfinThisSession && !state.isSyncingJellyfin) {
                        syncJellyfinLibrary(state.jellyfinConfig, showToast = false)
                    }
                }
                if (state.seerConfig.isConfigured && !hasSyncedSeerThisSession) {
                    syncSeerData(state.seerConfig)
                }
                if (state.weatherInfo == null) {
                    fetchWeather(state.weatherCity)
                }
            }
        }
        networkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    override fun onCleared() {
        super.onCleared()
        jellyfinWebSocketClient.disconnect()
        networkCallback?.let { cb ->
            val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
    }

    fun checkForUpdates(silent: Boolean = false) = updateDelegate.checkForUpdates(silent)

    fun dismissUpdate() = updateDelegate.dismissUpdate()

    fun downloadAndInstallUpdate(context: Context) = updateDelegate.downloadAndInstallUpdate(context)
}
