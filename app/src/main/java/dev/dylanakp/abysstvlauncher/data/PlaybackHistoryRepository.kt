package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PlaybackHistoryRepository {

    private const val HISTORY_FILE = "playback_history.json"
    private val cache = ConcurrentHashMap<String, PlaybackHistoryEntry>()
    private var isLoaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (isLoaded) return
        isLoaded = true
        try {
            val file = File(context.filesDir, HISTORY_FILE)
            if (!file.exists()) return
            val raw = file.readText()
            if (raw.isBlank()) return
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("itemId", "")
                if (id.isBlank()) continue
                val entry = PlaybackHistoryEntry(
                    itemId = id,
                    seriesId = obj.optString("seriesId", ""),
                    seasonNumber = obj.optInt("seasonNumber", 0),
                    episodeNumber = obj.optInt("episodeNumber", 0),
                    episodeTitle = obj.optString("episodeTitle", ""),
                    streamUrl = obj.optString("streamUrl", ""),
                    positionMs = obj.optLong("positionMs", 0L),
                    durationMs = obj.optLong("durationMs", 0L),
                    lastPlayedTimestamp = obj.optLong("lastPlayedTimestamp", 0L),
                    title = obj.optString("title", ""),
                    posterUrl = obj.optString("posterUrl", ""),
                    backdropUrl = obj.optString("backdropUrl", ""),
                    category = obj.optString("category", "")
                )
                cache[id] = entry
                if (entry.seriesId.isNotBlank()) {
                    val existingSeries = cache[entry.seriesId]
                    if (existingSeries == null || entry.lastPlayedTimestamp >= existingSeries.lastPlayedTimestamp) {
                        cache[entry.seriesId] = entry.copy(itemId = entry.seriesId)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun getEntry(context: Context, itemId: String): PlaybackHistoryEntry? {
        ensureLoaded(context)
        return cache[itemId]
    }

    fun getAllEntries(context: Context): Map<String, PlaybackHistoryEntry> {
        ensureLoaded(context)
        return cache.toMap()
    }

    fun saveProgress(
        context: Context,
        itemId: String,
        positionMs: Long,
        durationMs: Long,
        seriesId: String = "",
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        episodeTitle: String = "",
        streamUrl: String = "",
        title: String = "",
        posterUrl: String = "",
        backdropUrl: String = "",
        category: String = ""
    ) {
        if (durationMs <= 0L && positionMs <= 0L) return
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val effectiveDuration = if (durationMs > 0L) durationMs else (positionMs * 10L).coerceAtLeast(3_600_000L)
        val ratio = positionMs.toFloat() / effectiveDuration.toFloat()

        val existing = cache[itemId]
        val resolvedTitle = title.ifBlank { existing?.title.orEmpty() }
        val resolvedPoster = posterUrl.ifBlank { existing?.posterUrl.orEmpty() }
        val resolvedBackdrop = backdropUrl.ifBlank { existing?.backdropUrl.orEmpty() }
        val resolvedCategory = category.ifBlank { existing?.category.orEmpty() }

        val entry = PlaybackHistoryEntry(
            itemId = itemId,
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            streamUrl = streamUrl,
            positionMs = if (ratio > 0.96f) effectiveDuration else positionMs,
            durationMs = effectiveDuration,
            lastPlayedTimestamp = now,
            title = resolvedTitle,
            posterUrl = resolvedPoster,
            backdropUrl = resolvedBackdrop,
            category = resolvedCategory
        )
        cache[itemId] = entry
        if (seriesId.isNotBlank()) {
            cache[seriesId] = entry.copy(itemId = seriesId)
        }
        persistToDisk(context)
    }

    fun clearEntry(context: Context, itemId: String) {
        ensureLoaded(context)
        cache.remove(itemId)
        persistToDisk(context)
    }

    fun markEpisodesUpToWatched(
        context: Context,
        seriesId: String,
        allEpisodes: List<SeriesEpisode>,
        targetEpisode: SeriesEpisode,
        title: String = "",
        posterUrl: String = "",
        backdropUrl: String = "",
        category: String = ""
    ) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val targetIdx = allEpisodes.indexOfFirst { it.id == targetEpisode.id }
        if (targetIdx == -1) return

        val existingSeries = cache[seriesId]
        val resolvedTitle = title.ifBlank { existingSeries?.title.orEmpty() }
        val resolvedPoster = posterUrl.ifBlank { existingSeries?.posterUrl.orEmpty() }
        val resolvedBackdrop = backdropUrl.ifBlank { existingSeries?.backdropUrl.orEmpty() }
        val resolvedCategory = category.ifBlank { existingSeries?.category.orEmpty() }.ifBlank { "SERIES" }

        for (i in 0..targetIdx) {
            val ep = allEpisodes[i]
            val duration = if (ep.savedDurationMs > 0L) ep.savedDurationMs else 1_800_000L
            val entry = PlaybackHistoryEntry(
                itemId = ep.id,
                seriesId = seriesId,
                seasonNumber = ep.seasonNumber,
                episodeNumber = ep.episodeNumber,
                episodeTitle = ep.title,
                streamUrl = ep.streamUrl,
                positionMs = duration,
                durationMs = duration,
                lastPlayedTimestamp = now + i,
                title = resolvedTitle,
                posterUrl = resolvedPoster,
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
            cache[ep.id] = entry
        }

        // Set series resume entry to next episode so "Continue / Reprendre" immediately points to it
        val nextEp = if (targetIdx + 1 < allEpisodes.size) allEpisodes[targetIdx + 1] else null
        if (nextEp != null) {
            val nextDuration = if (nextEp.savedDurationMs > 0L) nextEp.savedDurationMs else 1_800_000L
            cache[seriesId] = PlaybackHistoryEntry(
                itemId = seriesId,
                seriesId = seriesId,
                seasonNumber = nextEp.seasonNumber,
                episodeNumber = nextEp.episodeNumber,
                episodeTitle = nextEp.title,
                streamUrl = nextEp.streamUrl,
                positionMs = 1L, // 0.001% -> included in continueWatching (< 0.96f && > 0)
                durationMs = nextDuration,
                lastPlayedTimestamp = now + targetIdx + 1,
                title = resolvedTitle,
                posterUrl = resolvedPoster,
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
        } else {
            // Entire series finished!
            val targetDuration = if (targetEpisode.savedDurationMs > 0L) targetEpisode.savedDurationMs else 1_800_000L
            cache[seriesId] = PlaybackHistoryEntry(
                itemId = seriesId,
                seriesId = seriesId,
                seasonNumber = targetEpisode.seasonNumber,
                episodeNumber = targetEpisode.episodeNumber,
                episodeTitle = targetEpisode.title,
                streamUrl = targetEpisode.streamUrl,
                positionMs = targetDuration, // 100% finished
                durationMs = targetDuration,
                lastPlayedTimestamp = now + targetIdx + 1,
                title = resolvedTitle,
                posterUrl = resolvedPoster,
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
        }
        persistToDisk(context)
    }

    fun markSingleEpisodeWatched(
        context: Context,
        seriesId: String,
        allEpisodes: List<SeriesEpisode>,
        targetEpisode: SeriesEpisode,
        title: String = "",
        posterUrl: String = "",
        backdropUrl: String = "",
        category: String = ""
    ) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val duration = if (targetEpisode.savedDurationMs > 0L) targetEpisode.savedDurationMs else 1_800_000L

        val existingSeries = cache[seriesId]
        val resolvedTitle = title.ifBlank { existingSeries?.title.orEmpty() }
        val resolvedPoster = posterUrl.ifBlank { existingSeries?.posterUrl.orEmpty() }
        val resolvedBackdrop = backdropUrl.ifBlank { existingSeries?.backdropUrl.orEmpty() }
        val resolvedCategory = category.ifBlank { existingSeries?.category.orEmpty() }.ifBlank { "SERIES" }

        val entry = PlaybackHistoryEntry(
            itemId = targetEpisode.id,
            seriesId = seriesId,
            seasonNumber = targetEpisode.seasonNumber,
            episodeNumber = targetEpisode.episodeNumber,
            episodeTitle = targetEpisode.title,
            streamUrl = targetEpisode.streamUrl,
            positionMs = duration,
            durationMs = duration,
            lastPlayedTimestamp = now,
            title = resolvedTitle,
            posterUrl = resolvedPoster,
            backdropUrl = resolvedBackdrop,
            category = resolvedCategory
        )
        cache[targetEpisode.id] = entry

        val targetIdx = allEpisodes.indexOfFirst { it.id == targetEpisode.id }
        val nextEp = if (targetIdx != -1) {
            allEpisodes.drop(targetIdx + 1).firstOrNull { (cache[it.id]?.progressPercent ?: 0f) < 0.95f }
                ?: allEpisodes.firstOrNull { (cache[it.id]?.progressPercent ?: 0f) < 0.95f }
        } else null

        if (nextEp != null) {
            val nextDuration = if (nextEp.savedDurationMs > 0L) nextEp.savedDurationMs else 1_800_000L
            cache[seriesId] = PlaybackHistoryEntry(
                itemId = seriesId,
                seriesId = seriesId,
                seasonNumber = nextEp.seasonNumber,
                episodeNumber = nextEp.episodeNumber,
                episodeTitle = nextEp.title,
                streamUrl = nextEp.streamUrl,
                positionMs = 1L,
                durationMs = nextDuration,
                lastPlayedTimestamp = now,
                title = resolvedTitle,
                posterUrl = resolvedPoster,
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
        } else {
            cache[seriesId] = PlaybackHistoryEntry(
                itemId = seriesId,
                seriesId = seriesId,
                seasonNumber = targetEpisode.seasonNumber,
                episodeNumber = targetEpisode.episodeNumber,
                episodeTitle = targetEpisode.title,
                streamUrl = targetEpisode.streamUrl,
                positionMs = duration,
                durationMs = duration,
                lastPlayedTimestamp = now,
                title = resolvedTitle,
                posterUrl = resolvedPoster,
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
        }
        persistToDisk(context)
    }

    fun unmarkEpisodeWatched(
        context: Context,
        seriesId: String,
        episode: SeriesEpisode,
        title: String = "",
        posterUrl: String = "",
        backdropUrl: String = "",
        category: String = ""
    ) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        cache.remove(episode.id)

        val existingSeries = cache[seriesId]
        val resolvedTitle = title.ifBlank { existingSeries?.title.orEmpty() }
        val resolvedPoster = posterUrl.ifBlank { existingSeries?.posterUrl.orEmpty() }
        val resolvedBackdrop = backdropUrl.ifBlank { existingSeries?.backdropUrl.orEmpty() }
        val resolvedCategory = category.ifBlank { existingSeries?.category.orEmpty() }.ifBlank { "SERIES" }

        // Point series resume back to this episode
        val epDuration = if (episode.savedDurationMs > 0L) episode.savedDurationMs else 1_800_000L
        cache[seriesId] = PlaybackHistoryEntry(
            itemId = seriesId,
            seriesId = seriesId,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title,
            streamUrl = episode.streamUrl,
            positionMs = 1L,
            durationMs = epDuration,
            lastPlayedTimestamp = now,
            title = resolvedTitle,
            posterUrl = resolvedPoster,
            backdropUrl = resolvedBackdrop,
            category = resolvedCategory
        )
        persistToDisk(context)
    }

    @Synchronized
    private fun persistToDisk(context: Context) {
        try {
            val arr = JSONArray()
            cache.values.forEach { entry ->
                val obj = JSONObject()
                obj.put("itemId", entry.itemId)
                obj.put("seriesId", entry.seriesId)
                obj.put("seasonNumber", entry.seasonNumber)
                obj.put("episodeNumber", entry.episodeNumber)
                obj.put("episodeTitle", entry.episodeTitle)
                obj.put("streamUrl", entry.streamUrl)
                obj.put("positionMs", entry.positionMs)
                obj.put("durationMs", entry.durationMs)
                obj.put("lastPlayedTimestamp", entry.lastPlayedTimestamp)
                obj.put("title", entry.title)
                obj.put("posterUrl", entry.posterUrl)
                obj.put("backdropUrl", entry.backdropUrl)
                obj.put("category", entry.category)
                arr.put(obj)
            }
            val file = File(context.filesDir, HISTORY_FILE)
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }
}
