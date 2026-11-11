package dev.dylanakp.abysstvlauncher.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.data.WeatherInfo
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository
import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.domain.repository.PlaybackHistoryRepository
import dev.dylanakp.abysstvlauncher.domain.repository.WeatherRepository
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val spotlightCandidates: List<MediaItem> = emptyList(),
    val heroIndex: Int = 0,
    val continueWatchingMovies: List<MediaItem> = emptyList(),
    val continueWatchingSeries: List<MediaItem> = emptyList(),
    val homeMovies: List<MediaItem> = emptyList(),
    val homeSeries: List<MediaItem> = emptyList(),
    val pinnedApps: List<TvAppInfo> = emptyList(),
    val weatherCity: String = "Paris",
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val weatherInfo: WeatherInfo? = null,
    val isLoading: Boolean = false
) {
    val activeHeroItem: MediaItem?
        get() = if (spotlightCandidates.isNotEmpty()) {
            spotlightCandidates.getOrElse(heroIndex % spotlightCandidates.size) {
                spotlightCandidates.first()
            }
        } else null
}

class HomeViewModel(
    private val preferences: LauncherPreferences,
    private val weatherRepo: WeatherRepository,
    private val mediaCache: MediaLibraryCache,
    private val historyRepo: PlaybackHistoryRepository,
    private val jellyfinRepo: JellyfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            weatherCity = preferences.weatherCity,
            temperatureUnit = preferences.temperatureUnit
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var heroRotationJob: Job? = null

    init {
        loadCachedData()
        startHeroRotation()
        refreshWeather()
    }

    fun advanceHero() {
        val size = _uiState.value.spotlightCandidates.size
        if (size > 1) {
            _uiState.update { it.copy(heroIndex = (it.heroIndex + 1) % size) }
        }
    }

    fun setHeroIndex(idx: Int) {
        val size = _uiState.value.spotlightCandidates.size
        if (size > 0) {
            _uiState.update { it.copy(heroIndex = idx.coerceIn(0, size - 1)) }
            resetHeroTimer()
        }
    }

    fun updateSpotlight(items: List<MediaItem>) {
        _uiState.update {
            it.copy(
                spotlightCandidates = items,
                heroIndex = if (items.isNotEmpty()) it.heroIndex % items.size else 0
            )
        }
    }

    fun updateHomeMedia(
        movies: List<MediaItem>,
        series: List<MediaItem>,
        continueMovies: List<MediaItem>,
        continueSeries: List<MediaItem>
    ) {
        _uiState.update {
            it.copy(
                homeMovies = movies,
                homeSeries = series,
                continueWatchingMovies = continueMovies,
                continueWatchingSeries = continueSeries
            )
        }
    }

    fun updatePinnedApps(apps: List<TvAppInfo>) {
        _uiState.update { it.copy(pinnedApps = apps) }
    }

    fun refreshWeather() {
        val city = _uiState.value.weatherCity
        viewModelScope.launch {
            val info = weatherRepo.getWeather(city)
            if (info != null) {
                _uiState.update { it.copy(weatherInfo = info) }
            }
        }
    }

    fun setWeatherCity(city: String) {
        preferences.weatherCity = city
        _uiState.update { it.copy(weatherCity = city) }
        weatherRepo.clearCache()
        refreshWeather()
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        preferences.temperatureUnit = unit
        _uiState.update { it.copy(temperatureUnit = unit) }
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            val cached = mediaCache.load()
            if (cached != null) {
                val spotlight = (cached.movies.take(5) + cached.series.take(5)).shuffled()
                _uiState.update {
                    it.copy(
                        spotlightCandidates = spotlight,
                        homeMovies = cached.movies.take(15),
                        homeSeries = cached.series.take(15)
                    )
                }
            }
        }
    }

    private fun startHeroRotation() {
        heroRotationJob?.cancel()
        heroRotationJob = viewModelScope.launch {
            while (isActive) {
                delay(9_000L)
                advanceHero()
            }
        }
    }

    private fun resetHeroTimer() {
        startHeroRotation()
    }

    override fun onCleared() {
        super.onCleared()
        heroRotationJob?.cancel()
    }
}
