package dev.dylanakp.abysstvlauncher.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.dylanakp.abysstvlauncher.data.JellyfinRepository
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.data.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.data.PlaybackHistoryRepository
import dev.dylanakp.abysstvlauncher.data.TmdbRepository
import dev.dylanakp.abysstvlauncher.data.local.AbyssDatabase
import dev.dylanakp.abysstvlauncher.player.PlayerManagerImpl
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

class RemoteHttpServerRouteTest {

    @Test
    fun apiRoutesAreReachableBeforeAssetCatchAll_andAuthRejectsUnauthorized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val client = OkHttpClient()
        val database = AbyssDatabase.getInstance(context)
        val preferences = LauncherPreferences(context)
        val tmdb = TmdbRepository(client, json)
        val jellyfin = JellyfinRepository(client, json, preferences, context, tmdb)
        val cache = MediaLibraryCache(database, json)
        val history = PlaybackHistoryRepository(database, json)
        val player = PlayerManagerImpl(context, preferences)
        val controller = TvCommandController(context, preferences, player, jellyfin, cache, history)
        val server = RemoteHttpServer(context, controller, json)
        val port = ServerSocket(0).use { it.localPort }

        server.start(port)
        try {
            assertEquals(200, status(port, "/api/health"))
            assertEquals(401, status(port, "/api/state"))
            assertEquals(200, status(port, "/index.html"))
        } finally {
            server.stop()
        }
    }

    private fun status(port: Int, path: String): Int {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
