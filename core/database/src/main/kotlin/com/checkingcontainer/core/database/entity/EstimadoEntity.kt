package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.DamageItemStatus
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.core.model.MedicionSnapshot
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "estimados")
data class EstimadoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inspectionId: Long,
    val containerNo: String,
    val manufacturer: String = "",
    val unitModel: String = "",
    val unitModelNo: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val unitType: String = "",
    val clientName: String = "",
    val clientId: Long? = null,
    val clientIdNumber: String = "",
    val clientDireccion: String = "",
    val clientTelefono: String = "",
    val clientEmail: String = "",
    val sitioClienteId: Long? = null,
    val sitioNombre: String = "",
    val ordenTrabajo: String = "",
    val location: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val closedAt: Long? = null,
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    val damages: String = "[]",
    val mediciones: String = "[]",
    val hasIva: Int = 0,
    val reportUrl: String? = null,
) {
    fun toDomain(): Estimado = Estimado(
        id = id,
        inspectionId = inspectionId,
        containerNo = containerNo,
        manufacturer = manufacturer,
        unitModel = unitModel,
        unitModelNo = unitModelNo,
        unitSerialNo = unitSerialNo,
        yearOfBuilt = yearOfBuilt,
        unitType = unitType,
        clientName = clientName,
        clientId = clientId,
        clientIdNumber = clientIdNumber,
        clientDireccion = clientDireccion,
        clientTelefono = clientTelefono,
        clientEmail = clientEmail,
        sitioClienteId = sitioClienteId,
        sitioNombre = sitioNombre,
        ordenTrabajo = ordenTrabajo,
        location = location,
        technicianId = technicianId,
        technicianName = technicianName,
        createdAt = createdAt,
        approvedAt = approvedAt,
        closedAt = closedAt,
        status = status,
        damages = parseDamages(damages),
        mediciones = parseMediciones(mediciones),
        hasIva = hasIva != 0,
        reportUrl = reportUrl,
    )
}

private fun parseDamages(json: String): List<DamageItem> = buildList {
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return@buildList
    repeat(arr.length()) { i ->
        val obj = arr.getJSONObject(i)
        add(
            DamageItem(
                id = obj.optString("id"),
                damageDescription = obj.optString("damageDescription"),
                // Lee la lista nueva (damagePhotos) y, si no existe, migra desde el
                // campo único legado (damagePhoto). Igual para reparación.
                damagePhotos = parsePhotos(obj, "damagePhotos", "damagePhoto"),
                repairAction = obj.optString("repairAction"),
                repairPhotos = parsePhotos(obj, "repairPhotos", "repairPhoto"),
                status = runCatching { DamageItemStatus.valueOf(obj.optString("status")) }
                    .getOrDefault(DamageItemStatus.PENDIENTE),
                laborCost = if (obj.has("laborCost") && !obj.isNull("laborCost")) obj.getDouble("laborCost") else null,
                materialCost = if (obj.has("materialCost") && !obj.isNull("materialCost")) obj.getDouble("materialCost") else null,
            ),
        )
    }
}

/**
 * Lee las fotos de un ítem aceptando ambos formatos: el array nuevo [arrayKey]
 * y, como respaldo, el campo único legado [legacyKey] (estimados creados antes
 * de soportar varias fotos por ítem).
 */
private fun parsePhotos(obj: JSONObject, arrayKey: String, legacyKey: String): List<String> {
    obj.optJSONArray(arrayKey)?.let { arr ->
        return buildList {
            repeat(arr.length()) { i -> arr.optString(i).takeIf { it.isNotEmpty() }?.let { add(it) } }
        }
    }
    return obj.optString(legacyKey).takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
}

