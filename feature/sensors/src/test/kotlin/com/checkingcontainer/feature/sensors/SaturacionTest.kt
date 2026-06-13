package com.checkingcontainer.feature.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaturacionTest {

    // Tramo real de R-134a (vapSatPressures vs vapSat en °F×10):
    // axis[10]=16.0 → -116 (-11.6 °F) ; axis[11]=17.6 → -77 (-7.7 °F)
    private val axis = listOf(16.0, 17.6, 19.2)
    private val tempsX10 = listOf(-116, -77, -42)

    @Test
    fun `interpola la temp de saturacion y la pasa a Celsius`() {
        // p=16.8 (medio entre 16.0 y 17.6) → satF ≈ -9.65 → satC ≈ -23.14
        val c = Saturacion.satTempC(axis, tempsX10, 16.8)
        assertEquals(-23.14, c, 0.2)
    }

    @Test
    fun `sin dato devuelve sin dato`() {
        assertEquals(SensorReading.SIN_DATO, Saturacion.satTempC(axis, tempsX10, SensorReading.SIN_DATO), 0.001)
    }

    @Test
    fun `presion por debajo del primer punto del eje es invalida`() {
        // 10.0 < axis[0]=16.0 → indexOf 0 → invalido
        assertEquals(SensorReading.SIN_DATO, Saturacion.satTempC(axis, tempsX10, 10.0), 0.001)
    }

    @Test
    fun `centinela -300 marca fuera de rango`() {
        val conCentinela = listOf(-3000, -77, -42)
        assertEquals(SensorReading.SIN_DATO, Saturacion.satTempC(axis, conCentinela, 16.8), 0.001)
    }

    @Test
    fun `superheat resta saturacion de la succion`() {
        // succión 5°C, sat vapor -23.14°C → SH ≈ 28.14
        assertEquals(28.14, Saturacion.superheat(5.0, -23.14), 0.01)
        assertEquals(SensorReading.SIN_DATO, Saturacion.superheat(SensorReading.SIN_DATO, -23.0), 0.001)
    }

    @Test
    fun `subcooling resta liquido de la saturacion`() {
        // sat líquido 40°C, líquido medido 33°C → SC = 7
        assertEquals(7.0, Saturacion.subcooling(40.0, 33.0), 0.01)
        assertEquals(SensorReading.SIN_DATO, Saturacion.subcooling(40.0, SensorReading.SIN_DATO), 0.001)
    }
}
