package dev.dylanakp.abysstvlauncher.data

import android.util.Log
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository as IJellyfinRepository
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.JellyfinDisplayMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

enum class JellyfinPlaystateCommand {
    PAUSE,
    UNPAUSE,
    PLAY_PAUSE,
    STOP,
    SEEK,
    FAST_FORWARD,
    REWIND
}

data class JellyfinPlaystateEvent(
    val command: JellyfinPlaystateCommand,
    val seekPositionMs: Long? = null
)

internal sealed interface JellyfinWsEvent {
    data class ForceKeepAlive(val intervalSec: Int) : JellyfinWsEvent
    data object KeepAlive : JellyfinWsEvent
    data class DisplayMessage(val message: JellyfinDisplayMessage) : JellyfinWsEvent
    data class Playstate(val event: JellyfinPlaystateEvent) : JellyfinWsEvent
}

internal fun parseJellyfinWsEvent(raw: String, json: Json): JellyfinWsEvent? {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val messageType = root["MessageType"]?.jsonPrimitive?.contentOrNull ?: return null

    return when (messageType) {
        "ForceKeepAlive" -> {
            val sec = root["Data"]?.jsonPrimitive?.intOrNull ?: 30
            JellyfinWsEvent.ForceKeepAlive(sec)
        }
        "KeepAlive" -> JellyfinWsEvent.KeepAlive
        "GeneralCommand" -> parseGeneralCommand(root["Data"]?.jsonObject)
        "Playstate" -> parsePlaystateCommand(root["Data"]?.jsonObject)
        else -> null
    }
}

private fun parsePlaystateCommand(data: JsonObject?): JellyfinWsEvent? {
    if (data == null) return null
    val rawCommand = data["Command"]?.jsonPrimitive?.contentOrNull ?: return null
    val command = when (rawCommand.lowercase()) {
        "pause" -> JellyfinPlaystateCommand.PAUSE
        "unpause" -> JellyfinPlaystateCommand.UNPAUSE
        "playpause" -> JellyfinPlaystateCommand.PLAY_PAUSE
        "stop" -> JellyfinPlaystateCommand.STOP
        "seek" -> JellyfinPlaystateCommand.SEEK
        "fastforward" -> JellyfinPlaystateCommand.FAST_FORWARD
        "rewind" -> JellyfinPlaystateCommand.REWIND
        else -> return null
    }
    val seekTicks = data["SeekPositionTicks"]?.jsonPrimitive?.longOrNull
    val seekMs = seekTicks?.let { (it / 10_000L).coerceAtLeast(0L) }
    return JellyfinWsEvent.Playstate(JellyfinPlaystateEvent(command, seekMs))
}

