package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.CatalogEntry

@Entity(tableName = "catalog_entries")
data class CatalogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val manufacturerId: Long,
    val modelFamily: String,
    val description: String,
    val serialRangeStart: Long?,
    val serialRangeEnd: Long?,
) {
    fun toDomain() = CatalogEntry(
        id = id,
        manufacturerId = manufacturerId,
        modelFamily = modelFamily,
        description = description,
        serialRangeStart = serialRangeStart,
        serialRangeEnd = serialRangeEnd,
    )
}

fun CatalogEntry.toEntity() = CatalogEntryEntity(
    id = id,
    manufacturerId = manufacturerId,
    modelFamily = modelFamily,
    description = description,
    serialRangeStart = serialRangeStart,
    serialRangeEnd = serialRangeEnd,
)
