package dev.dylanakp.abysstvlauncher.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["lastPlayedTimestamp"])
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey
    val itemId: String,
    val seriesId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeTitle: String = "",
    val streamUrl: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val title: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val category: String = ""
) {
    fun toDomain(): PlaybackHistoryEntry = PlaybackHistoryEntry(
        itemId = itemId,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        streamUrl = streamUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        lastPlayedTimestamp = lastPlayedTimestamp,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        category = category
    )

    companion object {
        fun fromDomain(entry: PlaybackHistoryEntry): PlaybackHistoryEntity = PlaybackHistoryEntity(
            itemId = entry.itemId,
            seriesId = entry.seriesId,
            seasonNumber = entry.seasonNumber,
            episodeNumber = entry.episodeNumber,
            episodeTitle = entry.episodeTitle,
            streamUrl = entry.streamUrl,
            positionMs = entry.positionMs,
            durationMs = entry.durationMs,
            lastPlayedTimestamp = entry.lastPlayedTimestamp,
            title = entry.title,
            posterUrl = entry.posterUrl,
            backdropUrl = entry.backdropUrl,
            category = entry.category
        )
    }
}
