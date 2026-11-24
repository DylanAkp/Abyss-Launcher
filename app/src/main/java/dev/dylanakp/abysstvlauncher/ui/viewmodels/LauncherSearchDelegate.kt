package dev.dylanakp.abysstvlauncher.ui.viewmodels

import dev.dylanakp.abysstvlauncher.data.JellyfinRepository
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.data.SeerRepository
import dev.dylanakp.abysstvlauncher.ui.LauncherUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class LauncherSearchDelegate(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LauncherUiState>,
    private val getPrefs: () -> LauncherPreferences?,
    private val jellyfinRepository: JellyfinRepository,
    private val seerRepository: SeerRepository
) {
    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.isBlank()) {
            uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = scope.launch(Dispatchers.IO) {
            delay(350L)
            uiState.update { it.copy(isSearching = true) }
            val qLower = q.lowercase()
            val jfConfig = uiState.value.jellyfinConfig

            val jfRemoteResults = if (jfConfig.isConfigured) {
                jellyfinRepository.search(jfConfig, q)
            } else emptyList()

            val matchedMov = if (jfRemoteResults.isEmpty()) {
                uiState.value.movies.filter { it.title.lowercase().contains(qLower) }
            } else emptyList()
            val matchedSer = if (jfRemoteResults.isEmpty()) {
                uiState.value.series.filter { it.title.lowercase().contains(qLower) }
            } else emptyList()

            val seerResults = if (uiState.value.seerConfig.isConfigured) {
                seerRepository.search(uiState.value.seerConfig, q)
            } else emptyList()

            val combined = (jfRemoteResults + matchedMov + matchedSer + seerResults).distinctBy { it.id }
            uiState.update {
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
        val current = uiState.value.searchHistory.toMutableList()
        current.remove(q)
        current.add(0, q)
        val trimmed = current.take(15)
        getPrefs()?.searchHistory = trimmed
        uiState.update { it.copy(searchHistory = trimmed) }
    }

    fun clearSearchHistory() {
        getPrefs()?.searchHistory = emptyList()
        uiState.update { it.copy(searchHistory = emptyList()) }
    }

    fun removeSearchHistoryItem(item: String) {
        val current = uiState.value.searchHistory.toMutableList()
        current.remove(item)
        getPrefs()?.searchHistory = current
        uiState.update { it.copy(searchHistory = current) }
    }
}
