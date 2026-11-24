package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import android.util.Log
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.domain.repository.SeerRepository as ISeerRepository
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

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

private sealed interface SeerCredential {
    data class ApiKey(val value: String) : SeerCredential
    data class SessionCookie(val value: String) : SeerCredential

    fun applyTo(builder: Request.Builder) {
        when (this) {
            is ApiKey -> builder.header("X-Api-Key", value)
            is SessionCookie -> builder.header("Cookie", value)
        }
    }

    fun storedValue(): String = when (this) {
        is ApiKey -> value
        is SessionCookie -> value
    }

    companion object {
        fun from(raw: String): SeerCredential? = when {
            raw.isBlank() -> null
            raw.startsWith("connect.sid=") -> SessionCookie(raw.substringBefore(";"))
            else -> ApiKey(raw)
        }
    }
}

class SeerRepository(
    private val client: OkHttpClient,
    private val json: Json,
    private val context: Context
) : ISeerRepository {

    companion object {
        private const val TMDB_IMG_W500 = "https://image.tmdb.org/t/p/w500"
        private const val TMDB_IMG_W1280 = "https://image.tmdb.org/t/p/w1280"
        private const val METADATA_CACHE_MAX = 200
    }

    private val metadataCache = object : LinkedHashMap<Pair<Boolean, Int>, Triple<String, String, String>>(
        METADATA_CACHE_MAX, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<Boolean, Int>, Triple<String, String, String>>
        ): Boolean = size > METADATA_CACHE_MAX
    }

    override fun clearCache() = synchronized(metadataCache) { metadataCache.clear() }

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

            SeerCredential.from(apiKey)?.applyTo(reqBuilder)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("AbyssSeer", "HTTP ${resp.code} on $urlStr")
                    return null
                }
                resp.body?.string()
            }
        } catch (e: IOException) {
            Log.w("AbyssSeer", "Network error in httpGet $urlStr", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("AbyssSeer", "Invalid URL in httpGet $urlStr", e)
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

            SeerCredential.from(apiKey)?.applyTo(reqBuilder)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                Pair(resp.code, text)
            }
        } catch (e: IOException) {
            Log.w("AbyssSeer", "Network error in httpPost $urlStr", e)
            Pair(-1, e.message ?: "Network error")
        } catch (e: IllegalArgumentException) {
            Log.w("AbyssSeer", "Invalid URL in httpPost $urlStr", e)
            Pair(-1, e.message ?: "Invalid URL")
        }
    }

    override suspend fun authenticateWithJellyfin(serverUrl: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(serverUrl)
        if (server.isBlank() || user.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL and user are required"))
        }

        try {
            val body = json.encodeToString(SeerLoginRequest(username = user, password = pass))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$server/api/v1/auth/jellyfin")
                .post(body)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val cookies = response.headers("Set-Cookie")
                    val sessionCookie = cookies.find { it.startsWith("connect.sid=") }
                        ?.substringBefore(";")

                    var apiKey = ""
                    if (!sessionCookie.isNullOrBlank()) {
                        try {
                            val settingsReq = Request.Builder()
                                .url("$server/api/v1/settings/main")
                                .header("Accept", "application/json")
                                .apply { SeerCredential.SessionCookie(sessionCookie).applyTo(this) }
                                .build()
                            client.newCall(settingsReq).execute().use { settingsResp ->
                                if (settingsResp.isSuccessful) {
                                    val settingsJson = settingsResp.body?.string().orEmpty()
                                    apiKey = json.decodeFromString<SeerSettingsResponse>(settingsJson).apiKey
                                }
                            }
                        } catch (e: IOException) {
                            Log.w("AbyssSeer", "Failed to fetch Seer API key after login", e)
                        } catch (e: SerializationException) {
                            Log.w("AbyssSeer", "Failed to parse Seer settings response", e)
                        }
                    }

                    val credential = when {
                        apiKey.isNotBlank() -> SeerCredential.ApiKey(apiKey)
                        !sessionCookie.isNullOrBlank() -> SeerCredential.SessionCookie(sessionCookie)
                        else -> return@withContext Result.failure(
                            IllegalStateException("Seer authentication produced no usable credential")
                        )
                    }
                    Result.success(credential.storedValue())
                } else {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    Result.failure(Exception("Authentication failed (${response.code}): $err"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AbyssSeer", "Seer authentication failed", e)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("AbyssSeer", "Failed to parse Seer status response", e)
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
        if (bytes <= 0L) return context.getString(R.string.seer_bytes_zero)
        val gigabytes = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gigabytes >= 1.0) {
            String.format(java.util.Locale.US, context.getString(R.string.seer_bytes_gb), gigabytes)
        } else {
            val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, context.getString(R.string.seer_bytes_mb), megabytes)
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
            val missingLookups = root.results.mapNotNull { reqObj ->
                val mediaObj = reqObj.media ?: return@mapNotNull null
                val isTv = mediaObj.mediaType.equals("tv", ignoreCase = true)
                val tmdbId = mediaObj.tmdbId
                val title = reqObj.movie?.title?.ifBlank { null } ?: reqObj.tv?.name?.ifBlank { null } ?: ""
                if (title.isBlank() && tmdbId > 0 && !synchronized(metadataCache) { metadataCache.containsKey(Pair(isTv, tmdbId)) }) {
                    Pair(isTv, tmdbId)
                } else null
            }.distinct()

            if (missingLookups.isNotEmpty()) {
                val fetched = coroutineScope {
                    missingLookups.map { (isTv, tmdbId) ->
                        async {
                            val endpoint = if (isTv) "$server/api/v1/tv/$tmdbId?language=${currentLang()}" else "$server/api/v1/movie/$tmdbId?language=${currentLang()}"
                            val detailRaw = httpGet(endpoint, config.apiKey) ?: return@async null
                            try {
                                val dJson = json.decodeFromString<SeerDiscoverItemDto>(detailRaw)
                                val title = if (isTv) dJson.name else dJson.title
                                if (title.isNotBlank()) {
                                    Pair(isTv, tmdbId) to Triple(title, dJson.posterPath.orEmpty(), dJson.backdropPath.orEmpty())
                                } else null
                            } catch (e: SerializationException) {
                                Log.w("AbyssSeer", "Failed to parse Seer detail for tmdbId=$tmdbId", e)
                                null
                            }
                        }
                    }.awaitAll()
                }
                synchronized(metadataCache) {
                    fetched.filterNotNull().forEach { (key, meta) -> metadataCache[key] = meta }
                }
            }

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
                    val cached = synchronized(metadataCache) { metadataCache[Pair(isTv, tmdbId)] }
                    if (cached != null) {
                        title = cached.first
                        posterPath = cached.second
                        backdropPath = cached.third
                    }
                }

                if (title.isBlank()) {
                    title = context.getString(
                        if (isTv) R.string.seer_untitled_series else R.string.seer_untitled_movie,
                        tmdbId
                    )
                }

                val poster = if (posterPath.isNotBlank()) "$TMDB_IMG_W500$posterPath" else ""
                val backdrop = if (backdropPath.isNotBlank()) "$TMDB_IMG_W1280$backdropPath" else poster

                val quality = when {
                    progress != null -> "↓ ${(progress * 100).toInt()}%"
                    else -> ""
                }

                results.add(
                    MediaItem(
                        id = "seer_req_${mediaType}_${tmdbId}",
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("AbyssSeer", "Failed to parse Seer requests", e)
        }
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
                Result.success(context.getString(R.string.seer_request_success))
            } else {
                Result.failure(Exception(context.getString(R.string.seer_request_error, code, text)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AbyssSeer", "Seer requestMedia failed for tmdbId=$tmdbId", e)
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
        } catch (e: SerializationException) {
            Log.w("AbyssSeer", "Failed to parse Seer discover results", e)
        }
        return list
    }
}
