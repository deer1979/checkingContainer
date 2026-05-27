package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.ReeferUnitRepository
import com.checkingcontainer.core.model.ReeferUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repositorio de unidades reefer.
 *
 * Estado actual: **local-only** — Room es la única fuente de verdad.
 *
 * TODO: Inyectar [com.checkingcontainer.core.network.RemoteDataSource]
 *   (Google Sheets/Drive API) y añadir:
 *   - pull inicial al arrancar la app
 *   - push optimista en create/update/delete
 *   - polling/notificaciones de cambios remotos
 */
@Singleton
class ReeferUnitRepositoryImpl @Inject constructor(
    private val dao: ReeferUnitDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ReeferUnitRepository {

    // ── Reads ─────────────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<ReeferUnit>> =
        dao.observeAll()
            .map { rows -> rows.map(ReeferUnitEntity::toDomain) }
            .flowOn(ioDispatcher)

    override fun observeLast24h(): Flow<List<ReeferUnit>> {
        val since = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        return dao.observeLast24h(since)
            .map { rows -> rows.map(ReeferUnitEntity::toDomain) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getById(id: Long): ReeferUnit? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun getLatestByContainerNo(containerNo: String): ReeferUnit? =
        withContext(ioDispatcher) { dao.getLatestByContainerNo(containerNo)?.toDomain() }

    override suspend fun getAllByContainerNo(containerNo: String): List<ReeferUnit> =
        withContext(ioDispatcher) { dao.getAllByContainerNo(containerNo).map { it.toDomain() } }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun create(unit: ReeferUnit): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val id = dao.insert(unit.toEntity())
            // TODO: pushCreate(unit.copy(id = id)) — Google Sheets API
            id
        }
    }

    override suspend fun update(unit: ReeferUnit): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            dao.update(unit.toEntity())
            // TODO: pushUpdate(unit) — Google Sheets API
            Unit
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        dao.delete(id)
        // TODO: pushDelete(id) — Google Sheets API
    }
}
