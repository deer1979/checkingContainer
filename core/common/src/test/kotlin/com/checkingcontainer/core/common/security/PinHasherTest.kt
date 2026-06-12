package com.checkingcontainer.core.common.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `hash nunca contiene el PIN en claro`() {
        val hash = PinHasher.hash("123456")
        assertTrue(hash.startsWith("v1:"))
        assertFalse(hash.contains("123456"))
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
        val h1 = PinHasher.hash("123456")
        val h2 = PinHasher.hash("123456")
        assertNotEquals(h1, h2)
        assertTrue(PinHasher.verify("123456", h1))
        assertTrue(PinHasher.verify("123456", h2))
    }

    @Test
    fun `verify contra valor legado en texto plano`() {
        assertTrue(PinHasher.verify("123456", "123456"))
        assertFalse(PinHasher.verify("123456", "000000"))
    }

    @Test
    fun `ensureHashed es idempotente`() {
        val hash = PinHasher.ensureHashed("123456")
        assertTrue(PinHasher.isHashed(hash))
        // Volver a pasar el hash no lo re-hashea
        assertTrue(PinHasher.ensureHashed(hash) === hash || PinHasher.ensureHashed(hash) == hash)
        assertTrue(PinHasher.verify("123456", PinHasher.ensureHashed(hash)))
    }

    @Test
    fun `isHashed distingue legado de hasheado`() {
        assertFalse(PinHasher.isHashed("123456"))
        assertTrue(PinHasher.isHashed(PinHasher.hash("123456")))
    }

    @Test
    fun `verify rechaza hashes corruptos sin lanzar excepcion`() {
        assertFalse(PinHasher.verify("123456", "v1:no-es-base64"))
        assertFalse(PinHasher.verify("123456", "v1:::"))
    }
}
