package dev.dylanakp.abysstvlauncher.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.ui.player.PlayerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class TrackOption(
    val label: String,
    val subLabel: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isSelected: Boolean,
    val isOffOption: Boolean = false
)

data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val videoResolutionBadge: String = "",
    val error: String? = null,
    val isEnded: Boolean = false
)

interface PlayerManager {
    val player: ExoPlayer
    val state: StateFlow<PlayerPlaybackState>

    fun prepare(context: Context, session: PlayerSession)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun selectAudioTrack(group: Tracks.Group, trackIndex: Int)
    fun selectSubtitleTrack(group: Tracks.Group?, trackIndex: Int, isOff: Boolean)
    fun retry(context: Context, session: PlayerSession)
    fun release()
    fun attachPlayerView(playerView: PlayerView)
    fun detachPlayerView(playerView: PlayerView)
}

@OptIn(UnstableApi::class)
class PlayerManagerImpl(
    private val appContext: Context
) : PlayerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressPollJob: Job? = null

    private val _state = MutableStateFlow(PlayerPlaybackState())
    override val state: StateFlow<PlayerPlaybackState> = _state.asStateFlow()

    private fun isBluetoothOrHeadsetConnected(): Boolean {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> true
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    ) {
                        true
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET || device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    ) {
                        true
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        device.type == AudioDeviceInfo.TYPE_HEARING_AID
                    ) {
                        true
                    } else false
                }
            }
        }
    }

    init {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                updateAudioRouting()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateAudioRouting()
            }
        }, null)
    }

    private fun updateAudioRouting() {
        val isHeadset = isBluetoothOrHeadsetConnected()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .apply {
                if (isHeadset) {
                    setMaxAudioChannelCount(2)
                } else {
                    setMaxAudioChannelCount(Int.MAX_VALUE)
                }
            }
            .build()
    }

    override val player: ExoPlayer by lazy {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AbyssTVLauncher/1.0.0 (Android TV; Jellyfin)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)

        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                val sink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                return object : ForwardingAudioSink(sink) {
                    override fun getFormatSupport(format: Format): Int {
                        if (isBluetoothOrHeadsetConnected() && format.sampleMimeType != MimeTypes.AUDIO_RAW) {
                            return AudioSink.SINK_FORMAT_UNSUPPORTED
                        }
                        return super.getFormatSupport(format)
                    }

                    override fun supportsFormat(format: Format): Boolean {
                        if (isBluetoothOrHeadsetConnected() && format.sampleMimeType != MimeTypes.AUDIO_RAW) {
                            return false
                        }
                        return super.supportsFormat(format)
                    }
                }
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_000, 4_000)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(appContext).setDataSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                playWhenReady = true
                addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val ended = playbackState == Player.STATE_ENDED
            val dur = player.duration.coerceAtLeast(0L)
            val pos = player.currentPosition.coerceAtLeast(0L)

            _state.update {
                it.copy(
                    isBuffering = buffering,
                    isEnded = ended,
                    durationMs = if (dur > 0L) dur else it.durationMs,
                    currentPositionMs = pos
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshTracks(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    error = "Playback error (${error.errorCodeName})",
                    isBuffering = false
                )
            }
        }
    }

    private fun startProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = scope.launch {
            while (isActive) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.coerceAtLeast(0L)
                val buf = player.bufferedPosition.coerceAtLeast(0L)
                _state.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = if (dur > 0L) dur else it.durationMs,
                        bufferedPositionMs = buf
                    )
                }
                delay(500L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = null
    }

    override fun prepare(context: Context, session: PlayerSession) {
        _state.update {
            it.copy(
                error = null,
                isBuffering = true,
                isEnded = false
            )
        }

        val isHeadset = isBluetoothOrHeadsetConnected()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .apply {
                if (isHeadset) {
                    setMaxAudioChannelCount(2)
                } else {
                    setMaxAudioChannelCount(Int.MAX_VALUE)
                }
            }
            .build()

        val item = ExoMediaItem.fromUri(session.streamUrl)
        player.setMediaItem(item)

        if (session.startPositionMs > 0L) {
            player.seekTo(session.startPositionMs)
        } else if (session.savedResumePositionMs > 15_000L) {
            player.seekTo(session.savedResumePositionMs)
            _state.update { it.copy(currentPositionMs = session.savedResumePositionMs) }
        }

        player.prepare()
        player.playWhenReady = true
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(currentPositionMs = positionMs) }
    }

    override fun seekBy(deltaMs: Long) {
        val cur = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val target = if (dur > 0L) (cur + deltaMs).coerceIn(0L, dur) else (cur + deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    override fun selectAudioTrack(group: Tracks.Group, trackIndex: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
            )
            .build()
    }

    override fun selectSubtitleTrack(group: Tracks.Group?, trackIndex: Int, isOff: Boolean) {
        if (isOff || group == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                )
                .build()
        }
    }

    override fun retry(context: Context, session: PlayerSession) {
        val resumePos = player.currentPosition.coerceAtLeast(0L)
        prepare(context, session.copy(startPositionMs = resumePos))
    }

    override fun attachPlayerView(playerView: PlayerView) {
        playerView.player = player
    }

    override fun detachPlayerView(playerView: PlayerView) {
        if (playerView.player == player) {
            playerView.player = null
        }
    }

    override fun release() {
        stopProgressPolling()
        player.stop()
        player.clearMediaItems()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        _state.update { PlayerPlaybackState() }
    }

    private fun refreshTracks(tracks: Tracks) {
        val audioList = mutableListOf<TrackOption>()
        val textList = mutableListOf<TrackOption>()

        val isTextDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val anyTextSelected = !isTextDisabled && tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }

        textList.add(
            TrackOption(
                label = appContext.getString(R.string.subtitles_off),
                subLabel = "",
                group = null,
                trackIndex = -1,
                isSelected = !anyTextSelected,
                isOffOption = true
            )
        )

        var resBadge = ""

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val fmt = group.getTrackFormat(i)
                            if (fmt.height > 0) {
                                resBadge = when {
                                    fmt.height >= 2160 || fmt.width >= 3840 -> "4K UHD"
                                    fmt.height >= 1080 -> "1080p FHD"
                                    fmt.height >= 720 -> "720p HD"
                                    else -> "${fmt.height}p"
                                }
                            }
                        }
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val selected = group.isTrackSelected(i)
                        val defaultAudio = appContext.getString(R.string.default_audio_track, i + 1)
                        val langName = formatLanguage(fmt.language, fmt.label, defaultAudio)
                        val codec = formatCodec(fmt.sampleMimeType, fmt.codecs)
                        val channels = formatChannels(fmt.channelCount)
                        val meta = listOf(codec, channels).filter { it.isNotBlank() }.joinToString(" • ")
                        audioList.add(
                            TrackOption(
                                label = langName,
                                subLabel = meta,
                                group = group,
                                trackIndex = i,
                                isSelected = selected
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val selected = !isTextDisabled && group.isTrackSelected(i)
                        val defaultSub = appContext.getString(R.string.default_subtitle_track, i + 1)
                        val langName = formatLanguage(fmt.language, fmt.label, defaultSub)
                        val subType = formatSubtitleFormat(fmt.sampleMimeType, fmt.selectionFlags)
                        textList.add(
                            TrackOption(
                                label = langName,
                                subLabel = subType,
                                group = group,
                                trackIndex = i,
                                isSelected = selected
                            )
                        )
                    }
                }
            }
        }

        _state.update {
            it.copy(
                audioTracks = audioList,
                subtitleTracks = textList,
                videoResolutionBadge = resBadge
            )
        }
    }

    private fun formatLanguage(langCode: String?, trackLabel: String?, defaultLabel: String): String {
        val cleanLabel = trackLabel?.trim().orEmpty()
        val code = langCode?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val langDisplay = if (code.isNotBlank() && code != "und") {
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }
        } else ""

        return when {
            langDisplay.isNotBlank() && cleanLabel.isNotBlank() && !cleanLabel.equals(langDisplay, ignoreCase = true) ->
                "$langDisplay ($cleanLabel)"
            langDisplay.isNotBlank() -> langDisplay
            cleanLabel.isNotBlank() -> cleanLabel
            else -> defaultLabel
        }
    }

    private fun formatCodec(mimeType: String?, codecs: String?): String {
        val m = mimeType?.lowercase(Locale.ROOT).orEmpty()
        val c = codecs?.lowercase(Locale.ROOT).orEmpty()
        return when {
            m.contains("eac3") || c.contains("ec-3") -> "Dolby Digital+ (EAC3)"
            m.contains("ac3") || c.contains("ac-3") -> "Dolby Digital (AC3)"
            m.contains("truehd") -> "Dolby TrueHD"
            m.contains("dts-hd") || m.contains("dtshd") -> "DTS-HD MA"
            m.contains("dts") -> "DTS"
            m.contains("aac") -> "AAC"
            m.contains("opus") -> "Opus"
            m.contains("flac") -> "FLAC"
            m.contains("mp4a") -> "AAC"
            else -> if (c.isNotBlank()) c.uppercase() else ""
        }
    }

    private fun formatChannels(channelCount: Int): String = when (channelCount) {
        8 -> "7.1"
        6 -> "5.1"
        2 -> "2.0 Stereo"
        1 -> "Mono"
        else -> if (channelCount > 0) "${channelCount}.0" else ""
    }

    private fun formatSubtitleFormat(mimeType: String?, selectionFlags: Int): String {
        val m = mimeType?.lowercase(Locale.ROOT).orEmpty()
        val isForced = (selectionFlags and C.SELECTION_FLAG_FORCED) != 0
        val base = when {
            m.contains("subrip") || m.contains("srt") -> "SRT"
            m.contains("pgs") -> "PGS"
            m.contains("vtt") -> "VTT"
            m.contains("ass") || m.contains("ssa") -> "ASS"
            m.contains("dvb") -> "DVB"
            else -> ""
        }
        return if (isForced) {
            if (base.isNotBlank()) "$base (Forcé)" else "Forcé"
        } else base
    }
}
