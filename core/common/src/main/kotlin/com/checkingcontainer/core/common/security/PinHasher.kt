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

    /**
     * Coste de derivación. 150k mantiene el hash fuera del alcance de un ataque
     * casual sin penalizar el login en gama baja: 600k costaba ~0,8 s en JVM de
     * escritorio, lo que en ARM son varios segundos por inicio de sesión.
     *
     * El número de iteraciones va dentro del propio hash, así que los valores
     * `v2:600000:` ya emitidos siguen verificando sin migración.
     *
     * OJO: frente a un atacante con el hash en la mano, un PIN de 6 dígitos
     * (10^6 combinaciones) es forzable en minutos con GPU a cualquier coste
     * razonable. La protección real es que el hash NO sea legible: ver la
     * incidencia de identidad/reglas en FIREBASE.md.
     */
    private const val PBKDF2_ITERATIONS = 150_000
    private const val MAX_ACCEPTED_ITERATIONS = 2_000_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    /**
     * Hash precomputado del PIN por defecto `000000` del SuperAdmin sembrado en
     * la primera instalación. Se deja fijo para no pagar la derivación PBKDF2
     * dentro del `onCreate` de Room, que corre en el hilo que abra la base.
     *
     * El salt compartido no debilita nada aquí: el PIN por defecto es público y
     * debe cambiarse en el primer uso. `PinHasherTest` verifica que este valor
     * sigue correspondiendo a `000000` para que no se desincronice del algoritmo.
     */
    const val DEFAULT_SEED_PIN_HASH: String =
        "v2:150000:S2U/Pb54Vy2RKjMsJD/UHQ==:q/HNlYoWn/CHpOjc5J/sm+DUnsvjSTZn4fToqQtQDI4="

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
