package dev.dylanakp.abysstvlauncher.remote

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class RemoteHttpServer(
    private val context: Context,
    private val controller: TvCommandController,
    private val json: Json
) {
    private data class PairAttemptState(val failures: Int = 0, val blockedUntilMs: Long = 0L)

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val pairAttemptsByIp = ConcurrentHashMap<String, PairAttemptState>()

    val isRunning: Boolean
        get() = server != null

    @Synchronized
    fun start(port: Int = TvCommandController.DEFAULT_PORT) {
        if (server != null) return
        try {
            server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(this@RemoteHttpServer.json)
            }
            install(SSE)

            routing {
                get("/") {
                    serveAsset(call, "index.html")
                }

                get("/{path...}") {
                    val segments = call.parameters.getAll("path").orEmpty()
                    val rel = segments.joinToString("/").ifBlank { "index.html" }
                    if (rel.startsWith("api/")) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    serveAsset(call, rel)
                }

                get("/api/health") {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                }

                post("/api/pair") {
                    val clientIp = call.request.local.remoteAddress
                    val now = System.currentTimeMillis()
                    val currentAttempt = pairAttemptsByIp[clientIp]
                    if (currentAttempt != null && now < currentAttempt.blockedUntilMs) {
                        call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "rate_limited"))
                        return@post
                    }

                    val req = call.receive<PairRequest>()
                    val res = controller.pair(req.pin, req.token, req.deviceName)
                    if (res != null) {
                        pairAttemptsByIp.remove(clientIp)
                        call.respond(HttpStatusCode.OK, res)
                    } else {
                        val failures = (currentAttempt?.failures ?: 0) + 1
                        val backoffMs = 1000L shl (failures - 1).coerceAtMost(5)
                        pairAttemptsByIp[clientIp] = PairAttemptState(failures, now + backoffMs)
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_pin"))
                    }
                }

                route("/api") {
                    install(
                        createRouteScopedPlugin("RemoteTokenAuth") {
                            onCall { call ->
                                val reqPath = call.request.path()
                                if (reqPath == "/api/health" || reqPath == "/api/pair") return@onCall
                                val token = call.request.headers["X-Remote-Token"]
                                    ?: call.request.queryParameters["token"]
                                if (!controller.isTokenValid(token)) {
                                    call.respond(HttpStatusCode.Unauthorized)
                                }
                            }
                        }
                    )

                    get("/state") {
                        controller.refreshState()
                        call.respond(HttpStatusCode.OK, controller.tvStateFlow.value)
                    }

                    post("/key") {
                        val body = call.receive<KeyCommand>()
                        controller.sendKey(body.key, body.longPress)
                        call.respond(HttpStatusCode.OK)
                    }

                    post("/volume") {
                        val body = call.receive<VolumeCommand>()
                        controller.adjustVolume(body.action)
                        call.respond(HttpStatusCode.OK)
                    }

                    post("/seek") {
                        val body = call.receive<SeekCommand>()
                        controller.seek(body.positionMs, body.deltaMs)
                        call.respond(HttpStatusCode.OK)
                    }

                    post("/launch") {
                        val body = call.receive<LaunchCommand>()
                        val ok = controller.launchTarget(body.packageName)
                        if (ok) call.respond(HttpStatusCode.OK)
                        else call.respond(HttpStatusCode.NotFound)
                    }

                    post("/text") {
                        val body = call.receive<TextCommand>()
                        controller.sendText(body.text)
                        call.respond(HttpStatusCode.OK)
                    }

                    get("/app-icon/{pkg}") {
                        val pkg = call.parameters["pkg"].orEmpty()
                        val bytes = controller.getAppIconPng(pkg)
                        if (bytes != null) {
                            call.respondBytes(bytes, ContentType.Image.PNG)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }

                    post("/remote/ring") {
                        val ok = controller.ringPhysicalRemote()
                        if (ok) call.respond(HttpStatusCode.OK)
                        else call.respond(HttpStatusCode.NotFound)
                    }

                    get("/media/home") {
                        call.respond(HttpStatusCode.OK, controller.getMediaHome())
                    }

                    get("/media/search") {
                        val q = call.request.queryParameters["q"].orEmpty()
                        call.respond(HttpStatusCode.OK, controller.searchMedia(q))
                    }

                    post("/media/play") {
                        val body = call.receive<PlayMediaCommand>()
                        val ok = controller.playMediaById(body.id)
                        if (ok) call.respond(HttpStatusCode.OK)
                        else call.respond(HttpStatusCode.NotFound)
                    }

                    sse("/events") {
                        controller.onSseClientConnected()
                        try {
                            controller.tvStateFlow.collectLatest { state ->
                                send(ServerSentEvent(data = this@RemoteHttpServer.json.encodeToString(state)))
                            }
                        } finally {
                            controller.onSseClientDisconnected()
                        }
                    }
                }
            }
        }.start(wait = false)
        } catch (e: java.net.BindException) {
            android.util.Log.w("RemoteHttpServer", "Port $port already in use", e)
            server = null
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.stop(200, 500) }
        server = null
    }

    private suspend fun serveAsset(call: io.ktor.server.application.ApplicationCall, relativePath: String) {
        val clean = relativePath.trimStart('/').takeIf { !it.contains("..") } ?: "index.html"
        val bytes = runCatching {
            context.assets.open("remote/$clean").use { it.readBytes() }
        }.getOrNull()

        if (bytes == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val contentType = when {
            clean.endsWith(".html") -> ContentType.Text.Html
            clean.endsWith(".js") -> ContentType.Application.JavaScript
            clean.endsWith(".css") -> ContentType.Text.CSS
            clean.endsWith(".json") -> ContentType.Application.Json
            clean.endsWith(".svg") -> ContentType.Image.SVG
            clean.endsWith(".png") -> ContentType.Image.PNG
            clean.endsWith(".ico") -> ContentType.Image.XIcon
            else -> ContentType.Application.OctetStream
        }
        call.respondBytes(bytes, contentType)
    }
}
