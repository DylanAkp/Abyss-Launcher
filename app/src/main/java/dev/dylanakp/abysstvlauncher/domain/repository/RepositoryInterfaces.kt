package dev.dylanakp.abysstvlauncher.domain.repository

import android.content.Context
import dev.dylanakp.abysstvlauncher.data.WeatherInfo
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.MovieDetails
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary

interface JellyfinRepository {
    fun cleanUrl(raw: String): String
    fun getStreamUrl(config: JellyfinConfig, itemId: String, mediaSourceId: String? = null): String
    fun sanitizeStreamUrl(streamUrl: String, config: JellyfinConfig, mediaSourceId: String? = null): String
    fun getImageUrl(serverUrl: String, itemId: String, isBackdrop: Boolean = false): String
    suspend fun authenticate(config: JellyfinConfig): Result<JellyfinConfig>
    suspend fun testConnection(config: JellyfinConfig): Result<String>
    suspend fun syncLibrary(config: JellyfinConfig): Result<SyncedLibrary>
    suspend fun fetchMovies(config: JellyfinConfig): List<MediaItem>
    suspend fun fetchSeries(config: JellyfinConfig): List<MediaItem>
    suspend fun fetchResumeAndNextUp(config: JellyfinConfig): List<MediaItem>
    suspend fun fetchContinueWatching(config: JellyfinConfig): List<MediaItem>
    suspend fun fetchResumeItems(config: JellyfinConfig): List<MediaItem>
    suspend fun fetchNextUpEpisodes(config: JellyfinConfig): List<MediaItem>
    suspend fun fetchSeriesDetails(config: JellyfinConfig, seriesItem: MediaItem): SeriesDetails?
    suspend fun fetchMovieDetails(config: JellyfinConfig, movieItem: MediaItem): MovieDetails
    suspend fun preparePlayback(config: JellyfinConfig, itemId: String): Boolean
    suspend fun reportPlaybackStart(config: JellyfinConfig, itemId: String, positionMs: Long)
    suspend fun reportPlaybackProgress(config: JellyfinConfig, itemId: String, positionMs: Long, isPaused: Boolean)
    suspend fun reportPlaybackStopped(config: JellyfinConfig, itemId: String, positionMs: Long)
    suspend fun markWatched(config: JellyfinConfig, itemId: String)
    suspend fun markUnwatched(config: JellyfinConfig, itemId: String)
    suspend fun search(config: JellyfinConfig, query: String): List<MediaItem>
}

interface SeerRepository {
    fun clearCache()
    suspend fun authenticateWithJellyfin(serverUrl: String, user: String, pass: String): Result<String>
    suspend fun testConnection(config: SeerConfig): Result<String>
    suspend fun fetchTrending(config: SeerConfig): List<MediaItem>
    suspend fun fetchDiscoverMovies(config: SeerConfig): List<MediaItem>
    suspend fun fetchDiscoverTv(config: SeerConfig): List<MediaItem>
    suspend fun fetchUpcomingMovies(config: SeerConfig): List<MediaItem>
    suspend fun fetchRequests(config: SeerConfig): List<MediaItem>
    suspend fun search(config: SeerConfig, query: String): List<MediaItem>
    suspend fun requestMedia(config: SeerConfig, tmdbId: Int, isTv: Boolean): Result<String>
}

interface TmdbRepository {
    fun cleanTitleForMatching(raw: String): String
    fun titlesMatchSafely(titleA: String, titleB: String): Boolean
    suspend fun fetchTrending(): List<MediaItem>
    suspend fun fetchPopularMovies(): List<MediaItem>
    suspend fun fetchPopularSeries(): List<MediaItem>
    fun enrichWithJellyfinStreams(
        items: List<MediaItem>,
        jellyfinMovies: List<MediaItem>,
        jellyfinSeries: List<MediaItem>
    ): List<MediaItem>
    suspend fun fetchSeriesDetailsFromTmdb(
        tmdbId: Int,
        seriesItem: MediaItem
    ): SeriesDetails?
    suspend fun fetchMovieTrailer(tmdbId: Int): Pair<String, String>?
}

interface WeatherRepository {
    fun clearCache()
    suspend fun getWeather(cityName: String): WeatherInfo?
}

interface MediaLibraryCache {
    suspend fun clear(context: Context? = null)
    suspend fun save(context: Context? = null, library: SyncedLibrary)
    suspend fun load(context: Context? = null): SyncedLibrary?
    fun loadSync(context: Context? = null): SyncedLibrary?
}

interface PlaybackHistoryRepository {
    fun ensureLoaded(context: Context? = null)
    fun getProgressPercent(itemId: String): Float
    fun getSavedPositionMs(itemId: String): Long
    fun getSavedDurationMs(itemId: String): Long
    fun getEntry(context: Context, itemId: String): PlaybackHistoryEntry?
    fun getAllEntries(context: Context): Map<String, PlaybackHistoryEntry>
    fun saveProgress(
        context: Context,
        itemId: String,
        positionMs: Long,
        durationMs: Long,
        seriesId: String = "",
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        episodeTitle: String = "",
        streamUrl: String = "",
        title: String = "",
        posterUrl: String = "",
        backdropUrl: String = "",
        category: String = ""
    )
    fun clearEntry(context: Context, itemId: String)
}
