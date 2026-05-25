package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.ReeferUnit

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
)