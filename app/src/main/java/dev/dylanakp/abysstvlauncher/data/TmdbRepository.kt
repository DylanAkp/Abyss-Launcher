package dev.dylanakp.abysstvlauncher.data

import android.util.Log
import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.domain.repository.TmdbRepository as ITmdbRepository
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SeriesSeason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

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

class TmdbRepository(
    private val client: OkHttpClient,
    private val json: Json
) : ITmdbRepository {

    private val seasonRequestSemaphore = Semaphore(MAX_PARALLEL_SEASON_REQUESTS)
    private val apiKeyWarningLogged = AtomicBoolean(false)

    private fun hasApiKey(): Boolean {
        if (API_KEY.isNotBlank()) return true
        if (apiKeyWarningLogged.compareAndSet(false, true)) {
            Log.e("TmdbRepository", "TMDB_API_KEY is missing; TMDB requests are disabled")
        }
        return false
    }

    override fun cleanTitleForMatching(raw: String): String = Companion.cleanTitleForMatching(raw)

    override fun titlesMatchSafely(titleA: String, titleB: String): Boolean =
        Companion.titlesMatchSafely(titleA, titleB)

    companion object {
        private val API_KEY: String = BuildConfig.TMDB_API_KEY
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMG_W1280 = "https://image.tmdb.org/t/p/w1280"
        private const val IMG_W780 = "https://image.tmdb.org/t/p/w780"
        private const val MAX_PARALLEL_SEASON_REQUESTS = 4

        private val RE_DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val RE_ROMAN_II = Regex("\\bii\\b")
        private val RE_ROMAN_III = Regex("\\biii\\b")
        private val RE_ROMAN_IV = Regex("\\biv\\b")
        private val RE_YEAR_PARENS = Regex("[(\\[]\\s*\\d{4}\\s*[)\\]]")
        private val RE_PREFIX_TAGS = Regex("^(fr|vf|vostfr|vost|stfr|multi|vff|vo|fhd|uhd|4k|hevc)\\s*[-:|/]\\s*")
        private val RE_WORD_TAGS = Regex("\\b(fr|vf|vostfr|vost|stfr|multi|vff|vo|fhd|uhd|4k|hevc|1080p|720p|top 100|nouveautes|nouveaute)\\b")
        private val RE_NON_ALNUM = Regex("[^a-z0-9]")
        private val RE_WHITESPACE = Regex("\\s+")

        fun cleanTitleForMatching(raw: String): String {
            var t = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(RE_DIACRITICS, "")
                .lowercase(Locale.ROOT)
                .trim()

            t = t.replace(RE_ROMAN_II, "2")
                .replace(RE_ROMAN_III, "3")
                .replace(RE_ROMAN_IV, "4")
                .replace(RE_YEAR_PARENS, " ")
                .replace(RE_PREFIX_TAGS, " ")
                .replace(RE_WORD_TAGS, " ")
                .replace(RE_NON_ALNUM, " ")

            return t.replace(RE_WHITESPACE, " ").trim()
        }

        fun titlesMatchSafely(titleA: String, titleB: String): Boolean {
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

        fun metadataAgrees(a: MediaItem, b: MediaItem): Boolean {
            val aYear = a.year.take(4).toIntOrNull()
            val bYear = b.year.take(4).toIntOrNull()
            if (aYear != null && bYear != null && aYear != bYear) return false
            val aRating = a.rating.toFloatOrNull()
            val bRating = b.rating.toFloatOrNull()
            if (aRating != null && bRating != null && abs(aRating - bRating) > 1.0f) return false
            return true
        }
    }

    override suspend fun fetchTrending(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) return@withContext emptyList()
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/trending/all/week?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw)
    }

    override suspend fun fetchPopularMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) return@withContext emptyList()
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/movie/popular?api_key=$API_KEY&language=$lang&page=1"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw, forcedCategory = MediaCategory.MOVIE)
    }

    override suspend fun fetchPopularSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) return@withContext emptyList()
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/tv/popular?api_key=$API_KEY&language=$lang&page=1"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw, forcedCategory = MediaCategory.SERIES)
    }

    private class PoolIndex(pool: List<MediaItem>) {
        val byTmdbId = HashMap<Int, MediaItem>(pool.size)
        val byCleanTitle = HashMap<String, MediaItem>(pool.size)
        val byCompactTitle = HashMap<String, MediaItem>(pool.size)
        val cleanEntries = ArrayList<Pair<String, MediaItem>>(pool.size)

        init {
            for (jf in pool) {
                jf.tmdbId?.let { byTmdbId.putIfAbsent(it, jf) }
                val clean = TmdbRepository.cleanTitleForMatching(jf.title)
                if (clean.isNotBlank()) {
                    byCleanTitle.putIfAbsent(clean, jf)
                    byCompactTitle.putIfAbsent(clean.replace(" ", ""), jf)
                    cleanEntries.add(clean to jf)
                }
            }
        }

        fun findMatch(item: MediaItem): MediaItem? {
            val candidates = LinkedHashSet<MediaItem>()
            item.tmdbId?.let { byTmdbId[it]?.let(candidates::add) }
            val clean = TmdbRepository.cleanTitleForMatching(item.title)
            if (clean.isNotBlank()) {
                byCleanTitle[clean]?.let(candidates::add)
                byCompactTitle[clean.replace(" ", "")]?.let(candidates::add)
                for ((jfClean, jf) in cleanEntries) {
                    if (TmdbRepository.titlesMatchSafely(jfClean, clean)) candidates.add(jf)
                }
            }
            return candidates.firstOrNull { TmdbRepository.metadataAgrees(item, it) }
        }
    }

    override fun enrichWithJellyfinStreams(
        items: List<MediaItem>,
        jellyfinMovies: List<MediaItem>,
        jellyfinSeries: List<MediaItem>
    ): List<MediaItem> {
        val seriesIndex = PoolIndex(jellyfinSeries)
        val moviesIndex = PoolIndex(jellyfinMovies)

        return items.mapNotNull { item ->
            if (item.title.isBlank()) return@mapNotNull null

            val index = if (item.category == MediaCategory.SERIES) seriesIndex else moviesIndex
            val match = index.findMatch(item)

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
        if (!hasApiKey()) return@withContext null
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/tv/$tmdbId?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: return@withContext null
        try {
            val root = json.decodeFromString<TmdbTvDetailsDto>(raw)
            val seasonsList = coroutineScope {
                root.seasons
                    .filter { it.seasonNumber > 0 }
                    .map { sObj ->
                        async {
                            val seasonNum = sObj.seasonNumber
                            val sName = sObj.name.ifBlank { "Saison $seasonNum" }
                            val epCount = sObj.episodeCount

                            val seasonUrl = "$BASE_URL/tv/$tmdbId/season/$seasonNum?api_key=$API_KEY&language=$lang"
                            val seasonRaw = seasonRequestSemaphore.withPermit { httpGet(seasonUrl) }
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

                            if (epList.isNotEmpty()) SeriesSeason(seasonNum, sName, epList) else null
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
            }

            if (seasonsList.isNotEmpty()) {
                SeriesDetails(
                    seriesItem = seriesItem,
                    seasons = seasonsList
                )
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("TmdbRepository", "Failed to parse series details for tmdbId=$tmdbId", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("TmdbRepository", "Invalid series details for tmdbId=$tmdbId", e)
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
                        qualityBadge = "",
                        audioBadge = "",
                        streamUrl = "",
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl
                    )
                )
            }
        } catch (e: SerializationException) {
            Log.w("TmdbRepository", "Failed to parse TMDB results", e)
        } catch (e: IllegalArgumentException) {
            Log.w("TmdbRepository", "Invalid TMDB results payload", e)
        }
        return list
    }

    override suspend fun fetchMovieTrailer(tmdbId: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || !hasApiKey()) return@withContext null
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("TmdbRepository", "Failed to parse movie trailer for tmdbId=$tmdbId", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("TmdbRepository", "Invalid trailer payload for tmdbId=$tmdbId", e)
            null
        }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val req = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: IOException) {
            Log.w("TmdbRepository", "HTTP GET failed for ${urlStr.substringBefore("?")}", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("TmdbRepository", "Invalid URL: ${urlStr.substringBefore("?")}", e)
            null
        }
    }
}
