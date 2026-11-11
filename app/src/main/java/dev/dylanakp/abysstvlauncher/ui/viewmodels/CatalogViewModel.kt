package dev.dylanakp.abysstvlauncher.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository
import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.ui.components.CatalogSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogUiState(
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val moviesTop100: List<MediaItem> = emptyList(),
    val moviesNouveautes: List<MediaItem> = emptyList(),
    val seriesTop100: List<MediaItem> = emptyList(),
    val seriesNouveautes: List<MediaItem> = emptyList(),
    val activeCatalogSelection: CatalogSelection? = null,
    val selectedGenre: String? = null,
    val isLoading: Boolean = false
)

class CatalogViewModel(
    private val mediaCache: MediaLibraryCache,
    private val jellyfinRepo: JellyfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        loadCachedCatalog()
    }

    fun openCatalog(title: String, items: List<MediaItem>) {
        _uiState.update {
            it.copy(activeCatalogSelection = CatalogSelection(title = title, items = items))
        }
    }

    fun closeCatalog() {
        _uiState.update { it.copy(activeCatalogSelection = null) }
    }

    fun filterByGenre(genre: String?) {
        _uiState.update { it.copy(selectedGenre = genre) }
    }

    fun updateCatalog(movies: List<MediaItem>, series: List<MediaItem>) {
        val top100Mov = movies.filter { it.rating.toDoubleOrNull()?.let { r -> r >= 8.0 } ?: false }
        val newMov = movies.sortedByDescending { it.year }.take(20)

        val top100Ser = series.filter { it.rating.toDoubleOrNull()?.let { r -> r >= 8.0 } ?: false }
        val newSer = series.sortedByDescending { it.year }.take(20)

        _uiState.update {
            it.copy(
                movies = movies,
                series = series,
                moviesTop100 = top100Mov,
                moviesNouveautes = newMov,
                seriesTop100 = top100Ser,
                seriesNouveautes = newSer
            )
        }
    }

    private fun loadCachedCatalog() {
        viewModelScope.launch {
            val cached = mediaCache.load()
            if (cached != null) {
                updateCatalog(cached.movies, cached.series)
            }
        }
    }
}
