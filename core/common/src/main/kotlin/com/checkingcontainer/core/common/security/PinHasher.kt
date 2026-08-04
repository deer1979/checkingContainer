package com.checkingcontainer.core.common.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Protección versionada del PIN.
 *
 * Formato actual: `v2:<iteraciones>:<saltBase64>:<hashBase64>` usando
 * PBKDF2-HMAC-SHA256. También valida hashes `v1` (SHA-256 + salt) y valores
 * legados en texto plano para permitir una migración perezosa tras el login.
 */
object PinHasher {
    private const val CURRENT_PREFIX = "v2"
    private const val LEGACY_HASH_PREFIX = "v1"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 600_000
    private const val MAX_ACCEPTED_ITERATIONS = 2_000_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    /** Reconoce tanto el formato actual como el hash v1 heredado. */
    fun isHashed(stored: String): Boolean =
        stored.startsWith("$CURRENT_PREFIX:") || stored.startsWith("$LEGACY_HASH_PREFIX:")

    /** Indica que el valor debe re-hashearse después de autenticar correctamente. */
    fun needsUpgrade(stored: String): Boolean = !stored.startsWith("$CURRENT_PREFIX:")

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = pbkdf2(pin, salt, PBKDF2_ITERATIONS)
        val encoder = Base64.getEncoder()
        return buildString {
            append(CURRENT_PREFIX)
            append(':')
            append(PBKDF2_ITERATIONS)
            append(':')
            append(encoder.encodeToString(salt))
            append(':')
            append(encoder.encodeToString(derived))
        }
    }

    /**
     * Hashea valores en texto plano. Los hashes existentes se conservan porque
     * no existe el PIN original para elevarlos hasta que el usuario se autentica.
     */
    fun ensureHashed(pinOrHash: String): String =
        if (isHashed(pinOrHash)) pinOrHash else hash(pinOrHash)

    /** Valida v2, v1 o texto plano legado sin lanzar ante entradas corruptas. */
    fun verify(pin: String, stored: String): Boolean = when {
        stored.startsWith("$CURRENT_PREFIX:") -> verifyV2(pin, stored)
        stored.startsWith("$LEGACY_HASH_PREFIX:") -> verifyV1(pin, stored)
        else -> constantTimeEquals(
            pin.toByteArray(Charsets.UTF_8),
            stored.toByteArray(Charsets.UTF_8),
        )
    }

    private fun verifyV2(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull()
            ?.takeIf { it in 1..MAX_ACCEPTED_ITERATIONS }
            ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        if (salt.isEmpty() || expected.isEmpty()) return false
        return constantTimeEquals(pbkdf2(pin, salt, iterations), expected)
    }

    private fun verifyV1(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 3) return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        return constantTimeEquals(sha256(salt, pin), expected)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val specification = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                .generateSecret(specification)
                .encoded
        } finally {
            specification.clearPassword()
        }
    }

    private fun sha256(salt: ByteArray, pin: String): ByteArray =
        MessageDigest.getInstance("SHA-256").apply {
            update(salt)
            update(pin.toByteArray(Charsets.UTF_8))
        }.digest()

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (index in a.indices) {
            result = result or (a[index].toInt() xor b[index].toInt())
        }
        return result == 0
    }
}
