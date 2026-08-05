package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.MedicionSnapshot
import kotlinx.coroutines.flow.Flow

interface EstimadosRepository {
    suspend fun save(estimado: Estimado): Long
    fun observeByInspectionId(inspectionId: Long): Flow<Estimado?>
    suspend fun findByInspectionId(inspectionId: Long): Estimado?

    /** Lectura local directa, para consultar el estado de sincronización. */
    suspend fun findById(id: Long): Estimado?

    /** Reintenta subir los estimados que quedaron solo en el teléfono. */
    suspend fun subirPendientes(): Int

    /** Cuántos estimados están guardados localmente pero no en la nube. */
    fun observePendientesDeSubir(): Flow<Int>
    suspend fun delete(id: Long)
    fun observeOpen(): Flow<List<Estimado>>
    fun observeClosed(): Flow<List<Estimado>>
    fun countOpen(): Flow<Int>
    suspend fun uploadItemPhoto(inspectionId: Long, itemId: String, isDano: Boolean, bytes: ByteArray): String
    suspend fun deletePhoto(url: String)
    suspend fun uploadPdf(inspectionId: Long, bytes: ByteArray): String
    suspend fun searchByContainerNo(containerNo: String): List<Estimado>

    /**
     * Agrega una medición BLE al estimado ABIERTO de [containerNo].
     * @return true si se guardó; false si no hay estimado abierto para ese contenedor.
     */
    suspend fun addMedicion(containerNo: String, medicion: MedicionSnapshot): Boolean
}
