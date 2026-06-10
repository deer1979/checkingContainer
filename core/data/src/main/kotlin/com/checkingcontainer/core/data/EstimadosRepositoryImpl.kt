package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.Estimado
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
        storageService.uploadToPath("estimados/$inspectionId/items/$itemId/$phase.jpg", bytes)
    }

    override suspend fun deletePhoto(url: String): Unit = withContext(ioDispatcher) {
        storageService.deleteByUrl(url)
    }
}
