package dev.dylanakp.abysstvlauncher.remote

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private class RemoteUnauthorizedException : RuntimeException()

class RemoteHttpServer(
    private val context: Context,
    private val controller: TvCommandController,
    private val json: Json
) {
    private data class Ticket(val token: String, val expiresAtMs: Long)

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val tickets = ConcurrentHashMap<String, Ticket>()

    val isRunning: Boolean
        get() = server != null

    private fun bearerToken(call: ApplicationCall): String? = call.request.headers["Authorization"]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring(7)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun issueTicket(token: String): String {
        val id = UUID.randomUUID().toString()
        tickets[id] = Ticket(token, System.currentTimeMillis() + 60_000L)
        if (tickets.size > 128) {
            tickets.keys.take(tickets.size - 128).forEach { tickets.remove(it) }
        }
        return id
    }

    private fun tokenFromTicket(id: String?): String? {
        if (id.isNullOrBlank()) return null
        val ticket = tickets[id] ?: return null
        if (System.currentTimeMillis() >= ticket.expiresAtMs) {
            tickets.remove(id)
            return null
        }
        return ticket.token
    }

    @Synchronized
    fun start(port: Int = TvCommandController.DEFAULT_PORT) {
        if (server != null) return
        try {
            server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(ContentNegotiation) {
                    json(this@RemoteHttpServer.json)
                }
                install(SSE)

                intercept(ApplicationCallPipeline.Call) {
                    try {
                        proceed()
                    } catch (e: RemoteUnauthorizedException) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                    }
                }

                routing {
                    get("/api/health") {
                        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                    }

                    post("/api/pair") {
                        val req = call.receive<PairRequest>()
                        when (val result = controller.pair(req.pin, req.token, req.deviceName)) {
                            is PairResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                            is PairResult.Failure -> when (result.reason) {
                                PairResult.Reason.RATE_LIMITED -> call.respond(
                                    HttpStatusCode.TooManyRequests,
                                    mapOf("error" to "rate_limited", "retryAfterMs" to result.retryAfterMs)
                                )
                                PairResult.Reason.PIN_EXPIRED -> call.respond(
                                    HttpStatusCode.Unauthorized,
                                    mapOf("error" to "pin_expired")
                                )
                                PairResult.Reason.INVALID_PIN -> call.respond(
                                    HttpStatusCode.Unauthorized,
                                    mapOf("error" to "invalid_pin")
                                )
                            }
                        }
                    }

                    route("/api") {
                        install(
                            createRouteScopedPlugin("RemoteTokenAuth") {
                                onCall { call ->
                                    val reqPath = call.request.path()
                                    if (reqPath == "/api/health" || reqPath == "/api/pair") return@onCall
                                    val headerToken = call.request.headers["X-Remote-Token"]
                                    val bearer = call.request.headers["Authorization"]
                                        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                                        ?.substring(7)
                                        ?.trim()
                                    val ticketToken = if (reqPath == "/api/events" || reqPath.startsWith("/api/app-icon/")) {
                                        tokenFromTicket(call.request.queryParameters["ticket"])
                                    } else {
                                        null
                                    }
                                    val token = headerToken?.takeIf { it.isNotBlank() }
                                        ?: bearer?.takeIf { it.isNotBlank() }
                                        ?: ticketToken
                                    if (token.isNullOrBlank() || !controller.isTokenValid(token)) {
                                        throw RemoteUnauthorizedException()
                                    }
                                }
                            }
                        )

                        get("/ticket") {
                            val token = call.request.headers["X-Remote-Token"]?.takeIf { it.isNotBlank() }
                                ?: bearerToken(call)
                            if (token.isNullOrBlank() || !controller.isTokenValid(token)) {
                                throw RemoteUnauthorizedException()
                            }
                            call.respond(HttpStatusCode.OK, mapOf("ticket" to issueTicket(token)))
                        }

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
                            val valid = when {
                                body.positionMs != null && body.deltaMs == null -> body.positionMs >= 0L
                                body.deltaMs != null && body.positionMs == null ->
                                    body.deltaMs != 0L && kotlin.math.abs(body.deltaMs) <= 3_600_000L
                                else -> false
                            }
                            if (!valid) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_seek"))
                                return@post
                            }
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

                    get("/") {
                        serveAsset(call, "index.html")
                    }

                    get("/{path...}") {
                        val segments = call.parameters.getAll("path").orEmpty()
                        val rel = segments.joinToString("/").ifBlank { "index.html" }
                        serveAsset(call, rel)
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
        tickets.clear()
    }

    private suspend fun serveAsset(call: ApplicationCall, relativePath: String) {
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
