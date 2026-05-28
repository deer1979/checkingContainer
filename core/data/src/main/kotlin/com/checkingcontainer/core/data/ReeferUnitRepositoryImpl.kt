package com.checkingcontainer.core.data

import android.content.Context
import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.data.sync.RemoteSyncWorker
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.ReeferUnitRepository
import com.checkingcontainer.core.model.ReeferUnit
import com.checkingcontainer.core.network.RemoteDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio de unidades reefer.
 *
 * ## Sincronización
 * Cada escritura (create / update) guarda la entidad en Room con `syncPending = true`
 * (valor por defecto en [ReeferUnitEntity]) y luego encola [RemoteSyncWorker] para que
 * suba la fila a Google Sheets tan pronto como haya conexión.
 *
 * El Worker recoge **todas** las filas con `syncPending = 1` → permite que varias
 * escrituras offline se suban en bloque cuando vuelve la red.
 */
@Singleton
class ReeferUnitRepositoryImpl @Inject constructor(
    private val dao: ReeferUnitDao,
    @ApplicationContext private val context: Context,
    private val remoteDataSource: RemoteDataSource,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ReeferUnitRepository {

    private val syncScope = CoroutineScope(ioDispatcher + SupervisorJob())

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

    /**
     * Inserta [unit] en Room (syncPending = true por defecto en la entidad)
     * y encola [RemoteSyncWorker] para subirla a Sheets de inmediato.
     */
    override suspend fun create(unit: ReeferUnit): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val id = dao.insert(unit.toEntity())   // syncPending = 1 (default)
            RemoteSyncWorker.enqueueAfterWrite(context)
            id
        }
    }

    /**
     * Actualiza [unit] en Room (la entidad generada por [toEntity] tiene syncPending = true)
     * y encola [RemoteSyncWorker] para reflejar el cambio en Sheets.
     */
    override suspend fun update(unit: ReeferUnit): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            dao.update(unit.toEntity())             // syncPending = 1 (default)
            RemoteSyncWorker.enqueueAfterWrite(context)
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        dao.delete(id)
        syncScope.launch {
            remoteDataSource.deleteRow("reefer_units", id.toString())
                .onFailure { Log.w("ReeferUnitRepo", "deleteRow Sheets falló", it) }
        }
        Unit
    }
}
