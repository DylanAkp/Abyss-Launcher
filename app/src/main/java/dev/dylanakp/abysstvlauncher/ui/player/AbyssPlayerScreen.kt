package dev.dylanakp.abysstvlauncher.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import dev.dylanakp.abysstvlauncher.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderFocus
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlassElevated
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PlayerSession(
    val mediaItem: MediaItem,
    val streamUrl: String,
    val title: String,
    val subtitle: String = "",
    val startPositionMs: Long = 0L,
    val savedResumePositionMs: Long = 0L,
    val seriesId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val nextEpisode: SeriesEpisode? = null
)

private data class TrackOption(
    val label: String,
    val subLabel: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isSelected: Boolean,
    val isOffOption: Boolean = false
)

private enum class PlayerModalType {
    NONE,
    AUDIO_TRACKS,
    SUBTITLE_TRACKS
}

@OptIn(UnstableApi::class)
@Composable
fun AbyssPlayerScreen(
    session: PlayerSession,
    onBack: (positionMs: Long, durationMs: Long) -> Unit,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onPlayNextEpisode: (SeriesEpisode?) -> Unit,
    onLaunchExternalFallback: (String, String) -> Unit
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var currentWallClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showOsd by remember { mutableStateOf(true) }
    var lastInteractionTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var activeModal by remember { mutableStateOf(PlayerModalType.NONE) }

    // Rapid D-Pad seek accumulation state (-10s / +10s, +20s, +30s...)
    var accumulatedSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var seekPreviewTargetMs by remember { mutableLongStateOf(-1L) }
    var seekCommitTick by remember { mutableLongStateOf(0L) }

    // Resume prompt banner when savedResumePositionMs > 15_000L
    var showResumePrompt by remember(session.streamUrl) {
        mutableStateOf(session.savedResumePositionMs > 15_000L && session.startPositionMs == 0L)
    }

    var audioTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var videoResolutionBadge by remember { mutableStateOf("") }

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val resumeButtonFocusRequester = remember { FocusRequester() }
    val retryButtonFocusRequester = remember { FocusRequester() }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AbyssTVLauncher/1.0.0 (Android TV; Jellyfin)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                50_000,
                2_000,
                4_000
            )
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                // Default: Subtitles OFF unless explicitly enabled by user in OSD
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                playWhenReady = true
            }
    }

    fun refreshTracks(tracks: Tracks) {
        val audioList = mutableListOf<TrackOption>()
        val textList = mutableListOf<TrackOption>()

        val isTextDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val anyTextSelected = !isTextDisabled && tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        textList.add(
            TrackOption(
                label = context.getString(R.string.subtitles_off),
                subLabel = "",
                group = null,
                trackIndex = -1,
                isSelected = !anyTextSelected,
                isOffOption = true
            )
        )

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val fmt = group.getTrackFormat(i)
                            if (fmt.height > 0) {
                                videoResolutionBadge = when {
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
                        val defaultAudio = context.getString(R.string.default_audio_track, i + 1)
                        val langName = formatLanguage(fmt.language, fmt.label, i + 1, isAudio = true, defaultLabel = defaultAudio)
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
                        val defaultSub = context.getString(R.string.default_subtitle_track, i + 1)
                        val langName = formatLanguage(fmt.language, fmt.label, i + 1, isAudio = false, defaultLabel = defaultSub)
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
        audioTracks = audioList
        subtitleTracks = textList
    }

    // Load stream whenever session.streamUrl changes, with subtitles disabled by default
    LaunchedEffect(session.streamUrl) {
        playerError = null
        isBuffering = true
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        val item = ExoMediaItem.fromUri(session.streamUrl)
        exoPlayer.setMediaItem(item)
        if (session.startPositionMs > 0L) {
            exoPlayer.seekTo(session.startPositionMs)
        } else if (session.savedResumePositionMs > 15_000L) {
            exoPlayer.seekTo(session.savedResumePositionMs)
            currentPositionMs = session.savedResumePositionMs
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        lastInteractionTick = System.currentTimeMillis()
        showOsd = true
    }

    var hasHandledCompletion by remember(session.streamUrl) { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED && !hasHandledCompletion) {
                    hasHandledCompletion = true
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    if (dur > 0L) {
                        onProgressUpdate(dur, dur)
                    }
                    onPlayNextEpisode(session.nextEpisode)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = "Playback error (${error.errorCodeName})"
                showOsd = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (dur > 0L) {
                onProgressUpdate(pos, dur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying && !exoPlayer.isPlaying) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Poll playback position & periodically save history every 5 seconds
    LaunchedEffect(exoPlayer, session.streamUrl) {
        var tickCount = 0
        while (true) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L && dur != C.TIME_UNSET) {
                durationMs = dur
            }
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            tickCount++
            if (durationMs > 0L && tickCount % 10 == 0) {
                onProgressUpdate(currentPositionMs, durationMs)
            }
            delay(500L)
        }
    }

    // Continuously update wall-clock time every second so current hour and "Ends at" time stay accurate even when paused
    LaunchedEffect(Unit) {
        while (true) {
            currentWallClockMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // Commit rapid accumulated D-Pad seek after 420ms of inactivity
    LaunchedEffect(seekCommitTick) {
        if (seekCommitTick > 0L && accumulatedSeekDeltaMs != 0L) {
            delay(420L)
            val target = seekPreviewTargetMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
            exoPlayer.seekTo(target)
            currentPositionMs = target
            accumulatedSeekDeltaMs = 0L
            seekPreviewTargetMs = -1L
        }
    }

    // Auto-hide OSD after 4.5s of no interaction when playing
    LaunchedEffect(lastInteractionTick, isPlaying, activeModal, showResumePrompt) {
        if (activeModal == PlayerModalType.NONE && isPlaying && playerError == null) {
            delay(if (showResumePrompt) 6500L else 4500L)
            showOsd = false
            showResumePrompt = false
        }
    }

    // Remote D-Pad Focus Management across OSD, Modals, Error Overlay, and Fullscreen Video
    LaunchedEffect(showOsd, showResumePrompt, activeModal, playerError) {
        delay(60L)
        try {
            when {
                playerError != null -> retryButtonFocusRequester.requestFocus()
                activeModal != PlayerModalType.NONE -> {}
                showResumePrompt && showOsd -> resumeButtonFocusRequester.requestFocus()
                showOsd -> playPauseFocusRequester.requestFocus()
                else -> rootFocusRequester.requestFocus()
            }
        } catch (_: Exception) {}
    }

    fun triggerSeekDelta(deltaMs: Long) {
        if (durationMs <= 0L) return
        val basePos = if (seekPreviewTargetMs >= 0L) seekPreviewTargetMs else currentPositionMs
        accumulatedSeekDeltaMs += deltaMs
        seekPreviewTargetMs = (basePos + deltaMs).coerceIn(0L, durationMs)
        seekCommitTick = System.currentTimeMillis()
        lastInteractionTick = System.currentTimeMillis()
        showOsd = true
    }

    BackHandler {
        when {
            activeModal != PlayerModalType.NONE -> {
                activeModal = PlayerModalType.NONE
                lastInteractionTick = System.currentTimeMillis()
            }
            else -> {
                onBack(currentPositionMs, durationMs)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AbyssVoid)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                lastInteractionTick = System.currentTimeMillis()

                if (activeModal != PlayerModalType.NONE) {
                    return@onPreviewKeyEvent false
                }

                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_HOME -> {
                        onBack(currentPositionMs, durationMs)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        showOsd = true
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        exoPlayer.play()
                        showOsd = true
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        exoPlayer.pause()
                        showOsd = true
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        triggerSeekDelta(10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        triggerSeekDelta(-10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showOsd || accumulatedSeekDeltaMs != 0L) {
                            triggerSeekDelta(-10_000L)
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!showOsd || accumulatedSeekDeltaMs != 0L) {
                            triggerSeekDelta(10_000L)
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (!showOsd) {
                            showOsd = true
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (!showOsd) {
                            showOsd = true
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (!showOsd) {
                            showOsd = true
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    ) {
        // 1. Hardware-accelerated Media3 PlayerView Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.argb(140, 0, 0, 0),
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                android.graphics.Color.BLACK,
                                android.graphics.Typeface.DEFAULT_BOLD
                            )
                        )
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                    }
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Buffering Spinner or Seek Delta Center Pill
        if (isBuffering && playerError == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3000000))
                    .border(1.dp, AbyssBorderHairline, RoundedCornerShape(50))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Rapid Seek Delta Preview Badge (e.g. "+20s • 01:14:32")
        AnimatedVisibility(
            visible = accumulatedSeekDeltaMs != 0L,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val deltaSec = accumulatedSeekDeltaMs / 1000L
            val sign = if (deltaSec > 0) "+" else ""
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AbyssSurfaceGlassElevated)
                    .border(1.dp, Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AbyssIcon(
                        type = if (deltaSec >= 0) AbyssIconType.SEEK_FORWARD_10 else AbyssIconType.SEEK_BACK_10,
                        color = Color.White,
                        size = 18.dp
                    )
                    Text(
                        text = "${sign}${deltaSec}s",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color.White
                    )
                    if (seekPreviewTargetMs >= 0L) {
                        Text(
                            text = "•  ${formatDurationMs(seekPreviewTargetMs)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = AbyssTextSecondary
                        )
                    }
                }
            }
        }

        // 3. Error Overlay with External Player Fallback
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AbyssSurfaceGlassElevated)
                    .border(1.dp, AbyssBorderHairline, RoundedCornerShape(16.dp))
                    .padding(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = playerError ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AbyssTextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OsdPillButton(
                            label = stringResource(R.string.btn_retry),
                            icon = AbyssIconType.RESTART,
                            isPrimary = true,
                            focusRequester = retryButtonFocusRequester,
                            onClick = {
                                playerError = null
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        )
                        OsdPillButton(
                            label = stringResource(R.string.btn_external_player),
                            icon = AbyssIconType.EXTERNAL_LINK,
                            onClick = {
                                onLaunchExternalFallback(session.streamUrl, session.title)
                            }
                        )
                        OsdPillButton(
                            label = stringResource(R.string.btn_close),
                            icon = AbyssIconType.CLOSE,
                            onClick = { onBack(currentPositionMs, durationMs) }
                        )
                    }
                }
            }
        }

        // 4. Custom 10-Foot Glassmorphic OSD Overlay
        AnimatedVisibility(
            visible = showOsd,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Vignette & Metadata Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    AbyssVoid.copy(alpha = 0.90f),
                                    AbyssVoid.copy(alpha = 0.50f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 36.dp, vertical = 22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (videoResolutionBadge.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x33FFFFFF))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = videoResolutionBadge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            ),
                                            color = AbyssTextPrimary
                                        )
                                    }
                                }
                                if (session.subtitle.isNotBlank()) {
                                    Text(
                                        text = session.subtitle,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                        color = AbyssTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = AbyssTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Top-right current wall-clock time & Ends At ("Fin à HH:mm")
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(AbyssPillGlass)
                                .border(1.dp, AbyssBorderHairline, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentWallClockMs))
                            Text(
                                text = nowStr,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = AbyssTextPrimary
                            )

                            val displayPos = if (seekPreviewTargetMs >= 0L) seekPreviewTargetMs else currentPositionMs
                            val remainingMs = (durationMs - displayPos).coerceAtLeast(0L)
                            if (durationMs > 0L && remainingMs > 0L) {
                                val endsAtDate = Date(currentWallClockMs + remainingMs)
                                val endsAtStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(endsAtDate)
                                Text(
                                    text = "•",
                                    color = AbyssTextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = stringResource(R.string.ends_at_fmt, endsAtStr),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = AbyssGold
                                )
                            }
                        }
                    }
                }

                // Bottom Vignette, Resume Banner, Seekbar & Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    AbyssVoid.copy(alpha = 0.70f),
                                    AbyssVoid.copy(alpha = 0.96f)
                                )
                            )
                        )
                        .padding(horizontal = 36.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Seamless Resume / Restart Banner if resumed from saved timestamp
                    if (showResumePrompt) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AbyssSurfaceGlassElevated)
                                .border(1.dp, AbyssBorderHairline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.series_resume_at, formatDurationMs(session.savedResumePositionMs)),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = AbyssTextPrimary
                            )
                            OsdPillButton(
                                label = stringResource(R.string.cta_resume),
                                icon = AbyssIconType.PLAY,
                                isPrimary = true,
                                focusRequester = resumeButtonFocusRequester,
                                onClick = {
                                    showResumePrompt = false
                                    lastInteractionTick = System.currentTimeMillis()
                                }
                            )
                            OsdPillButton(
                                label = stringResource(R.string.restart_from_beginning),
                                icon = AbyssIconType.RESTART,
                                onClick = {
                                    exoPlayer.seekTo(0L)
                                    currentPositionMs = 0L
                                    showResumePrompt = false
                                    lastInteractionTick = System.currentTimeMillis()
                                }
                            )
                        }
                    }

                    // Interactive Seekbar & Time Indicators (for VOD / Series)
                    if (durationMs > 0L) {
                        val effectivePos = if (seekPreviewTargetMs >= 0L) seekPreviewTargetMs else currentPositionMs
                        val progressFraction = (effectivePos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        val bufferedFraction = (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        val remainingMs = (durationMs - effectivePos).coerceAtLeast(0L)
                        val endsAtStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(currentWallClockMs + remainingMs))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatDurationMs(effectivePos)}  /  ${formatDurationMs(durationMs)}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    color = AbyssTextPrimary
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "-${formatDurationMs(remainingMs)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                        color = AbyssTextSecondary
                                    )
                                    Text(
                                        text = stringResource(R.string.ends_at_fmt, endsAtStr),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }

                            // Progress Track Bar
                            var seekBarFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (seekBarFocused) 10.dp else 6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0x33FFFFFF))
                                    .onFocusChanged { seekBarFocused = it.isFocused }
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (ev.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    triggerSeekDelta(-10_000L)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    triggerSeekDelta(10_000L)
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .focusable()
                            ) {
                                // Buffered bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(bufferedFraction)
                                        .background(Color(0x55FFFFFF))
                                )
                                // Active progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressFraction)
                                        .background(if (seekBarFocused) AbyssGold else Color.White)
                                )
                            }
                        }
                    }

                    // Control Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OsdPillButton(
                            label = if (isPlaying) {
                                stringResource(R.string.player_pause)
                            } else {
                                stringResource(R.string.cta_play_stream)
                            },
                            icon = if (isPlaying) AbyssIconType.PAUSE else AbyssIconType.PLAY,
                            isPrimary = true,
                            focusRequester = playPauseFocusRequester,
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                lastInteractionTick = System.currentTimeMillis()
                            }
                        )

                        OsdPillButton(
                            label = "-10s",
                            icon = AbyssIconType.SEEK_BACK_10,
                            onClick = { triggerSeekDelta(-10_000L) }
                        )
                        OsdPillButton(
                            label = "+10s",
                            icon = AbyssIconType.SEEK_FORWARD_10,
                            onClick = { triggerSeekDelta(10_000L) }
                        )

                        if (session.nextEpisode != null) {
                            val nextEp = session.nextEpisode
                            val isNearEnd = durationMs > 0L && (durationMs - currentPositionMs) < 180_000L
                            val epCode = "S${nextEp.seasonNumber.toString().padStart(2, '0')}E${nextEp.episodeNumber.toString().padStart(2, '0')}"
                            OsdPillButton(
                                label = stringResource(R.string.player_next, epCode),
                                icon = AbyssIconType.SKIP_NEXT,
                                isPrimary = isNearEnd,
                                onClick = {
                                    onProgressUpdate(currentPositionMs, durationMs)
                                    onPlayNextEpisode(nextEp)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f, fill = false))

                        val activeAudioLabel = audioTracks.firstOrNull { it.isSelected }?.label
                        val audioTitle = stringResource(R.string.audio_tracks_title)
                        OsdPillButton(
                            label = if (!activeAudioLabel.isNullOrBlank()) "$audioTitle • $activeAudioLabel" else audioTitle,
                            icon = AbyssIconType.AUDIO_SPEAKER,
                            onClick = {
                                refreshTracks(exoPlayer.currentTracks)
                                activeModal = PlayerModalType.AUDIO_TRACKS
                                lastInteractionTick = System.currentTimeMillis()
                            }
                        )

                        val activeSub = subtitleTracks.firstOrNull { it.isSelected && !it.isOffOption }
                        val subTitle = stringResource(R.string.subtitles_title)
                        val subStateText = activeSub?.label
                        OsdPillButton(
                            label = if (subStateText != null) {
                                "$subTitle • $subStateText"
                            } else {
                                subTitle
                            },
                            icon = AbyssIconType.SUBTITLES_CC,
                            onClick = {
                                refreshTracks(exoPlayer.currentTracks)
                                activeModal = PlayerModalType.SUBTITLE_TRACKS
                                lastInteractionTick = System.currentTimeMillis()
                            }
                        )

                        OsdPillButton(
                            label = stringResource(R.string.btn_exit),
                            icon = AbyssIconType.CLOSE,
                            onClick = { onBack(currentPositionMs, durationMs) }
                        )
                    }
                }
            }
        }

        // 5. Audio / Subtitle / Live Channel Selection Side Drawer Modal
        AnimatedVisibility(
            visible = activeModal != PlayerModalType.NONE,
            enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut(tween(150)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlayerSideDrawerModal(
                modalType = activeModal,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                onSelectAudio = { opt ->
                    val group = opt.group
                    if (group != null) {
                        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(opt.trackIndex))
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(override)
                            .build()
                    }
                    activeModal = PlayerModalType.NONE
                    lastInteractionTick = System.currentTimeMillis()
                },
                onSelectSubtitle = { opt ->
                    if (opt.isOffOption) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    } else if (opt.group != null) {
                        val override = TrackSelectionOverride(opt.group.mediaTrackGroup, listOf(opt.trackIndex))
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(override)
                            .build()
                    }
                    activeModal = PlayerModalType.NONE
                    lastInteractionTick = System.currentTimeMillis()
                },
                onClose = {
                    activeModal = PlayerModalType.NONE
                    lastInteractionTick = System.currentTimeMillis()
                }
            )
        }
    }
}

