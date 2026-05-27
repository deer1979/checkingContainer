package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.CatalogEntry

@Entity(tableName = "catalog_entries")
data class CatalogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val manufacturerId: Long,
    val serie: String,
    val rangeStart: Int,
    val rangeEnd: Int,
    val unitModel: String,
    val unitType: String,
) {
    fun toDomain() = CatalogEntry(
        id = id,
        manufacturerId = manufacturerId,
        serie = serie,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        unitModel = unitModel,
        unitType = unitType,
    )
}

fun CatalogEntry.toEntity() = CatalogEntryEntity(
    id = id,
    manufacturerId = manufacturerId,
    serie = serie,
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
    unitModel = unitModel,
    unitType = unitType,
)
