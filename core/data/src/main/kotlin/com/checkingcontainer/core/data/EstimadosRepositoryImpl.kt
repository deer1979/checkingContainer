package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoFase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
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
        val persistedEntity = entity.copy(id = if (estimado.id == 0L) savedId else estimado.id)
        firestoreService.upsertEstimado(persistedEntity)
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

    override suspend fun uploadPhoto(inspectionId: Long, fase: EstimadoFase, bytes: ByteArray): String =
        withContext(ioDispatcher) {
            val folder = if (fase == EstimadoFase.DANO) "dano" else "reparacion"
            val fileName = "${UUID.randomUUID()}.jpg"
            storageService.uploadToPath("estimados/$inspectionId/$folder/$fileName", bytes)
        }

    override suspend fun deletePhoto(url: String): Unit = withContext(ioDispatcher) {
        storageService.deleteByUrl(url)
    }
}
