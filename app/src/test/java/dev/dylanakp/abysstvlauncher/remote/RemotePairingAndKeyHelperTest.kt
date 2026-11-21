package dev.dylanakp.abysstvlauncher.remote

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePairingAndKeyHelperTest {

    @Test
    fun pair_withValidPin_issuesTokenAndTruncatesDeviceName() {
        val longName = "A".repeat(48)
        val outcome = RemotePairingAndKeyHelper.pair(
            expectedPin = "123456",
            pairedTokens = emptySet(),
            pin = " 123456 ",
            token = null,
            deviceName = longName,
            tokenGenerator = { "tok123" }
        )

        assertNotNull(outcome)
        assertEquals("tok123", outcome!!.response.token)
        assertEquals(32, outcome.response.deviceName.length)
        assertTrue(outcome.updatedTokens.contains("tok123|${"A".repeat(32)}"))
    }

    @Test
    fun pair_withInvalidPinAndInvalidToken_returnsNull() {
        val outcome = RemotePairingAndKeyHelper.pair(
            expectedPin = "123456",
            pairedTokens = setOf("existing|Pixel"),
            pin = "000000",
            token = "wrong",
            deviceName = "Phone"
        )
        assertNull(outcome)
    }

    @Test
    fun pair_withValidExistingToken_reusesTokenWithoutMutatingSet() {
        val existing = setOf("existing|Pixel")
        val outcome = RemotePairingAndKeyHelper.pair(
            expectedPin = "123456",
            pairedTokens = existing,
            pin = null,
            token = "existing",
            deviceName = "  "
        )

        assertNotNull(outcome)
        assertEquals("existing", outcome!!.response.token)
        assertEquals("Phone", outcome.response.deviceName)
        assertEquals(existing, outcome.updatedTokens)
    }

    @Test
    fun isTokenValid_matchesTokenPrefixBeforePipe() {
        val tokens = setOf("abc123|LivingRoomPhone", "def456|Tablet")
        assertTrue(RemotePairingAndKeyHelper.isTokenValid(tokens, "abc123"))
        assertFalse(RemotePairingAndKeyHelper.isTokenValid(tokens, "LivingRoomPhone"))
        assertFalse(RemotePairingAndKeyHelper.isTokenValid(tokens, ""))
        assertFalse(RemotePairingAndKeyHelper.isTokenValid(tokens, null))
    }

    @Test
    fun resolveKeyCode_mapsKnownKeysCaseInsensitively() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, RemotePairingAndKeyHelper.resolveKeyCode("dpad_up"))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, RemotePairingAndKeyHelper.resolveKeyCode("OK"))
        assertEquals(KeyEvent.KEYCODE_BACK, RemotePairingAndKeyHelper.resolveKeyCode("BACK"))
        assertNull(RemotePairingAndKeyHelper.resolveKeyCode("UNKNOWN_KEY"))
    }
}