@Composable
private fun PlayerSideDrawerModal(
    modalType: PlayerModalType,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectSubtitle: (TrackOption) -> Unit,
    onClose: () -> Unit
) {
    val firstItemRequester = remember { FocusRequester() }
    val closeBtnRequester = remember { FocusRequester() }

    LaunchedEffect(modalType) {
        delay(80L)
        try {
            firstItemRequester.requestFocus()
        } catch (_: Exception) {
            try { closeBtnRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val headerTitle = when (modalType) {
        PlayerModalType.AUDIO_TRACKS -> stringResource(R.string.audio_tracks_title)
        PlayerModalType.SUBTITLE_TRACKS -> stringResource(R.string.subtitles_title)
        PlayerModalType.NONE -> ""
    }

    Box(
        modifier = Modifier
            .width(380.dp)
            .fillMaxHeight()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AbyssSurfaceGlassElevated)
            .border(1.dp, AbyssBorderHairline, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = AbyssTextPrimary
                )
                OsdPillButton(
                    label = "",
                    icon = AbyssIconType.CLOSE,
                    focusRequester = closeBtnRequester,
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (modalType) {
                PlayerModalType.AUDIO_TRACKS -> {
                    if (audioTracks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_audio_tracks),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AbyssTextSecondary
                        )
                    } else {
                        val selectedIdx = audioTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(audioTracks) { idx, opt ->
                                TrackOptionRowItem(
                                    title = opt.label,
                                    subtitle = opt.subLabel,
                                    isSelected = opt.isSelected,
                                    focusRequester = if (idx == selectedIdx) firstItemRequester else null,
                                    onClick = { onSelectAudio(opt) }
                                )
                            }
                        }
                    }
                }
                PlayerModalType.SUBTITLE_TRACKS -> {
                    val selectedIdx = subtitleTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(subtitleTracks) { idx, opt ->
                            TrackOptionRowItem(
                                title = opt.label,
                                subtitle = opt.subLabel,
                                isSelected = opt.isSelected,
                                focusRequester = if (idx == selectedIdx) firstItemRequester else null,
                                onClick = { onSelectSubtitle(opt) }
                            )
                        }
                    }
                }
                PlayerModalType.NONE -> Unit
            }
        }
    }
}

