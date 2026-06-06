package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.checkingcontainer.core.database.entity.InspectionEntity
import com.checkingcontainer.core.database.entity.InspectionWithEquipmentRow
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {

    @Query("SELECT * FROM inspections WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): InspectionEntity?

    @Transaction
    @Query("SELECT * FROM inspections WHERE createdAt > :since ORDER BY createdAt DESC")
    fun observeLast24hWithEquipment(since: Long): Flow<List<InspectionWithEquipmentRow>>

    @Query("SELECT * FROM inspections WHERE containerNo = :containerNo ORDER BY createdAt DESC LIMIT 2")
    suspend fun getLatest2ByContainerNo(containerNo: String): List<InspectionEntity>

    @Query("SELECT COUNT(*) FROM inspections WHERE containerNo = :containerNo")
    suspend fun countByContainerNo(containerNo: String): Int

    @Query("SELECT * FROM inspections WHERE containerNo = :containerNo ORDER BY createdAt DESC")
    suspend fun getAllByContainerNo(containerNo: String): List<InspectionEntity>

    @Query("SELECT * FROM inspections WHERE containerNo = :containerNo AND createdAt >= :startOfDay ORDER BY createdAt DESC LIMIT 1")
    suspend fun findTodayByContainerNo(containerNo: String, startOfDay: Long): InspectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(inspection: InspectionEntity): Long

    @Update
    suspend fun update(inspection: InspectionEntity)

    @Query("""
        UPDATE inspections SET
            idDigitador        = :idDigitador,
            timestampDigitador = :timestampDigitador,
            statusDigitacion   = :statusDigitacion,
            noteDigitacion     = :noteDigitacion,
            avisoDigitacion    = :avisoDigitacion,
            diasPendiente      = :diasPendiente
        WHERE id = :id
    """)
    suspend fun updateDigitacion(
        id: Long,
        idDigitador: String?,
        timestampDigitador: Long?,
        statusDigitacion: String?,
        noteDigitacion: String?,
        avisoDigitacion: String?,
        diasPendiente: Int?,
    )

    @Query("DELETE FROM inspections WHERE id = :id")
    suspend fun delete(id: Long)
}
