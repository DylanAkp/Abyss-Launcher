package dev.dylanakp.abysstvlauncher.data

import android.util.Log
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository as IJellyfinRepository
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.MediaVersion
import dev.dylanakp.abysstvlauncher.model.MovieDetails
import dev.dylanakp.abysstvlauncher.model.SeriesDetails
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SeriesSeason
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
private data class JellyfinQueryResponseDto(
    @SerialName("Items") val items: List<JellyfinItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

@Serializable
private data class JellyfinItemDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("Tagline") val tagline: String? = null,
    @SerialName("OverviewTagline") val overviewTagline: String? = null,
    @SerialName("UserData") val userData: JellyfinUserDataDto? = null,
    @SerialName("ProviderIds") val providerIds: Map<String, String>? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("LocalTrailerCount") val localTrailerCount: Int = 0,
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSourceDto> = emptyList(),
    @SerialName("RemoteTrailers") val remoteTrailers: List<JellyfinTrailerDto> = emptyList()
)

@Serializable
private data class JellyfinUserDataDto(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0L,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false
)

@Serializable
private data class JellyfinMediaSourceDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Container") val container: String = "",
    @SerialName("Size") val size: Long = 0L,
    @SerialName("Bitrate") val bitrate: Long = 0L,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStreamDto> = emptyList()
)

@Serializable
private data class JellyfinMediaStreamDto(
    @SerialName("Type") val type: String = "",
    @SerialName("Codec") val codec: String = "",
    @SerialName("DisplayTitle") val displayTitle: String = "",
    @SerialName("VideoRange") val videoRange: String = "",
    @SerialName("Channels") val channels: Int = 0,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null
)

@Serializable
private data class JellyfinTrailerDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Url") val url: String = "",
    @SerialName("Name") val name: String = "Trailer"
)

@Serializable
private data class JellyfinAuthResponseDto(
    @SerialName("User") val user: JellyfinAuthUserDto? = null,
    @SerialName("AccessToken") val accessToken: String = "",
    @SerialName("Server") val server: JellyfinServerDto? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("ServerId") val serverId: String = ""
)

@Serializable
private data class JellyfinAuthUserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = ""
)

@Serializable
private data class JellyfinServerDto(
    @SerialName("Name") val name: String = ""
)

@Serializable
private data class JellyfinPublicInfoDto(
    @SerialName("ServerName") val serverName: String = "Jellyfin"
)

@Serializable
private data class JellyfinAuthPayload(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String
)

@Serializable
private data class JellyfinPlaybackStartPayload(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("PlayMethod") val playMethod: String = "DirectPlay"
)

@Serializable
private data class JellyfinPlaybackProgressPayload(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("EventName") val eventName: String = "timeupdate"
)

@Serializable
private data class JellyfinPlaybackStoppedPayload(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long
)

object JellyfinRepository : IJellyfinRepository, KoinComponent {

    private const val CLIENT_NAME = "AbyssLauncher"
    private const val DEVICE_NAME = "Android TV"
    private const val CLIENT_VERSION = "2.0.0"

    private val client: OkHttpClient by inject()
    private val json: Json by inject()

    private fun resolveDeviceId(): String =
        org.koin.core.context.GlobalContext.getOrNull()?.get<LauncherPreferences>()?.deviceId
            ?: "Abyss-TV-Player"

