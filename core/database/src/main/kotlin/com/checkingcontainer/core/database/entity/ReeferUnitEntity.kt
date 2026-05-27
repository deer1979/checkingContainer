package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.ReeferUnit
import java.util.UUID

@Entity(tableName = "reefer_units")
data class ReeferUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val containerNo: String,
    val manufacturer: String,
    val unitModel: String,
    val unitModelNo: String,
    val unitSerialNo: String,
    val yearOfBuilt: String,
    val createdAt: Long,
    val status: InspStatus = InspStatus.INSP,
    val ptiInstruction: PtiInstruction? = null,
    val brand: Brand = Brand.CARRIER,
    val unitType: String = "",
    val deployedAs: String? = null,
    val technicianId: Long = 0,
    val technicianName: String = "",
    val observations: String = "",
    val syncId: String = UUID.randomUUID().toString(),
    val syncPending: Boolean = true,
) {
    fun toDomain(): ReeferUnit = ReeferUnit(
        id = id,
        containerNo = containerNo,
        manufacturer = manufacturer,
        unitModel = unitModel,
        unitModelNo = unitModelNo,
        unitSerialNo = unitSerialNo,
        yearOfBuilt = yearOfBuilt,
        createdAt = createdAt,
        status = status,
        ptiInstruction = ptiInstruction,
        brand = brand,
        unitType = unitType,
        deployedAs = deployedAs,
        technicianId = technicianId,
        technicianName = technicianName,
        observations = observations,
    )
}

fun ReeferUnit.toEntity(): ReeferUnitEntity = ReeferUnitEntity(
    id = id,
    containerNo = containerNo,
    manufacturer = manufacturer,
    unitModel = unitModel,
    unitModelNo = unitModelNo,
    unitSerialNo = unitSerialNo,
    yearOfBuilt = yearOfBuilt,
    createdAt = createdAt,
    status = status,
    ptiInstruction = ptiInstruction,
    brand = brand,
    unitType = unitType,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    observations = observations,
)
