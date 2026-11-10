package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import dev.dylanakp.abysstvlauncher.data.local.AbyssDatabase
import dev.dylanakp.abysstvlauncher.data.local.MediaItemEntity
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache as IMediaLibraryCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object MediaLibraryCache : IMediaLibraryCache, KoinComponent {

    private const val LEGACY_CACHE_FILE = "synced_library.json"
    private val database: AbyssDatabase by inject()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun clear(context: Context?): Unit = withContext(Dispatchers.IO) {
        runCatching {
            database.mediaLibraryDao().clearAll()
            context?.let { File(it.filesDir, LEGACY_CACHE_FILE).delete() }
        }
    }

    override suspend fun save(context: Context?, library: SyncedLibrary) = withContext(Dispatchers.IO) {
        if (library.movies.isEmpty() && library.series.isEmpty() && library.tmdbTrending.isEmpty() && library.seerTrending.isEmpty()) {
            return@withContext
        }

        val dao = database.mediaLibraryDao()

        fun mapEntities(items: List<MediaItem>, section: String): List<MediaItemEntity> =
            items.mapIndexed { idx, item -> MediaItemEntity.fromDomain(item, section, idx) }

        dao.replaceSection("movies", mapEntities(library.movies, "movies"))
        dao.replaceSection("series", mapEntities(library.series, "series"))
        dao.replaceSection("tmdbTrending", mapEntities(library.tmdbTrending, "tmdbTrending"))
        dao.replaceSection("seerTrending", mapEntities(library.seerTrending, "seerTrending"))
        dao.replaceSection("seerPopularMovies", mapEntities(library.seerPopularMovies, "seerPopularMovies"))
        dao.replaceSection("seerPopularTv", mapEntities(library.seerPopularTv, "seerPopularTv"))
        dao.replaceSection("seerRequests", mapEntities(library.seerRequests, "seerRequests"))
    }

    override suspend fun load(context: Context?): SyncedLibrary? = withContext(Dispatchers.IO) {
        context?.let { migrateLegacyFileIfPresent(it) }

        val dao = database.mediaLibraryDao()
        val movies = dao.getItemsBySection("movies").map { it.toDomain() }
        val series = dao.getItemsBySection("series").map { it.toDomain() }
        val tmdbTrending = dao.getItemsBySection("tmdbTrending").map { it.toDomain() }
        val seerTrending = dao.getItemsBySection("seerTrending").map { it.toDomain() }
        val seerPopularMovies = dao.getItemsBySection("seerPopularMovies").map { it.toDomain() }
        val seerPopularTv = dao.getItemsBySection("seerPopularTv").map { it.toDomain() }
        val seerRequests = dao.getItemsBySection("seerRequests").map { it.toDomain() }

        if (movies.isEmpty() && series.isEmpty() && tmdbTrending.isEmpty() && seerTrending.isEmpty()) {
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

    override fun loadSync(context: Context?): SyncedLibrary? = runBlocking(Dispatchers.IO) {
        load(context)
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
