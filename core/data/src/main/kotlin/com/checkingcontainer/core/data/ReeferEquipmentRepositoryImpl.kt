package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import com.checkingcontainer.core.model.ReeferEquipment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReeferEquipmentRepositoryImpl @Inject constructor(
    private val dao: ReeferUnitDao,
    private val firestoreService: FirestoreService,
    private val storageService: StorageService,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ReeferEquipmentRepository {

    override suspend fun findByContainerNo(containerNo: String): ReeferEquipment? =
        withContext(ioDispatcher) { dao.findByContainerNo(containerNo)?.toDomain() }

    override suspend fun fetchFromFirestore(containerNo: String): ReeferEquipment? =
        withContext(ioDispatcher) {
            firestoreService.fetchEquipment(containerNo)?.also { equipment ->
                dao.insertIfAbsent(equipment.toEntity())
            }
        }

    override suspend fun upsert(equipment: ReeferEquipment): Unit = withContext(ioDispatcher) {
        val entity = equipment.toEntity()
        dao.upsert(entity)
        firestoreService.upsertEquipment(entity)
    }

    override suspend fun uploadFotoPlaca(bytes: ByteArray): String =
        withContext(ioDispatcher) {
            storageService.uploadToPath(
                "placas/${java.util.UUID.randomUUID()}.jpg",
                bytes,
            )
        }
}
