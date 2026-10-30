package dev.dylanakp.abysstvlauncher.data

import android.util.Log
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object SeerRepository {

    private const val TMDB_IMG_W500 = "https://image.tmdb.org/t/p/w500"
    private const val TMDB_IMG_W1280 = "https://image.tmdb.org/t/p/w1280"

    private val client by lazy {
        OkHttpClient.Builder()
            .dns(PreferIpv4Dns)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun cleanUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isNotBlank() && !u.startsWith("http://") && !u.startsWith("https://")) {
            val isLocal = u.startsWith("192.168.") || u.startsWith("10.") || u.startsWith("172.") || u.startsWith("localhost") || u.contains(":5055")
            u = if (isLocal) "http://$u" else "https://$u"
        }
        return u
    }

    fun clearCache() = metadataCache.clear()

    private fun currentLang(): String = if (java.util.Locale.getDefault().language.startsWith("fr")) "fr" else "en"

    private fun httpGet(urlStr: String, apiKey: String): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "AbyssLauncher/2.0")
                .header("Accept", "application/json")
                .header("Accept-Language", currentLang())

            if (apiKey.isNotBlank()) {
                if (apiKey.startsWith("connect.sid=")) {
                    reqBuilder.header("Cookie", apiKey)
                } else {
                    reqBuilder.header("X-Api-Key", apiKey)
                }
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) {
                resp.body?.string()
            } else if (resp.code == 401 || resp.code == 403) {
                Log.w("AbyssSeer", "Seer HTTP ${resp.code} for $urlStr - token invalid or expired")
                throw SecurityException("401 Unauthorized")
            } else {
                Log.w("AbyssSeer", "Seer HTTP ${resp.code} for $urlStr")
                null
            }
        } catch (e: Exception) {
            Log.e("AbyssSeer", "Error in httpGet $urlStr: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    private fun httpPost(urlStr: String, apiKey: String, body: JSONObject): Pair<Int, String> {
        return try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val reqBody = body.toString().toRequestBody(mediaType)
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

    suspend fun authenticateWithJellyfin(serverUrl: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(serverUrl)
        if (server.isBlank() || user.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL and user are required"))
        }

        try {
            Log.d("AbyssSeer", "Authenticating against Seer ($server) using Jellyfin login for $user")
            val url = URL("$server/api/v1/auth/jellyfin")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONObject().apply {
                put("username", user)
                put("password", pass)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

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
                            apiKey = JSONObject(settingsJson).optString("apiKey", "")
                        }
                    } catch (_: Exception) {}
                }

                val finalToken = when {
                    apiKey.isNotBlank() -> apiKey
                    !sessionCookie.isNullOrBlank() -> sessionCookie
                    else -> "connected"
                }
                Log.d("AbyssSeer", "Authenticated successfully! Token acquired.")
                Result.success(finalToken)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Log.e("AbyssSeer", "Seer auth failed ($code): $err")
                Result.failure(Exception("Seer auth failed ($code): $err"))
            }
        } catch (e: Exception) {
            Log.e("AbyssSeer", "Seer auth error", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(config: SeerConfig): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (server.isBlank() || config.apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL and API Key are required"))
        }

        val raw = httpGet("$server/api/v1/status", config.apiKey)
            ?: return@withContext Result.failure(Exception("Cannot connect to Seer server"))

        try {
            val json = JSONObject(raw)
            val version = json.optString("version", "Seer Connected")
            Result.success(version)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTrending(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/trending?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw)
    }

    suspend fun fetchDiscoverMovies(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/movies?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw, forcedCategory = MediaCategory.MOVIE)
    }

    suspend fun fetchDiscoverTv(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/tv?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw, forcedCategory = MediaCategory.SERIES)
    }

    suspend fun fetchUpcomingMovies(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/discover/movies/upcoming?language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw, forcedCategory = MediaCategory.MOVIE)
    }

    private val metadataCache = java.util.concurrent.ConcurrentHashMap<Pair<Boolean, Int>, Triple<String, String, String>>()

    fun parseDownloadTelemetry(dlArray: JSONArray?): Triple<Float?, String?, String?> {
        if (dlArray == null || dlArray.length() == 0) return Triple(null, null, null)

        var totalSize = 0L
        var totalSizeLeft = 0L
        var eta: String? = null
        var foundAny = false

        for (i in 0 until dlArray.length()) {
            val dl = dlArray.optJSONObject(i) ?: continue
            foundAny = true
            val size = dl.optLong("size", 0L)
            val sizeLeft = dl.optLong("sizeLeft", 0L)
            totalSize += size
            totalSizeLeft += sizeLeft

            if (eta == null) {
                val t = dl.optString("timeLeft", dl.optString("timeleft", "")).trim()
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

    suspend fun fetchRequests(config: SeerConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) return@withContext emptyList()
        val raw = httpGet("$server/api/v1/request?take=25&skip=0&filter=all&sort=added", config.apiKey)
            ?: return@withContext emptyList()

        val results = mutableListOf<MediaItem>()
        try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("results") ?: return@withContext emptyList()
            for (i in 0 until arr.length()) {
                val reqObj = arr.optJSONObject(i) ?: continue
                val mediaObj = reqObj.optJSONObject("media") ?: continue
                val mediaType = mediaObj.optString("mediaType", "movie")
                val isTv = mediaType.equals("tv", ignoreCase = true)
                val tmdbId = mediaObj.optInt("tmdbId", 0)
                val statusCode = mediaObj.optInt("status", 2)
                val seerStatus = SeerMediaStatus.fromCode(statusCode)

                val dlArray = mediaObj.optJSONArray("downloadStatus")
                    ?: mediaObj.optJSONArray("downloadStatus4k")
                    ?: reqObj.optJSONArray("downloadStatus")
                val (progress, eta, sizeStr) = parseDownloadTelemetry(dlArray)

                // Detailed media metadata might be in reqObj.movie/tv or fetched from Seer endpoint
                var title = reqObj.optJSONObject("movie")?.optString("title")
                    ?: reqObj.optJSONObject("tv")?.optString("name")
                    ?: ""

                var posterPath = reqObj.optJSONObject("movie")?.optString("posterPath")
                    ?: reqObj.optJSONObject("tv")?.optString("posterPath")
                    ?: ""
                var backdropPath = reqObj.optJSONObject("movie")?.optString("backdropPath")
                    ?: reqObj.optJSONObject("tv")?.optString("backdropPath")
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
                                val dJson = JSONObject(detailRaw)
                                title = if (isTv) dJson.optString("name", "") else dJson.optString("title", "")
                                posterPath = dJson.optString("posterPath", "")
                                backdropPath = dJson.optString("backdropPath", "")
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
                    seerStatus == SeerMediaStatus.PROCESSING -> "Téléchargement…"
                    else -> seerStatus.localizedLabel
                }

                results.add(
                    MediaItem(
                        id = "seer_req_${tmdbId}",
                        title = title,
                        localizedTitle = title,
                        category = if (isTv) MediaCategory.SERIES else MediaCategory.MOVIE,
                        groupTitle = "Seer Demandes",
                        localizedGroupTitle = "Seer Requests",
                        qualityBadge = quality,
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        tmdbId = if (tmdbId > 0) tmdbId else null,
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

    suspend fun search(config: SeerConfig, query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || query.isBlank()) return@withContext emptyList()
        val enc = URLEncoder.encode(query, "UTF-8")
        val raw = httpGet("$server/api/v1/search?query=$enc&language=${currentLang()}", config.apiKey) ?: return@withContext emptyList()
        parseSeerResults(raw)
    }

    suspend fun requestMedia(config: SeerConfig, tmdbId: Int, isTv: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Seer is not configured"))
        }

        try {
            val endpoint = "$server/api/v1/request"
            val body = JSONObject().apply {
                put("mediaType", if (isTv) "tv" else "movie")
                put("mediaId", tmdbId)
                if (isTv) {
                    put("seasons", "all")
                }
            }

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
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("results") ?: return emptyList()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val tmdbId = obj.optInt("id", 0)
                if (tmdbId <= 0) continue

                val mediaType = obj.optString("mediaType", "")
                val isTv = if (forcedCategory != null) {
                    forcedCategory == MediaCategory.SERIES
                } else {
                    mediaType.equals("tv", ignoreCase = true)
                }

                val title = if (isTv) {
                    obj.optString("name", obj.optString("title", ""))
                } else {
                    obj.optString("title", obj.optString("name", ""))
                }
                if (title.isBlank()) continue

                val overview = obj.optString("overview", "")
                val posterPath = obj.optString("posterPath", "")
                val backdropPath = obj.optString("backdropPath", "")
                val voteAvg = obj.optDouble("voteAverage", 0.0)
                val rating = if (voteAvg > 0.0) String.format(java.util.Locale.US, "%.1f", voteAvg) else ""
                val releaseDate = if (isTv) obj.optString("firstAirDate", "") else obj.optString("releaseDate", "")
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""

                val mediaInfo = obj.optJSONObject("mediaInfo")
                val statusCode = mediaInfo?.optInt("status", 1) ?: 1
                val seerStatus = SeerMediaStatus.fromCode(statusCode)

                val dlArray = mediaInfo?.optJSONArray("downloadStatus")
                    ?: mediaInfo?.optJSONArray("downloadStatus4k")
                val (progress, eta, sizeStr) = parseDownloadTelemetry(dlArray)

                val poster = if (posterPath.isNotBlank()) "$TMDB_IMG_W500$posterPath" else ""
                val backdrop = if (backdropPath.isNotBlank()) "$TMDB_IMG_W1280$backdropPath" else poster

                val category = if (isTv) MediaCategory.SERIES else MediaCategory.MOVIE

                val quality = when {
                    progress != null -> "↓ ${(progress * 100).toInt()}%"
                    seerStatus == SeerMediaStatus.PROCESSING -> "Téléchargement…"
                    else -> seerStatus.localizedLabel
                }

                list.add(
                    MediaItem(
                        id = "seer_${if (isTv) "tv" else "movie"}_${tmdbId}",
                        title = title,
                        localizedTitle = title,
                        category = category,
                        synopsis = overview,
                        localizedSynopsis = overview,
                        groupTitle = if (isTv) "Séries" else "Films",
                        localizedGroupTitle = if (isTv) "Séries" else "Films",
                        rating = rating,
                        year = year,
                        duration = if (isTv) "Série" else "",
                        localizedDuration = if (isTv) "Série" else "",
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
