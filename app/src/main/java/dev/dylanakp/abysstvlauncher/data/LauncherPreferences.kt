package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import android.content.SharedPreferences
import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.model.AbyssAccentPreset
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.model.SeerConfig

class LauncherPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("abyss_tv_prefs", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() {
            val code = prefs.getString("language", AppLanguage.AUTO.code) ?: AppLanguage.AUTO.code
            return AppLanguage.entries.find { it.code == code } ?: AppLanguage.AUTO
        }
        set(value) {
            prefs.edit().putString("language", value.code).apply()
        }

    var accentPreset: AbyssAccentPreset
        get() = AbyssAccentPreset.fromId(
            prefs.getString("accent_preset", AbyssAccentPreset.ABYSS_VIOLET.id)
                ?: AbyssAccentPreset.ABYSS_VIOLET.id
        )
        set(value) {
            prefs.edit().putString("accent_preset", value.id).apply()
        }

    var autoRotateHero: Boolean
        get() = prefs.getBoolean("auto_rotate_hero", true)
        set(value) {
            prefs.edit().putBoolean("auto_rotate_hero", value).apply()
        }

    var showHiddenApps: Boolean
        get() = prefs.getBoolean("show_hidden_apps", false)
        set(value) {
            prefs.edit().putBoolean("show_hidden_apps", value).apply()
        }

    var jellyfinConfig: JellyfinConfig
        get() {
            val defaultSrv = BuildConfig.DEFAULT_JELLYFIN_SERVER
            val defaultUser = BuildConfig.DEFAULT_JELLYFIN_USER
            val defaultPass = BuildConfig.DEFAULT_JELLYFIN_PASS
            val srv = prefs.getString("jf_server", null)?.ifBlank { null } ?: defaultSrv
            val user = prefs.getString("jf_user", null)?.ifBlank { null } ?: defaultUser
            val pass = prefs.getString("jf_pass", null)?.ifBlank { null } ?: defaultPass
            return JellyfinConfig(
                serverUrl = srv,
                username = user,
                password = pass,
                userId = prefs.getString("jf_user_id", "") ?: "",
                accessToken = prefs.getString("jf_token", "") ?: "",
                serverName = prefs.getString("jf_server_name", "Jellyfin") ?: "Jellyfin",
                playerPreference = PlayerPreference.fromId(
                    prefs.getString("jf_player_pref", PlayerPreference.ABYSS_INTERNAL.id)
                        ?: PlayerPreference.ABYSS_INTERNAL.id
                ),
                lastSyncTimestamp = prefs.getLong("jf_last_sync", 0L),
                syncedMoviesCount = prefs.getInt("jf_movies_count", 0),
                syncedSeriesCount = prefs.getInt("jf_series_count", 0)
            )
        }
        set(value) {
            prefs.edit()
                .putString("jf_server", value.serverUrl)
                .putString("jf_user", value.username)
                .putString("jf_pass", value.password)
                .putString("jf_user_id", value.userId)
                .putString("jf_token", value.accessToken)
                .putString("jf_server_name", value.serverName)
                .putString("jf_player_pref", value.playerPreference.id)
                .putLong("jf_last_sync", value.lastSyncTimestamp)
                .putInt("jf_movies_count", value.syncedMoviesCount)
                .putInt("jf_series_count", value.syncedSeriesCount)
                .apply()
        }

    var seerConfig: SeerConfig
        get() {
            val defaultSrv = BuildConfig.DEFAULT_SEER_SERVER
            val defaultKey = BuildConfig.DEFAULT_SEER_API_KEY
            val srv = prefs.getString("seer_server", null)?.ifBlank { null } ?: defaultSrv
            val key = prefs.getString("seer_key", null)?.ifBlank { null } ?: defaultKey
            return SeerConfig(
                serverUrl = srv,
                apiKey = key,
                lastSyncTimestamp = prefs.getLong("seer_last_sync", 0L),
                requestedCount = prefs.getInt("seer_req_count", 0)
            )
        }
        set(value) {
            prefs.edit()
                .putString("seer_server", value.serverUrl)
                .putString("seer_key", value.apiKey)
                .putLong("seer_last_sync", value.lastSyncTimestamp)
                .putInt("seer_req_count", value.requestedCount)
                .apply()
        }

    var pinnedPackages: List<String>
        get() {
            val raw = prefs.getString("pinned_packages", null)
            if (raw == null) {
                return listOf(
                    "org.jellyfin.androidtv",
                    "com.google.android.youtube.tv",
                    "io.gh.reisxd.tizentube.cobalt",
                    "com.netflix.ninja",
                    "org.videolan.vlc",
                    "com.android.tv.settings",
                    "com.android.settings"
                )
            }
            return raw.split("|").filter { it.isNotBlank() && it != "ar.tvplayer.tv" }
        }
        set(value) {
            prefs.edit().putString("pinned_packages", value.distinct().joinToString("|")).apply()
        }

    var hiddenPackages: Set<String>
        get() = prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("hidden_packages", value).apply()
        }

    var watchlistIds: Set<String>
        get() = prefs.getStringSet("watchlist_ids", emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("watchlist_ids", value).apply()
        }

    var weatherCity: String
        get() = prefs.getString("weather_city", "Paris") ?: "Paris"
        set(value) {
            prefs.edit().putString("weather_city", value.trim().ifBlank { "Paris" }).apply()
        }

    var temperatureUnit: dev.dylanakp.abysstvlauncher.model.TemperatureUnit
        get() {
            val raw = prefs.getString("temp_unit", dev.dylanakp.abysstvlauncher.model.TemperatureUnit.CELSIUS.name)
                ?: dev.dylanakp.abysstvlauncher.model.TemperatureUnit.CELSIUS.name
            return runCatching { dev.dylanakp.abysstvlauncher.model.TemperatureUnit.valueOf(raw) }
                .getOrDefault(dev.dylanakp.abysstvlauncher.model.TemperatureUnit.CELSIUS)
        }
        set(value) {
            prefs.edit().putString("temp_unit", value.name).apply()
        }

    var searchHistory: List<String>
        get() {
            val raw = prefs.getString("search_history", "") ?: ""
            if (raw.isBlank()) return emptyList()
            return raw.split("|||").filter { it.isNotBlank() }
        }
        set(value) {
            val trimmed = value.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(15).joinToString("|||")
            prefs.edit().putString("search_history", trimmed).apply()
        }
}
