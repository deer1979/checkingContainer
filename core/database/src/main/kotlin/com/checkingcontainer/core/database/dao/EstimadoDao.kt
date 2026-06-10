package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.checkingcontainer.core.database.entity.EstimadoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EstimadoDao {

    @Query("SELECT * FROM estimados WHERE inspectionId = :inspectionId LIMIT 1")
    fun observeByInspectionId(inspectionId: Long): Flow<EstimadoEntity?>

    @Query("SELECT * FROM estimados WHERE inspectionId = :inspectionId LIMIT 1")
    suspend fun findByInspectionId(inspectionId: Long): EstimadoEntity?

    @Query("SELECT * FROM estimados WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): EstimadoEntity?

    @Query("SELECT * FROM estimados ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EstimadoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EstimadoEntity): Long

    @Query("DELETE FROM estimados WHERE id = :id")
    suspend fun deleteById(id: Long)
}
