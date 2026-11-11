package dev.dylanakp.abysstvlauncher.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository
import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.domain.repository.SeerRepository
import dev.dylanakp.abysstvlauncher.domain.repository.TmdbRepository
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val tmdbTrending: List<MediaItem> = emptyList(),
    val seerTrending: List<MediaItem> = emptyList(),
    val seerPopularMovies: List<MediaItem> = emptyList(),
    val seerPopularTv: List<MediaItem> = emptyList(),
    val seerRequests: List<MediaItem> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<MediaItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val isRequestingOnSeer: Boolean = false,
    val requestResultMessage: String? = null
)

class DiscoverViewModel(
    private val tmdbRepo: TmdbRepository,
    private val seerRepo: SeerRepository,
    private val jellyfinRepo: JellyfinRepository,
    private val mediaCache: MediaLibraryCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadCachedDiscover()
    }

    fun onSearchQueryChanged(query: String, seerConfig: SeerConfig? = null) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350L)
            _uiState.update { it.copy(isSearching = true) }

            val results = mutableListOf<MediaItem>()
            if (seerConfig != null && seerConfig.isConfigured) {
                results.addAll(seerRepo.search(seerConfig, query))
            }

            _uiState.update {
                it.copy(
                    searchResults = results,
                    isSearching = false
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
    }

    fun requestOnSeer(config: SeerConfig, tmdbId: Int, isTv: Boolean, onDone: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingOnSeer = true) }
            val res = seerRepo.requestMedia(config, tmdbId, isTv)
            _uiState.update {
                it.copy(
                    isRequestingOnSeer = false,
                    requestResultMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                )
            }
            onDone(res)
        }
    }

    fun updateDiscoverData(
        tmdbTrending: List<MediaItem>,
        seerTrending: List<MediaItem>,
        seerMovies: List<MediaItem>,
        seerTv: List<MediaItem>,
        seerRequests: List<MediaItem>
    ) {
        _uiState.update {
            it.copy(
                tmdbTrending = tmdbTrending,
                seerTrending = seerTrending,
                seerPopularMovies = seerMovies,
                seerPopularTv = seerTv,
                seerRequests = seerRequests
            )
        }
    }

    private fun loadCachedDiscover() {
        viewModelScope.launch {
            val cached = mediaCache.load()
            if (cached != null) {
                updateDiscoverData(
                    tmdbTrending = cached.tmdbTrending,
                    seerTrending = cached.seerTrending,
                    seerMovies = cached.seerPopularMovies,
                    seerTv = cached.seerPopularTv,
                    seerRequests = cached.seerRequests
                )
            }
        }
    }
}
