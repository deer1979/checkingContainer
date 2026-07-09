package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.ClientDao
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.ClientsRepository
import com.checkingcontainer.core.model.Client
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientsRepositoryImpl @Inject constructor(
    private val dao: ClientDao,
    private val firestoreService: FirestoreService,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ClientsRepository {

    override fun observeActive(): Flow<List<Client>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun search(query: String): List<Client> = withContext(ioDispatcher) {
        dao.search(query).map { it.toDomain() }
    }

    override suspend fun findById(id: Long): Client? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun save(client: Client): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val entity = client.copy(
            createdAt = if (client.id == 0L) now else client.createdAt,
            updatedAt = now,
        ).toEntity()
        val savedId = dao.upsert(entity)
        val persisted = entity.copy(id = if (client.id == 0L) savedId else client.id)
        firestoreService.upsertClient(persisted)
        savedId
    }

    override suspend fun deactivate(id: Long): Unit = withContext(ioDispatcher) {
        val entity = dao.findById(id) ?: return@withContext
        val baja = entity.copy(isActive = 0, updatedAt = System.currentTimeMillis())
        dao.upsert(baja)
        firestoreService.upsertClient(baja)
    }
}
