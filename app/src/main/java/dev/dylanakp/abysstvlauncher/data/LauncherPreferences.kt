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
import dev.dylanakp.abysstvlauncher.model.TrackPreference
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
    private val loadComplete = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val writeChannel = kotlinx.coroutines.channels.Channel<(androidx.datastore.preferences.core.MutablePreferences) -> Unit>(
        kotlinx.coroutines.channels.Channel.UNLIMITED
    )

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
            try {
                migrateLegacyPrefsIfNeeded()
                dataFlow.collect { prefs ->
                    memoryPrefs.set(prefs)
                    loadComplete.complete(Unit)
                }
            } finally {
                loadComplete.complete(Unit)
            }
        }
        scope.launch {
            for (mutation in writeChannel) {
                dataStore.edit { mutation(it) }
            }
        }
    }

    suspend fun awaitLoaded() {
        loadComplete.await()
    }

    private suspend fun migrateLegacyPrefsIfNeeded() {
        val legacyPrefs = context.applicationContext.getSharedPreferences("abyss_tv_prefs", Context.MODE_PRIVATE)
        if (legacyPrefs.all.isEmpty()) return

        dataStore.edit { mut ->
            if (mut.asMap().isNotEmpty()) return@edit
            legacyPrefs.getString("language", null)?.let { mut[KEY_LANGUAGE] = it }
            legacyPrefs.getString("accent_preset", null)?.let { mut[KEY_ACCENT_PRESET] = it }
            if (legacyPrefs.contains("auto_rotate_hero")) mut[KEY_AUTO_ROTATE_HERO] = legacyPrefs.getBoolean("auto_rotate_hero", true)
            if (legacyPrefs.contains("show_hidden_apps")) mut[KEY_SHOW_HIDDEN_APPS] = legacyPrefs.getBoolean("show_hidden_apps", false)
            legacyPrefs.getString("jf_server", null)?.let { mut[KEY_JF_SERVER] = it }
            legacyPrefs.getString("jf_user", null)?.let { mut[KEY_JF_USER] = it }
            legacyPrefs.getString("jf_user_id", null)?.let { mut[KEY_JF_USER_ID] = it }
            legacyPrefs.getString("jf_token", null)?.let { mut[KEY_JF_TOKEN] = SecureTokenStorage.encrypt(it) }
            legacyPrefs.getString("jf_server_name", null)?.let { mut[KEY_JF_SERVER_NAME] = it }
            legacyPrefs.getString("jf_player_pref", null)?.let { mut[KEY_JF_PLAYER_PREF] = it }
            if (legacyPrefs.contains("jf_last_sync")) mut[KEY_JF_LAST_SYNC] = legacyPrefs.getLong("jf_last_sync", 0L)
            if (legacyPrefs.contains("jf_movies_count")) mut[KEY_JF_MOVIES_COUNT] = legacyPrefs.getInt("jf_movies_count", 0)
            if (legacyPrefs.contains("jf_series_count")) mut[KEY_JF_SERIES_COUNT] = legacyPrefs.getInt("jf_series_count", 0)
            legacyPrefs.getString("seer_server", null)?.let { mut[KEY_SEER_SERVER] = it }
            legacyPrefs.getString("seer_key", null)?.let { mut[KEY_SEER_KEY] = SecureTokenStorage.encrypt(it) }
            if (legacyPrefs.contains("seer_last_sync")) mut[KEY_SEER_LAST_SYNC] = legacyPrefs.getLong("seer_last_sync", 0L)
            if (legacyPrefs.contains("seer_req_count")) mut[KEY_SEER_REQ_COUNT] = legacyPrefs.getInt("seer_req_count", 0)
            legacyPrefs.getString("pinned_packages", null)?.let { mut[KEY_PINNED_PACKAGES] = it }
            legacyPrefs.getStringSet("hidden_packages", null)?.let { mut[KEY_HIDDEN_PACKAGES] = it }
            legacyPrefs.getStringSet("watchlist_ids", null)?.let { mut[KEY_WATCHLIST_IDS] = it }
            legacyPrefs.getString("weather_city", null)?.let { mut[KEY_WEATHER_CITY] = it }
            legacyPrefs.getString("temp_unit", null)?.let { mut[KEY_TEMP_UNIT] = it }
            legacyPrefs.getString("search_history", null)?.let { mut[KEY_SEARCH_HISTORY] = it }
        }
        legacyPrefs.edit().clear().apply()
    }

    private inline fun <T> read(crossinline block: (Preferences) -> T): T {
        return block(memoryPrefs.get())
    }

    private fun mutate(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        memoryPrefs.updateAndGet { current ->
            current.toMutablePreferences().apply(block).toPreferences()
        }
        writeChannel.trySend(block)
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

            JellyfinConfig(
                serverUrl = srv,
                username = user,
                password = defaultPass,
                userId = prefs[KEY_JF_USER_ID] ?: "",
                accessToken = SecureTokenStorage.decrypt(prefs[KEY_JF_TOKEN] ?: ""),
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
            prefs.remove(KEY_JF_PASS)
            prefs[KEY_JF_USER_ID] = value.userId
            prefs[KEY_JF_TOKEN] = SecureTokenStorage.encrypt(value.accessToken)
            prefs[KEY_JF_SERVER_NAME] = value.serverName
            prefs[KEY_JF_PLAYER_PREF] = value.playerPreference.id
            prefs[KEY_JF_LAST_SYNC] = value.lastSyncTimestamp
            prefs[KEY_JF_MOVIES_COUNT] = value.syncedMoviesCount
            prefs[KEY_JF_SERIES_COUNT] = value.syncedSeriesCount
        }

    var trackPreference: TrackPreference
        get() = read { TrackPreference.decode(it[KEY_TRACK_PREF]) }
        set(value) = mutate { it[KEY_TRACK_PREF] = value.encode() }

    fun seriesTrackPreference(seriesId: String): TrackPreference =
        read { TrackPreference.decode(it[seriesTrackKey(seriesId)]) }

    fun setSeriesTrackPreference(seriesId: String, preference: TrackPreference) =
        mutate { it[seriesTrackKey(seriesId)] = preference.encode() }

    private fun seriesTrackKey(seriesId: String) = stringPreferencesKey("track_pref_series_$seriesId")

    var seerConfig: SeerConfig
        get() = read { prefs ->
            val defaultSrv = BuildConfig.DEFAULT_SEER_SERVER
            val defaultKey = BuildConfig.DEFAULT_SEER_API_KEY
            val srv = prefs[KEY_SEER_SERVER]?.ifBlank { null } ?: defaultSrv
            val key = SecureTokenStorage.decrypt(prefs[KEY_SEER_KEY] ?: "").ifBlank { null } ?: defaultKey

            SeerConfig(
                serverUrl = srv,
                apiKey = key,
                lastSyncTimestamp = prefs[KEY_SEER_LAST_SYNC] ?: 0L,
                requestedCount = prefs[KEY_SEER_REQ_COUNT] ?: 0
            )
        }
        set(value) = mutate { prefs ->
            prefs[KEY_SEER_SERVER] = value.serverUrl
            prefs[KEY_SEER_KEY] = SecureTokenStorage.encrypt(value.apiKey)
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

    var remoteServerEnabled: Boolean
        get() = read { prefs -> prefs[KEY_REMOTE_ENABLED] ?: true }
        set(value) = mutate { prefs -> prefs[KEY_REMOTE_ENABLED] = value }

    var remotePairingPin: String
        get() = read { prefs ->
            prefs[KEY_REMOTE_PIN]?.takeIf { it.length == 6 } ?: run {
                val generated = dev.dylanakp.abysstvlauncher.remote.RemotePairingAndKeyHelper.generatePin()
                mutate { it[KEY_REMOTE_PIN] = generated }
                generated
            }
        }
        set(value) = mutate { prefs -> prefs[KEY_REMOTE_PIN] = value }

    val deviceId: String
        get() = read { prefs ->
            prefs[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() } ?: run {
                val generated = java.util.UUID.randomUUID().toString()
                mutate { it[KEY_DEVICE_ID] = generated }
                generated
            }
        }

    var remotePairedTokens: Set<String>
        get() = read { prefs -> prefs[KEY_REMOTE_TOKENS] ?: emptySet() }
        set(value) = mutate { prefs -> prefs[KEY_REMOTE_TOKENS] = value }

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
        private val KEY_TRACK_PREF = stringPreferencesKey("track_pref_default")

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
        private val KEY_REMOTE_ENABLED = booleanPreferencesKey("remote_enabled")
        private val KEY_REMOTE_PIN = stringPreferencesKey("remote_pin")
        private val KEY_REMOTE_TOKENS = stringSetPreferencesKey("remote_tokens")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
