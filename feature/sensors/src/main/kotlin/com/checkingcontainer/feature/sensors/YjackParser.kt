package com.checkingcontainer.feature.sensors

/**
 * Decodifica el Service Data del advertising BLE de los sensores Yellow Jacket
 * (TITANMAX, P51, pinzas, vacuómetro, amperímetro). El protocolo se obtuvo por
 * interoperabilidad; las fórmulas están documentadas en PROTOCOLO_YJACK_BLE.md.
 *
 * Estructura del byte[] (little-endian):
 *  - [0]  batería (nibble alto*10) / link (nibble bajo*10)
 *  - [1]  incremento de lectura
 *  - [2..5] int32 serial
 *  - [6..] lecturas según el tipo de sensor (identificado por el UUID del advertising)
 */
object YjackParser {

    // Service Data UUIDs (minúsculas) que identifican cada sensor en el advertising.
    const val UUID_TITAN_PRESION = "8b2a2afb-e67d-44b8-9985-7c164352e411"
    const val UUID_TITAN_TEMP = "0117cb56-3ee8-41b8-9482-1ddc7d6f5fb8"
    const val UUID_P51_PRESION = "85310c03-1608-4167-9f21-e81a42334674"
    const val UUID_P51_TEMP = "1e368999-7899-4f6f-9725-0c9ccba9e3db"
    const val UUID_VACIO = "b94aeb61-aa02-4a8e-a6a3-39bf61b1c9f6"
    const val UUID_CORRIENTE = "3d34ca72-2165-4e40-ad6b-e6608ca5080e"
    const val UUID_PINZA_TEMP = "0460657c-7e40-45d5-8ad3-279728e3c88e"

    /** UUIDs que nos interesan escanear. */
    val UUIDS_CONOCIDOS = listOf(
        UUID_TITAN_PRESION, UUID_TITAN_TEMP, UUID_P51_PRESION, UUID_P51_TEMP,
        UUID_VACIO, UUID_CORRIENTE, UUID_PINZA_TEMP,
    )

    private const val NO_DATO_RAW = 32767

    fun tipoDe(uuid: String): SensorType = when (uuid.lowercase()) {
        UUID_TITAN_PRESION, UUID_P51_PRESION -> SensorType.PRESION
        UUID_TITAN_TEMP, UUID_P51_TEMP, UUID_PINZA_TEMP -> SensorType.TEMPERATURA
        UUID_VACIO -> SensorType.VACIO
        UUID_CORRIENTE -> SensorType.CORRIENTE
        else -> SensorType.DESCONOCIDO
    }

    /**
     * Parsea el service data de un sensor. Devuelve null si el buffer es
     * demasiado corto o el tipo es desconocido.
     */
    fun parse(uuid: String, data: ByteArray, deviceName: String, rssi: Int = 0): SensorReading? {
        val tipo = tipoDe(uuid)
        if (tipo == SensorType.DESCONOCIDO || data.size < 6) return null

        val bateria = (data[0].toInt() and 0xFF) / 16 * 10
        var i = 6 // saltamos batería(1) + incremento(1) + serial(4)

        fun int16(): Int {
            val v = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
            i += 2
            return v.toShort().toInt() // con signo
        }
        fun escala10(raw: Int): Double =
            if (raw == NO_DATO_RAW) SensorReading.SIN_DATO else raw / 10.0
        fun sfloat(): Double {
            val b0 = data[i].toInt() and 0xFF
            val b1 = data[i + 1].toInt() and 0xFF
            i += 2
            return decodeSFloat(b0, b1)
        }

        return when (tipo) {
            SensorType.PRESION, SensorType.VACIO -> {
                if (data.size < i + 6) return null
                SensorReading(deviceName, tipo, escala10(int16()), escala10(int16()), sfloat(), bateria, rssi)
            }
            SensorType.CORRIENTE -> {
                if (data.size < i + 6) return null
                val s1 = escala10(int16())
                val s2 = escala10(int16())
                val s3 = int16() * 1000.0
                SensorReading(deviceName, tipo, s1, s2, s3, bateria, rssi)
            }
            SensorType.TEMPERATURA -> {
                if (data.size < i + 4) return null
                // Pinza/YJPressure descartan el primer short; TITAN temp lo usa.
                val esPinza = uuid.equals(UUID_PINZA_TEMP, ignoreCase = true)
                if (esPinza) {
                    int16() // descartado
                    SensorReading(deviceName, tipo, escala10(int16()), SensorReading.SIN_DATO, bateria = bateria, rssi = rssi)
                } else {
                    SensorReading(deviceName, tipo, escala10(int16()), escala10(int16()), bateria = bateria, rssi = rssi)
                }
            }
            SensorType.DESCONOCIDO -> null
        }
    }

    /**
     * SFLOAT IEEE-11073 de 16 bits: mantisa de 12 bits con signo × 10^exponente
     * (4 bits con signo). Valores especiales → SIN_DATO.
     */
    fun decodeSFloat(b0: Int, b1: Int): Double {
        // NaN / ±Inf reservados
        if (b1 == 0x07 && (b0 == 0xFE || b0 == 0xFF)) return SensorReading.SIN_DATO
        if (b1 == 0x08 && (b0 == 0x00 || b0 == 0x01 || b0 == 0x02)) return SensorReading.SIN_DATO
        val mantissa = signed(b0 or ((b1 and 0x0F) shl 8), 12)
        val exponent = signed(b1 shr 4, 4)
        return mantissa * Math.pow(10.0, exponent.toDouble())
    }

    /** Interpreta [value] (de [bits] bits) como entero con signo en complemento a dos. */
    private fun signed(value: Int, bits: Int): Int {
        val signBit = 1 shl (bits - 1)
        return if (value and signBit != 0) value - (1 shl bits) else value
    }
}