private fun List<DamageItem>.toJson(): String {
    val arr = JSONArray()
    forEach { item ->
        arr.put(
            JSONObject().apply {
                put("id", item.id)
                put("damageDescription", item.damageDescription)
                put("damagePhotos", JSONArray(item.damagePhotos))
                put("repairAction", item.repairAction)
                put("repairPhotos", JSONArray(item.repairPhotos))
                put("status", item.status.name)
                if (item.laborCost != null) put("laborCost", item.laborCost) else put("laborCost", JSONObject.NULL)
                if (item.materialCost != null) put("materialCost", item.materialCost) else put("materialCost", JSONObject.NULL)
            },
        )
    }
    return arr.toString()
}

fun Estimado.toEntity(): EstimadoEntity = EstimadoEntity(
    id = id,
    inspectionId = inspectionId,
    containerNo = containerNo,
    manufacturer = manufacturer,
    unitModel = unitModel,
    unitModelNo = unitModelNo,
    unitSerialNo = unitSerialNo,
    yearOfBuilt = yearOfBuilt,
    unitType = unitType,
    clientName = clientName,
    clientId = clientId,
    clientIdNumber = clientIdNumber,
    clientDireccion = clientDireccion,
    clientTelefono = clientTelefono,
    clientEmail = clientEmail,
    sitioClienteId = sitioClienteId,
    sitioNombre = sitioNombre,
    ordenTrabajo = ordenTrabajo,
    location = location,
    technicianId = technicianId,
    technicianName = technicianName,
    createdAt = createdAt,
    approvedAt = approvedAt,
    closedAt = closedAt,
    status = status,
    damages = damages.toJson(),
    mediciones = mediciones.medicionesToJson(),
    hasIva = if (hasIva) 1 else 0,
    reportUrl = reportUrl,
)

private fun parseMediciones(json: String): List<MedicionSnapshot> = buildList {
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return@buildList
    repeat(arr.length()) { i ->
        val obj = arr.getJSONObject(i)
        add(
            MedicionSnapshot(
                timestamp = obj.optLong("timestamp"),
                refrigerante = obj.optString("refrigerante"),
                presionAltaPsig = obj.optDoubleOrNull("presionAltaPsig"),
                presionBajaPsig = obj.optDoubleOrNull("presionBajaPsig"),
                satLiquidoC = obj.optDoubleOrNull("satLiquidoC"),
                satVaporC = obj.optDoubleOrNull("satVaporC"),
                superheatC = obj.optDoubleOrNull("superheatC"),
                subcoolingC = obj.optDoubleOrNull("subcoolingC"),
                tempSuccionC = obj.optDoubleOrNull("tempSuccionC"),
                tempDescargaC = obj.optDoubleOrNull("tempDescargaC"),
                corrienteA = obj.optDoubleOrNull("corrienteA"),
                dispositivos = obj.optJSONArray("dispositivos")?.let { d ->
                    buildList { repeat(d.length()) { j -> d.optString(j).takeIf { it.isNotEmpty() }?.let { add(it) } } }
                } ?: emptyList(),
            ),
        )
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

private fun List<MedicionSnapshot>.medicionesToJson(): String {
    val arr = JSONArray()
    forEach { m ->
        arr.put(
            JSONObject().apply {
                put("timestamp", m.timestamp)
                put("refrigerante", m.refrigerante)
                put("presionAltaPsig", m.presionAltaPsig ?: JSONObject.NULL)
                put("presionBajaPsig", m.presionBajaPsig ?: JSONObject.NULL)
                put("satLiquidoC", m.satLiquidoC ?: JSONObject.NULL)
                put("satVaporC", m.satVaporC ?: JSONObject.NULL)
                put("superheatC", m.superheatC ?: JSONObject.NULL)
                put("subcoolingC", m.subcoolingC ?: JSONObject.NULL)
                put("tempSuccionC", m.tempSuccionC ?: JSONObject.NULL)
                put("tempDescargaC", m.tempDescargaC ?: JSONObject.NULL)
                put("corrienteA", m.corrienteA ?: JSONObject.NULL)
                put("dispositivos", JSONArray(m.dispositivos))
            },
        )
    }
    return arr.toString()
}
