package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.model.AbyssAccentPreset
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "abyss_tv_prefs",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "abyss_tv_prefs"))
    }
)

class LauncherPreferences(private val context: Context) {

    private val dataStore = context.applicationContext.launcherDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, Any>()

    val dataFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    init {
        scope.launch {
            dataFlow.collect { prefs ->
                prefs[KEY_LANGUAGE]?.let { memoryCache["language"] = it }
                prefs[KEY_ACCENT_PRESET]?.let { memoryCache["accentPreset"] = it }
                prefs[KEY_AUTO_ROTATE_HERO]?.let { memoryCache["autoRotateHero"] = it }
                prefs[KEY_SHOW_HIDDEN_APPS]?.let { memoryCache["showHiddenApps"] = it }
                prefs[KEY_WEATHER_CITY]?.let { memoryCache["weatherCity"] = it }
                prefs[KEY_TEMP_UNIT]?.let { memoryCache["temperatureUnit"] = it }
            }
        }
    }

    private fun <T> read(block: (Preferences) -> T): T {
        val current = scope.runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) { block(dataFlow.first()) }
        }.getOrNull()
        return current ?: block(emptyPreferences())
    }

    private fun mutate(block: (MutablePreferences: androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch {
            dataStore.edit { block(it) }
        }
    }

    var language: AppLanguage
        get() = read { prefs ->
            val code = prefs[KEY_LANGUAGE] ?: AppLanguage.AUTO.code
            AppLanguage.entries.find { it.code == code } ?: AppLanguage.AUTO
        }
        set(value) = mutate { it[KEY_LANGUAGE] = value.code }

    var accentPreset: AbyssAccentPreset
        get() = read { prefs ->
            AbyssAccentPreset.fromId(prefs[KEY_ACCENT_PRESET] ?: AbyssAccentPreset.ABYSS_VIOLET.id)
        }
        set(value) = mutate { it[KEY_ACCENT_PRESET] = value.id }

    var autoRotateHero: Boolean
        get() = read { prefs -> prefs[KEY_AUTO_ROTATE_HERO] ?: true }
        set(value) = mutate { it[KEY_AUTO_ROTATE_HERO] = value }

    var showHiddenApps: Boolean
        get() = read { prefs -> prefs[KEY_SHOW_HIDDEN_APPS] ?: false }
        set(value) = mutate { it[KEY_SHOW_HIDDEN_APPS] = value }

    var jellyfinConfig: JellyfinConfig
        get() = read { prefs ->
            val defaultSrv = BuildConfig.DEFAULT_JELLYFIN_SERVER
            val defaultUser = BuildConfig.DEFAULT_JELLYFIN_USER
            val defaultPass = BuildConfig.DEFAULT_JELLYFIN_PASS
            val srv = prefs[KEY_JF_SERVER]?.ifBlank { null } ?: defaultSrv
            val user = prefs[KEY_JF_USER]?.ifBlank { null } ?: defaultUser
            val pass = prefs[KEY_JF_PASS]?.ifBlank { null } ?: defaultPass

            JellyfinConfig(
                serverUrl = srv,
                username = user,
                password = pass,
                userId = prefs[KEY_JF_USER_ID] ?: "",
                accessToken = prefs[KEY_JF_TOKEN] ?: "",
                serverName = prefs[KEY_JF_SERVER_NAME] ?: "Jellyfin",
                playerPreference = PlayerPreference.fromId(
                    prefs[KEY_JF_PLAYER_PREF] ?: PlayerPreference.ABYSS_INTERNAL.id
                ),
                lastSyncTimestamp = prefs[KEY_JF_LAST_SYNC] ?: 0L,
                syncedMoviesCount = prefs[KEY_JF_MOVIES_COUNT] ?: 0,
                syncedSeriesCount = prefs[KEY_JF_SERIES_COUNT] ?: 0
            )
        }
        set(value) = mutate { prefs ->
            prefs[KEY_JF_SERVER] = value.serverUrl
            prefs[KEY_JF_USER] = value.username
            prefs[KEY_JF_PASS] = value.password
            prefs[KEY_JF_USER_ID] = value.userId
            prefs[KEY_JF_TOKEN] = value.accessToken
            prefs[KEY_JF_SERVER_NAME] = value.serverName
            prefs[KEY_JF_PLAYER_PREF] = value.playerPreference.id
            prefs[KEY_JF_LAST_SYNC] = value.lastSyncTimestamp
            prefs[KEY_JF_MOVIES_COUNT] = value.syncedMoviesCount
            prefs[KEY_JF_SERIES_COUNT] = value.syncedSeriesCount
        }

    var seerConfig: SeerConfig
        get() = read { prefs ->
            val defaultSrv = BuildConfig.DEFAULT_SEER_SERVER
            val defaultKey = BuildConfig.DEFAULT_SEER_API_KEY
            val srv = prefs[KEY_SEER_SERVER]?.ifBlank { null } ?: defaultSrv
            val key = prefs[KEY_SEER_KEY]?.ifBlank { null } ?: defaultKey

            SeerConfig(
                serverUrl = srv,
                apiKey = key,
                lastSyncTimestamp = prefs[KEY_SEER_LAST_SYNC] ?: 0L,
                requestedCount = prefs[KEY_SEER_REQ_COUNT] ?: 0
            )
        }
        set(value) = mutate { prefs ->
            prefs[KEY_SEER_SERVER] = value.serverUrl
            prefs[KEY_SEER_KEY] = value.apiKey
            prefs[KEY_SEER_LAST_SYNC] = value.lastSyncTimestamp
            prefs[KEY_SEER_REQ_COUNT] = value.requestedCount
        }

    var pinnedPackages: List<String>
        get() = read { prefs ->
            val raw = prefs[KEY_PINNED_PACKAGES]
                ?: return@read listOf(
                    "org.jellyfin.androidtv",
                    "com.google.android.youtube.tv",
                    "io.gh.reisxd.tizentube.cobalt",
                    "com.netflix.ninja",
                    "org.videolan.vlc",
                    "com.android.tv.settings",
                    "com.android.settings"
                )
            raw.split("|").filter { it.isNotBlank() && it != "ar.tvplayer.tv" }
        }
        set(value) = mutate { prefs ->
            prefs[KEY_PINNED_PACKAGES] = value.distinct().joinToString("|")
        }

    var hiddenPackages: Set<String>
        get() = read { prefs -> prefs[KEY_HIDDEN_PACKAGES] ?: emptySet() }
        set(value) = mutate { prefs -> prefs[KEY_HIDDEN_PACKAGES] = value }

    var watchlistIds: Set<String>
        get() = read { prefs -> prefs[KEY_WATCHLIST_IDS] ?: emptySet() }
        set(value) = mutate { prefs -> prefs[KEY_WATCHLIST_IDS] = value }

    var weatherCity: String
        get() = read { prefs -> prefs[KEY_WEATHER_CITY] ?: "Paris" }
        set(value) = mutate { prefs -> prefs[KEY_WEATHER_CITY] = value.trim().ifBlank { "Paris" } }

    var temperatureUnit: TemperatureUnit
        get() = read { prefs ->
            val raw = prefs[KEY_TEMP_UNIT] ?: TemperatureUnit.CELSIUS.name
            runCatching { TemperatureUnit.valueOf(raw) }.getOrDefault(TemperatureUnit.CELSIUS)
        }
        set(value) = mutate { prefs -> prefs[KEY_TEMP_UNIT] = value.name }

    var searchHistory: List<String>
        get() = read { prefs ->
            val raw = prefs[KEY_SEARCH_HISTORY] ?: ""
            if (raw.isBlank()) emptyList() else raw.split("|||").filter { it.isNotBlank() }
        }
        set(value) = mutate { prefs ->
            val trimmed = value.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(15).joinToString("|||")
            prefs[KEY_SEARCH_HISTORY] = trimmed
        }

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_ACCENT_PRESET = stringPreferencesKey("accent_preset")
        private val KEY_AUTO_ROTATE_HERO = booleanPreferencesKey("auto_rotate_hero")
        private val KEY_SHOW_HIDDEN_APPS = booleanPreferencesKey("show_hidden_apps")

        private val KEY_JF_SERVER = stringPreferencesKey("jf_server")
        private val KEY_JF_USER = stringPreferencesKey("jf_user")
        private val KEY_JF_PASS = stringPreferencesKey("jf_pass")
        private val KEY_JF_USER_ID = stringPreferencesKey("jf_user_id")
        private val KEY_JF_TOKEN = stringPreferencesKey("jf_token")
        private val KEY_JF_SERVER_NAME = stringPreferencesKey("jf_server_name")
        private val KEY_JF_PLAYER_PREF = stringPreferencesKey("jf_player_pref")
        private val KEY_JF_LAST_SYNC = longPreferencesKey("jf_last_sync")
        private val KEY_JF_MOVIES_COUNT = intPreferencesKey("jf_movies_count")
        private val KEY_JF_SERIES_COUNT = intPreferencesKey("jf_series_count")

        private val KEY_SEER_SERVER = stringPreferencesKey("seer_server")
        private val KEY_SEER_KEY = stringPreferencesKey("seer_key")
        private val KEY_SEER_LAST_SYNC = longPreferencesKey("seer_last_sync")
        private val KEY_SEER_REQ_COUNT = intPreferencesKey("seer_req_count")

        private val KEY_PINNED_PACKAGES = stringPreferencesKey("pinned_packages")
        private val KEY_HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
        private val KEY_WATCHLIST_IDS = stringSetPreferencesKey("watchlist_ids")
        private val KEY_WEATHER_CITY = stringPreferencesKey("weather_city")
        private val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit")
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
    }
}
