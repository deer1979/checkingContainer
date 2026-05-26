package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Manufacturer

@Entity(tableName = "manufacturers")
data class ManufacturerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val modelPrefixes: String,
) {
    fun toDomain() = Manufacturer(
        id = id,
        name = name,
        modelPrefixes = modelPrefixes.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    )
}

fun Manufacturer.toEntity() = ManufacturerEntity(
    id = id,
    name = name,
    modelPrefixes = modelPrefixes.joinToString(","),
)
