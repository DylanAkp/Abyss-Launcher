package dev.dylanakp.abysstvlauncher.ui.components

import dev.dylanakp.abysstvlauncher.model.PlaybackHistoryEntry
import dev.dylanakp.abysstvlauncher.model.SeriesEpisode
import dev.dylanakp.abysstvlauncher.model.SeriesSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesResumeTargetTest {

    private fun episode(
        id: String,
        season: Int,
        number: Int,
        progress: Float = 0f,
        savedPosMs: Long = 0L
    ) = SeriesEpisode(
        id = id,
        seriesId = "series1",
        seasonNumber = season,
        episodeNumber = number,
        title = "Ep $number",
        streamUrl = "https://example.com/$id",
        progressPercent = progress,
        savedPositionMs = savedPosMs
    )

    @Test
    fun resolveSeriesResumeTarget_emptySeasons_returnsNull() {
        val result = resolveSeriesResumeTarget(emptyList(), null, emptyMap())
        assertNull(result)
    }

    @Test
    fun resolveSeriesResumeTarget_finishedEpisode_advancesToNextUnwatched() {
        val ep1 = episode("ep1", 1, 1)
        val ep2 = episode("ep2", 1, 2)
        val seasons = listOf(SeriesSeason(1, "Season 1", listOf(ep1, ep2)))
        val history = mapOf(
            "ep1" to PlaybackHistoryEntry(
                itemId = "ep1",
                seriesId = "series1",
                seasonNumber = 1,
                episodeNumber = 1,
                positionMs = 2_400_000L,
                durationMs = 2_400_000L,
                lastPlayedTimestamp = 1000L
            )
        )
        val seriesEntry = PlaybackHistoryEntry(
            itemId = "series1",
            seriesId = "series1",
            seasonNumber = 1,
            episodeNumber = 1,
            positionMs = 2_400_000L,
            durationMs = 2_400_000L,
            lastPlayedTimestamp = 1000L
        )

        val target = resolveSeriesResumeTarget(seasons, seriesEntry, history)
        assertNotNull(target)
        assertEquals("ep2", target!!.first.id)
        assertEquals(0L, target.second)
        assertTrue(target.third)
    }

    @Test
    fun resolveSeriesResumeTarget_inProgressEpisode_returnsSavedPosition() {
        val ep1 = episode("ep1", 1, 1)
        val ep2 = episode("ep2", 1, 2)
        val seasons = listOf(SeriesSeason(1, "Season 1", listOf(ep1, ep2)))
        val history = mapOf(
            "ep2" to PlaybackHistoryEntry(
                itemId = "ep2",
                seriesId = "series1",
                seasonNumber = 1,
                episodeNumber = 2,
                positionMs = 540_000L,
                durationMs = 2_400_000L,
                lastPlayedTimestamp = 1000L
            )
        )
        val seriesEntry = PlaybackHistoryEntry(
            itemId = "series1",
            seriesId = "series1",
            seasonNumber = 1,
            episodeNumber = 2,
            positionMs = 540_000L,
            durationMs = 2_400_000L,
            lastPlayedTimestamp = 1000L
        )

        val target = resolveSeriesResumeTarget(seasons, seriesEntry, history)
        assertNotNull(target)
        assertEquals("ep2", target!!.first.id)
        assertEquals(540_000L, target.second)
        assertTrue(target.third)
    }

    @Test
    fun resolveSeriesResumeTarget_noHistory_defaultsToFirstEpisode() {
        val ep1 = episode("ep1", 1, 1)
        val seasons = listOf(SeriesSeason(1, "Season 1", listOf(ep1)))

        val target = resolveSeriesResumeTarget(seasons, null, emptyMap())
        assertNotNull(target)
        assertEquals("ep1", target!!.first.id)
        assertEquals(0L, target.second)
        assertFalse(target.third)
    }
}