@Composable
private fun TrackOptionRowItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(110),
        label = "trackRowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0x28FFFFFF)
                    else -> Color(0x12FFFFFF)
                }
            )
            .border(
                1.dp,
                if (isFocused || isSelected) Color.White else AbyssBorderHairline,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = if (isFocused) Color.Black else AbyssTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isFocused) Color(0xFF444444) else AbyssTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                AbyssIcon(
                    type = AbyssIconType.CHECK,
                    color = if (isFocused) Color.Black else Color.White,
                    size = 14.dp
                )
            }
        }
    }
}

@Composable
private fun OsdPillButton(
    label: String,
    icon: AbyssIconType? = null,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(110),
        label = "osdBtnScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isPrimary -> Color(0x33FFFFFF)
            else -> AbyssPillGlass
        },
        animationSpec = tween(110),
        label = "osdBtnBg"
    )
    val borderColor = when {
        isFocused -> Color.White
        isPrimary -> Color(0x66FFFFFF)
        else -> AbyssBorderHairline
    }
    val contentColor = when {
        isFocused -> Color.Black
        else -> AbyssTextPrimary
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                1.dp,
                borderColor,
                RoundedCornerShape(50)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = if (label.isEmpty()) 10.dp else 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (icon != null) {
                AbyssIcon(
                    type = icon,
                    color = contentColor,
                    size = 13.dp
                )
            }
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

private fun formatLanguage(
    langCode: String?,
    trackLabel: String?,
    index: Int,
    isAudio: Boolean,
    defaultLabel: String
): String {
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
        m.contains("dts") || c.contains("dts") -> "DTS"
        m.contains("truehd") -> "Dolby TrueHD"
        m.contains("flac") -> "FLAC"
        m.contains("aac") || c.contains("mp4a") -> "AAC"
        m.contains("opus") -> "Opus"
        m.contains("vorbis") -> "Vorbis"
        m.contains("mpeg") || m.contains("mp3") -> "MP3"
        else -> ""
    }
}

private fun formatChannels(channelCount: Int): String {
    return when (channelCount) {
        8 -> "7.1"
        6 -> "5.1"
        2 -> "2.0 Stéréo"
        1 -> "1.0 Mono"
        else -> if (channelCount > 0) "${channelCount}ch" else ""
    }
}

private fun formatSubtitleFormat(mimeType: String?, selectionFlags: Int): String {
    val m = mimeType?.lowercase(Locale.ROOT).orEmpty()
    val fmt = when {
        m.contains("subrip") || m.contains("srt") -> "SRT"
        m.contains("ssa") || m.contains("ass") -> "ASS/SSA"
        m.contains("vtt") -> "WebVTT"
        m.contains("pgs") -> "PGS"
        m.contains("dvbsubs") -> "DVB"
        m.contains("ttml") -> "TTML"
        else -> ""
    }
    val forced = if ((selectionFlags and C.SELECTION_FLAG_FORCED) != 0) "Forcé" else ""
    return listOf(fmt, forced).filter { it.isNotBlank() }.joinToString(" • ")
}
