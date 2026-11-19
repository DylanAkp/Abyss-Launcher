package dev.dylanakp.abysstvlauncher.remote

import android.view.KeyEvent
import java.security.MessageDigest
import java.security.SecureRandom

internal object RemotePairingAndKeyHelper {

    private val secureRandom = SecureRandom()

    fun generatePin(): String = (100000 + secureRandom.nextInt(900000)).toString()

    fun generateToken(): String = ByteArray(32)
        .also { secureRandom.nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    val KEY_MAP: Map<String, Int> = mapOf(
        "DPAD_UP" to KeyEvent.KEYCODE_DPAD_UP,
        "DPAD_DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "DPAD_LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
        "DPAD_RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "DPAD_CENTER" to KeyEvent.KEYCODE_DPAD_CENTER,
        "OK" to KeyEvent.KEYCODE_DPAD_CENTER,
        "ENTER" to KeyEvent.KEYCODE_ENTER,
        "BACK" to KeyEvent.KEYCODE_BACK,
        "MENU" to KeyEvent.KEYCODE_MENU,
        "DEL" to KeyEvent.KEYCODE_DEL
    )

    fun resolveKeyCode(key: String): Int? = KEY_MAP[key.uppercase()]

    fun isTokenValid(pairedTokens: Set<String>, token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        var matched = false
        for (entry in pairedTokens) {
            if (constantTimeEquals(entry.substringBefore("|"), token)) {
                matched = true
            }
        }
        return matched
    }

    data class PairingOutcome(
        val response: PairResponse,
        val updatedTokens: Set<String>
    )

    fun pair(
        expectedPin: String,
        pairedTokens: Set<String>,
        pin: String?,
        token: String?,
        deviceName: String?,
        tokenGenerator: () -> String = ::generateToken
    ): PairingOutcome? {
        val cleanPin = pin?.trim().orEmpty()
        val validPin = cleanPin.isNotEmpty() && constantTimeEquals(cleanPin, expectedPin)
        val validExisting = !token.isNullOrBlank() && isTokenValid(pairedTokens, token)
        if (!validPin && !validExisting) return null

        val issued = if (validExisting) token!! else tokenGenerator()
        val label = deviceName?.trim()?.take(32)?.ifBlank { "Phone" } ?: "Phone"
        val updated = if (!validExisting) {
            pairedTokens.toMutableSet().apply { add("$issued|$label") }
        } else {
            pairedTokens
        }
        return PairingOutcome(
            response = PairResponse(token = issued, deviceName = label),
            updatedTokens = updated
        )
    }
}
