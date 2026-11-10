package dev.dylanakp.abysstvlauncher.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.data.AppRepository
import dev.dylanakp.abysstvlauncher.data.AppUpdate
import dev.dylanakp.abysstvlauncher.data.AppUpdateRepository
import dev.dylanakp.abysstvlauncher.data.JellyfinRepository
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
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
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.ui.player.PlayerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Built-in Player & Series Episode Picker state
    val activePlayerSession: PlayerSession? = null,
    val selectedSeriesForEpisodes: MediaItem? = null,
    val activeSeriesDetails: SeriesDetails? = null,
    val isLoadingSeriesDetails: Boolean = false,

    val allApps: List<TvAppInfo> = emptyList(),
    val selectedAppForMenu: TvAppInfo? = null,
    val selectedMediaForMenu: MediaItem? = null,

    val isSettingsDrawerOpen: Boolean = false,
    val weatherCity: String = "Paris",
    val temperatureUnit: dev.dylanakp.abysstvlauncher.model.TemperatureUnit = dev.dylanakp.abysstvlauncher.model.TemperatureUnit.CELSIUS,
    val weatherInfo: WeatherInfo? = null,
    val jellyfinConfig: JellyfinConfig = JellyfinConfig(),
    val seerConfig: SeerConfig = SeerConfig(),
    val isSyncingJellyfin: Boolean = false,
    val isTestingSeer: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val statusBannerMessage: String? = null,
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

    val pinnedApps: List<TvAppInfo>
        get() = allApps.filter { it.isPinned && !it.isHidden }.sortedBy { it.pinOrder }

    val visibleApps: List<TvAppInfo>
        get() = allApps.filter { !it.isHidden }

    val hiddenAppsList: List<TvAppInfo>
        get() = allApps.filter { it.isHidden }

    val watchlistItems: List<MediaItem>
        get() = (movies + series).filter { watchlistIds.contains(it.id) }
}

class LauncherViewModel : ViewModel() {

