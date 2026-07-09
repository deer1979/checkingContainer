package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.core.model.MedicionSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EstimadosRepositoryImpl @Inject constructor(
    private val dao: EstimadoDao,
    private val firestoreService: FirestoreService,
    private val storageService: StorageService,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EstimadosRepository {

    override suspend fun addMedicion(containerNo: String, medicion: MedicionSnapshot): Boolean =
        withContext(ioDispatcher) {
            val abierto = dao.findByContainerNo(containerNo)
                .firstOrNull { it.status == EstimadoStatus.ABIERTO }
                ?.toDomain()
                ?: return@withContext false
            save(abierto.copy(mediciones = abierto.mediciones + medicion))
            true
        }

    override suspend fun save(estimado: Estimado): Long = withContext(ioDispatcher) {
        val entity = estimado.toEntity()
        val savedId = dao.upsert(entity)
        val persisted = entity.copy(id = if (estimado.id == 0L) savedId else estimado.id)
        firestoreService.upsertEstimado(persisted)
        savedId
    }

    override fun observeByInspectionId(inspectionId: Long): Flow<Estimado?> =
        dao.observeByInspectionId(inspectionId).map { it?.toDomain() }

    override suspend fun findByInspectionId(inspectionId: Long): Estimado? =
        withContext(ioDispatcher) { dao.findByInspectionId(inspectionId)?.toDomain() }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        dao.deleteById(id)
        firestoreService.deleteEstimado(id)
    }

    override fun observeOpen(): Flow<List<Estimado>> =
        dao.observeOpen().map { list -> list.map { it.toDomain() } }

    override fun observeClosed(): Flow<List<Estimado>> =
        dao.observeClosed().map { list -> list.map { it.toDomain() } }

    override fun countOpen(): Flow<Int> = dao.countOpen()

    override suspend fun uploadItemPhoto(
        inspectionId: Long,
        itemId: String,
        isDano: Boolean,
        bytes: ByteArray,
    ): String = withContext(ioDispatcher) {
        val phase = if (isDano) "dano" else "reparacion"
        // Sufijo único: cada ítem admite varias fotos por fase; un nombre fijo
        // sobrescribiría la anterior.
        val unico = java.util.UUID.randomUUID().toString().take(8)
        storageService.uploadToPath("estimados/$inspectionId/items/$itemId/$phase-$unico.jpg", bytes)
    }

    override suspend fun deletePhoto(url: String): Unit = withContext(ioDispatcher) {
        storageService.deleteByUrl(url)
    }

    override suspend fun uploadPdf(inspectionId: Long, bytes: ByteArray): String =
        withContext(ioDispatcher) {
            storageService.uploadToPath(
                "estimados/$inspectionId/reporte.pdf",
                bytes,
                contentType = "application/pdf",
            )
        }

    override suspend fun searchByContainerNo(containerNo: String): List<Estimado> =
        withContext(ioDispatcher) {
            val local = dao.findByContainerNo(containerNo)
            if (local.isNotEmpty()) return@withContext local.map { it.toDomain() }
            val remote = firestoreService.fetchEstimadosByContainerNo(containerNo)
            remote.forEach { dao.upsert(it) }
            remote.map { it.toDomain() }
        }
}