    override fun cleanUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isNotBlank() && !u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
            val is172Private = u.startsWith("172.") &&
                u.substringAfter("172.").substringBefore(".").toIntOrNull()?.let { it in 16..31 } == true
            val isLocal = u.startsWith("192.168.") || u.startsWith("10.") || is172Private || u.startsWith("localhost") || u.contains(":8096")
            u = if (isLocal) "http://$u" else "https://$u"
        }
        return u
    }

    private fun buildAuthHeader(token: String = ""): String {
        val deviceId = resolveDeviceId()
        return "MediaBrowser Client=\"$CLIENT_NAME\", Device=\"$DEVICE_NAME\", DeviceId=\"$deviceId\", Version=\"$CLIENT_VERSION\"" +
                if (token.isNotBlank()) ", Token=\"$token\"" else ""
    }

    override fun getStreamUrl(config: JellyfinConfig, itemId: String, mediaSourceId: String?): String {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val params = buildList {
            add("static=true")
            if (userId.isNotBlank()) add("userId=$userId")
            if (!mediaSourceId.isNullOrBlank()) add("MediaSourceId=$mediaSourceId")
        }.joinToString("&")
        return "$server/Videos/$itemId/stream?$params"
    }

    override fun sanitizeStreamUrl(streamUrl: String, config: JellyfinConfig, mediaSourceId: String?): String {
        if (streamUrl.isBlank()) return streamUrl
        var url = streamUrl
            .replace(Regex("([?&])api_key=[^&]*&?"), "$1")
            .trimEnd('&', '?')
        if (config.userId.isNotBlank() && !url.contains("userId=")) {
            url = if (url.contains("?")) "$url&userId=${config.userId}" else "$url?userId=${config.userId}"
        }
        if (!mediaSourceId.isNullOrBlank() && !url.contains("MediaSourceId=")) {
            url = if (url.contains("?")) "$url&MediaSourceId=$mediaSourceId" else "$url?MediaSourceId=$mediaSourceId"
        }
        return url
    }

    override fun getImageUrl(serverUrl: String, itemId: String, isBackdrop: Boolean): String {
        val server = cleanUrl(serverUrl)
        if (server.isBlank() || itemId.isBlank()) return ""
        return if (isBackdrop) {
            "$server/Items/$itemId/Images/Backdrop/0?fillWidth=1280&quality=85"
        } else {
            "$server/Items/$itemId/Images/Primary?fillWidth=400&quality=90"
        }
    }

    override suspend fun authenticate(config: JellyfinConfig): Result<JellyfinConfig> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (server.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        try {
            val endpoint = "$server/Users/AuthenticateByName"
            val body = json.encodeToString(JellyfinAuthPayload(username = config.username, pw = config.password))
            val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
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
                    val resp = response.body?.string().orEmpty()
                    val auth = json.decodeFromString<JellyfinAuthResponseDto>(resp)
                    val token = auth.accessToken
                    val userId = auth.user?.id.orEmpty()
                    var serverName = auth.server?.name?.ifBlank { null } ?: auth.serverName.orEmpty()

                    if (serverName.isBlank()) {
                        val publicInfo = httpGet("$server/System/Info/Public", token)
                        if (publicInfo != null) {
                            serverName = runCatching {
                                json.decodeFromString<JellyfinPublicInfoDto>(publicInfo).serverName
                            }.getOrDefault("Jellyfin")
                        }
                    }
                    if (serverName.isBlank()) serverName = "Jellyfin"

                    val updated = config.copy(
                        serverUrl = server,
                        userId = userId,
                        accessToken = token,
                        serverName = serverName
                    )
                    Result.success(updated)
                } else {
                    val err = response.body?.string() ?: "HTTP $code"
                    Result.failure(Exception("Jellyfin auth failed ($code): $err"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AbyssJellyfin", "Jellyfin authentication failed", e)
            Result.failure(e)
        }
    }

    override suspend fun testConnection(config: JellyfinConfig): Result<String> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (server.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        try {
            val endpoint = "$server/System/Info/Public"
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Authorization", buildAuthHeader(config.accessToken))
                .header("X-Emby-Authorization", buildAuthHeader(config.accessToken))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val resp = response.body?.string().orEmpty()
                    val name = runCatching {
                        json.decodeFromString<JellyfinPublicInfoDto>(resp).serverName
                    }.getOrDefault("Jellyfin")
                    Result.success(name)
                } else {
                    Result.failure(Exception("Connection failed with HTTP ${response.code}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AbyssJellyfin", "Jellyfin authentication failed", e)
            Result.failure(e)
        }
    }

    private fun httpGet(urlStr: String, token: String): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("User-Agent", "AbyssLauncher/2.0")

            if (token.isNotBlank()) {
                reqBuilder.header("X-Emby-Token", token)
                reqBuilder.header("Authorization", buildAuthHeader(token))
                reqBuilder.header("X-Emby-Authorization", buildAuthHeader(token))
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (e: IOException) {
            Log.w("AbyssJellyfin", "HTTP GET failed for ${urlStr.substringBefore("?")}", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("AbyssJellyfin", "Invalid URL in HTTP GET: ${urlStr.substringBefore("?")}", e)
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> httpGetJson(urlStr: String, token: String): T? {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("User-Agent", "AbyssLauncher/2.0")

            if (token.isNotBlank()) {
                reqBuilder.header("X-Emby-Token", token)
                reqBuilder.header("Authorization", buildAuthHeader(token))
                reqBuilder.header("X-Emby-Authorization", buildAuthHeader(token))
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                json.decodeFromStream<T>(body.byteStream())
            }
        } catch (e: IOException) {
            Log.w("AbyssJellyfin", "HTTP GET stream failed for ${urlStr.substringBefore("?")}", e)
            null
        } catch (e: SerializationException) {
            Log.w("AbyssJellyfin", "JSON stream decode failed for ${urlStr.substringBefore("?")}", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("AbyssJellyfin", "Invalid URL in HTTP GET stream: ${urlStr.substringBefore("?")}", e)
            null
        }
    }

    private fun httpPost(urlStr: String, token: String, bodyJson: String? = null): Boolean {
        return try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val reqBody = (bodyJson ?: "").toRequestBody(mediaType)
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("User-Agent", "AbyssLauncher/2.0")
                .post(reqBody)

            if (token.isNotBlank()) {
                reqBuilder.header("X-Emby-Token", token)
                reqBuilder.header("Authorization", buildAuthHeader(token))
                reqBuilder.header("X-Emby-Authorization", buildAuthHeader(token))
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: IOException) {
            Log.w("AbyssJellyfin", "HTTP POST failed for ${urlStr.substringBefore("?")}", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w("AbyssJellyfin", "Invalid URL in HTTP POST: ${urlStr.substringBefore("?")}", e)
            false
        }
    }

    private fun httpDelete(urlStr: String, token: String): Boolean {
        return try {
            val reqBuilder = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("User-Agent", "AbyssLauncher/2.0")
                .delete()

            if (token.isNotBlank()) {
                reqBuilder.header("X-Emby-Token", token)
                reqBuilder.header("Authorization", buildAuthHeader(token))
                reqBuilder.header("X-Emby-Authorization", buildAuthHeader(token))
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: IOException) {
            Log.w("AbyssJellyfin", "HTTP DELETE failed for ${urlStr.substringBefore("?")}", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w("AbyssJellyfin", "Invalid URL in HTTP DELETE: ${urlStr.substringBefore("?")}", e)
            false
        }
    }

    override suspend fun syncLibrary(config: JellyfinConfig): Result<SyncedLibrary> = withContext(Dispatchers.IO) {
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

                val library = SyncedLibrary(
                    movies = movies,
                    series = series
                )
                Result.success(library)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AbyssJellyfin", "Jellyfin authentication failed", e)
            Result.failure(e)
        }
    }

    override suspend fun fetchMovies(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        val fields = "Overview,Genres,CommunityRating,PremiereDate,ProductionYear,RunTimeTicks,MediaStreams,PrimaryImageAspectRatio,ProviderIds,UserData"
        val url = "$server/Users/$userId/Items?IncludeItemTypes=Movie&Recursive=true&SortBy=DateCreated,SortName&SortOrder=Descending&Fields=$fields"

        val root = httpGetJson<JellyfinQueryResponseDto>(url, token) ?: return@withContext emptyList()
        val items = mutableListOf<MediaItem>()
        try {
            for (obj in root.items) {
                val id = obj.id
                if (id.isBlank()) continue
                val name = obj.name
                val overview = obj.overview.orEmpty()
                val year = obj.productionYear?.takeIf { it > 0 }?.toString().orEmpty()
                val rating = obj.communityRating?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()

                val runTimeTicks = obj.runTimeTicks ?: 0L
                val durationMs = runTimeTicks / 10_000L
                val durationStr = if (durationMs > 0L) {
                    val m = durationMs / 60_000L
                    "${m / 60}h ${m % 60}m"
                } else ""

                val userData = obj.userData
                val played = userData?.played ?: false
                val posTicks = userData?.playbackPositionTicks ?: 0L
                val posMs = posTicks / 10_000L
                val progress = when {
                    played -> 1.0f
                    durationMs > 0L && posMs > 0L -> (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    else -> 0f
                }

                val groupTitle = obj.genres.firstOrNull().orEmpty()
                val tmdbId = obj.providerIds?.get("Tmdb")?.toIntOrNull()

                val poster = "$server/Items/$id/Images/Primary?fillWidth=400&quality=90"
                val backdrop = "$server/Items/$id/Images/Backdrop/0?fillWidth=1280&quality=85"
                val streamUrl = getStreamUrl(config, id)

                items.add(
                    MediaItem(
                        id = id,
                        title = name,
                        synopsis = overview,
                        category = MediaCategory.MOVIE,
                        groupTitle = groupTitle,
                        rating = rating,
                        year = year,
                        durationOrSeasons = durationStr,
                        qualityBadge = "",
                        audioBadge = "",
                        streamUrl = streamUrl,
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        progressPercent = progress,
                        jellyfinId = id,
                        tmdbId = tmdbId
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("AbyssJellyfin", "Failed to parse Jellyfin movies", e)
        }
        items
    }

    override suspend fun fetchSeries(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        val fields = "Overview,Genres,CommunityRating,PremiereDate,ProductionYear,RunTimeTicks,PrimaryImageAspectRatio,ProviderIds,UserData"
        val url = "$server/Users/$userId/Items?IncludeItemTypes=Series&Recursive=true&SortBy=DateCreated,SortName&SortOrder=Descending&Fields=$fields"

        val root = httpGetJson<JellyfinQueryResponseDto>(url, token) ?: return@withContext emptyList()
        val items = mutableListOf<MediaItem>()
        try {
            for (obj in root.items) {
                val id = obj.id
                if (id.isBlank()) continue
                val name = obj.name
                val overview = obj.overview.orEmpty()
                val year = obj.productionYear?.takeIf { it > 0 }?.toString().orEmpty()
                val rating = obj.communityRating?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()

                val groupTitle = obj.genres.firstOrNull().orEmpty()
                val tmdbId = obj.providerIds?.get("Tmdb")?.toIntOrNull()

                val poster = "$server/Items/$id/Images/Primary?fillWidth=400&quality=90"
                val backdrop = "$server/Items/$id/Images/Backdrop/0?fillWidth=1280&quality=85"

                items.add(
                    MediaItem(
                        id = id,
                        title = name,
                        synopsis = overview,
                        category = MediaCategory.SERIES,
                        groupTitle = groupTitle,
                        rating = rating,
                        year = year,
                        durationOrSeasons = "",
                        qualityBadge = "TV HD",
                        audioBadge = "5.1",
                        streamUrl = "",
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        jellyfinId = id,
                        tmdbId = tmdbId
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("AbyssJellyfin", "Failed to parse Jellyfin movies", e)
        }
        items
    }

    override suspend fun fetchResumeAndNextUp(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val (resumeList, nextUpList) = coroutineScope {
            val resumeDeferred = async { fetchResumeItems(config) }
            val nextUpDeferred = async { fetchNextUpEpisodes(config) }
            resumeDeferred.await() to nextUpDeferred.await()
        }

        val seen = mutableSetOf<String>()
        val combined = mutableListOf<MediaItem>()

        for (item in resumeList) {
            if (seen.add(item.id)) {
                combined.add(item)
            }
        }

        for (item in nextUpList) {
            if (seen.add(item.id)) {
                combined.add(item)
            }
        }

        combined
    }

    override suspend fun fetchContinueWatching(config: JellyfinConfig): List<MediaItem> =
        fetchResumeAndNextUp(config)

    override suspend fun fetchResumeItems(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        if (!config.isConfigured) return@withContext emptyList()

        val results = mutableListOf<MediaItem>()
        val seenSeriesIds = mutableSetOf<String>()

        val resumeUrl = "$server/UserItems/Resume?userId=$userId&limit=25&mediaTypes=Movie,Episode&Fields=Overview,PrimaryImageAspectRatio,UserData,RunTimeTicks,SeriesName,SeriesId,SeasonId,ParentIndexNumber,IndexNumber"
        val resumeRaw = httpGet(resumeUrl, token)
        if (resumeRaw != null) {
            try {
                val root = json.decodeFromString<JellyfinQueryResponseDto>(resumeRaw)
                for (obj in root.items) {
                    val type = obj.type
                    val id = obj.id
                    val runTimeTicks = obj.runTimeTicks ?: 0L
                    val userData = obj.userData
                    val posTicks = userData?.playbackPositionTicks ?: 0L
                    val durationMs = runTimeTicks / 10_000L
                    val posMs = posTicks / 10_000L
                    val progress = if (durationMs > 0L) (posMs.toFloat() / durationMs.toFloat()).coerceIn(0.01f, 1f) else 0.05f

                    if (type.equals("Movie", ignoreCase = true)) {
                        val name = obj.name
                        results.add(
                            MediaItem(
                                id = id,
                                title = name,
                                category = MediaCategory.MOVIE,
                                synopsis = obj.overview.orEmpty(),
                                qualityBadge = "",
                                streamUrl = getStreamUrl(config, id),
                                posterUrl = "$server/Items/$id/Images/Primary?fillWidth=400&quality=90",
                                backdropUrl = "$server/Items/$id/Images/Backdrop/0?fillWidth=1280&quality=85",
                                progressPercent = progress,
                                jellyfinId = id
                            )
                        )
                    } else if (type.equals("Episode", ignoreCase = true)) {
                        val seriesId = obj.seriesId.orEmpty()
                        val seriesName = obj.seriesName ?: obj.name
                        val sNum = obj.parentIndexNumber ?: 1
                        val eNum = obj.indexNumber ?: 1
                        val epTitle = obj.name
                        val sCode = "S${sNum.toString().padStart(2, '0')}E${eNum.toString().padStart(2, '0')}"

                        val targetSeriesId = seriesId.ifBlank { id }
                        seenSeriesIds.add(targetSeriesId)

                        results.add(
                            MediaItem(
                                id = targetSeriesId,
                                title = seriesName,
                                subtitle = "$sCode • $epTitle",
                                category = MediaCategory.SERIES,
                                synopsis = obj.overview.orEmpty(),
                                qualityBadge = "",
                                streamUrl = getStreamUrl(config, id),
                                posterUrl = if (seriesId.isNotBlank()) {
                                    "$server/Items/$seriesId/Images/Primary?fillWidth=400&quality=90"
                                } else {
                                    "$server/Items/$id/Images/Primary?fillWidth=400&quality=90"
                                },
                                backdropUrl = "$server/Items/$id/Images/Primary?fillWidth=1280&quality=85",
                                progressPercent = progress,
                                jellyfinId = targetSeriesId
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                Log.w("AbyssJellyfin", "Failed to parse Jellyfin resume items", e)
            }
        }
        results
    }

    override suspend fun fetchNextUpEpisodes(config: JellyfinConfig): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        if (!config.isConfigured) return@withContext emptyList()

        val results = mutableListOf<MediaItem>()
        val nextUpUrl = "$server/Shows/NextUp?userId=$userId&limit=25&Fields=Overview,PrimaryImageAspectRatio,UserData,RunTimeTicks,SeriesName,SeriesId,SeasonId,ParentIndexNumber,IndexNumber"
        val nextUpRaw = httpGet(nextUpUrl, token)
        if (nextUpRaw != null) {
            try {
                val root = json.decodeFromString<JellyfinQueryResponseDto>(nextUpRaw)
                for (obj in root.items) {
                    val id = obj.id
                    val seriesId = obj.seriesId.orEmpty()
                    val seriesName = obj.seriesName ?: obj.name
                    val sNum = obj.parentIndexNumber ?: 1
                    val eNum = obj.indexNumber ?: 1
                    val epTitle = obj.name
                    val sCode = "S${sNum.toString().padStart(2, '0')}E${eNum.toString().padStart(2, '0')}"
                    val targetSeriesId = seriesId.ifBlank { id }

                    results.add(
                        MediaItem(
                            id = targetSeriesId,
                            title = seriesName,
                            subtitle = "$sCode • $epTitle",
                            category = MediaCategory.SERIES,
                            synopsis = obj.overview.orEmpty(),
                            qualityBadge = "",
                            streamUrl = getStreamUrl(config, id),
                            posterUrl = if (seriesId.isNotBlank()) {
                                "$server/Items/$seriesId/Images/Primary?fillWidth=400&quality=90"
                            } else {
                                "$server/Items/$id/Images/Primary?fillWidth=400&quality=90"
                            },
                            backdropUrl = "$server/Items/$id/Images/Primary?fillWidth=1280&quality=85",
                            progressPercent = 0.001f,
                            jellyfinId = targetSeriesId
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                Log.w("AbyssJellyfin", "Failed to parse Jellyfin next up episodes", e)
            }
        }
        results
    }

    override suspend fun fetchSeriesDetails(config: JellyfinConfig, seriesItem: MediaItem): SeriesDetails? = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        val seriesId = seriesItem.id

        try {
            val (seasonsRaw, allEpisodesRaw) = coroutineScope {
                val seasonsDeferred = async {
                    httpGet("$server/Shows/$seriesId/Seasons?userId=$userId&Fields=Overview,PrimaryImageAspectRatio", token)
                }
                val episodesDeferred = async {
                    httpGet("$server/Shows/$seriesId/Episodes?userId=$userId&Fields=Overview,PrimaryImageAspectRatio,UserData,RunTimeTicks,IndexNumber,ParentIndexNumber,MediaSources", token)
                }
                seasonsDeferred.await() to episodesDeferred.await()
            }
            if (seasonsRaw == null) return@withContext null

            val root = json.decodeFromString<JellyfinQueryResponseDto>(seasonsRaw)
            val allEpisodes = if (allEpisodesRaw != null) {
                json.decodeFromString<JellyfinQueryResponseDto>(allEpisodesRaw).items
            } else emptyList()

            val bySeasonId = allEpisodes.groupBy { it.seasonId.orEmpty() }
            val bySeasonNum = allEpisodes.groupBy { it.parentIndexNumber ?: 1 }

            val seasonList = root.items.mapIndexed { s, sObj ->
                val seasonId = sObj.id
                val seasonNum = sObj.indexNumber ?: (s + 1)
                val sName = sObj.name.ifBlank { "Saison $seasonNum" }
                val rawEps = bySeasonId[seasonId]?.takeIf { it.isNotEmpty() } ?: bySeasonNum[seasonNum].orEmpty()

                val epList = rawEps.mapIndexed { e, eObj ->
                    val epId = eObj.id
                    val epNum = eObj.indexNumber ?: (e + 1)
                    val epName = eObj.name.ifBlank { "Épisode $epNum" }
                    val overview = eObj.overview.orEmpty()
                    val durationMs = (eObj.runTimeTicks ?: 0L) / 10_000L
                    val userData = eObj.userData
                    val played = userData?.played ?: false
                    val posMs = (userData?.playbackPositionTicks ?: 0L) / 10_000L
                    val progress = when {
                        played -> 1.0f
                        durationMs > 0L && posMs > 0L -> (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val epVersions = parseMediaVersions(eObj.mediaSources)
                    SeriesEpisode(
                        id = epId,
                        seriesId = seriesId,
                        seasonNumber = seasonNum,
                        episodeNumber = epNum,
                        title = epName,
                        plot = overview,
                        streamUrl = getStreamUrl(config, epId),
                        posterUrl = "$server/Items/$epId/Images/Primary?fillWidth=400&quality=90",
                        progressPercent = progress,
                        savedPositionMs = posMs,
                        savedDurationMs = durationMs,
                        versions = epVersions,
                        selectedVersionId = epVersions.firstOrNull()?.id
                    )
                }
                SeriesSeason(
                    seasonNumber = seasonNum,
                    name = sName,
                    episodes = epList
                )
            }
            SeriesDetails(seriesItem = seriesItem, seasons = seasonList)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("AbyssJellyfin", "Failed to parse series details for $seriesId", e)
            null
        }
    }

    override suspend fun fetchMovieDetails(config: JellyfinConfig, movieItem: MediaItem): MovieDetails = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        val jfId = movieItem.jellyfinId.ifBlank { movieItem.id }

        var tagline = ""
        val genres = mutableListOf<String>()
        val versions = mutableListOf<MediaVersion>()
        var trailerUrl = ""
        var trailerName = ""
        var isLocalTrailer = false

        if (config.isConfigured && jfId.isNotBlank()) {
            try {
                val url = "$server/Users/$userId/Items/$jfId"
                val raw = httpGet(url, token)
                if (raw != null) {
                    val obj = json.decodeFromString<JellyfinItemDto>(raw)
                    tagline = obj.taglines.firstOrNull()
                        ?: obj.overviewTagline?.ifBlank { null }
                        ?: obj.tagline.orEmpty()
                    tagline = tagline.removePrefix("[\"").removeSuffix("\"]").removeSurrounding("\"").trim()
                    genres.addAll(obj.genres)
                    versions.addAll(parseMediaVersions(obj.mediaSources))

                    if (obj.localTrailerCount > 0) {
                        val trailersRaw = httpGet("$server/Users/$userId/Items/$jfId/LocalTrailers", token)
                        if (trailersRaw != null) {
                            val localTrailers = runCatching {
                                json.decodeFromString<List<JellyfinTrailerDto>>(trailersRaw)
                            }.getOrDefault(emptyList())

                            val firstTrailer = localTrailers.firstOrNull()
                            if (firstTrailer != null && firstTrailer.id.isNotBlank()) {
                                isLocalTrailer = true
                                trailerUrl = getStreamUrl(config, firstTrailer.id)
                                trailerName = firstTrailer.name.ifBlank { "Bande-annonce" }
                            }
                        }
                    }

                    if (trailerUrl.isBlank()) {
                        val firstRemote = obj.remoteTrailers.firstOrNull()
                        if (firstRemote != null && firstRemote.url.isNotBlank()) {
                            trailerUrl = firstRemote.url
                            trailerName = firstRemote.name.ifBlank { "Bande-annonce" }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                Log.w("AbyssJellyfin", "Failed to parse movie details for $jfId", e)
            }
        }

        if (trailerUrl.isBlank() && movieItem.tmdbId != null) {
            val tmdbTrailer = TmdbRepository.fetchMovieTrailer(movieItem.tmdbId)
            if (tmdbTrailer != null) {
                trailerUrl = tmdbTrailer.first
                trailerName = tmdbTrailer.second
            }
        }

        MovieDetails(
            movieItem = movieItem,
            tagline = tagline,
            genres = genres,
            versions = versions,
            selectedVersionId = versions.firstOrNull()?.id.orEmpty(),
            trailerUrl = trailerUrl,
            trailerName = trailerName,
            isLocalTrailer = isLocalTrailer
        )
    }

    private fun parseMediaVersions(sources: List<JellyfinMediaSourceDto>): List<MediaVersion> {
        return sources.mapIndexed { i, src ->
            var srcName = src.name
            val container = src.container
            var resStr = ""
            var vCodec = ""
            var aCodec = ""

            for (stream in src.mediaStreams) {
                val type = stream.type
                if (type.equals("Video", ignoreCase = true) && resStr.isBlank()) {
                    val height = stream.height ?: 0
                    val width = stream.width ?: 0
                    vCodec = stream.codec.uppercase()
                    val videoRange = stream.videoRange
                    resStr = when {
                        height >= 2000 || width >= 3800 -> "4K"
                        height >= 1000 || width >= 1900 -> "1080p"
                        height >= 700 || width >= 1200 -> "720p"
                        else -> if (height > 0) "${height}p" else ""
                    }
                    if (videoRange.isNotBlank() && videoRange != "SDR") {
                        resStr = if (resStr.isNotBlank()) "$resStr $videoRange" else videoRange
                    }
                } else if (type.equals("Audio", ignoreCase = true) && aCodec.isBlank()) {
                    val rawCodec = stream.codec.uppercase()
                    val channels = when (stream.channels) {
                        8 -> "7.1"
                        6 -> "5.1"
                        2 -> "2.0"
                        else -> ""
                    }
                    aCodec = if (rawCodec.isNotBlank()) {
                        if (channels.isNotBlank()) "$rawCodec $channels" else rawCodec
                    } else {
                        stream.displayTitle.take(10)
                    }
                }
            }

            if (srcName.isBlank() || srcName.equals("File", ignoreCase = true)) {
                srcName = listOf(resStr, vCodec, container.uppercase()).filter { it.isNotBlank() }.joinToString(" • ")
                if (srcName.isBlank()) srcName = "Version ${i + 1}"
            }

            MediaVersion(
                id = src.id,
                name = srcName,
                container = container,
                resolution = resStr,
                videoCodec = vCodec,
                audioCodec = aCodec,
                sizeBytes = src.size,
                bitrate = src.bitrate
            )
        }
    }

    override suspend fun preparePlayback(config: JellyfinConfig, itemId: String): Boolean = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        if (!config.isConfigured || itemId.isBlank() || userId.isBlank()) return@withContext false
        try {
            httpGet("$server/Users/$userId/Items/$itemId", config.accessToken) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("AbyssJellyfin", "preparePlayback failed for $itemId", e)
            false
        }
    }

    override suspend fun reportPlaybackStart(config: JellyfinConfig, itemId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || itemId.isBlank()) return@withContext
        val body = json.encodeToString(
            JellyfinPlaybackStartPayload(
                itemId = itemId,
                positionTicks = positionMs * 10_000L
            )
        )
        httpPost("$server/Sessions/Playing", config.accessToken, body)
    }

    override suspend fun reportPlaybackProgress(config: JellyfinConfig, itemId: String, positionMs: Long, isPaused: Boolean) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || itemId.isBlank()) return@withContext
        val body = json.encodeToString(
            JellyfinPlaybackProgressPayload(
                itemId = itemId,
                positionTicks = positionMs * 10_000L,
                isPaused = isPaused
            )
        )
        httpPost("$server/Sessions/Playing/Progress", config.accessToken, body)
    }

    override suspend fun reportPlaybackStopped(config: JellyfinConfig, itemId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        if (!config.isConfigured || itemId.isBlank()) return@withContext
        val body = json.encodeToString(
            JellyfinPlaybackStoppedPayload(
                itemId = itemId,
                positionTicks = positionMs * 10_000L
            )
        )
        httpPost("$server/Sessions/Playing/Stopped", config.accessToken, body)
    }

    override suspend fun markWatched(config: JellyfinConfig, itemId: String) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        if (!config.isConfigured || itemId.isBlank() || userId.isBlank()) return@withContext
        httpPost("$server/Users/$userId/PlayedItems/$itemId", config.accessToken)
    }

    override suspend fun markUnwatched(config: JellyfinConfig, itemId: String) = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        if (!config.isConfigured || itemId.isBlank() || userId.isBlank()) return@withContext
        httpDelete("$server/Users/$userId/PlayedItems/$itemId", config.accessToken)
    }

    override suspend fun search(config: JellyfinConfig, query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val server = cleanUrl(config.serverUrl)
        val userId = config.userId
        val token = config.accessToken
        if (!config.isConfigured || query.isBlank() || userId.isBlank()) return@withContext emptyList()

        val encQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val fields = "Overview,Genres,CommunityRating,PremiereDate,ProductionYear,RunTimeTicks,PrimaryImageAspectRatio,ProviderIds,UserData"
        val url = "$server/Users/$userId/Items?SearchTerm=$encQuery&IncludeItemTypes=Movie,Series&Recursive=true&Limit=30&Fields=$fields"

        val raw = httpGet(url, token) ?: return@withContext emptyList()
        val results = mutableListOf<MediaItem>()
        try {
            val root = json.decodeFromString<JellyfinQueryResponseDto>(raw)
            for (obj in root.items) {
                val id = obj.id
                if (id.isBlank()) continue
                val type = obj.type
                val category = if (type.equals("Series", ignoreCase = true)) MediaCategory.SERIES else MediaCategory.MOVIE
                val name = obj.name
                val overview = obj.overview.orEmpty()
                val year = obj.productionYear?.takeIf { it > 0 }?.toString().orEmpty()
                val rating = obj.communityRating?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()

                val runTimeTicks = obj.runTimeTicks ?: 0L
                val durationMs = runTimeTicks / 10_000L
                val durationStr = if (durationMs > 0L) {
                    val m = durationMs / 60_000L
                    "${m / 60}h ${m % 60}m"
                } else ""

                val userData = obj.userData
                val played = userData?.played ?: false
                val posTicks = userData?.playbackPositionTicks ?: 0L
                val posMs = posTicks / 10_000L
                val progress = when {
                    played -> 1.0f
                    durationMs > 0L && posMs > 0L -> (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    else -> 0f
                }

                val groupTitle = obj.genres.firstOrNull().orEmpty()
                val tmdbId = obj.providerIds?.get("Tmdb")?.toIntOrNull()

                val poster = "$server/Items/$id/Images/Primary?fillWidth=400&quality=90"
                val backdrop = "$server/Items/$id/Images/Backdrop/0?fillWidth=1280&quality=85"
                val streamUrl = if (category == MediaCategory.MOVIE) {
                    getStreamUrl(config, id)
                } else ""

                results.add(
                    MediaItem(
                        id = id,
                        title = name,
                        synopsis = overview,
                        category = category,
                        groupTitle = groupTitle,
                        rating = rating,
                        year = year,
                        durationOrSeasons = durationStr,
                        qualityBadge = "",
                        audioBadge = "",
                        streamUrl = streamUrl,
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        progressPercent = progress,
                        jellyfinId = id,
                        tmdbId = tmdbId
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Log.w("AbyssJellyfin", "Failed to parse Jellyfin search results", e)
        }
        results
    }
}