    private var prefs: LauncherPreferences? = null
    private var appContext: Context? = null
    private var isInitialized = false
    private var needsAppRefreshOnResume = false
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private fun getString(@StringRes resId: Int, vararg args: Any): String {
        return appContext?.getString(resId, *args).orEmpty()
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext
        val preferences = prefs ?: LauncherPreferences(context).also { prefs = it }

        val lang = preferences.language
        applyLanguage(lang)

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

        // 1. Parallel load of local disk cache, playback history, and installed apps on IO
        viewModelScope.launch(Dispatchers.IO) {
            val cachedDeferred = async { MediaLibraryCache.loadSync(context) }
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
                watchlistIds = preferences.watchlistIds,
                playbackHistory = history,
                searchHistory = preferences.searchHistory,
                seerTrending = cached?.seerTrending.orEmpty(),
                seerPopularMovies = cached?.seerPopularMovies.orEmpty(),
                seerPopularTv = cached?.seerPopularTv.orEmpty(),
                seerRequests = cached?.seerRequests.orEmpty()
            )

            val derived = withContext(Dispatchers.Default) {
                recomputeDerivedState(
                    current = baseState,
                    movies = enrichedMovies.ifEmpty { baseState.movies },
                    series = enrichedSeries.ifEmpty { baseState.series },
                    tmdbTrending = cached?.tmdbTrending ?: baseState.tmdbTrending,
                    playbackHistory = history
                )
            }
            _uiState.value = derived

            // 2. Auto-sync Jellyfin library if configured
            if (jfConfig.isConfigured) {
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
                        withContext(Dispatchers.Default) {
                            val updated = recomputeDerivedState(
                                current = _uiState.value,
                                tmdbTrending = enrichedTrending
                            )
                            _uiState.value = updated
                        }
                    }
                } catch (_: Exception) {}
            }

            // 5. Check for app updates on boot
            checkForUpdates(silent = true)
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

    private fun recomputeDerivedState(
        current: LauncherUiState,
        movies: List<MediaItem> = current.movies,
        series: List<MediaItem> = current.series,
        tmdbTrending: List<MediaItem> = current.tmdbTrending,
        playbackHistory: Map<String, PlaybackHistoryEntry> = current.playbackHistory,
        selectedTab: AbyssNavTab = current.selectedTab
    ): LauncherUiState {
        val continueWatching = if (playbackHistory.isEmpty() && current.continueWatchingItems.isEmpty()) {
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

            // Combine with Jellyfin direct resume/next-up items (Jellyfin items take priority)
            val jfResume = current.continueWatchingItems
            (jfResume + fromHistory).distinctBy { it.id }
        }

        val continueMovies = continueWatching.filter { it.category == MediaCategory.MOVIE }
        val continueSeries = continueWatching.filter { it.category == MediaCategory.SERIES }

        val homeMov = movies.take(25)
        val homeSer = series.take(25)

        val movTop100 = movies.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(25)
        val movNouv = movies.take(25)
        val serTop100 = series.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(25)
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

        return current.copy(
            selectedTab = selectedTab,
            movies = movies,
            series = series,
            tmdbTrending = tmdbTrending,
            playbackHistory = playbackHistory,
            continueWatchingItems = continueWatching,
            continueWatchingMovies = continueMovies,
            continueWatchingSeries = continueSeries,
            homeMovies = homeMov,
            homeSeries = homeSer,
            moviesTop100 = movTop100,
            moviesNouveautes = movNouv,
            seriesTop100 = serTop100,
            seriesNouveautes = serNouv,
            spotlightCandidates = candidates
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
                    TmdbRepository.titlesMatchSafely(it.title, item.title) ||
                    TmdbRepository.titlesMatchSafely(it.localizedTitle, item.localizedTitle)
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
                    TmdbRepository.titlesMatchSafely(it.title, item.title) ||
                    TmdbRepository.titlesMatchSafely(it.localizedTitle, item.localizedTitle)
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

        if (playableMovieItem.streamUrl.isNotBlank()) {
            launchMoviePlayback(context, playableMovieItem)
        } else {
            openMediaDetails(item)
        }
    }

    private fun launchMoviePlayback(context: Context, item: MediaItem) {
        val state = _uiState.value
        val safeStreamUrl = JellyfinRepository.sanitizeStreamUrl(item.streamUrl, state.jellyfinConfig)
        val safeItem = item.copy(streamUrl = safeStreamUrl)

        if (state.jellyfinConfig.playerPreference != PlayerPreference.ABYSS_INTERNAL) {
            val launched = SystemAppHelper.playMediaExternal(context, safeItem, state.jellyfinConfig.playerPreference)
            if (launched) return
        }

        val savedEntry = PlaybackHistoryRepository.getEntry(context, safeItem.id)
        val savedPos = if (savedEntry != null && savedEntry.progressPercent < 0.95f) {
            savedEntry.positionMs
        } else 0L

        val session = PlayerSession(
            mediaItem = safeItem,
            streamUrl = safeItem.streamUrl,
            title = safeItem.title(),
            subtitle = listOf(safeItem.year, safeItem.groupTitle()).filter { it.isNotBlank() }.joinToString(" • "),
            startPositionMs = 0L,
            savedResumePositionMs = savedPos
        )

        // Jellyfin playback probe & start report
        viewModelScope.launch(Dispatchers.IO) {
            val jfId = safeItem.jellyfinId.ifBlank { safeItem.id }
            JellyfinRepository.preparePlayback(state.jellyfinConfig, jfId)
            JellyfinRepository.reportPlaybackStart(state.jellyfinConfig, jfId, savedPos)
        }

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
        viewModelScope.launch(Dispatchers.Default) {
            val derived = recomputeDerivedState(
                current = _uiState.value.copy(activePlayerSession = session),
                movies = applyHistoryToItems(_uiState.value.movies, updatedHistory),
                series = applyHistoryToItems(_uiState.value.series, updatedHistory),
                playbackHistory = updatedHistory
            )
            _uiState.value = derived
        }
    }

    fun openSeriesEpisodePicker(seriesItem: MediaItem) {
        _uiState.update {
            it.copy(
                selectedSeriesForEpisodes = seriesItem,
                activeSeriesDetails = null,
                isLoadingSeriesDetails = true
            )
        }
        viewModelScope.launch {
            val details = if (_uiState.value.jellyfinConfig.isConfigured) {
                JellyfinRepository.fetchSeriesDetails(_uiState.value.jellyfinConfig, seriesItem)
            } else null

            val resolvedDetails = details ?: run {
                seriesItem.tmdbId?.let { tmdbId ->
                    TmdbRepository.fetchSeriesDetailsFromTmdb(tmdbId, seriesItem)
                }
            }

            _uiState.update {
                it.copy(
                    activeSeriesDetails = resolvedDetails,
                    isLoadingSeriesDetails = false
                )
            }
        }
    }

    fun closeSeriesEpisodePicker() {
        _uiState.update {
            it.copy(
                selectedSeriesForEpisodes = null,
                activeSeriesDetails = null,
                isLoadingSeriesDetails = false
            )
        }
    }

    fun playSeriesEpisode(
        seriesItem: MediaItem,
        episode: SeriesEpisode,
        nextEpisode: SeriesEpisode?,
        startPositionMs: Long
    ) {
        val config = _uiState.value.jellyfinConfig
        val safeStreamUrl = JellyfinRepository.sanitizeStreamUrl(episode.streamUrl, config)
        val safeEpisode = episode.copy(streamUrl = safeStreamUrl)
        val epCode = "S${safeEpisode.seasonNumber.toString().padStart(2, '0')}E${safeEpisode.episodeNumber.toString().padStart(2, '0')}"
        val session = PlayerSession(
            mediaItem = seriesItem.copy(id = safeEpisode.id, streamUrl = safeEpisode.streamUrl),
            streamUrl = safeEpisode.streamUrl,
            title = "${seriesItem.title()} — $epCode",
            subtitle = safeEpisode.title,
            startPositionMs = 0L,
            savedResumePositionMs = startPositionMs,
            seriesId = seriesItem.id,
            seasonNumber = safeEpisode.seasonNumber,
            episodeNumber = safeEpisode.episodeNumber,
            nextEpisode = nextEpisode
        )

        // Jellyfin playback probe & start report
        viewModelScope.launch(Dispatchers.IO) {
            JellyfinRepository.preparePlayback(config, safeEpisode.id)
            JellyfinRepository.reportPlaybackStart(config, safeEpisode.id, startPositionMs)
        }

        appContext?.let { ctx ->
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
            viewModelScope.launch(Dispatchers.Default) {
                val derived = recomputeDerivedState(
                    current = _uiState.value.copy(activePlayerSession = session),
                    movies = applyHistoryToItems(_uiState.value.movies, updatedHistory),
                    series = applyHistoryToItems(_uiState.value.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
                _uiState.value = derived
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
                    showStatusMessage(getString(R.string.msg_series_complete), 3000L)
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
                showStatusMessage(getString(R.string.msg_playing_next, nextCode), 2500L)
                playSeriesEpisode(resolvedSeriesItem, targetNext, followingEp, 0L)
            }
        }
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        val ctx = appContext ?: return
        val session = _uiState.value.activePlayerSession ?: return
        if (durationMs <= 0L && positionMs <= 0L) return

        val itemId = session.mediaItem.id

        // Report to Jellyfin server
        viewModelScope.launch(Dispatchers.IO) {
            JellyfinRepository.reportPlaybackProgress(_uiState.value.jellyfinConfig, itemId, positionMs, isPaused = false)
        }

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
        val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
        viewModelScope.launch(Dispatchers.Default) {
            val derived = recomputeDerivedState(
                current = _uiState.value,
                movies = applyHistoryToItems(_uiState.value.movies, updatedHistory),
                series = applyHistoryToItems(_uiState.value.series, updatedHistory),
                playbackHistory = updatedHistory
            )
            _uiState.value = derived
        }
    }

    fun closePlayer(positionMs: Long, durationMs: Long) {
        val ctx = appContext
        val session = _uiState.value.activePlayerSession
        if (ctx != null && session != null && (durationMs > 0L || positionMs > 0L)) {
            val itemId = session.mediaItem.id

            // Report stopped to Jellyfin server
            viewModelScope.launch(Dispatchers.IO) {
                JellyfinRepository.reportPlaybackStopped(_uiState.value.jellyfinConfig, itemId, positionMs)
            }

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
            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            val derived = recomputeDerivedState(
                current = _uiState.value.copy(activePlayerSession = null),
                movies = applyHistoryToItems(_uiState.value.movies, updatedHistory),
                series = applyHistoryToItems(_uiState.value.series, updatedHistory),
                playbackHistory = updatedHistory
            )
            _uiState.value = derived
        } else {
            _uiState.update { it.copy(activePlayerSession = null) }
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
        _uiState.update { it.copy(jellyfinConfig = updated) }
        syncJellyfinLibrary(updated, showToast = true)
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
                    _uiState.update { it.copy(jellyfinConfig = authed) }
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

            // 2. Sync Library items
            var syncRes = JellyfinRepository.syncLibrary(activeConfig)

            // Auto-heal: If token expired (401) or sync failed, re-authenticate with credentials and retry
            if ((syncRes.isFailure || (syncRes.isSuccess && syncRes.getOrThrow().movies.isEmpty() && syncRes.getOrThrow().series.isEmpty()))
                && activeConfig.username.isNotBlank() && activeConfig.password.isNotBlank()
            ) {
                Log.i("AbyssJellyfin", "Sync failed or empty; attempting re-authentication with server...")
                val reAuthRes = JellyfinRepository.authenticate(activeConfig.copy(accessToken = ""))
                if (reAuthRes.isSuccess) {
                    activeConfig = reAuthRes.getOrThrow()
                    prefs?.jellyfinConfig = activeConfig
                    _uiState.update { it.copy(jellyfinConfig = activeConfig) }
                    syncRes = JellyfinRepository.syncLibrary(activeConfig)
                }
            }

            val resumeItems = JellyfinRepository.fetchResumeAndNextUp(activeConfig)

            if (syncRes.isSuccess) {
                val lib = syncRes.getOrThrow()
                if (lib.movies.isEmpty() && lib.series.isEmpty() && (_uiState.value.movies.isNotEmpty() || _uiState.value.series.isNotEmpty())) {
                    Log.w("AbyssJellyfin", "Sync returned empty items; preserving existing cached library")
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

                withContext(Dispatchers.Default) {
                    val derived = recomputeDerivedState(
                        current = _uiState.value.copy(
                            jellyfinConfig = updatedConfig,
                            isSyncingJellyfin = false,
                            continueWatchingItems = resumeItems
                        ),
                        movies = lib.movies,
                        series = lib.series
                    )
                    _uiState.value = derived
                }
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        showStatusMessage(getString(R.string.msg_sync_success), 3500L)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSyncingJellyfin = false) }
                    if (showToast) {
                        showStatusMessage(getString(R.string.msg_sync_error), 3500L)
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
        val safeTab = if (!updated.isConfigured && _uiState.value.selectedTab == AbyssNavTab.DISCOVER) AbyssNavTab.HOME else _uiState.value.selectedTab
        _uiState.update { it.copy(seerConfig = updated, isTestingSeer = true, selectedTab = safeTab) }

        viewModelScope.launch(Dispatchers.IO) {
            val testRes = SeerRepository.testConnection(updated)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isTestingSeer = false) }
                if (testRes.isSuccess) {
                    val ver = testRes.getOrThrow()
                    showStatusMessage(getString(R.string.msg_seer_connected, ver), 3000L)
                    syncSeerData(updated)
                } else {
                    showStatusMessage(getString(R.string.msg_seer_failed), 3500L)
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
                    _uiState.update { it.copy(seerConfig = authed) }
                    authed
                } else config
            } else config

            if (activeConfig.apiKey.isBlank()) {
                Log.w("AbyssSeer", "Seer apiKey is blank and auto-auth not completed")
                return@launch
            }

            Log.d("AbyssSeer", "Syncing Seer data from ${activeConfig.serverUrl}...")
            var trendingRes = runCatching { SeerRepository.fetchTrending(activeConfig) }
            var popMoviesRes = runCatching { SeerRepository.fetchDiscoverMovies(activeConfig) }
            var popTvRes = runCatching { SeerRepository.fetchDiscoverTv(activeConfig) }
            var requestsRes = runCatching { SeerRepository.fetchRequests(activeConfig) }

            // Auto-heal: If auth failed or results are empty and we have Jellyfin credentials, re-authenticate via Jellyfin
            val isAuthFailed = listOf(trendingRes, popMoviesRes, popTvRes, requestsRes).any {
                it.isFailure && it.exceptionOrNull() is SecurityException
            } || (trendingRes.getOrNull().isNullOrEmpty() && popMoviesRes.getOrNull().isNullOrEmpty() && jf.isConfigured && jf.username.isNotBlank())

            if (isAuthFailed && jf.isConfigured && jf.username.isNotBlank()) {
                Log.i("AbyssSeer", "Seer session expired or empty; re-authenticating with Jellyfin credentials...")
                val reAuthRes = SeerRepository.authenticateWithJellyfin(activeConfig.serverUrl, jf.username, jf.password)
                if (reAuthRes.isSuccess) {
                    val newKey = reAuthRes.getOrThrow()
                    activeConfig = activeConfig.copy(apiKey = newKey)
                    prefs?.seerConfig = activeConfig
                    _uiState.update { it.copy(seerConfig = activeConfig) }

                    trendingRes = runCatching { SeerRepository.fetchTrending(activeConfig) }
                    popMoviesRes = runCatching { SeerRepository.fetchDiscoverMovies(activeConfig) }
                    popTvRes = runCatching { SeerRepository.fetchDiscoverTv(activeConfig) }
                    requestsRes = runCatching { SeerRepository.fetchRequests(activeConfig) }
                }
            }

            val trending = trendingRes.getOrDefault(emptyList())
            val popMovies = popMoviesRes.getOrDefault(emptyList())
            val popTv = popTvRes.getOrDefault(emptyList())
            val requests = requestsRes.getOrDefault(emptyList())

            Log.d("AbyssSeer", "Seer sync complete: ${trending.size} trending, ${popMovies.size} pop movies, ${popTv.size} pop tv, ${requests.size} requests")

            // Guard against wiping out local cached Seer items if network temporarily failed
            if (trending.isEmpty() && popMovies.isEmpty() && popTv.isEmpty() && _uiState.value.seerTrending.isNotEmpty()) {
                Log.w("AbyssSeer", "Seer sync returned empty; preserving existing cached items")
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
                val currentLib = MediaLibraryCache.loadSync(ctx)
                val updatedLib = (currentLib ?: SyncedLibrary()).copy(
                    seerTrending = enrichedTrending,
                    seerPopularMovies = enrichedPopMovies,
                    seerPopularTv = enrichedPopTv,
                    seerRequests = requests
                )
                MediaLibraryCache.save(ctx, updatedLib)
            }

            withContext(Dispatchers.Default) {
                val updatedState = _uiState.value.copy(
                    seerConfig = updatedConfig,
                    seerTrending = enrichedTrending,
                    seerPopularMovies = enrichedPopMovies,
                    seerPopularTv = enrichedPopTv,
                    seerRequests = requests
                )
                val derived = recomputeDerivedState(updatedState)
                _uiState.value = derived
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
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(allApps = apps) }
            }
        }
    }

    fun handleHomePressed() {
        val currentSession = _uiState.value.activePlayerSession
        if (currentSession != null) {
            _uiState.update { it.copy(activePlayerSession = null) }
        }
        if (_uiState.value.isSearchActive) {
            setSearchActive(false)
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
        val currentSession = _uiState.value.activePlayerSession
        if (currentSession != null) {
            _uiState.update { it.copy(activePlayerSession = null) }
        }
    }

    fun onActivityResumed() {
        val jfConfig = _uiState.value.jellyfinConfig
        if (jfConfig.isConfigured) {
            viewModelScope.launch(Dispatchers.IO) {
                val resumeItems = JellyfinRepository.fetchResumeAndNextUp(jfConfig)
                if (resumeItems.isNotEmpty()) {
                    _uiState.update { it.copy(continueWatchingItems = resumeItems) }
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
                    showStatusMessage(getString(R.string.msg_seer_request_success), 3500L)
                    closeMediaDetails()
                    syncSeerData(config)
                } else {
                    val err = res.exceptionOrNull()?.message ?: getString(R.string.msg_seer_request_error)
                    showStatusMessage(err, 4000L)
                }
            }
        }
    }

    fun removeFromContinueWatching(item: MediaItem) {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PlaybackHistoryRepository.clearEntry(ctx, item.id)
            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            val filteredJfResume = _uiState.value.continueWatchingItems.filter { it.id != item.id }

            withContext(Dispatchers.Default) {
                val derived = recomputeDerivedState(
                    current = _uiState.value.copy(
                        selectedMediaForMenu = null,
                        continueWatchingItems = filteredJfResume
                    ),
                    playbackHistory = updatedHistory
                )
                _uiState.value = derived
            }
            showStatusMessage(getString(R.string.msg_removed_continue), 2500L)
        }
    }

    fun toggleMediaWatchedStatus(item: MediaItem) {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val hist = PlaybackHistoryRepository.getEntry(ctx, item.id)
            val isWatched = (hist?.progressPercent ?: 0f) >= 0.95f
            val jfConfig = _uiState.value.jellyfinConfig

            if (isWatched) {
                PlaybackHistoryRepository.clearEntry(ctx, item.id)
                JellyfinRepository.markUnwatched(jfConfig, item.jellyfinId.ifBlank { item.id })
                showStatusMessage(getString(R.string.msg_marked_single_unwatched), 2500L)
            } else {
                PlaybackHistoryRepository.saveProgress(
                    context = ctx,
                    itemId = item.id,
                    positionMs = 3_600_000L,
                    durationMs = 3_600_000L,
                    streamUrl = item.streamUrl,
                    title = item.title(),
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl.ifBlank { item.posterUrl },
                    category = item.category.name
                )
                JellyfinRepository.markWatched(jfConfig, item.jellyfinId.ifBlank { item.id })
                showStatusMessage(getString(R.string.msg_marked_single_watched), 2500L)
            }

            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            withContext(Dispatchers.Default) {
                val derived = recomputeDerivedState(
                    current = _uiState.value.copy(selectedMediaForMenu = null),
                    movies = applyHistoryToItems(_uiState.value.movies, updatedHistory),
                    series = applyHistoryToItems(_uiState.value.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
                _uiState.value = derived
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
        _uiState.update { it.copy(watchlistIds = currentIds) }
        showStatusMessage(getString(if (isAdded) R.string.msg_added_watchlist else R.string.msg_removed_watchlist), 2000L)
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
            val targetIdx = allEpisodes.indexOfFirst { it.id == targetEpisode.id }
            if (targetIdx != -1) {
                for (i in 0..targetIdx) {
                    val ep = allEpisodes[i]
                    JellyfinRepository.markWatched(jfConfig, ep.id)
                }
            }

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
            withContext(Dispatchers.Default) {
                val derived = recomputeDerivedState(
                    current = _uiState.value,
                    movies = applyHistoryToItems(_uiState.value.movies, updatedHistory),
                    series = applyHistoryToItems(_uiState.value.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
                _uiState.value = derived
            }

            showStatusMessage(getString(R.string.msg_all_prev_watched), 2500L)
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
            val isCurrentlyWatched = (PlaybackHistoryRepository.getEntry(ctx, targetEpisode.id)?.progressPercent ?: 0f) >= 0.95f
            if (isCurrentlyWatched) {
                PlaybackHistoryRepository.unmarkEpisodeWatched(
                    context = ctx,
                    seriesId = seriesItem.id,
                    episode = targetEpisode,
                    title = seriesItem.title(),
                    posterUrl = seriesItem.posterUrl,
                    backdropUrl = seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl },
                    category = MediaCategory.SERIES.name
                )
                JellyfinRepository.markUnwatched(jfConfig, targetEpisode.id)
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
                JellyfinRepository.markWatched(jfConfig, targetEpisode.id)
            }

            val updatedHistory = PlaybackHistoryRepository.getAllEntries(ctx)
            withContext(Dispatchers.Default) {
                val derived = recomputeDerivedState(
                    current = _uiState.value,
                    movies = applyHistoryToItems(_uiState.value.movies, updatedHistory),
                    series = applyHistoryToItems(_uiState.value.series, updatedHistory),
                    playbackHistory = updatedHistory
                )
                _uiState.value = derived
            }
            val epCode = "S${targetEpisode.seasonNumber.toString().padStart(2, '0')}E${targetEpisode.episodeNumber.toString().padStart(2, '0')}"
            val resId = if (isCurrentlyWatched) R.string.msg_marked_unwatched else R.string.msg_marked_watched
            showStatusMessage(getString(resId, epCode), 2500L)
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
        applyLanguage(lang)
        _uiState.update { it.copy(language = lang) }
        viewModelScope.launch {
            SeerRepository.clearCache()
            appContext?.let { MediaLibraryCache.clear(it) }
            syncJellyfinLibrary(_uiState.value.jellyfinConfig, showToast = false)
            syncSeerData(_uiState.value.seerConfig)
        }
    }

    private fun applyLanguage(lang: AppLanguage) {
        val targetLocale = when (lang) {
            AppLanguage.FR -> java.util.Locale.FRENCH
            AppLanguage.EN -> java.util.Locale.ENGLISH
            AppLanguage.AUTO -> java.util.Locale.getDefault()
        }
        java.util.Locale.setDefault(targetLocale)
        appContext?.let { ctx ->
            val res = ctx.resources
            val config = res.configuration
            config.setLocale(targetLocale)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
        }
    }

    fun setPlayerPreference(pref: PlayerPreference) {
        val updated = _uiState.value.jellyfinConfig.copy(playerPreference = pref)
        prefs?.jellyfinConfig = updated
        _uiState.update { it.copy(jellyfinConfig = updated) }
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
            showStatusMessage(getString(R.string.msg_app_pinned), 2000L)
        }
    }

    fun unpinApp(packageName: String) {
        val currentPinned = _uiState.value.allApps.filter { it.isPinned }.map { it.packageName }.toMutableList()
        if (currentPinned.remove(packageName)) {
            prefs?.pinnedPackages = currentPinned
            refreshAppsList()
            showStatusMessage(getString(R.string.msg_app_unpinned), 2000L)
        }
    }

    fun hideApp(packageName: String) {
        val currentHidden = _uiState.value.allApps.filter { it.isHidden }.map { it.packageName }.toMutableSet()
        currentHidden.add(packageName)
        prefs?.hiddenPackages = currentHidden
        refreshAppsList()
        showStatusMessage(getString(R.string.msg_app_hidden), 2000L)
    }

    fun unhideApp(packageName: String) {
        val currentHidden = _uiState.value.allApps.filter { it.isHidden }.map { it.packageName }.toMutableSet()
        if (currentHidden.remove(packageName)) {
            prefs?.hiddenPackages = currentHidden
            refreshAppsList()
            showStatusMessage(getString(R.string.msg_app_unhidden), 2000L)
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

    fun onResume() {
        if (needsAppRefreshOnResume) {
            needsAppRefreshOnResume = false
            refreshAppsList()
        }
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
            _uiState.update { it.copy(allApps = apps) }
        }
    }

    private var searchJob: Job? = null
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearching = true) }
            val qLower = q.lowercase()

            // 1. Direct Jellyfin Remote Search (or fallback to cached movies/series)
            val jfConfig = _uiState.value.jellyfinConfig
            val jfRemoteResults = if (jfConfig.isConfigured) {
                JellyfinRepository.search(jfConfig, q)
            } else emptyList()

            val matchedMov = if (jfRemoteResults.isEmpty()) {
                _uiState.value.movies.filter {
                    it.title.lowercase().contains(qLower) || it.localizedTitle.lowercase().contains(qLower)
                }
            } else emptyList()
            val matchedSer = if (jfRemoteResults.isEmpty()) {
                _uiState.value.series.filter {
                    it.title.lowercase().contains(qLower) || it.localizedTitle.lowercase().contains(qLower)
                }
            } else emptyList()

            // 2. Seer Search if configured
            val seerResults = if (_uiState.value.seerConfig.isConfigured) {
                SeerRepository.search(_uiState.value.seerConfig, q)
            } else emptyList()

            val combined = (jfRemoteResults + matchedMov + matchedSer + seerResults).distinctBy { it.id }

            _uiState.update {
                it.copy(
                    searchResults = combined,
                    isSearching = false
                )
            }
        }
    }

    fun commitSearchQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(q)
        current.add(0, q)
        val trimmed = current.take(15)
        prefs?.searchHistory = trimmed
        _uiState.update { it.copy(searchHistory = trimmed) }
    }

    fun clearSearchHistory() {
        prefs?.searchHistory = emptyList()
        _uiState.update { it.copy(searchHistory = emptyList()) }
    }

    fun removeSearchHistoryItem(item: String) {
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(item)
        prefs?.searchHistory = current
        _uiState.update { it.copy(searchHistory = current) }
    }

    fun showStatusMessage(message: String, durationMs: Long = 2500L) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = message) }
            appContext?.let { ctx ->
                try {
                    android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
            delay(durationMs)
            _uiState.update {
                if (it.statusBannerMessage == message) it.copy(statusBannerMessage = null) else it
            }
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (_uiState.value.isCheckingUpdates) return
        _uiState.update { it.copy(isCheckingUpdates = true) }
        viewModelScope.launch {
            AppUpdateRepository.checkLatestRelease()
                .onSuccess { update ->
                    _uiState.update { it.copy(isCheckingUpdates = false) }
                    if (update != null) {
                        _uiState.update {
                            it.copy(
                                pendingUpdate = update,
                                updateError = null,
                                isDownloadingUpdate = false,
                                updateDownloadProgress = 0f
                            )
                        }
                    } else if (!silent) {
                        showStatusMessage(getString(R.string.update_already_latest, BuildConfig.VERSION_NAME))
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isCheckingUpdates = false) }
                    if (!silent) {
                        showStatusMessage(getString(R.string.update_check_failed))
                    }
                }
        }
    }

    fun dismissUpdate() {
        _uiState.update {
            it.copy(
                pendingUpdate = null,
                isDownloadingUpdate = false,
                updateDownloadProgress = 0f,
                updateError = null
            )
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val update = _uiState.value.pendingUpdate
        Log.d("AbyssUpdate", "downloadAndInstallUpdate triggered: $update")
        if (update == null || _uiState.value.isDownloadingUpdate) return

        _uiState.update {
            it.copy(
                isDownloadingUpdate = true,
                updateDownloadProgress = 0f,
                updateError = null
            )
        }
        viewModelScope.launch {
            Log.d("AbyssUpdate", "Downloading APK from: ${update.apkUrl}")
            AppUpdateRepository.downloadApk(context, update.apkUrl) { progress ->
                _uiState.update { it.copy(updateDownloadProgress = progress) }
            }.onSuccess { apkFile ->
                Log.d("AbyssUpdate", "Download successful: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
                _uiState.update { it.copy(isDownloadingUpdate = false) }
                AppUpdateRepository.triggerInstall(context, apkFile).onSuccess {
                    // Close update modal once package installer is active
                    dismissUpdate()
                }.onFailure { error ->
                    Log.e("AbyssUpdate", "Install trigger failed", error)
                    _uiState.update {
                        it.copy(updateError = error.localizedMessage ?: "Installation failed")
                    }
                }
            }.onFailure { error ->
                Log.e("AbyssUpdate", "Download failed", error)
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateError = error.localizedMessage ?: "Download failed"
                    )
                }
            }
        }
    }
}
