package dev.dylanakp.abysstvlauncher.data

import android.util.Log

import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SeriesSeason
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object JellyfinRepository {

    private const val CLIENT_NAME = "AbyssLauncher"
    private const val DEVICE_NAME = "Android TV"
    private const val DEVICE_ID = "Abyss-TV-Player"
    private const val CLIENT_VERSION = "2.0.0"

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
            val isLocal = u.startsWith("192.168.") || u.startsWith("10.") || u.startsWith("172.") || u.startsWith("localhost") || u.contains(":8096")
            u = if (isLocal) "http://$u" else "https://$u"
        }
        return u
    }

    private fun buildAuthHeader(token: String = ""): String {
        return "MediaBrowser Client=\"$CLIENT_NAME\", Device=\"$DEVICE_NAME\", DeviceId=\"$DEVICE_ID\", Version=\"$CLIENT_VERSION\"" +
            if (token.isNotBlank()) ", Token=\"$token\"" else ""
    }

    suspend fun authenticate(config: JellyfinConfig): Result<JellyfinConfig> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (server.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        try {
            Log.d("AbyssJellyfin", "Authenticating against $server for user ${config.username}")
            val endpoint = "$server/Users/AuthenticateByName"
            val body = JSONObject().apply {
                put("Username", config.username)
                put("Pw", config.password)
            }
            val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Authorization", buildAuthHeader())
                .header("X-Emby-Authorization", buildAuthHeader())
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                if (response.isSuccessful) {
                    val resp = response.body?.string() ?: ""
                    val json = JSONObject(resp)
                    val token = json.optString("AccessToken", "")
                    val serverId = json.optString("ServerId", "")
                    val userObj = json.optJSONObject("User")
                    val userId = userObj?.optString("Id", "") ?: ""
                    var serverName = json.optJSONObject("Server")?.optString("Name")
                        ?: json.optString("ServerName", "")

                    if (serverName.isBlank()) {
                        val publicInfo = httpGet("$server/System/Info/Public", token)
                        if (publicInfo != null) {
                            try {
                                serverName = JSONObject(publicInfo).optString("ServerName", "Jellyfin")
                            } catch (_: Exception) {}
                        }
                    }
                    if (serverName.isBlank()) serverName = "Jellyfin"

                    val updated = config.copy(
                        serverUrl = server,
                        userId = userId,
                        accessToken = token,
                        serverName = serverName
                    )
                    Log.d("AbyssJellyfin", "Authenticated successfully! User: ${config.username}, UserID: $userId, Server: $serverName")
                    Result.success(updated)
                } else {
                    val err = response.body?.string() ?: "HTTP $code"
                    Log.e("AbyssJellyfin", "Authentication failed ($code): $err")
                    Result.failure(Exception("Jellyfin auth failed ($code): $err"))
                }
            }
        } catch (e: Exception) {
            Log.e("AbyssJellyfin", "Authentication error", e)
            Result.failure(e)
        }
    }

    private fun httpGet(urlStr: String, token: String): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("Authorization", buildAuthHeader(token))
                .header("X-Emby-Authorization", buildAuthHeader(token))

            if (token.isNotBlank()) {
                reqBuilder.header("X-Emby-Token", token)
                reqBuilder.header("X-MediaBrowser-Token", token)
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else if (response.code == 401) {
                    Log.w("AbyssJellyfin", "HTTP 401 Unauthorized for $urlStr - token invalid or expired")
                    throw SecurityException("401 Unauthorized")
                } else {
                    Log.w("AbyssJellyfin", "HTTP ${response.code} for $urlStr")
                    null
                }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.e("AbyssJellyfin", "Network error in httpGet: ${e.message}")
            null
        }
    }

    private fun httpPost(urlStr: String, token: String, jsonBody: JSONObject? = null): Boolean {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("Authorization", buildAuthHeader(token))
                .header("X-Emby-Authorization", buildAuthHeader(token))

            if (token.isNotBlank()) {
                reqBuilder.header("X-Emby-Token", token)
                reqBuilder.header("X-MediaBrowser-Token", token)
            }

            val body = (jsonBody?.toString() ?: "").toRequestBody("application/json; charset=utf-8".toMediaType())
            reqBuilder.post(body)

            client.newCall(reqBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun httpDelete(urlStr: String, token: String): Boolean {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("Authorization", buildAuthHeader(token))
                .header("X-Emby-Authorization", buildAuthHeader(token))
                .delete()

            if (token.isNotBlank()) {
                reqBuilder.header("X-Emby-Token", token)
                reqBuilder.header("X-MediaBrowser-Token", token)
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getStreamUrl(config: JellyfinConfig, itemId: String): String {
        val server = cleanUrl(config.serverUrl)
        val token = config.accessToken
        return "$server/Videos/$itemId/stream?static=true&api_key=$token"
    }

    fun getImageUrl(serverUrl: String, itemId: String, isBackdrop: Boolean = false): String {
        val server = cleanUrl(serverUrl)
        if (server.isBlank() || itemId.isBlank()) return ""
        return if (isBackdrop) {
            "$server/Items/$itemId/Images/Backdrop/0?fillWidth=1280&quality=85"
        } else {
            "$server/Items/$itemId/Images/Primary?fillWidth=400&quality=90"
        }
    }

    suspend fun syncLibrary(config: JellyfinConfig): Result<SyncedLibrary> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Jellyfin is not configured"))
        }

        try {
            coroutineScope {
                val moviesDeferred = async { fetchMovies(config) }
                val seriesDeferred = async { fetchSeries(config) }

                val movies = moviesDeferred.await()
                val series = seriesDeferred.await()
                Log.d("AbyssJellyfin", "Sync completed: ${movies.size} movies, ${series.size} series")

                Result.success(
                    SyncedLibrary(
                        movies = movies,
                        series = series
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMovies(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        val fields = "Overview,Genres,CommunityRating,PremiereDate,ProductionYear,RunTimeTicks,MediaStreams,PrimaryImageAspectRatio,ProviderIds,UserData"
        val url = "$server/Users/$userId/Items?IncludeItemTypes=Movie&Recursive=true&SortBy=DateCreated,SortName&SortOrder=Descending&Fields=$fields"

        val raw = httpGet(url, token) ?: return@withContext emptyList()
        val items = mutableListOf<MediaItem>()
        try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("Items") ?: return@withContext emptyList()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("Id", "")
                if (id.isBlank()) continue
                val name = obj.optString("Name", "")
                val overview = obj.optString("Overview", "")
                val year = obj.optInt("ProductionYear", 0).let { if (it > 0) it.toString() else "" }
                val rating = obj.optDouble("CommunityRating", 0.0).let { if (it > 0.0) String.format(java.util.Locale.US, "%.1f", it) else "" }

                val runTimeTicks = obj.optLong("RunTimeTicks", 0L)
                val durationMs = runTimeTicks / 10_000L
                val durationStr = if (durationMs > 0L) {
                    val m = durationMs / 60_000L
                    "${m / 60}h ${m % 60}m"
                } else ""

                val userData = obj.optJSONObject("UserData")
                val played = userData?.optBoolean("Played", false) ?: false
                val posTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
                val posMs = posTicks / 10_000L
                val progress = when {
                    played -> 1.0f
                    durationMs > 0L && posMs > 0L -> (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    else -> 0f
                }

                val genresArr = obj.optJSONArray("Genres")
                val groupTitle = if (genresArr != null && genresArr.length() > 0) {
                    genresArr.optString(0, "Films")
                } else "Films"

                val providerIds = obj.optJSONObject("ProviderIds")
                val tmdbId = providerIds?.optInt("Tmdb", 0)?.let { if (it > 0) it else null }

                val poster = "$server/Items/$id/Images/Primary?fillWidth=400&quality=90"
                val backdrop = "$server/Items/$id/Images/Backdrop/0?fillWidth=1280&quality=85"
                val streamUrl = "$server/Videos/$id/stream?static=true&api_key=$token"

                items.add(
                    MediaItem(
                        id = id,
                        title = name,
                        localizedTitle = name,
                        synopsis = overview,
                        localizedSynopsis = overview,
                        category = MediaCategory.MOVIE,
                        groupTitle = groupTitle,
                        localizedGroupTitle = groupTitle,
                        rating = rating,
                        year = year,
                        duration = durationStr,
                        localizedDuration = durationStr,
                        qualityBadge = "4K UHD",
                        audioBadge = "Atmos 7.1",
                        streamUrl = streamUrl,
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        progressPercent = progress,
                        jellyfinId = id,
                        tmdbId = tmdbId
                    )
                )
            }
        } catch (_: Exception) {}
        items
    }

    suspend fun fetchSeries(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        val fields = "Overview,Genres,CommunityRating,PremiereDate,ProductionYear,RunTimeTicks,PrimaryImageAspectRatio,ProviderIds,UserData"
        val url = "$server/Users/$userId/Items?IncludeItemTypes=Series&Recursive=true&SortBy=DateCreated,SortName&SortOrder=Descending&Fields=$fields"

        val raw = httpGet(url, token) ?: return@withContext emptyList()
        val items = mutableListOf<MediaItem>()
        try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("Items") ?: return@withContext emptyList()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("Id", "")
                if (id.isBlank()) continue
                val name = obj.optString("Name", "")
                val overview = obj.optString("Overview", "")
                val year = obj.optInt("ProductionYear", 0).let { if (it > 0) it.toString() else "" }
                val rating = obj.optDouble("CommunityRating", 0.0).let { if (it > 0.0) String.format(java.util.Locale.US, "%.1f", it) else "" }

                val genresArr = obj.optJSONArray("Genres")
                val groupTitle = if (genresArr != null && genresArr.length() > 0) {
                    genresArr.optString(0, "Séries")
                } else "Séries"

                val providerIds = obj.optJSONObject("ProviderIds")
                val tmdbId = providerIds?.optInt("Tmdb", 0)?.let { if (it > 0) it else null }

                val poster = "$server/Items/$id/Images/Primary?fillWidth=400&quality=90"
                val backdrop = "$server/Items/$id/Images/Backdrop/0?fillWidth=1280&quality=85"

                items.add(
                    MediaItem(
                        id = id,
                        title = name,
                        localizedTitle = name,
                        synopsis = overview,
                        localizedSynopsis = overview,
                        category = MediaCategory.SERIES,
                        groupTitle = groupTitle,
                        localizedGroupTitle = groupTitle,
                        rating = rating,
                        year = year,
                        duration = "Série",
                        localizedDuration = "Série",
                        qualityBadge = "HD",
                        audioBadge = "5.1",
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        jellyfinId = id,
                        tmdbId = tmdbId
                    )
                )
            }
        } catch (_: Exception) {}
        items
    }

    suspend fun fetchResumeAndNextUp(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        if (!config.isConfigured) return@withContext emptyList()

        val results = mutableListOf<MediaItem>()
        val seenSeriesIds = mutableSetOf<String>()

        // 1. Fetch Resume items (in-progress movies and partially watched episodes)
        val resumeUrl = "$server/UserItems/Resume?userId=$userId&limit=25&mediaTypes=Movie,Episode&Fields=Overview,PrimaryImageAspectRatio,UserData,RunTimeTicks,SeriesName,SeriesId,SeasonId,ParentIndexNumber,IndexNumber"
        val resumeRaw = httpGet(resumeUrl, token)
        if (resumeRaw != null) {
            try {
                val root = JSONObject(resumeRaw)
                val arr = root.optJSONArray("Items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val type = obj.optString("Type", "")
                        val id = obj.optString("Id", "")
                        val runTimeTicks = obj.optLong("RunTimeTicks", 0L)
                        val userData = obj.optJSONObject("UserData")
                        val posTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
                        val durationMs = runTimeTicks / 10_000L
                        val posMs = posTicks / 10_000L
                        val progress = if (durationMs > 0L) (posMs.toFloat() / durationMs.toFloat()).coerceIn(0.01f, 1f) else 0.05f

                        if (type.equals("Movie", ignoreCase = true)) {
                            val name = obj.optString("Name", "")
                            results.add(
                                MediaItem(
                                    id = id,
                                    title = name,
                                    localizedTitle = name,
                                    category = MediaCategory.MOVIE,
                                    synopsis = obj.optString("Overview", ""),
                                    localizedSynopsis = obj.optString("Overview", ""),
                                    qualityBadge = "REPRENDRE",
                                    streamUrl = "$server/Videos/$id/stream?static=true&api_key=$token",
                                    posterUrl = "$server/Items/$id/Images/Primary?fillWidth=400&quality=90",
                                    backdropUrl = "$server/Items/$id/Images/Backdrop/0?fillWidth=1280&quality=85",
                                    progressPercent = progress,
                                    jellyfinId = id
                                )
                            )
                        } else if (type.equals("Episode", ignoreCase = true)) {
                            val seriesId = obj.optString("SeriesId", "")
                            val seriesName = obj.optString("SeriesName", obj.optString("Name", ""))
                            val sNum = obj.optInt("ParentIndexNumber", 1)
                            val eNum = obj.optInt("IndexNumber", 1)
                            val epTitle = obj.optString("Name", "")
                            val sCode = "S${sNum.toString().padStart(2, '0')}E${eNum.toString().padStart(2, '0')}"

                            val targetSeriesId = seriesId.ifBlank { id }
                            seenSeriesIds.add(targetSeriesId)

                            results.add(
                                MediaItem(
                                    id = targetSeriesId,
                                    title = seriesName,
                                    localizedTitle = seriesName,
                                    subtitle = "$sCode • $epTitle",
                                    localizedSubtitle = "$sCode • $epTitle",
                                    category = MediaCategory.SERIES,
                                    synopsis = obj.optString("Overview", ""),
                                    localizedSynopsis = obj.optString("Overview", ""),
                                    qualityBadge = sCode,
                                    streamUrl = "$server/Videos/$id/stream?static=true&api_key=$token",
                                    posterUrl = "$server/Items/$targetSeriesId/Images/Primary?fillWidth=400&quality=90",
                                    backdropUrl = "$server/Items/$targetSeriesId/Images/Backdrop/0?fillWidth=1280&quality=85",
                                    progressPercent = progress,
                                    jellyfinId = id
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fetch NextUp (Next unwatched episode for series in progress!)
        val nextUpUrl = "$server/Shows/NextUp?userId=$userId&limit=25&Fields=Overview,PrimaryImageAspectRatio,UserData,RunTimeTicks,SeriesName,SeriesId,SeasonId,ParentIndexNumber,IndexNumber"
        val nextUpRaw = httpGet(nextUpUrl, token)
        if (nextUpRaw != null) {
            try {
                val root = JSONObject(nextUpRaw)
                val arr = root.optJSONArray("Items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("Id", "")
                        val seriesId = obj.optString("SeriesId", "")
                        val targetSeriesId = seriesId.ifBlank { id }

                        // Skip if already present in resume items (partially watched episode takes priority)
                        if (seenSeriesIds.contains(targetSeriesId)) continue
                        seenSeriesIds.add(targetSeriesId)

                        val seriesName = obj.optString("SeriesName", obj.optString("Name", ""))
                        val sNum = obj.optInt("ParentIndexNumber", 1)
                        val eNum = obj.optInt("IndexNumber", 1)
                        val epTitle = obj.optString("Name", "")
                        val sCode = "S${sNum.toString().padStart(2, '0')}E${eNum.toString().padStart(2, '0')}"

                        results.add(
                            MediaItem(
                                id = targetSeriesId,
                                title = seriesName,
                                localizedTitle = seriesName,
                                subtitle = "$sCode • $epTitle",
                                localizedSubtitle = "$sCode • $epTitle",
                                category = MediaCategory.SERIES,
                                synopsis = obj.optString("Overview", ""),
                                localizedSynopsis = obj.optString("Overview", ""),
                                qualityBadge = sCode,
                                streamUrl = "$server/Videos/$id/stream?static=true&api_key=$token",
                                posterUrl = "$server/Items/$targetSeriesId/Images/Primary?fillWidth=400&quality=90",
                                backdropUrl = "$server/Items/$targetSeriesId/Images/Backdrop/0?fillWidth=1280&quality=85",
                                progressPercent = 0.001f, // Next episode ready to play!
                                jellyfinId = id
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        Log.d("AbyssJellyfin", "Total resume + next up items: ${results.size}")
        results.distinctBy { it.id }
    }

    suspend fun fetchSeriesDetails(config: JellyfinConfig, seriesItem: MediaItem): SeriesDetails? = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        val seriesId = seriesItem.id

        val seasonsUrl = "$server/Shows/$seriesId/Seasons?userId=$userId&Fields=Overview,PrimaryImageAspectRatio"
        val seasonsRaw = httpGet(seasonsUrl, token) ?: return@withContext null

        try {
            val root = JSONObject(seasonsRaw)
            val seasonsArr = root.optJSONArray("Items") ?: return@withContext null
            val seasonList = mutableListOf<SeriesSeason>()

            for (s in 0 until seasonsArr.length()) {
                val sObj = seasonsArr.optJSONObject(s) ?: continue
                val seasonId = sObj.optString("Id", "")
                val seasonNum = sObj.optInt("IndexNumber", s + 1)
                val sName = sObj.optString("Name", "Saison $seasonNum")

                val epUrl = "$server/Shows/$seriesId/Episodes?seasonId=$seasonId&userId=$userId&Fields=Overview,PrimaryImageAspectRatio,UserData,RunTimeTicks,IndexNumber,ParentIndexNumber"
                val epRaw = httpGet(epUrl, token)
                val epList = mutableListOf<SeriesEpisode>()
                if (epRaw != null) {
                    val epRoot = JSONObject(epRaw)
                    val epArr = epRoot.optJSONArray("Items")
                    if (epArr != null) {
                        for (e in 0 until epArr.length()) {
                            val eObj = epArr.optJSONObject(e) ?: continue
                            val epId = eObj.optString("Id", "")
                            val epNum = eObj.optInt("IndexNumber", e + 1)
                            val epName = eObj.optString("Name", "Épisode $epNum")
                            val overview = eObj.optString("Overview", "")
                            val runTimeTicks = eObj.optLong("RunTimeTicks", 0L)
                            val durationMs = runTimeTicks / 10_000L
                            val userData = eObj.optJSONObject("UserData")
                            val played = userData?.optBoolean("Played", false) ?: false
                            val posTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
                            val posMs = posTicks / 10_000L
                            val progress = when {
                                played -> 1.0f
                                durationMs > 0L && posMs > 0L -> (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                else -> 0f
                            }

                            epList.add(
                                SeriesEpisode(
                                    id = epId,
                                    seriesId = seriesId,
                                    seasonNumber = seasonNum,
                                    episodeNumber = epNum,
                                    title = epName,
                                    plot = overview,
                                    streamUrl = "$server/Videos/$epId/stream?static=true&api_key=$token",
                                    posterUrl = "$server/Items/$epId/Images/Primary?fillWidth=400&quality=90",
                                    progressPercent = progress,
                                    savedPositionMs = posMs,
                                    savedDurationMs = durationMs
                                )
                            )
                        }
                    }
                }
                seasonList.add(
                    SeriesSeason(
                        seasonNumber = seasonNum,
                        name = sName,
                        episodes = epList
                    )
                )
            }
            SeriesDetails(seriesItem = seriesItem, seasons = seasonList)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun reportPlaybackStart(config: JellyfinConfig, itemId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || itemId.isBlank()) return@withContext
        val body = JSONObject().apply {
            put("ItemId", itemId)
            put("PositionTicks", positionMs * 10_000L)
            put("PlayMethod", "DirectPlay")
        }
        httpPost("$server/Sessions/Playing", config.accessToken, body)
    }

    suspend fun reportPlaybackProgress(config: JellyfinConfig, itemId: String, positionMs: Long, isPaused: Boolean) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || itemId.isBlank()) return@withContext
        val body = JSONObject().apply {
            put("ItemId", itemId)
            put("PositionTicks", positionMs * 10_000L)
            put("IsPaused", isPaused)
            put("EventName", "timeupdate")
        }
        httpPost("$server/Sessions/Playing/Progress", config.accessToken, body)
    }

    suspend fun reportPlaybackStopped(config: JellyfinConfig, itemId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || itemId.isBlank()) return@withContext
        val body = JSONObject().apply {
            put("ItemId", itemId)
            put("PositionTicks", positionMs * 10_000L)
        }
        httpPost("$server/Sessions/Playing/Stopped", config.accessToken, body)
    }

    suspend fun markWatched(config: JellyfinConfig, itemId: String) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        if (!config.isConfigured || itemId.isBlank() || userId.isBlank()) return@withContext
        httpPost("$server/Users/$userId/PlayedItems/$itemId", config.accessToken)
    }

    suspend fun markUnwatched(config: JellyfinConfig, itemId: String) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        if (!config.isConfigured || itemId.isBlank() || userId.isBlank()) return@withContext
        httpDelete("$server/Users/$userId/PlayedItems/$itemId", config.accessToken)
    }
}
