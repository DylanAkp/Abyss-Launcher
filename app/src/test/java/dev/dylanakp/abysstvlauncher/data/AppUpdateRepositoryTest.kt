package dev.dylanakp.abysstvlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    @Test
    fun parseVersionCode_parsesStandardSemverAndPrefixes() {
        assertEquals(20301, AppUpdateRepository.parseVersionCode("2.3.1"))
        assertEquals(20301, AppUpdateRepository.parseVersionCode("v2.3.1"))
        assertEquals(10400, AppUpdateRepository.parseVersionCode("V1.4"))
        assertEquals(10, AppUpdateRepository.parseVersionCode("10"))
    }

    @Test
    fun parseVersionCode_returnsZeroForInvalidInputs() {
        assertEquals(0, AppUpdateRepository.parseVersionCode(""))
        assertEquals(0, AppUpdateRepository.parseVersionCode("abc"))
        assertEquals(0, AppUpdateRepository.parseVersionCode("1.2.3.4"))
    }

    @Test
    fun parseVersionCode_ordersMinorVersionsNumerically() {
        assertTrue(
            AppUpdateRepository.parseVersionCode("v2.10.0") >
                AppUpdateRepository.parseVersionCode("v2.9.9")
        )
    }
}
