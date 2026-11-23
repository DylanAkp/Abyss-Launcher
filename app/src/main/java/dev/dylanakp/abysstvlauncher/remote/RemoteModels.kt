package dev.dylanakp.abysstvlauncher.remote

import kotlinx.serialization.Serializable

@Serializable
data class PairRequest(
    val pin: String? = null,
    val token: String? = null,
    val deviceName: String? = null
)

@Serializable
data class PairResponse(
    val token: String,
    val deviceName: String
)

sealed interface PairResult {
    data class Success(val response: PairResponse) : PairResult
    data class Failure(val reason: Reason, val retryAfterMs: Long = 0L) : PairResult

    enum class Reason { INVALID_PIN, RATE_LIMITED, PIN_EXPIRED }
}

@Serializable
data class KeyCommand(
    val key: String,
    val longPress: Boolean = false
)

@Serializable
data class VolumeCommand(
    val action: String
)

@Serializable
data class LaunchCommand(
    val packageName: String
)

@Serializable
data class TextCommand(
    val text: String
)

@Serializable
data class SeekCommand(
    val positionMs: Long? = null,
    val deltaMs: Long? = null
)

@Serializable
data class NowPlayingState(
    val isPlaying: Boolean,
    val title: String,
    val subtitle: String = "",
    val sourceApp: String = "",
    val artworkUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isInternalPlayer: Boolean = false
)

@Serializable
data class RemoteAppInfo(
    val packageName: String,
    val label: String
)

@Serializable
data class TvRemoteState(
    val nowPlaying: NowPlayingState? = null,
    val volume: Int = 0,
    val maxVolume: Int = 100,
    val isMuted: Boolean = false,
    val canRingRemote: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val connectedClients: Int = 0,
    val apps: List<RemoteAppInfo> = emptyList()
)

@Serializable
data class RemoteMediaItem(
    val id: String,
    val jellyfinId: String = "",
    val title: String,
    val subtitle: String = "",
    val category: String = "",
    val posterUrl: String = "",
    val progressPct: Int = 0,
    val year: String = ""
)

@Serializable
data class RemoteMediaHomeResponse(
    val continueWatching: List<RemoteMediaItem> = emptyList(),
    val watchlist: List<RemoteMediaItem> = emptyList()
)

@Serializable
data class PlayMediaCommand(
    val id: String
)
