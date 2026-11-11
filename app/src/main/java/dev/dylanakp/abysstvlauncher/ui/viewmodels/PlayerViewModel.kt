package dev.dylanakp.abysstvlauncher.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository
import dev.dylanakp.abysstvlauncher.domain.repository.PlaybackHistoryRepository
import dev.dylanakp.abysstvlauncher.domain.repository.TmdbRepository
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.MovieDetails
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.player.PlayerManager
import dev.dylanakp.abysstvlauncher.ui.player.PlayerSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val activePlayerSession: PlayerSession? = null,
    val selectedMovieForDetails: MediaItem? = null,
    val activeMovieDetails: MovieDetails? = null,
    val isLoadingMovieDetails: Boolean = false,
    val selectedSeriesForEpisodes: MediaItem? = null,
    val activeSeriesDetails: SeriesDetails? = null,
    val isLoadingSeriesDetails: Boolean = false
)

class PlayerViewModel(
    private val playerManager: PlayerManager,
    private val jellyfinRepo: JellyfinRepository,
    private val tmdbRepo: TmdbRepository,
    private val historyRepo: PlaybackHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun openMovieDetails(item: MediaItem, config: JellyfinConfig) {
        _uiState.update {
            it.copy(
                selectedMovieForDetails = item,
                isLoadingMovieDetails = true,
                activeMovieDetails = null
            )
        }
        viewModelScope.launch {
            val details = jellyfinRepo.fetchMovieDetails(config, item)
            _uiState.update {
                it.copy(
                    activeMovieDetails = details,
                    isLoadingMovieDetails = false
                )
            }
        }
    }

    fun closeMovieDetails() {
        _uiState.update {
            it.copy(
                selectedMovieForDetails = null,
                activeMovieDetails = null,
                isLoadingMovieDetails = false
            )
        }
    }

    fun openSeriesDetails(item: MediaItem, config: JellyfinConfig) {
        _uiState.update {
            it.copy(
                selectedSeriesForEpisodes = item,
                isLoadingSeriesDetails = true,
                activeSeriesDetails = null
            )
        }
        viewModelScope.launch {
            val details = if (config.isConfigured && (item.jellyfinId.isNotBlank() || item.id.isNotBlank())) {
                jellyfinRepo.fetchSeriesDetails(config, item)
            } else if (item.tmdbId != null) {
                tmdbRepo.fetchSeriesDetailsFromTmdb(item.tmdbId, item)
            } else null

            _uiState.update {
                it.copy(
                    activeSeriesDetails = details,
                    isLoadingSeriesDetails = false
                )
            }
        }
    }

    fun closeSeriesDetails() {
        _uiState.update {
            it.copy(
                selectedSeriesForEpisodes = null,
                activeSeriesDetails = null,
                isLoadingSeriesDetails = false
            )
        }
    }

    fun startPlayback(session: PlayerSession) {
        _uiState.update { it.copy(activePlayerSession = session) }
    }

    fun stopPlayback(positionMs: Long, durationMs: Long, context: Context, config: JellyfinConfig) {
        val session = _uiState.value.activePlayerSession
        if (session != null && durationMs > 0L) {
            historyRepo.saveProgress(
                context = context,
                itemId = session.mediaItem.id,
                positionMs = positionMs,
                durationMs = durationMs,
                seriesId = session.seriesId,
                seasonNumber = session.seasonNumber,
                episodeNumber = session.episodeNumber,
                episodeTitle = session.subtitle,
                streamUrl = session.streamUrl,
                title = session.title,
                posterUrl = session.mediaItem.posterUrl,
                backdropUrl = session.mediaItem.backdropUrl,
                category = session.mediaItem.category.name
            )

            viewModelScope.launch {
                jellyfinRepo.reportPlaybackStopped(config, session.mediaItem.id, positionMs)
            }
        }
        _uiState.update { it.copy(activePlayerSession = null) }
        playerManager.release()
    }

    fun playTrailer(title: String, trailerUrl: String) {
        val session = PlayerSession(
            mediaItem = MediaItem(
                id = "trailer_${System.currentTimeMillis()}",
                title = title,
                category = MediaCategory.MOVIE,
                streamUrl = trailerUrl
            ),
            streamUrl = trailerUrl,
            title = title
        )
        startPlayback(session)
    }

    fun selectMovieVersion(versionId: String, config: JellyfinConfig) {
        val current = _uiState.value.activeMovieDetails ?: return
        val chosen = current.versions.find { it.id == versionId } ?: return
        val newStream = jellyfinRepo.getStreamUrl(config, current.movieItem.id, chosen.id)
        val updatedItem = current.movieItem.copy(streamUrl = newStream)

        _uiState.update {
            it.copy(
                activeMovieDetails = current.copy(
                    selectedVersionId = versionId,
                    movieItem = updatedItem
                ),
                selectedMovieForDetails = updatedItem
            )
        }
    }
}
