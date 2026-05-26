package com.checkingcontainer.core.model

data class ReeferUnit(
    val id: Long = 0,
    val containerNo: String,
    val manufacturer: String = "",
    val unitModel: String,
    val unitModelNo: String = "",
    val unitSerialNo: String,
    val yearOfBuilt: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: InspStatus = InspStatus.INSP,
    val ptiInstruction: PtiInstruction? = null,
    val unitType: UnitType = UnitType.CARRIER,
    val deployedAs: String? = null,
    val technicianId: Long = 0,
    val technicianName: String = "",
    val observations: String = "",
)
