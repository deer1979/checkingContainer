package com.checkingcontainer.core.common.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Hash de PIN con SHA-256 + salt aleatorio por usuario, formato
 * `"v1:<saltBase64>:<hashBase64>"` guardado en la misma columna TEXT `pin`.
 * Los valores legados (PIN en texto plano) se detectan con [isHashed] y se
 * migran perezosamente en el primer login correcto.
 */
object PinHasher {
    private const val PREFIX = "v1"

    fun isHashed(stored: String): Boolean = stored.startsWith("$PREFIX:")

    fun hash(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val enc = Base64.getEncoder()
        return "$PREFIX:${enc.encodeToString(salt)}:${enc.encodeToString(sha256(salt, pin))}"
    }

    /** Hashea solo si el valor aún está en texto plano (idempotente). */
    fun ensureHashed(pinOrHash: String): String =
        if (isHashed(pinOrHash)) pinOrHash else hash(pinOrHash)

    /** Valida contra hash v1 o, si el valor guardado es legado, contra texto plano. */
    fun verify(pin: String, stored: String): Boolean {
        if (!isHashed(stored)) {
            return constantTimeEquals(pin.toByteArray(Charsets.UTF_8), stored.toByteArray(Charsets.UTF_8))
        }
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[1]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return false
        return constantTimeEquals(sha256(salt, pin), expected)
    }

    private fun sha256(salt: ByteArray, pin: String): ByteArray =
        MessageDigest.getInstance("SHA-256").apply {
            update(salt)
            update(pin.toByteArray(Charsets.UTF_8))
        }.digest()

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
