package dev.dylanakp.abysstvlauncher.remote

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalMediaSnapshot(
    val isPlaying: Boolean,
    val title: String,
    val subtitle: String,
    val sourceApp: String,
    val positionMs: Long,
    val durationMs: Long
)

class RemoteMediaListenerService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private var trackedController: MediaController? = null
    private val appLabelCache = mutableMapOf<String, String>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            trackedController?.let { publishSnapshot(it) }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            trackedController?.let { publishSnapshot(it) }
        }
    }

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateFromControllers(controllers.orEmpty())
        }

    fun getActiveController(): MediaController? = trackedController

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(this, RemoteMediaListenerService::class.java)
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(activeSessionsListener, component)
            updateFromControllers(sessionManager?.getActiveSessions(component).orEmpty())
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        runCatching { trackedController?.unregisterCallback(controllerCallback) }
        trackedController = null
        _externalMedia.value = null
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    fun refreshNow() {
        val component = ComponentName(this, RemoteMediaListenerService::class.java)
        runCatching {
            updateFromControllers(sessionManager?.getActiveSessions(component).orEmpty())
        }
    }

    private fun updateFromControllers(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull { controller ->
            val pkg = controller.packageName
            pkg != packageName && controller.playbackState?.state in ACTIVE_STATES
        } ?: controllers.firstOrNull { it.packageName != packageName }

        if (chosen?.sessionToken != trackedController?.sessionToken) {
            runCatching { trackedController?.unregisterCallback(controllerCallback) }
            trackedController = chosen
            if (chosen != null) {
                runCatching { chosen.registerCallback(controllerCallback) }
            }
        }
        if (chosen == null) {
            _externalMedia.value = null
            return
        }
        publishSnapshot(chosen)
    }

    private fun publishSnapshot(chosen: MediaController) {
        val meta = chosen.metadata
        val pb = chosen.playbackState
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        if (title.isBlank()) {
            _externalMedia.value = null
            return
        }
        val subtitle = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val pos = pb?.position?.coerceAtLeast(0L) ?: 0L
        val playing = pb?.state == PlaybackState.STATE_PLAYING
        val pkg = chosen.packageName
        val appLabel = appLabelCache.getOrPut(pkg) {
            runCatching {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(pkg)
        }

        _externalMedia.value = ExternalMediaSnapshot(
            isPlaying = playing,
            title = title,
            subtitle = subtitle,
            sourceApp = appLabel,
            positionMs = pos,
            durationMs = duration
        )
    }

    companion object {
        private val ACTIVE_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_BUFFERING
        )

        @Volatile
        var instance: RemoteMediaListenerService? = null
            private set

        private val _externalMedia = MutableStateFlow<ExternalMediaSnapshot?>(null)
        val externalMedia: StateFlow<ExternalMediaSnapshot?> = _externalMedia.asStateFlow()
    }
}
