package dev.dylanakp.abysstvlauncher.remote

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import dev.dylanakp.abysstvlauncher.MainActivity
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.data.SystemAppHelper
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository
import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.domain.repository.PlaybackHistoryRepository
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TvCommandController(
    private val context: Context,
    private val preferences: LauncherPreferences,
    private val playerManager: PlayerManager,
    private val jellyfinRepo: JellyfinRepository,
    private val mediaCache: MediaLibraryCache,
    private val historyRepo: PlaybackHistoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val activeSseClients = AtomicInteger(0)
    private val instrumentation = Instrumentation()
    private val iconCache = ConcurrentHashMap<String, ByteArray>()
    private val mediaById = ConcurrentHashMap<String, MediaItem>()

    private val _playMediaRequests = MutableSharedFlow<MediaItem>(extraBufferCapacity = 4)
    val playMediaRequests: SharedFlow<MediaItem> = _playMediaRequests.asSharedFlow()

    @Volatile
    private var foregroundActivity: WeakReference<Activity>? = null

    private val _tvStateFlow = MutableStateFlow(buildSnapshot())
    val tvStateFlow: StateFlow<TvRemoteState> = _tvStateFlow.asStateFlow()

    init {
        scope.launch {
            combine(
                playerManager.state,
                playerManager.currentSession,
                RemoteMediaListenerService.externalMedia
            ) { _, _, _ -> Unit }
                .collect {
                    refreshState()
                }
        }
    }

    fun attachActivity(activity: Activity) {
        foregroundActivity = WeakReference(activity)
        refreshState()
    }

    fun detachActivity(activity: Activity) {
        if (foregroundActivity?.get() === activity) {
            foregroundActivity = null
        }
    }

    fun getPairingPin(): String = preferences.remotePairingPin

    fun regeneratePin(): String {
        val next = (100000..999999).random().toString()
        preferences.remotePairingPin = next
        return next
    }

    fun getPairedDeviceCount(): Int = preferences.remotePairedTokens.size

    fun revokeAllDevices() {
        preferences.remotePairedTokens = emptySet()
        regeneratePin()
        refreshState()
    }

    fun pair(pin: String?, token: String?, deviceName: String?): PairResponse? {
        val cleanPin = pin?.trim().orEmpty()
        val expectedPin = preferences.remotePairingPin
        val validPin = cleanPin.isNotEmpty() && cleanPin == expectedPin
        val validExisting = !token.isNullOrBlank() && isTokenValid(token)
        if (!validPin && !validExisting) return null

        val issued = if (validExisting) token else UUID.randomUUID().toString().replace("-", "")
        val label = deviceName?.trim()?.take(32)?.ifBlank { "Phone" } ?: "Phone"
        if (!validExisting) {
            val updated = preferences.remotePairedTokens.toMutableSet()
            updated.add("$issued|$label")
            preferences.remotePairedTokens = updated
        }
        refreshState()
        return PairResponse(token = issued, deviceName = label)
    }

    fun isTokenValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return preferences.remotePairedTokens.any { entry ->
            entry.substringBefore("|") == token
        }
    }

    fun onSseClientConnected() {
        activeSseClients.incrementAndGet()
        refreshState()
    }

    fun onSseClientDisconnected() {
        activeSseClients.updateAndGet { (it - 1).coerceAtLeast(0) }
        refreshState()
    }

    fun refreshState() {
        _tvStateFlow.value = buildSnapshot()
    }

    private fun buildSnapshot(): TvRemoteState {
        val session = playerManager.currentSession.value
        val pState = playerManager.state.value
        val ext = RemoteMediaListenerService.externalMedia.value

        val nowPlaying = when {
            session != null -> NowPlayingState(
                isPlaying = pState.isPlaying,
                title = session.title,
                subtitle = session.subtitle,
                sourceApp = "Abyss Player",
                artworkUrl = session.mediaItem.posterUrl.ifBlank { session.mediaItem.backdropUrl }.ifBlank { null },
                positionMs = pState.currentPositionMs,
                durationMs = pState.durationMs,
                isInternalPlayer = true
            )
            ext != null -> NowPlayingState(
                isPlaying = ext.isPlaying,
                title = ext.title,
                subtitle = ext.subtitle,
                sourceApp = ext.sourceApp,
                artworkUrl = null,
                positionMs = ext.positionMs,
                durationMs = ext.durationMs,
                isInternalPlayer = false
            )
            else -> null
        }

        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val muted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        val canRing = SystemAppHelper.isPackageInstalled(context.packageManager, REMOTE_LOCATOR_PKG)
        val apps = loadInstalledApps()

        return TvRemoteState(
            nowPlaying = nowPlaying,
            volume = vol,
            maxVolume = maxVol,
            isMuted = muted,
            canRingRemote = canRing,
            accessibilityEnabled = RemoteAccessibilityService.instance != null,
            notificationAccessEnabled = RemoteMediaListenerService.instance != null,
            connectedClients = activeSseClients.get(),
            apps = apps
        )
    }

    fun sendKey(key: String) {
        when (key.uppercase()) {
            "PLAY_PAUSE" -> handleMediaPlayPause()
            "NEXT" -> handleMediaNext()
            "PREVIOUS" -> handleMediaPrevious()
            "REWIND" -> handleSeekDelta(-10_000L)
            "FAST_FORWARD" -> handleSeekDelta(10_000L)
            "HOME" -> {
                val acc = RemoteAccessibilityService.instance
                if (acc != null) {
                    acc.performGlobalKey("HOME")
                } else {
                    launchHome()
                }
            }
            else -> {
                val keyCode = KEY_MAP[key.uppercase()] ?: return
                val activity = foregroundActivity?.get()
                if (activity != null && !activity.isFinishing) {
                    scope.launch {
                        runCatching {
                            instrumentation.setInTouchMode(false)
                            instrumentation.sendKeyDownUpSync(keyCode)
                        }.onFailure {
                            activity.runOnUiThread {
                                activity.window?.decorView?.requestFocus()
                                val now = SystemClock.uptimeMillis()
                                activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
                                activity.dispatchKeyEvent(KeyEvent(now + 15, now + 15, KeyEvent.ACTION_UP, keyCode, 0))
                            }
                        }
                    }
                } else {
                    RemoteAccessibilityService.instance?.performGlobalKey(key.uppercase())
                }
            }
        }
    }

    fun sendText(text: String) {
        val activity = foregroundActivity?.get() ?: return
        if (activity.isFinishing || text.isEmpty()) return
        activity.runOnUiThread {
            val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            val events = kcm.getEvents(text.toCharArray())
            if (events != null) {
                events.forEach { activity.dispatchKeyEvent(it) }
            }
        }
    }

    fun adjustVolume(action: String) {
        when (action.uppercase()) {
            "UP" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            "DOWN" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            "MUTE" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
        }
        refreshState()
    }

    fun seek(positionMs: Long?, deltaMs: Long?) {
        if (playerManager.currentSession.value != null) {
            scope.launch(Dispatchers.Main) {
                if (positionMs != null) {
                    playerManager.seekTo(positionMs.coerceAtLeast(0L))
                } else if (deltaMs != null) {
                    playerManager.seekBy(deltaMs)
                }
            }
            return
        }
        val controller = RemoteMediaListenerService.activeController ?: return
        val target = when {
            positionMs != null -> positionMs.coerceAtLeast(0L)
            deltaMs != null -> ((controller.playbackState?.position ?: 0L) + deltaMs).coerceAtLeast(0L)
            else -> return
        }
        controller.transportControls.seekTo(target)
    }

    fun launchTarget(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return SystemAppHelper.launchApp(context, packageName)
    }

    fun ringPhysicalRemote(): Boolean {
        return SystemAppHelper.launchApp(context, REMOTE_LOCATOR_PKG)
    }

    suspend fun getMediaHome(): RemoteMediaHomeResponse {
        historyRepo.ensureLoaded(context)
        val cfg = preferences.jellyfinConfig
        val cached = mediaCache.load(context)
        val allCached = (cached?.movies.orEmpty() + cached?.series.orEmpty())

        val resumeList = if (cfg.isConfigured && cfg.accessToken.isNotBlank()) {
            runCatching { jellyfinRepo.fetchResumeAndNextUp(cfg) }.getOrDefault(emptyList())
        } else emptyList()

        val cwById = linkedMapOf<String, MediaItem>()
        resumeList.forEach { item -> cwById[item.id] = item }
        if (cwById.isEmpty()) {
            val entries = historyRepo.getAllEntries(context)
            allCached.filter { entries.containsKey(it.id) || (it.jellyfinId.isNotBlank() && entries.containsKey(it.jellyfinId)) }
                .take(20)
                .forEach { cwById[it.id] = it }
        }

        val watchlistIds = preferences.watchlistIds
        val watchlist = allCached.filter { watchlistIds.contains(it.id) || watchlistIds.contains(it.jellyfinId) }.take(25)

        return RemoteMediaHomeResponse(
            continueWatching = cwById.values.map { it.toRemoteMedia() },
            watchlist = watchlist.map { it.toRemoteMedia() }
        )
    }

    suspend fun searchMedia(query: String): List<RemoteMediaItem> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val cfg = preferences.jellyfinConfig
        val results = if (cfg.isConfigured && cfg.accessToken.isNotBlank()) {
            runCatching { jellyfinRepo.search(cfg, q) }.getOrDefault(emptyList())
        } else {
            val cached = mediaCache.load(context)
            val all = cached?.movies.orEmpty() + cached?.series.orEmpty()
            all.filter { it.title.contains(q, ignoreCase = true) }.take(25)
        }
        return results.map { it.toRemoteMedia() }
    }

    fun playMediaById(id: String): Boolean {
        val item = mediaById[id] ?: return false
        if (foregroundActivity?.get() == null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            runCatching { context.startActivity(intent) }
        }
        return _playMediaRequests.tryEmit(item)
    }

    private fun MediaItem.toRemoteMedia(): RemoteMediaItem {
        mediaById[id] = this
        val pct = (historyRepo.getProgressPercent(id) * 100f).toInt().coerceIn(0, 100)
        return RemoteMediaItem(
            id = id,
            jellyfinId = jellyfinId,
            title = title,
            subtitle = subtitle.ifBlank { durationOrSeasons },
            category = category.name,
            posterUrl = posterUrl.ifBlank { backdropUrl },
            progressPct = pct,
            year = year
        )
    }

    private fun handleMediaPlayPause() {
        if (playerManager.currentSession.value != null) {
            scope.launch(Dispatchers.Main) { playerManager.togglePlayPause() }
            return
        }
        val controller = RemoteMediaListenerService.activeController
        if (controller != null) {
            val playing = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            if (playing) controller.transportControls.pause() else controller.transportControls.play()
            return
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    private fun handleMediaNext() {
        val controller = RemoteMediaListenerService.activeController
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    private fun handleMediaPrevious() {
        val controller = RemoteMediaListenerService.activeController
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    private fun handleSeekDelta(deltaMs: Long) {
        seek(positionMs = null, deltaMs = deltaMs)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun launchHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun getLocalIpAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    fun getAppIconPng(packageName: String): ByteArray? {
        iconCache[packageName]?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = 96
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
            } else {
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                }
            }
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.toByteArray()
            }.also { bytes ->
                iconCache[packageName] = bytes
            }
        }.getOrNull()
    }

    private fun loadInstalledApps(): List<RemoteAppInfo> {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val hidden = if (preferences.showHiddenApps) emptySet() else preferences.hiddenPackages
        val seen = linkedMapOf<String, RemoteAppInfo>()
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { cat ->
            val intent = Intent(Intent.ACTION_MAIN).addCategory(cat)
            pm.queryIntentActivities(intent, 0).forEach { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@forEach
                if (pkg != selfPkg && pkg !in hidden && !seen.containsKey(pkg)) {
                    val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                    seen[pkg] = RemoteAppInfo(pkg, label)
                }
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    companion object {
        const val DEFAULT_PORT = 5400
        const val REMOTE_LOCATOR_PKG = "com.nvidia.remotelocator"

        private val KEY_MAP = mapOf(
            "DPAD_UP" to KeyEvent.KEYCODE_DPAD_UP,
            "DPAD_DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
            "DPAD_LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
            "DPAD_RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "DPAD_CENTER" to KeyEvent.KEYCODE_DPAD_CENTER,
            "OK" to KeyEvent.KEYCODE_DPAD_CENTER,
            "ENTER" to KeyEvent.KEYCODE_ENTER,
            "BACK" to KeyEvent.KEYCODE_BACK,
            "MENU" to KeyEvent.KEYCODE_MENU,
            "DEL" to KeyEvent.KEYCODE_DEL
        )
    }
}
