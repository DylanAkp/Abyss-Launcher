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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dylanakp.abysstvlauncher.player.PlayerManager
import dev.dylanakp.abysstvlauncher.player.TrackOption
import org.koin.compose.koinInject
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
import androidx.media3.common.AudioAttributes
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

enum class PlayerModalType {
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
    onLaunchExternalFallback: (String, String) -> Unit,
    playerManager: PlayerManager = koinInject()
) {
    val context = LocalContext.current
    val playerState by playerManager.state.collectAsStateWithLifecycle()
    val exoPlayer = playerManager.player

    val isPlaying = playerState.isPlaying
    val isBuffering = playerState.isBuffering
    val playerError = playerState.error
    val currentPositionMs = playerState.currentPositionMs
    val durationMs = playerState.durationMs
    val bufferedPositionMs = playerState.bufferedPositionMs
    val audioTracks = playerState.audioTracks
    val subtitleTracks = playerState.subtitleTracks
    val videoResolutionBadge = playerState.videoResolutionBadge

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

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val resumeButtonFocusRequester = remember { FocusRequester() }
    val retryButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(session.streamUrl) {
        playerManager.prepare(context, session)
        lastInteractionTick = System.currentTimeMillis()
        showOsd = true
    }

    var hasHandledCompletion by remember(session.streamUrl) { mutableStateOf(false) }

    LaunchedEffect(playerState.isEnded) {
        if (playerState.isEnded && !hasHandledCompletion) {
            hasHandledCompletion = true
            val dur = playerState.durationMs
            if (dur > 0L) {
                onProgressUpdate(dur, dur)
            }
            onPlayNextEpisode(session.nextEpisode)
        }
    }

    DisposableEffect(session.streamUrl) {
        onDispose {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (dur > 0L) {
                onProgressUpdate(pos, dur)
            }
            playerManager.release()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    playerManager.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying && !exoPlayer.isPlaying) {
                        playerManager.play()
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

    // Periodically report playback progress every 5 seconds
    LaunchedEffect(currentPositionMs, durationMs) {
        if (durationMs > 0L && currentPositionMs > 0L) {
            onProgressUpdate(currentPositionMs, durationMs)
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
            playerManager.seekTo(target)
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
                    .background(Color(0xCC101420))
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
                    .background(Color(0xCC101420))
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
                    .background(Color(0xF0121622))
                    .padding(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = playerError,
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
                                playerManager.retry(context, session)
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
                                .background(Color(0x66101420))
                                .padding(horizontal = 16.dp, vertical = 7.dp),
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
                                .background(Color(0xDD181D2A))
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
                                    playerManager.seekTo(0L)
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
                            label = activeAudioLabel?.ifBlank { audioTitle } ?: audioTitle,
                            icon = AbyssIconType.AUDIO_SPEAKER,
                            onClick = {
                                activeModal = PlayerModalType.AUDIO_TRACKS
                                lastInteractionTick = System.currentTimeMillis()
                            }
                        )

                        val activeSub = subtitleTracks.firstOrNull { it.isSelected && !it.isOffOption }
                        val subTitle = stringResource(R.string.subtitles_title)
                        val subLabel = activeSub?.label?.ifBlank { subTitle } ?: subTitle
                        OsdPillButton(
                            label = subLabel,
                            icon = AbyssIconType.SUBTITLES_CC,
                            onClick = {
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
                    opt.group?.let { playerManager.selectAudioTrack(it, opt.trackIndex) }
                    activeModal = PlayerModalType.NONE
                    lastInteractionTick = System.currentTimeMillis()
                },
                onSelectSubtitle = { opt ->
                    playerManager.selectSubtitleTrack(opt.group, opt.trackIndex, opt.isOffOption)
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
