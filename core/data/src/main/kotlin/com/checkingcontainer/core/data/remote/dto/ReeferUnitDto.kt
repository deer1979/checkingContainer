package com.checkingcontainer.core.data.remote.dto

import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReeferUnitDto(
    @SerialName("sync_id") val syncId: String,
    @SerialName("container_no") val containerNo: String,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("unit_model") val unitModel: String,
    @SerialName("unit_model_no") val unitModelNo: String,
    @SerialName("unit_serial_no") val unitSerialNo: String,
    @SerialName("year_of_built") val yearOfBuilt: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("status") val status: String,
    @SerialName("pti_instruction") val ptiInstruction: String?,
    @SerialName("brand") val brand: String,
    @SerialName("unit_type") val unitType: String,
    @SerialName("deployed_as") val deployedAs: String?,
    @SerialName("technician_id") val technicianId: Long,
    @SerialName("technician_name") val technicianName: String,
    @SerialName("observations") val observations: String,
)

fun ReeferUnitEntity.toDto() = ReeferUnitDto(
    syncId = syncId,
    containerNo = containerNo,
    manufacturer = manufacturer,
    unitModel = unitModel,
    unitModelNo = unitModelNo,
    unitSerialNo = unitSerialNo,
    yearOfBuilt = yearOfBuilt,
    createdAt = createdAt,
    status = status.name,
    ptiInstruction = ptiInstruction?.name,
    brand = brand.name,
    unitType = unitType,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    observations = observations,
)
