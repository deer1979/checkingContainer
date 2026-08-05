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

    @Query("SELECT * FROM estimados WHERE containerNo = :containerNo ORDER BY createdAt DESC")
    suspend fun findByContainerNo(containerNo: String): List<EstimadoEntity>

    @Query("SELECT * FROM estimados WHERE status = 'ABIERTO' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<EstimadoEntity>>

    @Query("SELECT * FROM estimados WHERE status = 'CERRADO' ORDER BY closedAt DESC")
    fun observeClosed(): Flow<List<EstimadoEntity>>

    @Query("SELECT COUNT(*) FROM estimados WHERE status = 'ABIERTO'")
    fun countOpen(): Flow<Int>

    @Query("SELECT id FROM estimados WHERE status = 'ABIERTO'")
    suspend fun findOpenIds(): List<Long>

    @Query("SELECT * FROM estimados ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EstimadoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EstimadoEntity): Long

    /** Estimados que se quedaron sin número de contenedor (dato corrupto). */
    @Query("SELECT * FROM estimados WHERE containerNo = ''")
    suspend fun findSinContenedor(): List<EstimadoEntity>

    /** Guardados en el teléfono cuya subida la nube nunca confirmó. */
    @Query("SELECT * FROM estimados WHERE subidoEn IS NULL OR subidoEn < updatedAt")
    suspend fun findPendientesDeSubir(): List<EstimadoEntity>

    @Query("SELECT COUNT(*) FROM estimados WHERE subidoEn IS NULL OR subidoEn < updatedAt")
    fun observeCountPendientes(): Flow<Int>

    @Query("DELETE FROM estimados WHERE id = :id")
    suspend fun deleteById(id: Long)
}
