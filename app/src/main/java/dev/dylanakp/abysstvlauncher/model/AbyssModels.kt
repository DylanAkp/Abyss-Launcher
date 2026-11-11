package dev.dylanakp.abysstvlauncher.model

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class AppLanguage(val code: String, val label: String) {
    AUTO("auto", "Auto (Système / System)"),
    FR("fr", "Français"),
    EN("en", "English")
}

enum class TemperatureUnit(val symbol: String, val label: String, val localizedLabel: String) {
    CELSIUS("°C", "Celsius (°C)", "Celsius (°C)"),
    FAHRENHEIT("°F", "Fahrenheit (°F)", "Fahrenheit (°F)")
}

enum class AbyssNavTab {
    HOME,
    MOVIES,
    SERIES,
    DISCOVER,
    APPS
}

enum class MediaCategory {
    MOVIE,
    SERIES,
    SEER_REQUEST
}

enum class SeerMediaStatus(val code: Int, val label: String, val localizedLabel: String) {
    UNKNOWN(1, "Not in Library", "Non disponible"),
    PENDING_APPROVAL(2, "Pending Approval", "En attente"),
    PROCESSING(3, "Downloading…", "Téléchargement…"),
    PARTIALLY_AVAILABLE(4, "Partially Available", "Partiellement disponible"),
    AVAILABLE(5, "Available", "Disponible");

    fun label(): String = if (java.util.Locale.getDefault().language.startsWith("fr")) localizedLabel else label

    companion object {
        fun fromCode(code: Int): SeerMediaStatus = entries.find { it.code == code } ?: UNKNOWN
    }
}

enum class AbyssAccentPreset(
    val id: String,
    val label: String,
    val localizedLabel: String,
    val primary: Color,
    val secondary: Color,
    val glow: Color
) {
    ABYSS_VIOLET(
        id = "abyss_violet",
        label = "Abyss Violet (Default)",
        localizedLabel = "Violet Abyss (Défaut)",
        primary = Color(0xFF7C5CFF),
        secondary = Color(0xFF38BDF8),
        glow = Color(0x667C5CFF)
    ),
    OCEANIC_CYAN(
        id = "oceanic_cyan",
        label = "Bioluminescent Cyan",
        localizedLabel = "Cyan Bioluminescent",
        primary = Color(0xFF06B6D4),
        secondary = Color(0xFF6366F1),
        glow = Color(0x6606B6D4)
    ),
    CRIMSON_CINEMA(
        id = "crimson_cinema",
        label = "Crimson Cinema",
        localizedLabel = "Cinéma Cramoisi",
        primary = Color(0xFFF43F5E),
        secondary = Color(0xFFFB923C),
        glow = Color(0x66F43F5E)
    ),
    EMERALD_AURORA(
        id = "emerald_aurora",
        label = "Emerald Aurora",
        localizedLabel = "Aurore Émeraude",
        primary = Color(0xFF10B981),
        secondary = Color(0xFF38BDF8),
        glow = Color(0x6610B981)
    );

    companion object {
        fun fromId(id: String): AbyssAccentPreset =
            entries.find { it.id == id } ?: ABYSS_VIOLET
    }
}

enum class PlayerPreference(val id: String, val label: String, val localizedLabel: String) {
    ABYSS_INTERNAL("abyss_internal", "Abyss Player (Direct Play / ExoPlayer)", "Lecteur Abyss (Lecture directe / ExoPlayer)"),
    EXTERNAL_PLAYER("external", "External Player (VLC / JustPlayer / Vimu)", "Lecteur externe (VLC / JustPlayer / Vimu)"),
    SYSTEM_CHOOSER("chooser", "Always Ask (System Chooser)", "Toujours demander (Sélecteur Android)");

    companion object {
        fun fromId(id: String): PlayerPreference =
            entries.find { it.id == id } ?: ABYSS_INTERNAL
    }
}

@Immutable
data class SeriesEpisode(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String = "",
    val duration: String = "",
    val rating: String = "",
    val streamUrl: String,
    val posterUrl: String = "",
    val containerExtension: String = "mp4",
    val progressPercent: Float = 0f,
    val savedPositionMs: Long = 0L,
    val savedDurationMs: Long = 0L
)

@Immutable
data class SeriesSeason(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<SeriesEpisode>
)

@Immutable
data class SeriesDetails(
    val seriesItem: MediaItem,
    val seasons: List<SeriesSeason>
)

