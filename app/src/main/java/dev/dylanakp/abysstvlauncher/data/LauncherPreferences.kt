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
import kotlinx.coroutines.launch
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
    private val memoryPrefs = java.util.concurrent.atomic.AtomicReference<Preferences>(emptyPreferences())

    val dataFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    init {
        val legacyPrefs = context.applicationContext.getSharedPreferences("abyss_tv_prefs", Context.MODE_PRIVATE)
        val initialMap = mutableMapOf<Preferences.Key<*>, Any>()
        legacyPrefs.getString("language", null)?.let { initialMap[KEY_LANGUAGE] = it }
        legacyPrefs.getString("accent_preset", null)?.let { initialMap[KEY_ACCENT_PRESET] = it }
        if (legacyPrefs.contains("auto_rotate_hero")) initialMap[KEY_AUTO_ROTATE_HERO] = legacyPrefs.getBoolean("auto_rotate_hero", true)
        if (legacyPrefs.contains("show_hidden_apps")) initialMap[KEY_SHOW_HIDDEN_APPS] = legacyPrefs.getBoolean("show_hidden_apps", false)
        legacyPrefs.getString("jf_server", null)?.let { initialMap[KEY_JF_SERVER] = it }
        legacyPrefs.getString("jf_user", null)?.let { initialMap[KEY_JF_USER] = it }
        legacyPrefs.getString("jf_pass", null)?.let { initialMap[KEY_JF_PASS] = it }
        legacyPrefs.getString("jf_user_id", null)?.let { initialMap[KEY_JF_USER_ID] = it }
        legacyPrefs.getString("jf_token", null)?.let { initialMap[KEY_JF_TOKEN] = it }
        legacyPrefs.getString("jf_server_name", null)?.let { initialMap[KEY_JF_SERVER_NAME] = it }
        legacyPrefs.getString("jf_player_pref", null)?.let { initialMap[KEY_JF_PLAYER_PREF] = it }
        if (legacyPrefs.contains("jf_last_sync")) initialMap[KEY_JF_LAST_SYNC] = legacyPrefs.getLong("jf_last_sync", 0L)
        if (legacyPrefs.contains("jf_movies_count")) initialMap[KEY_JF_MOVIES_COUNT] = legacyPrefs.getInt("jf_movies_count", 0)
        if (legacyPrefs.contains("jf_series_count")) initialMap[KEY_JF_SERIES_COUNT] = legacyPrefs.getInt("jf_series_count", 0)
        legacyPrefs.getString("seer_server", null)?.let { initialMap[KEY_SEER_SERVER] = it }
        legacyPrefs.getString("seer_key", null)?.let { initialMap[KEY_SEER_KEY] = it }
        if (legacyPrefs.contains("seer_last_sync")) initialMap[KEY_SEER_LAST_SYNC] = legacyPrefs.getLong("seer_last_sync", 0L)
        if (legacyPrefs.contains("seer_req_count")) initialMap[KEY_SEER_REQ_COUNT] = legacyPrefs.getInt("seer_req_count", 0)
        legacyPrefs.getString("pinned_packages", null)?.let { initialMap[KEY_PINNED_PACKAGES] = it }
        legacyPrefs.getStringSet("hidden_packages", null)?.let { initialMap[KEY_HIDDEN_PACKAGES] = it }
        legacyPrefs.getStringSet("watchlist_ids", null)?.let { initialMap[KEY_WATCHLIST_IDS] = it }
        legacyPrefs.getString("weather_city", null)?.let { initialMap[KEY_WEATHER_CITY] = it }
        legacyPrefs.getString("temp_unit", null)?.let { initialMap[KEY_TEMP_UNIT] = it }
        legacyPrefs.getString("search_history", null)?.let { initialMap[KEY_SEARCH_HISTORY] = it }

        if (initialMap.isNotEmpty()) {
            memoryPrefs.set(androidx.datastore.preferences.core.emptyPreferences().toPreferences().let {
                val mut = it.toMutablePreferences()
                initialMap.forEach { (k, v) ->
                    @Suppress("UNCHECKED_CAST")
                    mut[k as Preferences.Key<Any>] = v
                }
                mut.toPreferences()
            })
        }

        scope.launch {
            dataFlow.collect { prefs ->
                memoryPrefs.set(prefs)
            }
        }
    }

    private inline fun <T> read(crossinline block: (Preferences) -> T): T {
        return block(memoryPrefs.get())
    }

    private fun mutate(block: (MutablePreferences: androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val updated = memoryPrefs.get().toMutablePreferences().apply(block).toPreferences()
        memoryPrefs.set(updated)
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
