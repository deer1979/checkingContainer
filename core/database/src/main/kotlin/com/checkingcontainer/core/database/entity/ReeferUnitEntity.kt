package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.CampoFicha
import com.checkingcontainer.core.model.ReeferEquipment
import com.checkingcontainer.core.model.TipoEquipo

@Entity(tableName = "reefer_units")
data class ReeferUnitEntity(
    @PrimaryKey val containerNo: String,
    val manufacturer: String = "",
    val unitModel: String = "",
    val unitModelNo: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val brand: Brand = Brand.CARRIER,
    val unitType: String = "",
    val tipoEquipo: TipoEquipo = TipoEquipo.REEFER,
    val fichaTecnica: String = "[]",
    val fotoPlacaUrl: String? = null,
) {
    fun toDomain(): ReeferEquipment = ReeferEquipment(
        containerNo = containerNo,
        manufacturer = manufacturer,
        unitModel = unitModel,
        unitModelNo = unitModelNo,
        unitSerialNo = unitSerialNo,
        yearOfBuilt = yearOfBuilt,
        brand = brand,
        unitType = unitType,
        tipoEquipo = tipoEquipo,
        fichaTecnica = parseFicha(fichaTecnica),
        fotoPlacaUrl = fotoPlacaUrl,
    )
}

fun ReeferEquipment.toEntity(): ReeferUnitEntity = ReeferUnitEntity(
    containerNo = containerNo,
    manufacturer = manufacturer,
    unitModel = unitModel,
    unitModelNo = unitModelNo,
    unitSerialNo = unitSerialNo,
    yearOfBuilt = yearOfBuilt,
    brand = brand,
    unitType = unitType,
    tipoEquipo = tipoEquipo,
    fichaTecnica = fichaTecnica.fichaToJson(),
    fotoPlacaUrl = fotoPlacaUrl,
)

private fun parseFicha(json: String): List<CampoFicha> = buildList {
    val arr = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return@buildList
    repeat(arr.length()) { i ->
        val obj = arr.optJSONObject(i) ?: return@repeat
        val etiqueta = obj.optString("etiqueta")
        val valor = obj.optString("valor")
        if (etiqueta.isNotEmpty() && valor.isNotEmpty()) add(CampoFicha(etiqueta, valor))
    }
}

private fun List<CampoFicha>.fichaToJson(): String {
    val arr = org.json.JSONArray()
    forEach { c ->
        arr.put(org.json.JSONObject().apply { put("etiqueta", c.etiqueta); put("valor", c.valor) })
    }
    return arr.toString()
}