@Immutable
data class MediaVersion(
    val id: String,
    val name: String,
    val container: String = "",
    val resolution: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val sizeBytes: Long = 0L,
    val bitrate: Long = 0L
) {
    val displaySize: String
        get() = if (sizeBytes > 0L) {
            val gb = sizeBytes.toDouble() / (1024 * 1024 * 1024)
            String.format(java.util.Locale.US, "%.1f GB", gb)
        } else ""
}

@Immutable
data class MovieDetails(
    val movieItem: MediaItem,
    val tagline: String = "",
    val genres: List<String> = emptyList(),
    val versions: List<MediaVersion> = emptyList(),
    val selectedVersionId: String = "",
    val trailerUrl: String = "",
    val trailerName: String = "",
    val isLocalTrailer: Boolean = false
)

@Immutable
data class PlaybackHistoryEntry(
    val itemId: String,
    val seriesId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeTitle: String = "",
    val streamUrl: String = "",
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedTimestamp: Long,
    val title: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val category: String = ""
) {
    val progressPercent: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(if (positionMs > 0L) 0.03f else 0f, 1f)
        } else if (positionMs > 0L) 0.05f else 0f
}

@Immutable
data class MediaItem(
    val id: String,
    val title: String,
    val localizedTitle: String = title,
    val subtitle: String = "",
    val localizedSubtitle: String = subtitle,
    val synopsis: String = "",
    val localizedSynopsis: String = synopsis,
    val category: MediaCategory,
    val groupTitle: String = "",
    val localizedGroupTitle: String = groupTitle,
    val rating: String = "8.6",
    val year: String = "2026",
    val duration: String = "2h 14m",
    val localizedDuration: String = duration,
    val qualityBadge: String = "4K UHD",
    val audioBadge: String = "Atmos 7.1",
    val streamUrl: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val palettePrimary: Long = 0xFF7C5CFF,
    val paletteSecondary: Long = 0xFF0EA5E9,
    val paletteDark: Long = 0xFF0B0E1A,
    val progressPercent: Float = 0f,
    val isFavorite: Boolean = false,
    val tmdbId: Int? = null,
    val jellyfinId: String = "",
    val seerStatus: SeerMediaStatus = SeerMediaStatus.UNKNOWN,
    val downloadProgress: Float? = null,
    val downloadEta: String? = null,
    val downloadSizeStr: String? = null
) {
    fun title(isFrench: Boolean = java.util.Locale.getDefault().language.startsWith("fr")): String = if (isFrench && localizedTitle.isNotBlank()) localizedTitle else title
    fun subtitle(isFrench: Boolean = java.util.Locale.getDefault().language.startsWith("fr")): String = if (isFrench && localizedSubtitle.isNotBlank()) localizedSubtitle else subtitle
    fun synopsis(isFrench: Boolean = java.util.Locale.getDefault().language.startsWith("fr")): String = if (isFrench && localizedSynopsis.isNotBlank()) localizedSynopsis else synopsis
    fun groupTitle(isFrench: Boolean = java.util.Locale.getDefault().language.startsWith("fr")): String = if (isFrench && localizedGroupTitle.isNotBlank()) localizedGroupTitle else groupTitle
    fun duration(isFrench: Boolean = java.util.Locale.getDefault().language.startsWith("fr")): String = if (isFrench && localizedDuration.isNotBlank()) localizedDuration else duration
}

@Immutable
data class TvAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val banner: Drawable? = null,
    val isLeanback: Boolean = true,
    val isPinned: Boolean = false,
    val isHidden: Boolean = false,
    val pinOrder: Int = Int.MAX_VALUE,
    val isSystemSettingsOrUtility: Boolean = false
)

@Immutable
data class JellyfinConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val userId: String = "",
    val accessToken: String = "",
    val serverName: String = "",
    val playerPreference: PlayerPreference = PlayerPreference.ABYSS_INTERNAL,
    val lastSyncTimestamp: Long = 0L,
    val syncedMoviesCount: Int = 0,
    val syncedSeriesCount: Int = 0
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && (accessToken.isNotBlank() || username.isNotBlank())
}

@Immutable
data class SeerConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val lastSyncTimestamp: Long = 0L,
    val requestedCount: Int = 0
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank()
}

@Immutable
data class SyncedLibrary(
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val tmdbTrending: List<MediaItem> = emptyList(),
    val seerTrending: List<MediaItem> = emptyList(),
    val seerPopularMovies: List<MediaItem> = emptyList(),
    val seerPopularTv: List<MediaItem> = emptyList(),
    val seerRequests: List<MediaItem> = emptyList()
)
