package com.checkingcontainer.core.common.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `hash actual usa v2 y nunca contiene el PIN en claro`() {
        val hash = PinHasher.hash("123456")
        assertTrue(hash.startsWith("v2:"))
        assertFalse(hash.contains("123456"))
        assertFalse(PinHasher.needsUpgrade(hash))
    }

    @Test
    fun `el hash del seed corresponde al PIN por defecto y no necesita upgrade`() {
        // Guarda contra que el valor precomputado se desincronice del algoritmo:
        // si alguien cambia iteraciones o formato sin regenerarlo, el SuperAdmin
        // sembrado no podría iniciar sesión en una instalación nueva.
        assertTrue(PinHasher.verify("000000", PinHasher.DEFAULT_SEED_PIN_HASH))
        assertFalse(PinHasher.verify("123456", PinHasher.DEFAULT_SEED_PIN_HASH))
        assertFalse(PinHasher.needsUpgrade(PinHasher.DEFAULT_SEED_PIN_HASH))
    }

    @Test
    fun `hashes v2 emitidos con 600k iteraciones siguen verificando`() {
        // El coste va dentro del propio hash, así que bajar las iteraciones por
        // defecto no invalida los PIN ya guardados por versiones anteriores.
        val emitidoCon600k =
            "v2:600000:MDEyMzQ1Njc4OWFiY2RlZg==:/V4olk4XJPnStEnsMtkTKlqWuFbI9fA58YIKQGlFNKk="
        assertTrue(PinHasher.verify("123456", emitidoCon600k))
        assertFalse(PinHasher.verify("654321", emitidoCon600k))
        assertFalse(PinHasher.needsUpgrade(emitidoCon600k))
    }

    @Test
    fun `verify acepta el PIN correcto y rechaza el incorrecto`() {
        val hash = PinHasher.hash("123456")
        assertTrue(PinHasher.verify("123456", hash))
        assertFalse(PinHasher.verify("654321", hash))
        assertFalse(PinHasher.verify("", hash))
    }

    @Test
    fun `dos hashes del mismo PIN difieren por el salt pero ambos verifican`() {
        val first = PinHasher.hash("123456")
        val second = PinHasher.hash("123456")
        assertNotEquals(first, second)
        assertTrue(PinHasher.verify("123456", first))
        assertTrue(PinHasher.verify("123456", second))
    }

    @Test
    fun `verify conserva compatibilidad con hash v1`() {
        val legacyV1 =
            "v1:MDEyMzQ1Njc4OWFiY2RlZg==:vUD/8HwIkQDMfmXuaKWdRSn5k8A+uWTdsMV2OxuIzHg="
        assertTrue(PinHasher.verify("123456", legacyV1))
        assertFalse(PinHasher.verify("654321", legacyV1))
        assertTrue(PinHasher.isHashed(legacyV1))
        assertTrue(PinHasher.needsUpgrade(legacyV1))
    }

    @Test
    fun `verify contra valor legado en texto plano`() {
        assertTrue(PinHasher.verify("123456", "123456"))
        assertFalse(PinHasher.verify("123456", "000000"))
        assertTrue(PinHasher.needsUpgrade("123456"))
    }

    @Test
    fun `ensureHashed es idempotente para hashes reconocidos`() {
        val current = PinHasher.ensureHashed("123456")
        assertTrue(PinHasher.isHashed(current))
        assertTrue(PinHasher.ensureHashed(current) == current)

        val legacyV1 =
            "v1:MDEyMzQ1Njc4OWFiY2RlZg==:vUD/8HwIkQDMfmXuaKWdRSn5k8A+uWTdsMV2OxuIzHg="
        assertTrue(PinHasher.ensureHashed(legacyV1) == legacyV1)
    }

    @Test
    fun `isHashed distingue texto plano de formatos versionados`() {
        assertFalse(PinHasher.isHashed("123456"))
        assertTrue(PinHasher.isHashed(PinHasher.hash("123456")))
        assertTrue(
            PinHasher.isHashed(
                "v1:MDEyMzQ1Njc4OWFiY2RlZg==:vUD/8HwIkQDMfmXuaKWdRSn5k8A+uWTdsMV2OxuIzHg=",
            ),
        )
    }

    @Test
    fun `verify rechaza hashes corruptos sin lanzar excepcion`() {
        assertFalse(PinHasher.verify("123456", "v1:no-es-base64"))
        assertFalse(PinHasher.verify("123456", "v1:::"))
        assertFalse(PinHasher.verify("123456", "v2:600000:no-es-base64:tampoco"))
        assertFalse(PinHasher.verify("123456", "v2:999999999:AA==:AA=="))
    }
}
