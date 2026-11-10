package dev.dylanakp.abysstvlauncher.data

import android.util.Log
import dev.dylanakp.abysstvlauncher.domain.repository.SeerRepository as ISeerRepository
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
private data class SeerLoginRequest(
    val username: String,
    val password: String
)

@Serializable
private data class SeerRequestPayload(
    val mediaType: String,
    val mediaId: Int,
    val seasons: String? = null
)

@Serializable
private data class SeerStatusResponse(
    val version: String = "Seer Connected"
)

@Serializable
private data class SeerSettingsResponse(
    val apiKey: String = ""
)

@Serializable
data class SeerDownloadStatusDto(
    val size: Long = 0L,
    val sizeLeft: Long = 0L,
    val timeLeft: String? = null,
    val timeleft: String? = null
)

@Serializable
private data class SeerMediaInfoDto(
    val status: Int = 1,
    val downloadStatus: List<SeerDownloadStatusDto>? = null,
    val downloadStatus4k: List<SeerDownloadStatusDto>? = null
)

@Serializable
private data class SeerDiscoverResultDto(
    val results: List<SeerDiscoverItemDto> = emptyList()
)

@Serializable
private data class SeerDiscoverItemDto(
    val id: Int = 0,
    val mediaType: String = "",
    val title: String = "",
    val name: String = "",
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Double = 0.0,
    val firstAirDate: String = "",
    val releaseDate: String = "",
    val mediaInfo: SeerMediaInfoDto? = null
)

@Serializable
private data class SeerRequestListDto(
    val results: List<SeerRequestItemDto> = emptyList()
)

@Serializable
private data class SeerRequestItemDto(
    val media: SeerRequestMediaDto? = null,
    val movie: SeerRequestMediaTitleDto? = null,
    val tv: SeerRequestMediaTitleDto? = null,
    val downloadStatus: List<SeerDownloadStatusDto>? = null
)

@Serializable
private data class SeerRequestMediaDto(
    val mediaType: String = "movie",
    val tmdbId: Int = 0,
    val status: Int = 2,
    val downloadStatus: List<SeerDownloadStatusDto>? = null,
    val downloadStatus4k: List<SeerDownloadStatusDto>? = null
)

@Serializable
private data class SeerRequestMediaTitleDto(
    val title: String = "",
    val name: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null
)

object SeerRepository : ISeerRepository, KoinComponent {

    private const val TMDB_IMG_W500 = "https://image.tmdb.org/t/p/w500"
    private const val TMDB_IMG_W1280 = "https://image.tmdb.org/t/p/w1280"

    private val client: OkHttpClient by inject()
    private val json: Json by inject()

    private val metadataCache = ConcurrentHashMap<Pair<Boolean, Int>, Triple<String, String, String>>()

    override fun clearCache() = metadataCache.clear()

    private fun cleanUrl(raw: String): String =
        raw.trim().trimEnd('/')

    private fun currentLang(): String =
        java.util.Locale.getDefault().language.ifEmpty { "en" }

