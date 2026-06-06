package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.entity.InspectionEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.model.Inspection
import com.checkingcontainer.core.model.InspectionWithEquipment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InspectionRepo"

@Singleton
class InspectionRepositoryImpl @Inject constructor(
    private val dao: InspectionDao,
    private val firestoreService: FirestoreService,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : InspectionRepository {

    private val syncScope = CoroutineScope(
        SupervisorJob() + ioDispatcher + CoroutineExceptionHandler { _, t ->
            if (t !is CancellationException) Log.e(TAG, "digitacion sync error: ${t.message}", t)
        },
    )

    init {
        syncScope.launch {
            firestoreService.observeInspectionsDigitacion()
                .catch { e -> Log.w(TAG, "digitacion flow error: ${e.message}") }
                .collect { updates ->
                    updates.forEach { update ->
                        try {
                            dao.updateDigitacion(
                                id = update.id,
                                idDigitador = update.idDigitador,
                                timestampDigitador = update.timestampDigitador,
                                statusDigitacion = update.statusDigitacion,
                                noteDigitacion = update.noteDigitacion,
                                avisoDigitacion = update.avisoDigitacion,
                                diasPendiente = update.diasPendiente,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "updateDigitacion failed id=${update.id}: ${e.message}")
                        }
                    }
                }
        }
    }

    override fun observeLast24h(): Flow<List<InspectionWithEquipment>> {
        val since = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        return dao.observeLast24hWithEquipment(since)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun findById(id: Long): Inspection? =
        withContext(ioDispatcher) { dao.findById(id)?.toDomain() }

    override suspend fun getLatest2ByContainerNo(containerNo: String): List<Inspection> =
        withContext(ioDispatcher) {
            dao.getLatest2ByContainerNo(containerNo).map { it.toDomain() }
        }

    override suspend fun countByContainerNo(containerNo: String): Int =
        withContext(ioDispatcher) { dao.countByContainerNo(containerNo) }

    override suspend fun getAllByContainerNo(containerNo: String): List<Inspection> =
        withContext(ioDispatcher) {
            dao.getAllByContainerNo(containerNo).map { it.toDomain() }
        }

    override suspend fun findTodayByContainerNo(containerNo: String): Inspection? =
        withContext(ioDispatcher) {
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            dao.findTodayByContainerNo(containerNo, startOfDay)?.toDomain()
        }

    override suspend fun create(inspection: Inspection): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val entity = inspection.toEntity()
            val id = dao.insert(entity)
            firestoreService.upsertInspection(entity.copy(id = id))
            id
        }
    }

    override suspend fun update(inspection: Inspection): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val entity = inspection.toEntity()
            dao.update(entity)
            firestoreService.upsertInspection(entity)
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        val entity = dao.findById(id) ?: return@withContext
        dao.delete(id)
        firestoreService.deleteInspection(entity.containerNo, id)
    }
}
