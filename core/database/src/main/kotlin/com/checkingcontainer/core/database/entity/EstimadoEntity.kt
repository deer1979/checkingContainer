package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
import org.json.JSONArray

@Entity(tableName = "estimados")
data class EstimadoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inspectionId: Long,
    val containerNo: String,
    val clientName: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val location: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    val damageDescription: String = "",
    val damagePhotos: String = "[]",
    val repairDescription: String = "",
    val repairPhotos: String = "[]",
    val reportUrl: String? = null,
) {
    fun toDomain(): Estimado = Estimado(
        id = id,
        inspectionId = inspectionId,
        containerNo = containerNo,
        clientName = clientName,
        technicianId = technicianId,
        technicianName = technicianName,
        location = location,
        createdAt = createdAt,
        closedAt = closedAt,
        status = status,
        damageDescription = damageDescription,
        damagePhotos = parseJsonArray(damagePhotos),
        repairDescription = repairDescription,
        repairPhotos = parseJsonArray(repairPhotos),
        reportUrl = reportUrl,
    )
}

private fun parseJsonArray(json: String): List<String> = buildList {
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return@buildList
    repeat(arr.length()) { add(arr.getString(it)) }
}

fun Estimado.toEntity(): EstimadoEntity = EstimadoEntity(
    id = id,
    inspectionId = inspectionId,
    containerNo = containerNo,
    clientName = clientName,
    technicianId = technicianId,
    technicianName = technicianName,
    location = location,
    createdAt = createdAt,
    closedAt = closedAt,
    status = status,
    damageDescription = damageDescription,
    damagePhotos = JSONArray(damagePhotos).toString(),
    repairDescription = repairDescription,
    repairPhotos = JSONArray(repairPhotos).toString(),
    reportUrl = reportUrl,
)
