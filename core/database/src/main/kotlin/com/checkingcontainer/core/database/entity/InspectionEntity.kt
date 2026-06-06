package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Inspection
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction

@Entity(
    tableName = "inspections",
    foreignKeys = [
        ForeignKey(
            entity = ReeferUnitEntity::class,
            parentColumns = ["containerNo"],
            childColumns = ["containerNo"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("containerNo")],
)
data class InspectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val containerNo: String,
    val createdAt: Long,
    val status: InspStatus = InspStatus.INSP,
    val ptiInstruction: PtiInstruction? = null,
    val deployedAs: String? = null,
    val technicianId: Long = 0,
    val technicianName: String = "",
    val location: String = "",
    val observations: String = "",
    val idDigitador: String? = null,
    val timestampDigitador: Long? = null,
    val statusDigitacion: String? = null,
    val noteDigitacion: String? = null,
    val avisoDigitacion: String? = null,
    val diasPendiente: Int? = null,
) {
    fun toDomain(): Inspection = Inspection(
        id = id,
        containerNo = containerNo,
        createdAt = createdAt,
        status = status,
        ptiInstruction = ptiInstruction,
        deployedAs = deployedAs,
        technicianId = technicianId,
        technicianName = technicianName,
        location = location,
        observations = observations,
        idDigitador = idDigitador,
        timestampDigitador = timestampDigitador,
        statusDigitacion = statusDigitacion,
        noteDigitacion = noteDigitacion,
        avisoDigitacion = avisoDigitacion,
        diasPendiente = diasPendiente,
    )
}

fun Inspection.toEntity(): InspectionEntity = InspectionEntity(
    id = id,
    containerNo = containerNo,
    createdAt = createdAt,
    status = status,
    ptiInstruction = ptiInstruction,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    location = location,
    observations = observations,
    idDigitador = idDigitador,
    timestampDigitador = timestampDigitador,
    statusDigitacion = statusDigitacion,
    noteDigitacion = noteDigitacion,
    avisoDigitacion = avisoDigitacion,
    diasPendiente = diasPendiente,
)