private fun parseGeneralCommand(data: JsonObject?): JellyfinWsEvent? {
    if (data == null) return null
    val name = data["Name"]?.jsonPrimitive?.contentOrNull ?: return null
    if (!name.equals("DisplayMessage", ignoreCase = true)) return null

    val args = data["Arguments"]?.jsonObject ?: return null
    val rawHeader = args["Header"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val header = if (rawHeader.equals("Message from server", ignoreCase = true)) "" else rawHeader
    val text = args["Text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (header.isBlank() && text.isBlank()) return null

    val rawTimeout = args["TimeoutMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val timeoutMs = if (rawTimeout != null && rawTimeout > 0L) {
        rawTimeout.coerceIn(1_000L, 60_000L)
    } else {
        DEFAULT_DISPLAY_TIMEOUT_MS
    }

    return JellyfinWsEvent.DisplayMessage(
        JellyfinDisplayMessage(
            header = header,
            text = text,
            timeoutMs = timeoutMs
        )
    )
}

private const val DEFAULT_DISPLAY_TIMEOUT_MS = 6_000L
private const val TAG = "AbyssJellyfinWS"

class JellyfinWebSocketClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val jellyfinRepository: IJellyfinRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _displayMessages = MutableSharedFlow<JellyfinDisplayMessage>(extraBufferCapacity = 16)
    val displayMessages: SharedFlow<JellyfinDisplayMessage> = _displayMessages.asSharedFlow()

    private val _playstateCommands = MutableSharedFlow<JellyfinPlaystateEvent>(extraBufferCapacity = 16)
    val playstateCommands: SharedFlow<JellyfinPlaystateEvent> = _playstateCommands.asSharedFlow()

    private val lock = Any()
    private var activeConfig: JellyfinConfig? = null
    private var webSocket: WebSocket? = null
    private var keepAliveJob: Job? = null
    private var reconnectJob: Job? = null
    private var keepAliveIntervalMs: Long = 15_000L
    private val reconnectAttempts = AtomicInteger(0)
    private val socketEpoch = AtomicInteger(0)

    fun connect(config: JellyfinConfig) {
        val wsUrl = jellyfinRepository.buildWebSocketUrl(config)
        if (wsUrl == null) {
            disconnect()
            return
        }

        synchronized(lock) {
            val current = activeConfig
            if (webSocket != null &&
                current?.serverUrl == config.serverUrl &&
                current.accessToken == config.accessToken
            ) {
                return
            }
            activeConfig = config
            reconnectAttempts.set(0)
            openSocketLocked(config, wsUrl)
        }
    }

    fun reconnectIfNeeded() {
        val cfg = synchronized(lock) { activeConfig } ?: return
        val wsUrl = jellyfinRepository.buildWebSocketUrl(cfg) ?: return
        synchronized(lock) {
            if (webSocket == null) {
                openSocketLocked(cfg, wsUrl)
            }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            activeConfig = null
            socketEpoch.incrementAndGet()
            reconnectJob?.cancel()
            reconnectJob = null
            keepAliveJob?.cancel()
            keepAliveJob = null
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
        }
    }

    private fun openSocketLocked(config: JellyfinConfig, wsUrl: String) {
        reconnectJob?.cancel()
        reconnectJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null
        webSocket?.cancel()

        val authHeader = jellyfinRepository.buildAuthHeaderValue(config.accessToken)
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", authHeader)
            .header("X-Emby-Authorization", authHeader)
            .build()

        val epoch = socketEpoch.incrementAndGet()
        lateinit var createdSocket: WebSocket
        createdSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (epoch != socketEpoch.get()) {
                    webSocket.close(1000, "Stale socket")
                    return
                }
                reconnectAttempts.set(0)
                Log.i(TAG, "WebSocket connected")
                scope.launch {
                    jellyfinRepository.reportCapabilities(config)
                }
                startKeepAlive(webSocket, epoch)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (epoch != socketEpoch.get()) return
                when (val event = parseJellyfinWsEvent(text, json)) {
                    is JellyfinWsEvent.ForceKeepAlive -> {
                        keepAliveIntervalMs = event.intervalSec.coerceAtLeast(1) * 1000L
                        startKeepAlive(webSocket, epoch)
                    }
                    JellyfinWsEvent.KeepAlive -> {
                        webSocket.send("""{"MessageType":"KeepAlive"}""")
                    }
                    is JellyfinWsEvent.DisplayMessage -> {
                        _displayMessages.tryEmit(event.message)
                    }
                    is JellyfinWsEvent.Playstate -> {
                        _playstateCommands.tryEmit(event.event)
                    }
                    null -> Unit
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect(webSocket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                handleDisconnect(webSocket)
            }
        })
        webSocket = createdSocket
    }

    private fun startKeepAlive(socket: WebSocket, epoch: Int) {
        synchronized(lock) {
            keepAliveJob?.cancel()
            keepAliveJob = scope.launch {
                while (isActive) {
                    delay(keepAliveIntervalMs)
                    if (epoch != socketEpoch.get()) break
                    if (!socket.send("""{"MessageType":"KeepAlive"}""")) break
                }
            }
        }
    }

    private fun handleDisconnect(socket: WebSocket) {
        val cfgToReconnect = synchronized(lock) {
            if (webSocket !== socket) return
            webSocket = null
            keepAliveJob?.cancel()
            keepAliveJob = null
            activeConfig
        } ?: return

        val wsUrl = jellyfinRepository.buildWebSocketUrl(cfgToReconnect) ?: return
        val attempt = reconnectAttempts.getAndIncrement()
        val backoffMs = (5_000L shl attempt.coerceAtMost(3)).coerceAtMost(30_000L)

        synchronized(lock) {
            if (activeConfig == null || reconnectJob?.isActive == true) return
            reconnectJob = scope.launch {
                delay(backoffMs)
                synchronized(lock) {
                    val latest = activeConfig ?: return@launch
                    if (webSocket == null) {
                        openSocketLocked(latest, wsUrl)
                    }
                }
            }
        }
    }
}
