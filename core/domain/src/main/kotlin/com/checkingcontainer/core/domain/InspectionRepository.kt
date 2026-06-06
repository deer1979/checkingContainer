package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.Inspection
import com.checkingcontainer.core.model.InspectionWithEquipment
import kotlinx.coroutines.flow.Flow

interface InspectionRepository {
    /** Stream de las últimas 24h con datos de equipo para la pantalla de lista. */
    fun observeLast24h(): Flow<List<InspectionWithEquipment>>

    suspend fun findById(id: Long): Inspection?

    /** Las 2 inspecciones más recientes para el timeline por defecto (2 lecturas Room). */
    suspend fun getLatest2ByContainerNo(containerNo: String): List<Inspection>

    /** Total de inspecciones del contenedor para el badge de "N más". */
    suspend fun countByContainerNo(containerNo: String): Int

    /** Historial completo — solo se llama tras confirmación del usuario. */
    suspend fun getAllByContainerNo(containerNo: String): List<Inspection>

    /** Inspección de hoy del contenedor, para evitar duplicados en UnitEntry. */
    suspend fun findTodayByContainerNo(containerNo: String): Inspection?

    suspend fun create(inspection: Inspection): Result<Long>
    suspend fun update(inspection: Inspection): Result<Unit>
    suspend fun delete(id: Long)
}
