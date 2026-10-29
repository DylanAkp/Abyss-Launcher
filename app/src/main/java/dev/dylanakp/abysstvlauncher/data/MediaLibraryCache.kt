package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.SeerMediaStatus
import dev.dylanakp.abysstvlauncher.model.SyncedLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object MediaLibraryCache {

    private const val CACHE_FILE = "synced_library.json"
    private const val TMP_FILE = "synced_library.json.tmp"

    fun clear(context: Context) {
        try {
            File(context.filesDir, CACHE_FILE).delete()
            File(context.filesDir, TMP_FILE).delete()
        } catch (_: Throwable) {}
    }

    suspend fun save(context: Context, library: SyncedLibrary) = withContext(Dispatchers.IO) {
        if (library.movies.isEmpty() && library.series.isEmpty() && library.tmdbTrending.isEmpty() && library.seerTrending.isEmpty()) {
            return@withContext
        }

        try {
            val tmpFile = File(context.filesDir, TMP_FILE)
            val finalFile = File(context.filesDir, CACHE_FILE)

            FileOutputStream(tmpFile).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { osw ->
                    val writer = JsonWriter(osw)
                    writer.beginObject()

                    writer.name("movies")
                    writeItems(writer, library.movies)

                    writer.name("series")
                    writeItems(writer, library.series)

                    writer.name("tmdbTrending")
                    writeItems(writer, library.tmdbTrending)

                    writer.name("seerTrending")
                    writeItems(writer, library.seerTrending)

                    writer.name("seerPopularMovies")
                    writeItems(writer, library.seerPopularMovies)

                    writer.name("seerPopularTv")
                    writeItems(writer, library.seerPopularTv)

                    writer.name("seerRequests")
                    writeItems(writer, library.seerRequests)

                    writer.endObject()
                    writer.flush()
                }
            }

            if (tmpFile.exists() && tmpFile.length() > 0) {
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                tmpFile.renameTo(finalFile)
            }
        } catch (_: Throwable) {}
    }

    suspend fun load(context: Context): SyncedLibrary? = withContext(Dispatchers.IO) {
        loadSync(context)
    }

    fun loadSync(context: Context): SyncedLibrary? {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            var movies = emptyList<MediaItem>()
            var series = emptyList<MediaItem>()
            var tmdbTrending = emptyList<MediaItem>()
            var seerTrending = emptyList<MediaItem>()
            var seerPopularMovies = emptyList<MediaItem>()
            var seerPopularTv = emptyList<MediaItem>()
            var seerRequests = emptyList<MediaItem>()

            FileInputStream(file).use { fis ->
                InputStreamReader(fis, Charsets.UTF_8).use { isr ->
                    val reader = JsonReader(isr)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "movies" -> movies = readItems(reader, MediaCategory.MOVIE)
                            "series" -> series = readItems(reader, MediaCategory.SERIES)
                            "tmdbTrending" -> tmdbTrending = readItems(reader, MediaCategory.MOVIE)
                            "seerTrending" -> seerTrending = readItems(reader, MediaCategory.MOVIE)
                            "seerPopularMovies" -> seerPopularMovies = readItems(reader, MediaCategory.MOVIE)
                            "seerPopularTv" -> seerPopularTv = readItems(reader, MediaCategory.SERIES)
                            "seerRequests" -> seerRequests = readItems(reader, MediaCategory.SEER_REQUEST)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

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
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeItems(writer: JsonWriter, items: List<MediaItem>) {
        writer.beginArray()
        items.forEach { item ->
            writer.beginObject()
            writer.name("id").value(item.id)
            writer.name("title").value(item.title)
            writer.name("localizedTitle").value(item.localizedTitle)
            writer.name("subtitle").value(item.subtitle)
            writer.name("localizedSubtitle").value(item.localizedSubtitle)
            writer.name("synopsis").value(item.synopsis)
            writer.name("localizedSynopsis").value(item.localizedSynopsis)
            writer.name("category").value(item.category.name)
            writer.name("groupTitle").value(item.groupTitle)
            writer.name("localizedGroupTitle").value(item.localizedGroupTitle)
            writer.name("rating").value(item.rating)
            writer.name("year").value(item.year)
            writer.name("durationEn").value(item.duration)
            writer.name("durationFr").value(item.localizedDuration)
            writer.name("quality").value(item.qualityBadge)
            writer.name("audio").value(item.audioBadge)
            writer.name("streamUrl").value(item.streamUrl)
            writer.name("posterUrl").value(item.posterUrl)
            writer.name("backdropUrl").value(item.backdropUrl)
            writer.name("jellyfinId").value(item.jellyfinId)
            writer.name("seerStatus").value(item.seerStatus.code)
            if (item.tmdbId != null) {
                writer.name("tmdbId").value(item.tmdbId)
            }
            writer.endObject()
        }
        writer.endArray()
    }

    private fun readItems(reader: JsonReader, defaultCategory: MediaCategory): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        reader.beginArray()
        while (reader.hasNext()) {
            var id = ""
            var title = ""
            var localizedTitle = ""
            var subtitle = ""
            var localizedSubtitle = ""
            var synopsis = ""
            var localizedSynopsis = ""
            var category = defaultCategory
            var groupTitle = ""
            var localizedGroupTitle = ""
            var rating = ""
            var year = ""
            var durationEn = ""
            var durationFr = ""
            var quality = "HD"
            var audio = ""
            var streamUrl = ""
            var posterUrl = ""
            var backdropUrl = ""
            var jellyfinId = ""
            var seerStatus = SeerMediaStatus.UNKNOWN
            var tmdbId: Int? = null

            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                if (reader.peek() == android.util.JsonToken.NULL) {
                    reader.nextNull()
                    continue
                }
                when (field) {
                    "id" -> id = reader.nextString()
                    "title" -> title = reader.nextString()
                    "localizedTitle" -> localizedTitle = reader.nextString()
                    "subtitle" -> subtitle = reader.nextString()
                    "localizedSubtitle" -> localizedSubtitle = reader.nextString()
                    "synopsis" -> synopsis = reader.nextString()
                    "localizedSynopsis" -> localizedSynopsis = reader.nextString()
                    "category" -> {
                        val catStr = reader.nextString()
                        category = try { MediaCategory.valueOf(catStr) } catch (_: Exception) { defaultCategory }
                    }
                    "groupTitle" -> groupTitle = reader.nextString()
                    "localizedGroupTitle" -> localizedGroupTitle = reader.nextString()
                    "rating" -> rating = reader.nextString()
                    "year" -> year = reader.nextString()
                    "durationEn" -> durationEn = reader.nextString()
                    "durationFr" -> durationFr = reader.nextString()
                    "quality" -> quality = reader.nextString()
                    "audio" -> audio = reader.nextString()
                    "streamUrl" -> streamUrl = reader.nextString()
                    "posterUrl" -> posterUrl = reader.nextString()
                    "backdropUrl" -> backdropUrl = reader.nextString()
                    "jellyfinId" -> jellyfinId = reader.nextString()
                    "seerStatus" -> seerStatus = SeerMediaStatus.fromCode(reader.nextInt())
                    "tmdbId" -> tmdbId = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (id.isNotBlank()) {
                list.add(
                    MediaItem(
                        id = id,
                        title = title,
                        localizedTitle = localizedTitle,
                        subtitle = subtitle,
                        localizedSubtitle = localizedSubtitle,
                        synopsis = synopsis,
                        localizedSynopsis = localizedSynopsis,
                        category = category,
                        groupTitle = groupTitle,
                        localizedGroupTitle = localizedGroupTitle,
                        rating = rating,
                        year = year,
                        duration = durationEn,
                        localizedDuration = durationFr,
                        qualityBadge = quality,
                        audioBadge = audio,
                        streamUrl = streamUrl,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        jellyfinId = jellyfinId,
                        seerStatus = seerStatus,
                        tmdbId = tmdbId
                    )
                )
            }
        }
        reader.endArray()
        return list
    }
}
