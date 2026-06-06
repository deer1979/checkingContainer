package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.checkingcontainer.core.database.entity.ReeferUnitEntity

@Dao
interface ReeferUnitDao {

    @Query("SELECT * FROM reefer_units WHERE containerNo = :containerNo LIMIT 1")
    suspend fun findByContainerNo(containerNo: String): ReeferUnitEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(unit: ReeferUnitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(unit: ReeferUnitEntity)
}
