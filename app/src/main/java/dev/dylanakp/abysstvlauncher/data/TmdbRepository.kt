package dev.dylanakp.abysstvlauncher.data

import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.domain.repository.TmdbRepository as ITmdbRepository
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SeriesSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.text.Normalizer
import java.util.Locale

@Serializable
private data class TmdbTvDetailsDto(
    val seasons: List<TmdbSeasonDto> = emptyList()
)

@Serializable
private data class TmdbSeasonDto(
    @SerialName("season_number") val seasonNumber: Int = -1,
    val name: String = "",
    @SerialName("episode_count") val episodeCount: Int = 0
)

@Serializable
private data class TmdbSeasonDetailDto(
    val episodes: List<TmdbEpisodeDto> = emptyList()
)

@Serializable
private data class TmdbEpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int = 1,
    val name: String = "",
    val overview: String = "",
    @SerialName("still_path") val stillPath: String? = null
)

@Serializable
private data class TmdbPagedResultDto(
    val results: List<TmdbMediaDto> = emptyList()
)

@Serializable
private data class TmdbMediaDto(
    val id: Int = 0,
    @SerialName("media_type") val mediaType: String = "",
    val title: String = "",
    val name: String = "",
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val overview: String = ""
)

@Serializable
private data class TmdbVideosResponseDto(
    val results: List<TmdbVideoDto> = emptyList()
)

@Serializable
private data class TmdbVideoDto(
    val site: String = "",
    val type: String = "",
    val key: String = "",
    val name: String = "Trailer"
)

object TmdbRepository : ITmdbRepository, KoinComponent {

    private val client: OkHttpClient by inject()
    private val json: Json by inject()

    private val API_KEY: String = BuildConfig.TMDB_API_KEY
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMG_W1280 = "https://image.tmdb.org/t/p/w1280"
    private const val IMG_W780 = "https://image.tmdb.org/t/p/w780"

    override fun cleanTitleForMatching(raw: String): String {
        var t = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .trim()

        // Strip roman numerals
        t = t.replace(Regex("\\bii\\b"), "2")
            .replace(Regex("\\biii\\b"), "3")
            .replace(Regex("\\biv\\b"), "4")

        // Strip year in parens or brackets: (2024), [2023]
        t = t.replace(Regex("[(\\[]\\s*\\d{4}\\s*[)\\]]"), " ")

        // Strip common release and quality tags
        t = t.replace(Regex("^(fr|vf|vostfr|vost|stfr|multi|vff|vo|fhd|uhd|4k|hevc)\\s*[-:|/]\\s*"), " ")
        t = t.replace(Regex("\\b(fr|vf|vostfr|vost|stfr|multi|vff|vo|fhd|uhd|4k|hevc|1080p|720p|top 100|nouveautes|nouveaute)\\b"), " ")

        // Keep alphanumeric
        t = t.replace(Regex("[^a-z0-9]"), " ")

        return t.replace(Regex("\\s+"), " ").trim()
    }

    override fun titlesMatchSafely(titleA: String, titleB: String): Boolean {
        if (titleA.isBlank() || titleB.isBlank()) return false
        val a = cleanTitleForMatching(titleA)
        val b = cleanTitleForMatching(titleB)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        if (a.replace(" ", "") == b.replace(" ", "")) return true

        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a

        if (shorter.length >= 6 && longer.startsWith("$shorter ")) {
            return true
        }
        return false
    }

