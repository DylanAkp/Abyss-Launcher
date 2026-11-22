package dev.dylanakp.abysstvlauncher.data

import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinWebSocketClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun parseDisplayMessage_withHeaderTextAndTimeout() {
        val raw = """
            {
              "MessageType": "GeneralCommand",
              "MessageId": "abc",
              "Data": {
                "Name": "DisplayMessage",
                "ControllingUserId": "user1",
                "Arguments": {
                  "Header": "Server Notice",
                  "Text": "Restarting in 5 minutes",
                  "TimeoutMs": "8500"
                }
              }
            }
        """.trimIndent()

        val event = parseJellyfinWsEvent(raw, json)
        assertTrue(event is JellyfinWsEvent.DisplayMessage)
        val msg = (event as JellyfinWsEvent.DisplayMessage).message
        assertEquals("Server Notice", msg.header)
        assertEquals("Restarting in 5 minutes", msg.text)
        assertEquals(8500L, msg.timeoutMs)
    }

    @Test
    fun parseDisplayMessage_withoutTimeoutUsesDefaultAndClamps() {
        val rawNoTimeout = """
            {
              "MessageType": "GeneralCommand",
              "Data": {
                "Name": "DisplayMessage",
                "Arguments": {
                  "Text": "Hello TV"
                }
              }
            }
        """.trimIndent()

        val event = parseJellyfinWsEvent(rawNoTimeout, json) as JellyfinWsEvent.DisplayMessage
        assertEquals("", event.message.header)
        assertEquals("Hello TV", event.message.text)
        assertEquals(6000L, event.message.timeoutMs)

        val rawTooShort = """
            {
              "MessageType": "GeneralCommand",
              "Data": {
                "Name": "DisplayMessage",
                "Arguments": {
                  "Text": "Quick",
                  "TimeoutMs": "100"
                }
              }
            }
        """.trimIndent()
        val clamped = parseJellyfinWsEvent(rawTooShort, json) as JellyfinWsEvent.DisplayMessage
        assertEquals(1000L, clamped.message.timeoutMs)
    }

    @Test
    fun parseDisplayMessage_ignoresBlankOrOtherCommands() {
        val blank = """
            {
              "MessageType": "GeneralCommand",
              "Data": {
                "Name": "DisplayMessage",
                "Arguments": { "Header": "  ", "Text": "" }
              }
            }
        """.trimIndent()
        assertNull(parseJellyfinWsEvent(blank, json))

        val other = """
            {
              "MessageType": "GeneralCommand",
              "Data": { "Name": "GoHome", "Arguments": {} }
            }
        """.trimIndent()
        assertNull(parseJellyfinWsEvent(other, json))
    }

    @Test
    fun parseDisplayMessage_stripsDefaultServerHeader() {
        val raw = """
            {
              "MessageType": "GeneralCommand",
              "Data": {
                "Name": "DisplayMessage",
                "Arguments": {
                  "Header": "Message from server",
                  "Text": "Maintenance soon"
                }
              }
            }
        """.trimIndent()
        val event = parseJellyfinWsEvent(raw, json) as JellyfinWsEvent.DisplayMessage
        assertEquals("", event.message.header)
        assertEquals("Maintenance soon", event.message.text)
    }

    @Test
    fun parsePlaystate_pauseStopAndSeek() {
        val pause = parseJellyfinWsEvent("""{"MessageType":"Playstate","Data":{"Command":"Pause"}}""", json)
        assertEquals(JellyfinWsEvent.Playstate(JellyfinPlaystateEvent(JellyfinPlaystateCommand.PAUSE)), pause)

        val stop = parseJellyfinWsEvent("""{"MessageType":"Playstate","Data":{"Command":"Stop"}}""", json)
        assertEquals(JellyfinWsEvent.Playstate(JellyfinPlaystateEvent(JellyfinPlaystateCommand.STOP)), stop)

        val seek = parseJellyfinWsEvent(
            """{"MessageType":"Playstate","Data":{"Command":"Seek","SeekPositionTicks":125000000}}""",
            json
        )
        assertEquals(
            JellyfinWsEvent.Playstate(JellyfinPlaystateEvent(JellyfinPlaystateCommand.SEEK, 12_500L)),
            seek
        )
    }

    @Test
    fun parseKeepAliveAndForceKeepAlive() {
        val force = parseJellyfinWsEvent("""{"MessageType":"ForceKeepAlive","Data":40}""", json)
        assertEquals(JellyfinWsEvent.ForceKeepAlive(40), force)

        val keep = parseJellyfinWsEvent("""{"MessageType":"KeepAlive"}""", json)
        assertEquals(JellyfinWsEvent.KeepAlive, keep)
    }

    @Test
    fun buildWebSocketUrl_convertsHttpAndHttps() {
        val httpUrl = JellyfinRepository.buildWebSocketUrl(
            JellyfinConfig(serverUrl = "http://192.168.1.10:8096/", accessToken = "tok123")
        )
        assertTrue(httpUrl!!.startsWith("ws://192.168.1.10:8096/socket?deviceId="))
        assertFalse("api_key" in httpUrl)

        val httpsUrl = JellyfinRepository.buildWebSocketUrl(
            JellyfinConfig(serverUrl = "https://jf.example.com", accessToken = "tok123")
        )
        assertTrue(httpsUrl!!.startsWith("wss://jf.example.com/socket?deviceId="))
        assertFalse("api_key" in httpsUrl)
    }
}
