package com.checkingcontainer.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.checkingcontainer.core.model.InspectionWithEquipment

data class InspectionWithEquipmentRow(
    @Embedded val inspection: InspectionEntity,
    @Relation(
        parentColumn = "containerNo",
        entityColumn = "containerNo",
        entity = ReeferUnitEntity::class,
    )
    val equipmentList: List<ReeferUnitEntity>,
) {
    fun toDomain() = InspectionWithEquipment(
        inspection = inspection.toDomain(),
        equipment = (equipmentList.firstOrNull()
            ?: ReeferUnitEntity(containerNo = inspection.containerNo)).toDomain(),
    )
}
