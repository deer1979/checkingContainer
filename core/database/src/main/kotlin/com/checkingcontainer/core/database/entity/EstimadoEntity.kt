package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.DamageItemStatus
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
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
    val location: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val closedAt: Long? = null,
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    val damages: String = "[]",
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
        location = location,
        technicianId = technicianId,
        technicianName = technicianName,
        createdAt = createdAt,
        approvedAt = approvedAt,
        closedAt = closedAt,
        status = status,
        damages = parseDamages(damages),
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
                damagePhoto = obj.optString("damagePhoto").ifEmpty { null },
                repairAction = obj.optString("repairAction"),
                repairPhoto = obj.optString("repairPhoto").ifEmpty { null },
                status = runCatching { DamageItemStatus.valueOf(obj.optString("status")) }
                    .getOrDefault(DamageItemStatus.PENDIENTE),
                laborCost = if (obj.has("laborCost") && !obj.isNull("laborCost")) obj.getDouble("laborCost") else null,
                materialCost = if (obj.has("materialCost") && !obj.isNull("materialCost")) obj.getDouble("materialCost") else null,
            ),
        )
    }
}

private fun List<DamageItem>.toJson(): String {
    val arr = JSONArray()
    forEach { item ->
        arr.put(
            JSONObject().apply {
                put("id", item.id)
                put("damageDescription", item.damageDescription)
                put("damagePhoto", item.damagePhoto ?: "")
                put("repairAction", item.repairAction)
                put("repairPhoto", item.repairPhoto ?: "")
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
    location = location,
    technicianId = technicianId,
    technicianName = technicianName,
    createdAt = createdAt,
    approvedAt = approvedAt,
    closedAt = closedAt,
    status = status,
    damages = damages.toJson(),
    hasIva = if (hasIva) 1 else 0,
    reportUrl = reportUrl,
)
