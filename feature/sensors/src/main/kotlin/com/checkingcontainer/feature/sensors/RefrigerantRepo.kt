package com.checkingcontainer.feature.sensors

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carga la tabla PT de refrigerantes incrustada (res/raw/refrigerant_data.json,
 * 131 gases extraídos de YJACK VIEW). Expone los nombres y, por gas, las curvas
 * de saturación de vapor/líquido (°F ×10) junto con sus ejes de presión (PSIG).
 */
@Singleton
class RefrigerantRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    data class Gas(val nombre: String, val vapSat: List<Int>, val liqSat: List<Int>)

    var vapSatPressures: List<Double> = emptyList(); private set
    var liqSatPressures: List<Double> = emptyList(); private set
    private val gases = LinkedHashMap<String, Gas>()
    private var cargado = false

    val nombres: List<String> get() { asegurar(); return gases.keys.toList() }
    fun gas(nombre: String): Gas? { asegurar(); return gases[nombre] }

    @Synchronized
    private fun asegurar() {
        if (cargado) return
        cargado = true
        runCatching {
            val texto = context.resources.openRawResource(R.raw.refrigerant_data)
                .bufferedReader().use { it.readText() }
            val root = JSONObject(texto)
            vapSatPressures = root.getJSONArray("vapSatPressures").toDoubles()
            liqSatPressures = root.getJSONArray("liqSatPressures").toDoubles()
            val refs = root.getJSONArray("refrigerants")
            for (i in 0 until refs.length()) {
                val o = refs.getJSONObject(i)
                val nombre = o.getString("name")
                gases[nombre] = Gas(
                    nombre = nombre,
                    vapSat = o.getJSONArray("vapSat").toInts(),
                    liqSat = o.getJSONArray("liqSat").toInts(),
                )
            }
        }
    }

    private fun org.json.JSONArray.toDoubles(): List<Double> =
        List(length()) { getString(it).toDouble() }

    private fun org.json.JSONArray.toInts(): List<Int> =
        List(length()) { getInt(it) }
}
