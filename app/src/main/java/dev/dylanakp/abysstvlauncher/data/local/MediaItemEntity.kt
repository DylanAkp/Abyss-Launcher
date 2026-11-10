package dev.dylanakp.abysstvlauncher.data.local

import androidx.room.Entity
import androidx.room.Index
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus

@Entity(
    tableName = "cached_media_items",
    primaryKeys = ["id", "librarySection"],
    indices = [
        Index(value = ["librarySection"]),
        Index(value = ["sortIndex"])
    ]
)
data class MediaItemEntity(
    val id: String,
    val librarySection: String,
    val sortIndex: Int = 0,
    val title: String = "",
    val subtitle: String = "",
    val synopsis: String = "",
    val category: String = MediaCategory.MOVIE.name,
    val groupTitle: String = "",
    val rating: String = "8.6",
    val year: String = "2026",
    val durationOrSeasons: String = "",
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
    val seerStatusCode: Int = SeerMediaStatus.UNKNOWN.code,
    val downloadProgress: Float? = null,
    val downloadEta: String? = null,
    val downloadSizeStr: String? = null
) {
    fun toDomain(): MediaItem = MediaItem(
        id = id,
        title = title,
        subtitle = subtitle,
        synopsis = synopsis,
        category = runCatching { MediaCategory.valueOf(category) }.getOrDefault(MediaCategory.MOVIE),
        groupTitle = groupTitle,
        rating = rating,
        year = year,
        durationOrSeasons = durationOrSeasons,
        qualityBadge = qualityBadge,
        audioBadge = audioBadge,
        streamUrl = streamUrl,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        palettePrimary = palettePrimary,
        paletteSecondary = paletteSecondary,
        paletteDark = paletteDark,
        progressPercent = progressPercent,
        isFavorite = isFavorite,
        tmdbId = tmdbId,
        jellyfinId = jellyfinId,
        seerStatus = SeerMediaStatus.fromCode(seerStatusCode),
        downloadProgress = downloadProgress,
        downloadEta = downloadEta,
        downloadSizeStr = downloadSizeStr
    )

    companion object {
        fun fromDomain(item: MediaItem, section: String, index: Int): MediaItemEntity = MediaItemEntity(
            id = item.id,
            librarySection = section,
            sortIndex = index,
            title = item.title,
            subtitle = item.subtitle,
            synopsis = item.synopsis,
            category = item.category.name,
            groupTitle = item.groupTitle,
            rating = item.rating,
            year = item.year,
            durationOrSeasons = item.durationOrSeasons,
            qualityBadge = item.qualityBadge,
            audioBadge = item.audioBadge,
            streamUrl = item.streamUrl,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            palettePrimary = item.palettePrimary,
            paletteSecondary = item.paletteSecondary,
            paletteDark = item.paletteDark,
            progressPercent = item.progressPercent,
            isFavorite = item.isFavorite,
            tmdbId = item.tmdbId,
            jellyfinId = item.jellyfinId,
            seerStatusCode = item.seerStatus.code,
            downloadProgress = item.downloadProgress,
            downloadEta = item.downloadEta,
            downloadSizeStr = item.downloadSizeStr
        )
    }
}
