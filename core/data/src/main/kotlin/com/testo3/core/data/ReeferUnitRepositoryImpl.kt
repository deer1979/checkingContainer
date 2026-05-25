package com.testo3.core.data

import com.testo3.core.common.di.AppDispatcher
import com.testo3.core.common.di.Dispatcher
import com.testo3.core.database.dao.ReeferUnitDao
import com.testo3.core.database.entity.ReeferUnitEntity
import com.testo3.core.database.entity.toEntity
import com.testo3.core.domain.ReeferUnitRepository
import com.testo3.core.model.ReeferUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class ReeferUnitRepositoryImpl @Inject constructor(
    private val dao: ReeferUnitDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ReeferUnitRepository {

    override fun observeAll(): Flow<List<ReeferUnit>> =
        dao.observeAll()
            .map { rows -> rows.map(ReeferUnitEntity::toDomain) }
            .flowOn(ioDispatcher)

    override suspend fun getById(id: Long): ReeferUnit? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun create(unit: ReeferUnit): Result<Long> = withContext(ioDispatcher) {
        runCatching { dao.insert(unit.toEntity()) }
    }

    override suspend fun update(unit: ReeferUnit): Result<Unit> = withContext(ioDispatcher) {
        runCatching { dao.update(unit.toEntity()) }
    }

    override suspend fun delete(id: Long) = withContext(ioDispatcher) { dao.delete(id) }
}