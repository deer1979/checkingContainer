package com.checkingcontainer.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.network.GoogleSheetsSyncService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private const val TAG                  = "RemoteSyncWorker"
private const val WORK_NAME_PERIODIC   = "remote_sync_periodic"
private const val WORK_NAME_IMMEDIATE  = "remote_sync_immediate"
private const val WORK_NAME_AFTER_WRITE = "remote_sync_after_write"

/**
 * Worker de sincronización Room → Google Sheets.
 *
 * ## Flujo por ejecución
 * 1. Consulta en cada tabla las filas con `syncPending = 1`.
 * 2. Las agrupa en lotes de [BATCH_SIZE] para respetar la cuota de la Sheets API.
 * 3. Por cada fila: llama a [GoogleSheetsSyncService.pushDataToSheet].
 *    - Éxito → `markSynced(id)` → `syncPending = 0`.
 *    - Fallo  → la fila permanece pendiente; se intenta en el siguiente ciclo.
 * 4. Al terminar, re-comprueba si quedan pendientes (ítems escritos durante este run)
 *    y encola [WORK_NAME_AFTER_WRITE] para procesarlos sin esperar 15 min.
 *
 * ## Resiliencia
 * - Cada tabla se envuelve en try-catch independiente: si una falla, la otra continúa.
 * - [MAX_RETRY_ATTEMPTS] evita bucles infinitos; WorkManager usa backoff exponencial.
 *
 * ## Tablas sincronizadas
 * | Tabla        | Clave   | Nota                               |
 * |--------------|---------|-------------------------------------|
 * | reefer_units | id      | `toEntity()` inicializa syncPending=1|
 * | users        | nick    | `setActive()` también marca pending |
 *
 * `announcements`, `catalog_entries` y `manufacturers` no tienen `syncPending` —
 * se sincronizan vía [SyncManager.markAllExistingDataForSync] (push directo).
 */
