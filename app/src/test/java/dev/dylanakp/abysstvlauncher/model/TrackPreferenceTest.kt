package dev.dylanakp.abysstvlauncher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackPreferenceTest {

    @Test
    fun encodeDecode_roundTripsForcedSubtitleChoice() {
        val pref = TrackPreference("en", "fr", false, true)
        assertEquals(pref, TrackPreference.decode(pref.encode()))
    }

    @Test
    fun decode_treatsBlankFieldsAsInherited() {
        assertEquals(TrackPreference(), TrackPreference.decode("|||"))
        assertEquals(TrackPreference(audioLanguage = null, subtitleOff = false), TrackPreference.decode("||0|"))
    }

    @Test
    fun resolveWith_seriesOverridesOnlySpecifiedFields() {
        val global = TrackPreference("en", "fr", false, true)
        val series = TrackPreference(audioLanguage = "ja")
        assertEquals(TrackPreference("ja", "fr", false, true), series.resolveWith(global))
    }
}
