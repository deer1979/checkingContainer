package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.Estimado
import kotlinx.coroutines.flow.Flow

interface EstimadosRepository {
    suspend fun save(estimado: Estimado): Long
    fun observeByInspectionId(inspectionId: Long): Flow<Estimado?>
    suspend fun findByInspectionId(inspectionId: Long): Estimado?
    suspend fun delete(id: Long)
    fun observeOpen(): Flow<List<Estimado>>
    fun observeClosed(): Flow<List<Estimado>>
    fun countOpen(): Flow<Int>
    suspend fun uploadItemPhoto(inspectionId: Long, itemId: String, isDano: Boolean, bytes: ByteArray): String
    suspend fun deletePhoto(url: String)
    suspend fun uploadPdf(inspectionId: Long, bytes: ByteArray): String
    suspend fun searchByContainerNo(containerNo: String): List<Estimado>
}
