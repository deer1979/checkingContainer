package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.ReeferEquipment

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
)
