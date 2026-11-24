package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import androidx.room.withTransaction
import dev.dylanakp.abysstvlauncher.data.local.AbyssDatabase
import dev.dylanakp.abysstvlauncher.data.local.MediaItemEntity
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache as IMediaLibraryCache

class MediaLibraryCache(
    private val database: AbyssDatabase,
    private val json: Json
) : IMediaLibraryCache {

    companion object {
        private const val LEGACY_CACHE_FILE = "synced_library.json"
    }

    override suspend fun clear(context: Context?): Unit = withContext(Dispatchers.IO) {
        runCatching {
            database.mediaLibraryDao().clearAll()
            context?.let { File(it.filesDir, LEGACY_CACHE_FILE).delete() }
        }
    }

    override suspend fun save(context: Context?, library: SyncedLibrary) = withContext(Dispatchers.IO) {
        if (library.movies.isEmpty() && library.series.isEmpty() && library.tmdbTrending.isEmpty() &&
            library.seerTrending.isEmpty() && library.seerPopularMovies.isEmpty() &&
            library.seerPopularTv.isEmpty() && library.seerRequests.isEmpty()
        ) {
            return@withContext
        }

        val dao = database.mediaLibraryDao()

        fun mapEntities(items: List<MediaItem>, section: String): List<MediaItemEntity> =
            items.mapIndexed { idx, item -> MediaItemEntity.fromDomain(item, section, idx) }

        database.withTransaction {
            dao.replaceSection("movies", mapEntities(library.movies, "movies"))
            dao.replaceSection("series", mapEntities(library.series, "series"))
            dao.replaceSection("tmdbTrending", mapEntities(library.tmdbTrending, "tmdbTrending"))
            dao.replaceSection("seerTrending", mapEntities(library.seerTrending, "seerTrending"))
            dao.replaceSection("seerPopularMovies", mapEntities(library.seerPopularMovies, "seerPopularMovies"))
            dao.replaceSection("seerPopularTv", mapEntities(library.seerPopularTv, "seerPopularTv"))
            dao.replaceSection("seerRequests", mapEntities(library.seerRequests, "seerRequests"))
        }
    }

    override suspend fun load(context: Context?): SyncedLibrary? = withContext(Dispatchers.IO) {
        context?.let { migrateLegacyFileIfPresent(it) }

        val bySection = database.mediaLibraryDao().getAllItems().groupBy { it.librarySection }
        val movies = bySection["movies"].orEmpty().map { it.toDomain() }
        val series = bySection["series"].orEmpty().map { it.toDomain() }
        val tmdbTrending = bySection["tmdbTrending"].orEmpty().map { it.toDomain() }
        val seerTrending = bySection["seerTrending"].orEmpty().map { it.toDomain() }
        val seerPopularMovies = bySection["seerPopularMovies"].orEmpty().map { it.toDomain() }
        val seerPopularTv = bySection["seerPopularTv"].orEmpty().map { it.toDomain() }
        val seerRequests = bySection["seerRequests"].orEmpty().map { it.toDomain() }

        if (movies.isEmpty() && series.isEmpty() && tmdbTrending.isEmpty() && seerTrending.isEmpty() &&
            seerPopularMovies.isEmpty() && seerPopularTv.isEmpty() && seerRequests.isEmpty()
        ) {
            null
        } else {
            SyncedLibrary(
                movies = movies,
                series = series,
                tmdbTrending = tmdbTrending,
                seerTrending = seerTrending,
                seerPopularMovies = seerPopularMovies,
                seerPopularTv = seerPopularTv,
                seerRequests = seerRequests
            )
        }
    }

    private suspend fun migrateLegacyFileIfPresent(context: Context) {
        val file = File(context.filesDir, LEGACY_CACHE_FILE)
        if (!file.exists() || file.length() == 0L) return

        runCatching {
            val legacy = json.decodeFromString<SyncedLibrary>(file.readText())
            save(context, legacy)
            file.delete()
        }
    }
}