    override suspend fun fetchTrending(): List<MediaItem> = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/trending/all/week?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw)
    }

    override suspend fun fetchPopularMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/movie/popular?api_key=$API_KEY&language=$lang&page=1"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw, forcedCategory = MediaCategory.MOVIE)
    }

    override suspend fun fetchPopularSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/tv/popular?api_key=$API_KEY&language=$lang&page=1"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw, forcedCategory = MediaCategory.SERIES)
    }

    override fun enrichWithJellyfinStreams(
        items: List<MediaItem>,
        jellyfinMovies: List<MediaItem>,
        jellyfinSeries: List<MediaItem>
    ): List<MediaItem> {
        return items.mapNotNull { item ->
            if (item.title.isBlank()) return@mapNotNull null

            val candidatePool = if (item.category == MediaCategory.SERIES) jellyfinSeries else jellyfinMovies

            val match = candidatePool.firstOrNull { jf ->
                (item.tmdbId != null && jf.tmdbId != null && item.tmdbId == jf.tmdbId) ||
                    titlesMatchSafely(jf.title, item.title)
            }

            if (match != null) {
                item.copy(
                    id = match.id,
                    jellyfinId = match.jellyfinId,
                    streamUrl = match.streamUrl,
                    seerStatus = dev.dylanakp.abysstvlauncher.model.SeerMediaStatus.AVAILABLE,
                    posterUrl = item.posterUrl.ifBlank { match.posterUrl },
                    backdropUrl = item.backdropUrl.ifBlank { match.backdropUrl }
                )
            } else {
                item
            }
        }
    }

    override suspend fun fetchSeriesDetailsFromTmdb(
        tmdbId: Int,
        seriesItem: MediaItem
    ): SeriesDetails? = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/tv/$tmdbId?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: return@withContext null
        try {
            val root = json.decodeFromString<TmdbTvDetailsDto>(raw)
            val seasonsList = mutableListOf<SeriesSeason>()

            for (sObj in root.seasons) {
                val seasonNum = sObj.seasonNumber
                if (seasonNum <= 0) continue
                val sName = sObj.name.ifBlank { "Saison $seasonNum" }
                val epCount = sObj.episodeCount

                val seasonUrl = "$BASE_URL/tv/$tmdbId/season/$seasonNum?api_key=$API_KEY&language=$lang"
                val seasonRaw = httpGet(seasonUrl)
                val epList = mutableListOf<SeriesEpisode>()
                if (seasonRaw != null) {
                    val seasonRoot = json.decodeFromString<TmdbSeasonDetailDto>(seasonRaw)
                    seasonRoot.episodes.forEachIndexed { idx, eObj ->
                        val epNum = if (eObj.episodeNumber > 0) eObj.episodeNumber else idx + 1
                        val epName = eObj.name.ifBlank { "Épisode $epNum" }
                        val stillUrl = eObj.stillPath?.takeIf { it.isNotBlank() }?.let { "$IMG_W780$it" } ?: seriesItem.backdropUrl

                        epList.add(
                            SeriesEpisode(
                                id = "tmdb_ep_${tmdbId}_${seasonNum}_${epNum}",
                                seriesId = seriesItem.id,
                                seasonNumber = seasonNum,
                                episodeNumber = epNum,
                                title = epName,
                                plot = eObj.overview,
                                streamUrl = seriesItem.streamUrl,
                                posterUrl = stillUrl
                            )
                        )
                    }
                }

                if (epList.isEmpty() && epCount > 0) {
                    for (e in 1..epCount) {
                        epList.add(
                            SeriesEpisode(
                                id = "tmdb_ep_${tmdbId}_${seasonNum}_${e}",
                                seriesId = seriesItem.id,
                                seasonNumber = seasonNum,
                                episodeNumber = e,
                                title = "Épisode $e",
                                plot = "",
                                streamUrl = seriesItem.streamUrl,
                                posterUrl = seriesItem.backdropUrl
                            )
                        )
                    }
                }

                if (epList.isNotEmpty()) {
                    seasonsList.add(
                        SeriesSeason(
                            seasonNumber = seasonNum,
                            name = sName,
                            episodes = epList
                        )
                    )
                }
            }

            if (seasonsList.isNotEmpty()) {
                SeriesDetails(
                    seriesItem = seriesItem,
                    seasons = seasonsList
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTmdbResults(
        jsonString: String,
        forcedCategory: MediaCategory? = null
    ): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val root = json.decodeFromString<TmdbPagedResultDto>(jsonString)

            for (obj in root.results) {
                val mediaType = obj.mediaType
                val category = forcedCategory ?: if (mediaType == "tv") MediaCategory.SERIES else MediaCategory.MOVIE

                val title = obj.title.ifBlank { obj.name }.trim()
                if (title.isBlank()) continue

                val backdropPath = obj.backdropPath.orEmpty()
                val posterPath = obj.posterPath.orEmpty()
                if (backdropPath.isBlank() && posterPath.isBlank()) continue

                val backdropUrl = if (backdropPath.isNotBlank()) "$IMG_W1280$backdropPath" else "$IMG_W780$posterPath"
                val posterUrl = if (posterPath.isNotBlank()) "$IMG_W780$posterPath" else backdropUrl

                val releaseDate = obj.releaseDate.ifBlank { obj.firstAirDate }
                val year = releaseDate.take(4)

                val voteAvg = obj.voteAverage
                val rating = if (voteAvg > 0.0) String.format(Locale.US, "%.1f", voteAvg) else ""

                val overview = obj.overview.trim()
                val tmdbId = obj.id

                list.add(
                    MediaItem(
                        id = "tmdb_${category.name.lowercase()}_$tmdbId",
                        title = title,
                        subtitle = year,
                        synopsis = overview,
                        category = category,
                        groupTitle = "",
                        rating = rating,
                        year = year,
                        durationOrSeasons = "",
                        qualityBadge = "4K HDR",
                        audioBadge = "5.1",
                        streamUrl = "",
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    override suspend fun fetchMovieTrailer(tmdbId: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank() || tmdbId <= 0) return@withContext null
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/movie/$tmdbId/videos?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: httpGet("$BASE_URL/movie/$tmdbId/videos?api_key=$API_KEY&language=en-US") ?: return@withContext null
        try {
            val root = json.decodeFromString<TmdbVideosResponseDto>(raw)
            var fallbackKey: String? = null
            var fallbackName = "Trailer"
            for (obj in root.results) {
                val site = obj.site
                val type = obj.type
                val key = obj.key
                val name = obj.name
                if (site.equals("YouTube", ignoreCase = true) && key.isNotBlank()) {
                    if (type.equals("Trailer", ignoreCase = true)) {
                        return@withContext Pair("https://www.youtube.com/watch?v=$key", name)
                    } else if (fallbackKey == null) {
                        fallbackKey = key
                        fallbackName = name
                    }
                }
            }
            if (fallbackKey != null) Pair("https://www.youtube.com/watch?v=$fallbackKey", fallbackName) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeForSearch(str: String): String {
        val nfd = Normalizer.normalize(str, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val req = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) resp.body?.string() else null
        } catch (_: Exception) {
            null
        }
    }
}
