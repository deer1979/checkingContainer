package com.checkingcontainer.feature.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YjackParserTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    /** Cabecera: batería 0x90 (=90), incremento, 4 bytes de serial. */
    private val header = byteArrayOf(0x90.toByte(), 0x01, 0x10, 0x20, 0x30, 0x40)

    @Test
    fun `tipo se identifica por UUID`() {
        assertEquals(SensorType.PRESION, YjackParser.tipoDe(YjackParser.UUID_TITAN_PRESION))
        assertEquals(SensorType.TEMPERATURA, YjackParser.tipoDe(YjackParser.UUID_TITAN_TEMP))
        assertEquals(SensorType.VACIO, YjackParser.tipoDe(YjackParser.UUID_VACIO))
        assertEquals(SensorType.CORRIENTE, YjackParser.tipoDe(YjackParser.UUID_CORRIENTE))
        assertEquals(SensorType.DESCONOCIDO, YjackParser.tipoDe("0000-nope"))
    }

    @Test
    fun `presion decodifica los dos int16 entre 10`() {
        // presión1 = 1205 -> 120.5 ; presión2 = 452 -> 45.2 ; sfloat temp
        val data = header + le16(1205) + le16(452) + sfloat(231, 0) // 231*10^0 = 231? ver test sfloat
        val r = YjackParser.parse(YjackParser.UUID_TITAN_PRESION, data, "TITAN-1", -50)!!
        assertEquals(SensorType.PRESION, r.type)
        assertEquals(120.5, r.valor1, 0.001)
        assertEquals(45.2, r.valor2, 0.001)
        assertEquals(90, r.bateria)
        assertEquals(-50, r.rssi)
    }

    @Test
    fun `presion negativa por complemento a dos`() {
        val data = header + le16(0xFFFF) + le16(0) + sfloat(0, 0) // -1 -> -0.1
        val r = YjackParser.parse(YjackParser.UUID_TITAN_PRESION, data, "TITAN", 0)!!
        assertEquals(-0.1, r.valor1, 0.001)
    }

    @Test
    fun `valor 32767 se marca como sin dato`() {
        val data = header + le16(32767) + le16(100) + sfloat(0, 0)
        val r = YjackParser.parse(YjackParser.UUID_TITAN_PRESION, data, "TITAN", 0)!!
        assertTrue(!r.tieneValor1)
        assertEquals(10.0, r.valor2, 0.001)
    }

    @Test
    fun `corriente usa int16 entre 10 y tercer canal por mil`() {
        val data = header + le16(155) + le16(0) + le16(2) // 15.5 A ; 0 ; 2000
        val r = YjackParser.parse(YjackParser.UUID_CORRIENTE, data, "YJAMP", 0)!!
        assertEquals(SensorType.CORRIENTE, r.type)
        assertEquals(15.5, r.valor1, 0.001)
        assertEquals(2000.0, r.valor3, 0.001)
    }

    @Test
    fun `pinza de temperatura descarta primer short y usa el segundo`() {
        val data = header + le16(999) + le16(235) // descarta 999 ; temp 23.5
        val r = YjackParser.parse(YjackParser.UUID_PINZA_TEMP, data, "YJTC", 0)!!
        assertEquals(SensorType.TEMPERATURA, r.type)
        assertEquals(23.5, r.valor1, 0.001)
    }

    @Test
    fun `titan temperatura usa los dos shorts`() {
        val data = header + le16(40) + le16(55) // 4.0 ; 5.5
        val r = YjackParser.parse(YjackParser.UUID_TITAN_TEMP, data, "TITAN", 0)!!
        assertEquals(4.0, r.valor1, 0.001)
        assertEquals(5.5, r.valor2, 0.001)
    }

    @Test
    fun `buffer demasiado corto devuelve null`() {
        assertNull(YjackParser.parse(YjackParser.UUID_TITAN_PRESION, byteArrayOf(1, 2, 3), "x", 0))
    }

    @Test
    fun `sfloat mantisa por exponente`() {
        // mantisa 100, exponente -1 -> 10.0  ; b0=0x64 b1=0xF0 (exp 0xF = -1)
        assertEquals(10.0, YjackParser.decodeSFloat(0x64, 0xF0), 0.001)
        // mantisa 250, exponente 0 -> 250
        assertEquals(250.0, YjackParser.decodeSFloat(0xFA, 0x00), 0.001)
    }

    @Test
    fun `aPsig resta la atmosferica (absoluta a manometrica)`() {
        // 14.3 PSIA a la atmósfera -> ~0 PSIG, como la pantalla del TITANMAX
        assertEquals(-0.4, YjackParser.aPsig(14.3), 0.01)
        assertEquals(105.3, YjackParser.aPsig(120.0), 0.01)
        assertEquals(SensorReading.SIN_DATO, YjackParser.aPsig(SensorReading.SIN_DATO), 0.001)
    }

    @Test
    fun `aCelsius convierte de Fahrenheit`() {
        // 75.2 °F = 24.0 °C (lectura real del equipo)
        assertEquals(24.0, YjackParser.aCelsius(75.2), 0.05)
        assertEquals(0.0, YjackParser.aCelsius(32.0), 0.001)
        assertEquals(SensorReading.SIN_DATO, YjackParser.aCelsius(SensorReading.SIN_DATO), 0.001)
    }

    // Construye un sfloat con mantisa/exponente directos (para los tests de presión/vacío).
    private fun sfloat(mantissa: Int, exponent: Int): ByteArray {
        val b1 = ((exponent and 0x0F) shl 4) or ((mantissa shr 8) and 0x0F)
        val b0 = mantissa and 0xFF
        return byteArrayOf(b0.toByte(), b1.toByte())
    }
}
