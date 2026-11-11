package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import dev.dylanakp.abysstvlauncher.data.local.AbyssDatabase
import dev.dylanakp.abysstvlauncher.data.local.PlaybackHistoryEntity
import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import dev.dylanakp.abysstvlauncher.domain.repository.PlaybackHistoryRepository as IPlaybackHistoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PlaybackHistoryRepository : IPlaybackHistoryRepository, KoinComponent {

    private const val LEGACY_HISTORY_FILE = "playback_history.json"
    private val database: AbyssDatabase by inject()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, PlaybackHistoryEntry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isLoaded = false

    @Synchronized
    override fun ensureLoaded(context: Context?) {
        if (isLoaded) return
        isLoaded = true
        scope.launch {
            context?.let { migrateLegacyFileIfPresent(it) }
            val dao = database.playbackHistoryDao()
            val entities = dao.getAll()
            entities.forEach { entity ->
                val entry = entity.toDomain()
                cache[entry.itemId] = entry
                if (entry.seriesId.isNotBlank()) {
                    val existing = cache[entry.seriesId]
                    if (existing == null || entry.lastPlayedTimestamp >= existing.lastPlayedTimestamp) {
                        cache[entry.seriesId] = entry.copy(itemId = entry.seriesId)
                    }
                }
            }
        }
    }

    private suspend fun migrateLegacyFileIfPresent(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, LEGACY_HISTORY_FILE)
        if (!file.exists()) return@withContext

        runCatching {
            val raw = file.readText()
            if (raw.isNotBlank()) {
                val entries = json.decodeFromString<List<PlaybackHistoryEntry>>(raw)
                val entities = entries.map { PlaybackHistoryEntity.fromDomain(it) }
                if (entities.isNotEmpty()) {
                    AbyssDatabase.getInstance(context).playbackHistoryDao().upsertAll(entities)
                }
            }
            file.delete()
        }
    }

    override fun getProgressPercent(itemId: String): Float = cache[itemId]?.progressPercent ?: 0f
    override fun getSavedPositionMs(itemId: String): Long = cache[itemId]?.positionMs ?: 0L
    override fun getSavedDurationMs(itemId: String): Long = cache[itemId]?.durationMs ?: 0L

    override fun getEntry(context: Context, itemId: String): PlaybackHistoryEntry? {
        ensureLoaded(context)
        return cache[itemId]
    }

    override fun getAllEntries(context: Context): Map<String, PlaybackHistoryEntry> {
        ensureLoaded(context)
        return cache.toMap()
    }

    override fun saveProgress(
        context: Context,
        itemId: String,
        positionMs: Long,
        durationMs: Long,
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String,
        streamUrl: String,
        title: String,
        posterUrl: String,
        backdropUrl: String,
        category: String
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

        val toPersist = mutableListOf(PlaybackHistoryEntity.fromDomain(entry))
        if (seriesId.isNotBlank()) {
            val seriesEntry = entry.copy(itemId = seriesId)
            cache[seriesId] = seriesEntry
            toPersist.add(PlaybackHistoryEntity.fromDomain(seriesEntry))
        }

        scope.launch {
            AbyssDatabase.getInstance(context).playbackHistoryDao().upsertAll(toPersist)
        }
    }

    override fun clearEntry(context: Context, itemId: String) {
        ensureLoaded(context)
        cache.remove(itemId)
        scope.launch {
            AbyssDatabase.getInstance(context).playbackHistoryDao().delete(itemId)
        }
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

        val toPersist = mutableListOf<PlaybackHistoryEntity>()

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
            toPersist.add(PlaybackHistoryEntity.fromDomain(entry))
        }

        val nextEp = if (targetIdx + 1 < allEpisodes.size) allEpisodes[targetIdx + 1] else null
        val seriesEntry = if (nextEp != null) {
            val nextDuration = if (nextEp.savedDurationMs > 0L) nextEp.savedDurationMs else 1_800_000L
            PlaybackHistoryEntry(
                itemId = seriesId,
                seriesId = seriesId,
                seasonNumber = nextEp.seasonNumber,
                episodeNumber = nextEp.episodeNumber,
                episodeTitle = nextEp.title,
                streamUrl = nextEp.streamUrl,
                positionMs = 1L,
                durationMs = nextDuration,
                lastPlayedTimestamp = now + targetIdx + 1,
                title = resolvedTitle,
                posterUrl = resolvedPoster,
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
        } else {
            val targetDuration = if (targetEpisode.savedDurationMs > 0L) targetEpisode.savedDurationMs else 1_800_000L
            PlaybackHistoryEntry(
                itemId = seriesId,
                seriesId = seriesId,
                seasonNumber = targetEpisode.seasonNumber,
                episodeNumber = targetEpisode.episodeNumber,
                episodeTitle = targetEpisode.title,
                streamUrl = targetEpisode.streamUrl,
                positionMs = targetDuration,
                durationMs = targetDuration,
                lastPlayedTimestamp = now + targetIdx + 1,
                title = resolvedTitle,
                posterUrl = resolvedPoster,
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
        }
        cache[seriesId] = seriesEntry
        toPersist.add(PlaybackHistoryEntity.fromDomain(seriesEntry))

        scope.launch {
            AbyssDatabase.getInstance(context).playbackHistoryDao().upsertAll(toPersist)
        }
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

        val toPersist = mutableListOf(PlaybackHistoryEntity.fromDomain(entry))

        val targetIdx = allEpisodes.indexOfFirst { it.id == targetEpisode.id }
        val nextEp = if (targetIdx != -1) {
            allEpisodes.drop(targetIdx + 1).firstOrNull { (cache[it.id]?.progressPercent ?: 0f) < 0.95f }
                ?: allEpisodes.firstOrNull { (cache[it.id]?.progressPercent ?: 0f) < 0.95f }
        } else null

        val seriesEntry = if (nextEp != null) {
            val nextDuration = if (nextEp.savedDurationMs > 0L) nextEp.savedDurationMs else 1_800_000L
            PlaybackHistoryEntry(
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
            PlaybackHistoryEntry(
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
        cache[seriesId] = seriesEntry
        toPersist.add(PlaybackHistoryEntity.fromDomain(seriesEntry))

        scope.launch {
            AbyssDatabase.getInstance(context).playbackHistoryDao().upsertAll(toPersist)
        }
    }

    fun unmarkEpisodeWatched(
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

        val existingSeries = cache[seriesId]
        val resolvedTitle = title.ifBlank { existingSeries?.title.orEmpty() }
        val resolvedPoster = posterUrl.ifBlank { existingSeries?.posterUrl.orEmpty() }
        val resolvedBackdrop = backdropUrl.ifBlank { existingSeries?.backdropUrl.orEmpty() }
        val resolvedCategory = category.ifBlank { existingSeries?.category.orEmpty() }.ifBlank { "SERIES" }

        val epDuration = if (targetEpisode.savedDurationMs > 0L) targetEpisode.savedDurationMs else 1_800_000L
        val unmarkEntry = PlaybackHistoryEntry(
            itemId = targetEpisode.id,
            seriesId = seriesId,
            seasonNumber = targetEpisode.seasonNumber,
            episodeNumber = targetEpisode.episodeNumber,
            episodeTitle = targetEpisode.title,
            streamUrl = targetEpisode.streamUrl,
            positionMs = 0L,
            durationMs = epDuration,
            lastPlayedTimestamp = now,
            title = resolvedTitle,
            posterUrl = targetEpisode.posterUrl.ifBlank { resolvedPoster },
            backdropUrl = resolvedBackdrop,
            category = resolvedCategory
        )
        cache[targetEpisode.id] = unmarkEntry

        val resumeEpisode = when {
            targetIdx > 0 -> {
                allEpisodes.take(targetIdx).reversed().firstOrNull { ep ->
                    (cache[ep.id]?.progressPercent ?: 0f) < 0.95f
                } ?: targetEpisode
            }
            else -> targetEpisode
        }

        val resumeDuration = if (resumeEpisode.savedDurationMs > 0L) resumeEpisode.savedDurationMs else 1_800_000L
        val savedPos = if (resumeEpisode.id == targetEpisode.id) 0L else (cache[resumeEpisode.id]?.positionMs ?: 0L)
        val seriesEntry = PlaybackHistoryEntry(
            itemId = seriesId,
            seriesId = seriesId,
            seasonNumber = resumeEpisode.seasonNumber,
            episodeNumber = resumeEpisode.episodeNumber,
            episodeTitle = resumeEpisode.title,
            streamUrl = resumeEpisode.streamUrl,
            positionMs = savedPos,
            durationMs = resumeDuration,
            lastPlayedTimestamp = now,
            title = resolvedTitle,
            posterUrl = resolvedPoster,
            backdropUrl = resolvedBackdrop,
            category = resolvedCategory
        )
        cache[seriesId] = seriesEntry

        scope.launch {
            val dao = AbyssDatabase.getInstance(context).playbackHistoryDao()
            dao.upsert(PlaybackHistoryEntity.fromDomain(unmarkEntry))
            dao.upsert(PlaybackHistoryEntity.fromDomain(seriesEntry))
        }
    }

    fun unmarkEpisodesFrom(
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

        val unmarkEntries = mutableListOf<PlaybackHistoryEntity>()
        for (i in targetIdx until allEpisodes.size) {
            val ep = allEpisodes[i]
            val epDuration = if (ep.savedDurationMs > 0L) ep.savedDurationMs else 1_800_000L
            val entry = PlaybackHistoryEntry(
                itemId = ep.id,
                seriesId = seriesId,
                seasonNumber = ep.seasonNumber,
                episodeNumber = ep.episodeNumber,
                episodeTitle = ep.title,
                streamUrl = ep.streamUrl,
                positionMs = 0L,
                durationMs = epDuration,
                lastPlayedTimestamp = now,
                title = resolvedTitle,
                posterUrl = ep.posterUrl.ifBlank { resolvedPoster },
                backdropUrl = resolvedBackdrop,
                category = resolvedCategory
            )
            cache[ep.id] = entry
            unmarkEntries.add(PlaybackHistoryEntity.fromDomain(entry))
        }

        val resumeEpisode = targetEpisode
        val epDuration = if (resumeEpisode.savedDurationMs > 0L) resumeEpisode.savedDurationMs else 1_800_000L
        val seriesEntry = PlaybackHistoryEntry(
            itemId = seriesId,
            seriesId = seriesId,
            seasonNumber = resumeEpisode.seasonNumber,
            episodeNumber = resumeEpisode.episodeNumber,
            episodeTitle = resumeEpisode.title,
            streamUrl = resumeEpisode.streamUrl,
            positionMs = 0L,
            durationMs = epDuration,
            lastPlayedTimestamp = now,
            title = resolvedTitle,
            posterUrl = resolvedPoster,
            backdropUrl = resolvedBackdrop,
            category = resolvedCategory
        )
        cache[seriesId] = seriesEntry

        scope.launch {
            val dao = AbyssDatabase.getInstance(context).playbackHistoryDao()
            dao.upsertAll(unmarkEntries)
            dao.upsert(PlaybackHistoryEntity.fromDomain(seriesEntry))
        }
    }
}