    private fun httpGet(urlStr: String, apiKey: String): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "AbyssLauncher/2.0")
                .header("Accept", "application/json")

            if (apiKey.isNotBlank()) {
                if (apiKey.startsWith("connect.sid=")) {
                    reqBuilder.header("Cookie", apiKey)
                } else {
                    reqBuilder.header("X-Api-Key", apiKey)
                }
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful) {
                Log.w("AbyssSeer", "HTTP ${resp.code} on $urlStr")
                return null
            }
            resp.body?.string()
        } catch (e: Exception) {
            Log.e("AbyssSeer", "Error in httpGet $urlStr: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    private fun httpPost(urlStr: String, apiKey: String, bodyJson: String): Pair<Int, String> {
        return try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val reqBody = bodyJson.toRequestBody(mediaType)
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "AbyssLauncher/2.0")
                .header("Accept", "application/json")
                .post(reqBody)

            if (apiKey.isNotBlank()) {
                if (apiKey.startsWith("connect.sid=")) {
                    reqBuilder.header("Cookie", apiKey)
                } else {
                    reqBuilder.header("X-Api-Key", apiKey)
                }
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            val text = resp.body?.string().orEmpty()
            Pair(resp.code, text)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "Network error")
        }
    }

    override suspend fun authenticateWithJellyfin(serverUrl: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(serverUrl)
        if (server.isBlank() || user.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL and user are required"))
        }

        try {
            val url = URL("$server/api/v1/auth/jellyfin")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val body = json.encodeToString(SeerLoginRequest(username = user, password = pass))
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            if (code in 200..299) {
                val cookies = conn.headerFields["Set-Cookie"].orEmpty()
                val sessionCookie = cookies.find { it.startsWith("connect.sid=") }
                    ?.substringBefore(";")

                var apiKey = ""
                if (!sessionCookie.isNullOrBlank()) {
                    try {
                        val settingsConn = (URL("$server/api/v1/settings/main").openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            setRequestProperty("Cookie", sessionCookie)
                            setRequestProperty("Accept", "application/json")
                        }
                        if (settingsConn.responseCode in 200..299) {
                            val settingsJson = settingsConn.inputStream.bufferedReader().use { it.readText() }
                            apiKey = json.decodeFromString<SeerSettingsResponse>(settingsJson).apiKey
                        }
                    } catch (_: Exception) {}
                }

                val finalToken = when {
                    apiKey.isNotBlank() -> apiKey
                    !sessionCookie.isNullOrBlank() -> sessionCookie
                    else -> "connected"
                }
                Result.success(finalToken)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Result.failure(Exception("Authentication failed ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(config: SeerConfig): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (server.isBlank() || config.apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL and API Key are required"))
        }

        val raw = httpGet("$server/api/v1/status", config.apiKey)
            ?: return@withContext Result.failure(Exception("Cannot connect to Seer server"))

        try {
            val status = json.decodeFromString<SeerStatusResponse>(raw)
            Result.success(status.version.ifBlank { "Seer Connected" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchTrending(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/trending?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw)
    }

    override suspend fun fetchDiscoverMovies(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/movies?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw, forcedCategory = MediaCategory.MOVIE)
    }

    override suspend fun fetchDiscoverTv(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/tv?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw, forcedCategory = MediaCategory.SERIES)
    }

    override suspend fun fetchUpcomingMovies(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/movies/upcoming?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw, forcedCategory = MediaCategory.MOVIE)
    }

    fun parseDownloadTelemetry(dlList: List<SeerDownloadStatusDto>?): Triple<Float?, String?, String?> {
        if (dlList.isNullOrEmpty()) return Triple(null, null, null)

        var totalSize = 0L
        var totalSizeLeft = 0L
        var eta: String? = null
        var foundAny = false

        for (dl in dlList) {
            foundAny = true
            totalSize += dl.size
            totalSizeLeft += dl.sizeLeft

            if (eta == null) {
                val t = (dl.timeLeft ?: dl.timeleft ?: "").trim()
                if (t.isNotBlank() && t != "0" && t != "00:00:00") {
                    eta = t
                }
            }
        }

        if (!foundAny) return Triple(null, null, null)

        val progress = if (totalSize > 0L) {
            val downloaded = (totalSize - totalSizeLeft).coerceAtLeast(0L)
            (downloaded.toDouble() / totalSize.toDouble()).toFloat().coerceIn(0f, 1f)
        } else null

        val sizeStr = if (totalSize > 0L) {
            val downloaded = (totalSize - totalSizeLeft).coerceAtLeast(0L)
            "${formatBytes(downloaded)} / ${formatBytes(totalSize)}"
        } else null

        return Triple(progress, eta, sizeStr)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 Mo"
        val gigabytes = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gigabytes >= 1.0) {
            String.format(java.util.Locale.US, "%.1f Go", gigabytes)
        } else {
            val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.0f Mo", megabytes)
        }
    }

    override suspend fun fetchRequests(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/request?take=25&skip=0&filter=all&sort=added", config.apiKey)
            ?: return@withContext emptyList()

        val results = mutableListOf<MediaItem>()
        try {
            val root = json.decodeFromString<SeerRequestListDto>(raw)
            for (reqObj in root.results) {
                val mediaObj = reqObj.media ?: continue
                val mediaType = mediaObj.mediaType
                val isTv = mediaType.equals("tv", ignoreCase = true)
                val tmdbId = mediaObj.tmdbId
                val statusCode = mediaObj.status
                val seerStatus = SeerMediaStatus.fromCode(statusCode)

                val dlList = mediaObj.downloadStatus ?: mediaObj.downloadStatus4k ?: reqObj.downloadStatus
                val (progress, eta, sizeStr) = parseDownloadTelemetry(dlList)

                var title = reqObj.movie?.title?.ifBlank { null }
                    ?: reqObj.tv?.name?.ifBlank { null }
                    ?: ""

                var posterPath = reqObj.movie?.posterPath?.ifBlank { null }
                    ?: reqObj.tv?.posterPath?.ifBlank { null }
                    ?: ""
                var backdropPath = reqObj.movie?.backdropPath?.ifBlank { null }
                    ?: reqObj.tv?.backdropPath?.ifBlank { null }
                    ?: ""

                if (title.isBlank() && tmdbId > 0) {
                    val cached = metadataCache[Pair(isTv, tmdbId)]
                    if (cached != null) {
                        title = cached.first
                        posterPath = cached.second
                        backdropPath = cached.third
                    } else {
                        val endpoint = if (isTv) "$server/api/v1/tv/$tmdbId?language=${currentLang()}" else "$server/api/v1/movie/$tmdbId?language=${currentLang()}"
                        val detailRaw = httpGet(endpoint, config.apiKey)
                        if (!detailRaw.isNullOrBlank()) {
                            try {
                                val dJson = json.decodeFromString<SeerDiscoverItemDto>(detailRaw)
                                title = if (isTv) dJson.name else dJson.title
                                posterPath = dJson.posterPath.orEmpty()
                                backdropPath = dJson.backdropPath.orEmpty()
                                if (title.isNotBlank()) {
                                    metadataCache[Pair(isTv, tmdbId)] = Triple(title, posterPath, backdropPath)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                if (title.isBlank()) {
                    title = if (isTv) "Série #$tmdbId" else "Film #$tmdbId"
                }

                val poster = if (posterPath.isNotBlank()) "$TMDB_IMG_W500$posterPath" else ""
                val backdrop = if (backdropPath.isNotBlank()) "$TMDB_IMG_W1280$backdropPath" else poster

                val quality = when {
                    progress != null -> "↓ ${(progress * 100).toInt()}%"
                    else -> ""
                }

                results.add(
                    MediaItem(
                        id = "seer_req_${reqObj.hashCode()}_${tmdbId}",
                        title = title,
                        category = MediaCategory.SEER_REQUEST,
                        groupTitle = "",
                        rating = "",
                        year = "",
                        durationOrSeasons = "",
                        qualityBadge = quality,
                        audioBadge = "",
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        tmdbId = tmdbId,
                        seerStatus = seerStatus,
                        downloadProgress = progress,
                        downloadEta = eta,
                        downloadSizeStr = sizeStr
                    )
                )
            }
        } catch (_: Exception) {}
        results
    }

    override suspend fun search(config: SeerConfig, query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || query.isBlank()) return@withContext emptyList()
        val enc = URLEncoder.encode(query.trim(), "UTF-8")
        val raw = httpGet("$server/api/v1/search?query=$enc&language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw)
    }

    override suspend fun requestMedia(config: SeerConfig, tmdbId: Int, isTv: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext Result.failure(Exception("Seer is not configured"))

        try {
            val endpoint = "$server/api/v1/request"
            val payload = SeerRequestPayload(
                mediaType = if (isTv) "tv" else "movie",
                mediaId = tmdbId,
                seasons = if (isTv) "all" else null
            )
            val body = json.encodeToString(payload)

            val (code, text) = httpPost(endpoint, config.apiKey, body)
            if (code in 200..299) {
                Result.success("Demande envoyée avec succès sur Seer !")
            } else {
                Result.failure(Exception("Erreur Seer ($code) : $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSeerResults(
        rawJson: String,
        forcedCategory: MediaCategory? = null
    ): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val root = json.decodeFromString<SeerDiscoverResultDto>(rawJson)

            for (obj in root.results) {
                val tmdbId = obj.id
                if (tmdbId <= 0) continue

                val isTv = if (forcedCategory != null) {
                    forcedCategory == MediaCategory.SERIES
                } else {
                    obj.mediaType.equals("tv", ignoreCase = true)
                }

                val title = if (isTv) {
                    obj.name.ifBlank { obj.title }
                } else {
                    obj.title.ifBlank { obj.name }
                }
                if (title.isBlank()) continue

                val overview = obj.overview
                val posterPath = obj.posterPath.orEmpty()
                val backdropPath = obj.backdropPath.orEmpty()
                val voteAvg = obj.voteAverage
                val rating = if (voteAvg > 0.0) String.format(java.util.Locale.US, "%.1f", voteAvg) else ""
                val releaseDate = if (isTv) obj.firstAirDate else obj.releaseDate
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""

                val mediaInfo = obj.mediaInfo
                val statusCode = mediaInfo?.status ?: 1
                val seerStatus = SeerMediaStatus.fromCode(statusCode)

                val dlList = mediaInfo?.downloadStatus ?: mediaInfo?.downloadStatus4k
                val (progress, eta, sizeStr) = parseDownloadTelemetry(dlList)

                val poster = if (posterPath.isNotBlank()) "$TMDB_IMG_W500$posterPath" else ""
                val backdrop = if (backdropPath.isNotBlank()) "$TMDB_IMG_W1280$backdropPath" else poster

                val category = if (isTv) MediaCategory.SERIES else MediaCategory.MOVIE

                val quality = when {
                    progress != null -> "↓ ${(progress * 100).toInt()}%"
                    else -> ""
                }

                list.add(
                    MediaItem(
                        id = "seer_${if (isTv) "tv" else "movie"}_${tmdbId}",
                        title = title,
                        category = category,
                        synopsis = overview,
                        groupTitle = "",
                        rating = rating,
                        year = year,
                        durationOrSeasons = "",
                        qualityBadge = quality,
                        audioBadge = "5.1",
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        tmdbId = tmdbId,
                        seerStatus = seerStatus,
                        downloadProgress = progress,
                        downloadEta = eta,
                        downloadSizeStr = sizeStr
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
