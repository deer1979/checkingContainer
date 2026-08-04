package com.checkingcontainer.feature.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorsUiStateTest {

    @Test
    fun `asigna alta al primer canal y baja al segundo por defecto`() {
        val state = stateWithPressure(valor1 = 150.0, valor2 = 30.0)

        assertEquals(150.0, state.presionAltaRaw, 0.0)
        assertEquals(30.0, state.presionBajaRaw, 0.0)
    }

    @Test
    fun `respeta roles invertidos configurados por el tecnico`() {
        val deviceName = "TITAN-TEST"
        val state = stateWithPressure(
            deviceName = deviceName,
            valor1 = 150.0,
            valor2 = 30.0,
            roles = mapOf(
                claveRol(deviceName, SensorType.PRESION, 0) to RolMedicion.BAJA,
                claveRol(deviceName, SensorType.PRESION, 1) to RolMedicion.ALTA,
            ),
        )

        assertEquals(30.0, state.presionAltaRaw, 0.0)
        assertEquals(150.0, state.presionBajaRaw, 0.0)
    }

    @Test
    fun `sin tarjetas no reporta datos ni presion fuera de rango`() {
        val state = SensorsUiState()

        assertFalse(state.hayDatos)
        assertFalse(state.presionFueraDeRango)
        assertEquals(SensorReading.SIN_DATO, state.presionAltaRaw, 0.0)
        assertEquals(SensorReading.SIN_DATO, state.presionBajaRaw, 0.0)
    }

    @Test
    fun `clave de rol separa dispositivo tipo y canal`() {
        val first = claveRol("DEVICE", SensorType.PRESION, 0)
        val second = claveRol("DEVICE", SensorType.PRESION, 1)
        val temperature = claveRol("DEVICE", SensorType.TEMPERATURA, 0)

        assertTrue(first != second)
        assertTrue(first != temperature)
        assertEquals("DEVICE#PRESION#0", first)
    }

    private fun stateWithPressure(
        deviceName: String = "TITAN-TEST",
        valor1: Double,
        valor2: Double,
        roles: Map<String, RolMedicion> = emptyMap(),
    ): SensorsUiState {
        val reading = SensorReading(
            deviceName = deviceName,
            type = SensorType.PRESION,
            valor1 = valor1,
            valor2 = valor2,
        )
        val key = "$deviceName-${SensorType.PRESION}"
        return SensorsUiState(
            tarjetas = mapOf(key to TarjetaSensor(deviceName, reading)),
            roles = roles,
        )
    }
}
