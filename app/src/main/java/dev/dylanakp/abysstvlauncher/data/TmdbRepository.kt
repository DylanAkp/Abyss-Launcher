package dev.dylanakp.abysstvlauncher.data

import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SeriesSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

object TmdbRepository {

    private val API_KEY: String = BuildConfig.TMDB_API_KEY
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMG_W1280 = "https://image.tmdb.org/t/p/w1280"
    private const val IMG_W780 = "https://image.tmdb.org/t/p/w780"

    fun cleanTitleForMatching(raw: String): String {
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

    suspend fun fetchTrending(): List<MediaItem> = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/trending/all/week?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw)
    }

    suspend fun fetchPopularMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/movie/popular?api_key=$API_KEY&language=$lang&page=1"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw, forcedCategory = MediaCategory.MOVIE)
    }

    suspend fun fetchPopularSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/tv/popular?api_key=$API_KEY&language=$lang&page=1"
        val raw = httpGet(url) ?: return@withContext emptyList()
        parseTmdbResults(raw, forcedCategory = MediaCategory.SERIES)
    }

    fun enrichWithJellyfinStreams(
        items: List<MediaItem>,
        jellyfinMovies: List<MediaItem>,
        jellyfinSeries: List<MediaItem>
    ): List<MediaItem> {
        return items.mapNotNull { item ->
            if (item.title.isBlank() && item.localizedTitle.isBlank()) return@mapNotNull null

            val candidatePool = if (item.category == MediaCategory.SERIES) jellyfinSeries else jellyfinMovies

            val match = candidatePool.firstOrNull { jf ->
                (item.tmdbId != null && jf.tmdbId != null && item.tmdbId == jf.tmdbId) ||
                    titlesMatchSafely(jf.title, item.title) ||
                    titlesMatchSafely(jf.localizedTitle, item.title) ||
                    titlesMatchSafely(jf.title, item.localizedTitle) ||
                    titlesMatchSafely(jf.localizedTitle, item.localizedTitle)
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

    suspend fun fetchSeriesDetailsFromTmdb(
        tmdbId: Int,
        seriesItem: MediaItem
    ): SeriesDetails? = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/tv/$tmdbId?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: return@withContext null
        try {
            val root = JSONObject(raw)
            val seasonsArr = root.optJSONArray("seasons") ?: return@withContext null
            val seasonsList = mutableListOf<SeriesSeason>()

            for (s in 0 until seasonsArr.length()) {
                val sObj = seasonsArr.optJSONObject(s) ?: continue
                val seasonNum = sObj.optInt("season_number", -1)
                if (seasonNum <= 0) continue // Skip specials
                val sName = sObj.optString("name", "Saison $seasonNum")
                val epCount = sObj.optInt("episode_count", 0)

                val seasonUrl = "$BASE_URL/tv/$tmdbId/season/$seasonNum?api_key=$API_KEY&language=$lang"
                val seasonRaw = httpGet(seasonUrl)
                val epList = mutableListOf<SeriesEpisode>()
                if (seasonRaw != null) {
                    val seasonRoot = JSONObject(seasonRaw)
                    val epsArr = seasonRoot.optJSONArray("episodes")
                    if (epsArr != null) {
                        for (e in 0 until epsArr.length()) {
                            val eObj = epsArr.optJSONObject(e) ?: continue
                            val epNum = eObj.optInt("episode_number", e + 1)
                            val epName = eObj.optString("name", "Épisode $epNum")
                            val epOverview = eObj.optString("overview", "")
                            val stillPath = eObj.optString("still_path", "")
                            val stillUrl = if (stillPath.isNotBlank()) "$IMG_W780$stillPath" else seriesItem.backdropUrl

                            epList.add(
                                SeriesEpisode(
                                    id = "tmdb_ep_${tmdbId}_${seasonNum}_${epNum}",
                                    seriesId = seriesItem.id,
                                    seasonNumber = seasonNum,
                                    episodeNumber = epNum,
                                    title = epName,
                                    plot = epOverview,
                                    streamUrl = seriesItem.streamUrl,
                                    posterUrl = stillUrl
                                )
                            )
                        }
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
            val root = JSONObject(jsonString)
            val results: JSONArray = root.optJSONArray("results") ?: return emptyList()

            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue

                val mediaType = obj.optString("media_type", "")
                val category = forcedCategory ?: if (mediaType == "tv") MediaCategory.SERIES else MediaCategory.MOVIE

                val title = obj.optString("title", "").ifBlank { obj.optString("name", "") }.trim()
                if (title.isBlank()) continue

                val backdropPath = obj.optString("backdrop_path", "")
                val posterPath = obj.optString("poster_path", "")
                if (backdropPath.isBlank() && posterPath.isBlank()) continue

                val backdropUrl = if (backdropPath.isNotBlank()) "$IMG_W1280$backdropPath" else "$IMG_W780$posterPath"
                val posterUrl = if (posterPath.isNotBlank()) "$IMG_W780$posterPath" else backdropUrl

                val releaseDate = obj.optString("release_date", "").ifBlank { obj.optString("first_air_date", "") }
                val year = releaseDate.take(4)

                val voteAvg = obj.optDouble("vote_average", 0.0)
                val rating = if (voteAvg > 0.0) String.format(Locale.US, "%.1f", voteAvg) else ""

                val overview = obj.optString("overview", "").trim()
                val tmdbId = obj.optInt("id", 0)

                val groupTitle = when (category) {
                    MediaCategory.SERIES -> "Trending TV Shows"
                    else -> "Trending Movies"
                }
                val localizedGroupTitle = when (category) {
                    MediaCategory.SERIES -> "Séries Tendances TMDB"
                    else -> "Films Tendances TMDB"
                }

                list.add(
                    MediaItem(
                        id = "tmdb_${category.name.lowercase()}_$tmdbId",
                        title = title,
                        localizedTitle = title,
                        subtitle = if (year.isNotBlank()) "$year • ${if (category == MediaCategory.SERIES) "Series" else "Movie"}" else "Cinema",
                        localizedSubtitle = if (year.isNotBlank()) "$year • ${if (category == MediaCategory.SERIES) "Série" else "Film"}" else "Cinéma",
                        synopsis = overview,
                        localizedSynopsis = overview,
                        category = category,
                        groupTitle = groupTitle,
                        localizedGroupTitle = localizedGroupTitle,
                        rating = rating,
                        year = year,
                        duration = "",
                        localizedDuration = "",
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

    suspend fun fetchMovieTrailer(tmdbId: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank() || tmdbId <= 0) return@withContext null
        val lang = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/movie/$tmdbId/videos?api_key=$API_KEY&language=$lang"
        val raw = httpGet(url) ?: httpGet("$BASE_URL/movie/$tmdbId/videos?api_key=$API_KEY&language=en-US") ?: return@withContext null
        try {
            val root = JSONObject(raw)
            val results = root.optJSONArray("results") ?: return@withContext null
            var fallbackKey: String? = null
            var fallbackName = "Trailer"
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val site = obj.optString("site", "")
                val type = obj.optString("type", "")
                val key = obj.optString("key", "")
                val name = obj.optString("name", "Trailer")
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
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Abyss)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
