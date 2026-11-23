package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import android.util.Log
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
import dev.dylanakp.abysstvlauncher.remote.RemotePairingAndKeyHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "abyss_tv_prefs",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "abyss_tv_prefs"))
    }
)

class LauncherPreferences(private val context: Context) {

    private val dataStore = context.applicationContext.launcherDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryPrefs = AtomicReference<Preferences>(emptyPreferences())
    private val loadComplete = CompletableDeferred<Unit>()
    private val writeMutex = Mutex()
    private val identityMutex = Mutex()
    private val writeLock = Any()
    private var writesInFlight = 0
    private var idleSignal: CompletableDeferred<Unit>? = null

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
                dataFlow.collect { prefs ->
                    memoryPrefs.set(prefs)
                    loadComplete.complete(Unit)
                }
            } finally {
                loadComplete.complete(Unit)
            }
        }
        scope.launch {
            awaitLoaded()
            getOrCreateDeviceId()
            getOrCreateRemotePairingPin()
        }
    }

    suspend fun awaitLoaded() {
        loadComplete.await()
    }

    suspend fun awaitWrites() {
        val signal = synchronized(writeLock) {
            if (writesInFlight == 0) null
            else idleSignal ?: CompletableDeferred<Unit>().also { idleSignal = it }
        }
        signal?.await()
        memoryPrefs.set(dataFlow.first())
    }

    private fun mutate(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        synchronized(writeLock) {
            writesInFlight++
            if (idleSignal == null) idleSignal = CompletableDeferred()
        }
        scope.launch {
            try {
                writeMutex.withLock {
                    dataStore.edit { block(it) }
                }
            } catch (e: Exception) {
                Log.e("LauncherPreferences", "Failed to persist preference write", e)
            } finally {
                synchronized(writeLock) {
                    writesInFlight--
                    if (writesInFlight == 0) {
                        idleSignal?.complete(Unit)
                        idleSignal = null
                    }
                }
            }
        }
    }

    private fun encryptForStorage(plain: String): String =
        try {
            SecureTokenStorage.encryptOrThrow(plain)
        } catch (e: TokenStorageException) {
            Log.e("LauncherPreferences", "Failed to encrypt token for storage", e)
            ""
        }

    private fun readSecret(stored: String?, onPlaintext: (String) -> Unit): String {
        if (stored.isNullOrBlank()) return ""
        if (!SecureTokenStorage.isEncrypted(stored)) {
            onPlaintext(stored)
            return stored
        }
        return try {
            SecureTokenStorage.decryptOrThrow(stored)
        } catch (e: TokenStorageException) {
            Log.e("LauncherPreferences", "Failed to decrypt stored token", e)
            ""
        }
    }

    private inline fun <T> read(crossinline block: (Preferences) -> T): T {
        return block(memoryPrefs.get())
    }

    suspend fun getOrCreateDeviceId(): String {
        awaitLoaded()
        return identityMutex.withLock {
            val existing = dataFlow.first()[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() }
            if (existing != null) {
                existing
            } else {
                val generated = UUID.randomUUID().toString()
                mutate { it[KEY_DEVICE_ID] = generated }
                awaitWrites()
                generated
            }
        }
    }

    suspend fun getOrCreateRemotePairingPin(): String {
        awaitLoaded()
        return identityMutex.withLock {
            val existing = readSecret(dataFlow.first()[KEY_REMOTE_PIN]) { migrated ->
                mutate { it[KEY_REMOTE_PIN] = encryptForStorage(migrated) }
            }
            if (existing.length == 6) {
                if (read { it[KEY_REMOTE_PIN_ISSUED_AT] ?: 0L } == 0L) {
                    mutate { it[KEY_REMOTE_PIN_ISSUED_AT] = System.currentTimeMillis() }
                }
                existing
            } else {
                val generated = RemotePairingAndKeyHelper.generatePin()
                mutate {
                    it[KEY_REMOTE_PIN] = encryptForStorage(generated)
                    it[KEY_REMOTE_PIN_ISSUED_AT] = System.currentTimeMillis()
                }
                awaitWrites()
                generated
            }
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
            val storedPass = readSecret(prefs[KEY_JF_PASS]) { migrated ->
                mutate { it[KEY_JF_PASS] = encryptForStorage(migrated) }
            }
            JellyfinConfig(
                serverUrl = srv,
                username = user,
                password = storedPass.ifBlank { defaultPass },
                userId = prefs[KEY_JF_USER_ID] ?: "",
                accessToken = readSecret(prefs[KEY_JF_TOKEN]) { migrated ->
                    mutate { it[KEY_JF_TOKEN] = encryptForStorage(migrated) }
                },
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
            prefs[KEY_JF_PASS] = encryptForStorage(value.password)
            prefs[KEY_JF_USER_ID] = value.userId
            prefs[KEY_JF_TOKEN] = encryptForStorage(value.accessToken)
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
            val key = readSecret(prefs[KEY_SEER_KEY]) { migrated ->
                mutate { it[KEY_SEER_KEY] = encryptForStorage(migrated) }
            }.ifBlank { null } ?: defaultKey

            SeerConfig(
                serverUrl = srv,
                apiKey = key,
                lastSyncTimestamp = prefs[KEY_SEER_LAST_SYNC] ?: 0L,
                requestedCount = prefs[KEY_SEER_REQ_COUNT] ?: 0
            )
        }
        set(value) = mutate { prefs ->
            prefs[KEY_SEER_SERVER] = value.serverUrl
            prefs[KEY_SEER_KEY] = encryptForStorage(value.apiKey)
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
        get() = read { prefs -> prefs[KEY_REMOTE_ENABLED] ?: false }
        set(value) = mutate { prefs -> prefs[KEY_REMOTE_ENABLED] = value }

    var remotePairingPin: String
        get() = read { prefs ->
            readSecret(prefs[KEY_REMOTE_PIN]) { migrated ->
                mutate { it[KEY_REMOTE_PIN] = encryptForStorage(migrated) }
            }.takeIf { it.length == 6 } ?: ""
        }
        set(value) = mutate { prefs ->
            prefs[KEY_REMOTE_PIN] = encryptForStorage(value)
            prefs[KEY_REMOTE_PIN_ISSUED_AT] = System.currentTimeMillis()
        }

    var remotePinIssuedAt: Long
        get() = read { it[KEY_REMOTE_PIN_ISSUED_AT] ?: 0L }
        set(value) = mutate { it[KEY_REMOTE_PIN_ISSUED_AT] = value }

    val deviceId: String
        get() = read { prefs -> prefs[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() } ?: "" }

    var remotePairedTokens: Set<String>
        get() = read { prefs ->
            prefs[KEY_REMOTE_TOKENS].orEmpty().map { token ->
                readSecret(token) { migrated ->
                    mutate { p ->
                        p[KEY_REMOTE_TOKENS] = p[KEY_REMOTE_TOKENS].orEmpty() - token + migrated
                    }
                }
            }.toSet()
        }
        set(value) = mutate { prefs ->
            prefs[KEY_REMOTE_TOKENS] = value.mapNotNull { encryptForStorage(it).ifBlank { null } }.toSet()
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
        private val KEY_REMOTE_PIN_ISSUED_AT = longPreferencesKey("remote_pin_issued_at")
        private val KEY_REMOTE_TOKENS = stringSetPreferencesKey("remote_tokens")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