@HiltWorker
class RemoteSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sheetsService: GoogleSheetsSyncService,
    private val reeferUnitDao: ReeferUnitDao,
    private val userDao: UserDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Sync abandonado tras $runAttemptCount intentos — Result.failure()")
            return Result.failure()
        }

        Log.i(TAG, "▶ SyncWorker iniciado (intento ${runAttemptCount + 1}). Buscando datos pendientes...")
        var anyFailure = false

        // ── reefer_units ──────────────────────────────────────────────────────
        val pendingReefer = try {
            reeferUnitDao.getPending()
        } catch (ex: Exception) {
            Log.e(TAG, "Error al leer pendientes de [reefer_units]: ${ex.message}", ex)
            emptyList()
        }
        Log.i(TAG, "Encontrados ${pendingReefer.size} elementos pendientes en [reefer_units]")

        if (pendingReefer.isNotEmpty()) {
            val ok = try {
                syncInBatches(
                    tableName  = "reefer_units",
                    pending    = pendingReefer,
                    toSyncMap  = ReeferUnitEntity::toSyncMap,
                    getId      = { it.id },
                    markSynced = reeferUnitDao::markSynced,
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Error sincronizando tabla [reefer_units]: ${ex.message}", ex)
                false
            }
            if (!ok) anyFailure = true
        }

        // ── users ─────────────────────────────────────────────────────────────
        val pendingUsers = try {
            userDao.getPending()
        } catch (ex: Exception) {
            Log.e(TAG, "Error al leer pendientes de [users]: ${ex.message}", ex)
            emptyList()
        }
        Log.i(TAG, "Encontrados ${pendingUsers.size} elementos pendientes en [users]")

        if (pendingUsers.isNotEmpty()) {
            val ok = try {
                syncInBatches(
                    tableName  = "users",
                    pending    = pendingUsers,
                    toSyncMap  = UserEntity::toSyncMap,
                    getId      = { it.id },
                    markSynced = userDao::markSynced,
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Error sincronizando tabla [users]: ${ex.message}", ex)
                false
            }
            if (!ok) anyFailure = true
        }

        // ── Re-encolar si se escribieron ítems nuevos durante este run ─────────
        // Usar WORK_NAME_AFTER_WRITE (distinto al nombre actual) para que KEEP
        // lo encole sin cancelar este worker que todavía está en ejecución.
        val stillPending = try {
            reeferUnitDao.getPending().size + userDao.getPending().size
        } catch (_: Exception) { 0 }

        if (stillPending > 0) {
            Log.d(TAG, "$stillPending ítems nuevos detectados durante este sync — re-encolando")
            enqueueAfterWrite(applicationContext)
        }

        return if (anyFailure) {
            Log.w(TAG, "◀ SyncWorker parcial (alguna fila falló) — Result.retry()")
            Result.retry()
        } else {
            Log.i(TAG, "◀ SyncWorker completado sin errores ✅")
            Result.success()
        }
    }

    // ── Lógica de batch ───────────────────────────────────────────────────────

    /**
     * Envía [pending] a Sheets en lotes de [BATCH_SIZE].
     * Los fallos individuales de fila se loguean pero no interrumpen el lote;
     * la fila mantiene `syncPending = 1` y se retomará en el siguiente ciclo.
     *
     * @return `true` si todas las filas se sincronizaron correctamente.
     */
    private suspend fun <T : Any> syncInBatches(
        tableName: String,
        pending: List<T>,
        toSyncMap: (T) -> Map<String, Any?>,
        getId: (T) -> Long,
        markSynced: suspend (Long) -> Unit,
    ): Boolean {
        var allSuccess = true
        val batches = pending.chunked(BATCH_SIZE)
        Log.d(TAG, "[$tableName] procesando ${pending.size} filas en ${batches.size} lote(s)")

        batches.forEachIndexed { batchIdx, batch ->
            Log.d(TAG, "[$tableName] lote ${batchIdx + 1}/${batches.size} — ${batch.size} filas")

            batch.forEach { entity ->
                sheetsService.pushDataToSheet(tableName, toSyncMap(entity))
                    .onSuccess {
                        markSynced(getId(entity))
                        Log.d(TAG, "[$tableName] ✅ id=${getId(entity)} sincronizado y marcado")
                    }
                    .onFailure { ex ->
                        Log.e(TAG, "Error sincronizando tabla [$tableName]: ${ex.message}")
                        allSuccess = false
                        // syncPending = 1 se mantiene → se reintentará
                    }
            }

            if (batchIdx < batches.lastIndex) {
                Log.d(TAG, "[$tableName] pausa entre lotes (${BATCH_DELAY_MS}ms)...")
                delay(BATCH_DELAY_MS)
            }
        }

        val synced = pending.size - pending.count { /* mutable count */ false }
        Log.i(TAG, "[$tableName] lotes completados (allSuccess=$allSuccess)")
        return allSuccess
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BATCH_SIZE         = 10
        private const val BATCH_DELAY_MS     = 500L

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Sync inmediato al arrancar la app o tras una migración masiva.
         * Política KEEP: si ya hay un sync en cola o corriendo, no duplicar.
         */
        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<RemoteSyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "enqueueImmediate → $WORK_NAME_IMMEDIATE (KEEP)")
        }

        /**
         * Disparado por los repositorios justo después de cada `create` o `update`.
         *
         * Usa [WORK_NAME_AFTER_WRITE] — nombre distinto al sync de startup — para que:
         * - Si no hay sync corriendo: se encola y corre de inmediato.
         * - Si el sync de startup está corriendo: se encola SIN cancelarlo (KEEP).
         *   Al terminar ese sync, re-comprueba y encola este si quedan pendientes.
         * - Si ya hay un after-write pendiente: KEEP lo mantiene (todos los ítems
         *   escritos se acumulan en Room y el worker los recoge juntos).
         */
        fun enqueueAfterWrite(context: Context) {
            val request = OneTimeWorkRequestBuilder<RemoteSyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_AFTER_WRITE,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "enqueueAfterWrite → $WORK_NAME_AFTER_WRITE (KEEP)")
        }

        /** Sincronización periódica cada 15 min mientras haya conexión. */
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<RemoteSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "schedulePeriodicSync → $WORK_NAME_PERIODIC (KEEP, 15 min)")
        }
    }
}
