package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoFase
import kotlinx.coroutines.flow.Flow

interface EstimadosRepository {
    suspend fun save(estimado: Estimado): Long
    fun observeByInspectionId(inspectionId: Long): Flow<Estimado?>
    suspend fun findByInspectionId(inspectionId: Long): Estimado?
    suspend fun delete(id: Long)
    suspend fun uploadPhoto(inspectionId: Long, fase: EstimadoFase, bytes: ByteArray): String
    suspend fun deletePhoto(url: String)
}
