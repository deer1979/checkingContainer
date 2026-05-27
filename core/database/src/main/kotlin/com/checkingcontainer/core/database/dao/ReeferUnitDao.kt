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

    @Query("SELECT * FROM reefer_units WHERE containerNo = :containerNo ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestByContainerNo(containerNo: String): ReeferUnitEntity?

    @Query("SELECT * FROM reefer_units WHERE containerNo = :containerNo ORDER BY createdAt DESC")
    suspend fun getAllByContainerNo(containerNo: String): List<ReeferUnitEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(unit: ReeferUnitEntity): Long

    @Update
    suspend fun update(unit: ReeferUnitEntity)

    @Query("DELETE FROM reefer_units WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM reefer_units WHERE syncPending = 1")
    suspend fun getPending(): List<ReeferUnitEntity>

    @Query("UPDATE reefer_units SET syncPending = 0 WHERE id = :id")
    suspend fun markSynced(id: Long)
}
