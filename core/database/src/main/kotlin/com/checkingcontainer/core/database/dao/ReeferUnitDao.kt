package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReeferUnitDao {

    @Query("SELECT * FROM reefer_units ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReeferUnitEntity>>

    @Query("SELECT * FROM reefer_units WHERE createdAt > :since ORDER BY createdAt DESC")
    fun observeLast24h(since: Long): Flow<List<ReeferUnitEntity>>

    @Query("SELECT * FROM reefer_units WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReeferUnitEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(unit: ReeferUnitEntity): Long

    @Update
    suspend fun update(unit: ReeferUnitEntity)

    @Query("DELETE FROM reefer_units WHERE id = :id")
    suspend fun delete(id: Long)
}
