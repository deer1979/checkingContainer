package com.checkingcontainer.core.data.sync

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.dao.CatalogDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.database.entity.CatalogEntryEntity
import com.checkingcontainer.core.database.entity.ManufacturerEntity
import com.checkingcontainer.core.network.GoogleSheetsSyncService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ── Reporte de la migración ───────────────────────────────────────────────────

/**
 * Resultado de [SyncManager.markAllExistingDataForSync].
 * Registra cuántas filas fueron marcadas o enviadas por tabla.
 */
data class SyncMigrationReport(
    /** Filas de reefer_units marcadas como syncPending = 1 (el Worker las subirá). */
    val reeferUnitsMarked: Int = 0,
    /** Filas de users marcadas como syncPending = 1 (el Worker las subirá). */
    val usersMarked: Int = 0,
    /** Anuncios enviados directamente a Sheets (sin syncPending en schema). */
    val announcementsPushed: Int = 0,
    /** Fabricantes enviados directamente a Sheets (sin syncPending en schema). */
    val manufacturersPushed: Int = 0,
    /** Entradas de catálogo enviadas directamente a Sheets (sin syncPending en schema). */
    val catalogEntriesPushed: Int = 0,
) {
    override fun toString(): String =
        "Migración completada — " +
        "reefer_units: $reeferUnitsMarked marcadas, " +
        "users: $usersMarked marcados, " +
        "announcements: $announcementsPushed enviados, " +
        "manufacturers: $manufacturersPushed enviados, " +
        "catalog_entries: $catalogEntriesPushed enviadas"
}

// ── SyncManager ───────────────────────────────────────────────────────────────

/**
 * Orquesta la migración inicial de Room → Google Sheets.
 *
 * ## Estrategia por tabla
 *
 * | Tabla           | Tiene syncPending | Estrategia                            |
 * |-----------------|:-----------------:|---------------------------------------|
 * | reefer_units    | ✅                | Marca `syncPending = 1` → Worker push |
 * | users           | ✅                | Marca `syncPending = 1` → Worker push |
 * | announcements   | ❌                | Push directo en la migración          |
 * | manufacturers   | ❌                | Push directo en la migración          |
 * | catalog_entries | ❌                | Push directo en la migración          |
 *
 * ## Seguridad de una sola ejecución
 * Una vez completada, guarda [MIGRATION_FLAG] en DataStore.
 * Las llamadas posteriores a [markAllExistingDataForSync] devuelven
 * [Result.failure] inmediatamente sin tocar la base de datos.
 * Para resetear en desarrollo: llama a [resetMigrationFlag].
 *
 * ## Cómo usarla
 * ```kotlin
 * // Desde un ViewModel o desde Application.onCreate vía scope
 * syncManager.markAllExistingDataForSync()
 *     .onSuccess { report -> Log.i("Sync", report.toString()) }
 *     .onFailure { ex  -> Log.e("Sync", ex.message ?: "error") }
 * ```
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sheetsService: GoogleSheetsSyncService,
    private val reeferUnitDao: ReeferUnitDao,
    private val userDao: UserDao,
    private val announcementDao: AnnouncementDao,
    private val catalogDao: CatalogDao,
    private val dataStore: DataStore<Preferences>,
) {

    // ── Público ───────────────────────────────────────────────────────────────

    /**
     * Ejecuta la migración inicial.
     *
     * - Falla con [IllegalStateException] si ya se ejecutó anteriormente.
     * - Falla con la excepción original si ocurre un error durante la operación.
     * - En éxito, retorna [SyncMigrationReport] con los contadores por tabla
     *   y encola [RemoteSyncWorker] para que suba reefer_units y users.
     */
    suspend fun markAllExistingDataForSync(): Result<SyncMigrationReport> {
        // ── Guardia de una sola ejecución ─────────────────────────────────────
        val alreadyDone = dataStore.data.map { it[MIGRATION_FLAG] ?: false }.first()
        if (alreadyDone) {
            val msg = "markAllExistingDataForSync ya fue ejecutada. " +
                      "Llama a resetMigrationFlag() para forzar un re-run en desarrollo."
            Log.w(TAG, msg)
            return Result.failure(IllegalStateException(msg))
        }

        return runCatching {
            // ── reefer_units (vía Worker) ──────────────────────────────────────
            val reeferCount = reeferUnitDao.markAllPending()
            Log.i(TAG, "reefer_units marcadas para sync: $reeferCount")

            // ── users (vía Worker) ─────────────────────────────────────────────
            val usersCount = userDao.markAllPending()
            Log.i(TAG, "users marcados para sync: $usersCount")

            // ── announcements (push directo) ───────────────────────────────────
            val announcements = announcementDao.getAllOnce()
            pushDirectBatch("announcements", announcements, AnnouncementEntity::toSyncMap)
            Log.i(TAG, "announcements enviados directamente: ${announcements.size}")

            // ── manufacturers (push directo) ───────────────────────────────────
            val manufacturers = catalogDao.getAllManufacturers()
            pushDirectBatch("manufacturers", manufacturers, ManufacturerEntity::toSyncMap)
            Log.i(TAG, "manufacturers enviados directamente: ${manufacturers.size}")

            // ── catalog_entries (push directo) ─────────────────────────────────
            val catalogEntries = catalogDao.getAllCatalogEntries()
            pushDirectBatch("catalog_entries", catalogEntries, CatalogEntryEntity::toSyncMap)
            Log.i(TAG, "catalog_entries enviadas directamente: ${catalogEntries.size}")

            // ── Encolar Worker para reefer_units + users ───────────────────────
            RemoteSyncWorker.enqueueImmediate(context)
            Log.i(TAG, "RemoteSyncWorker encolado para reefer_units + users")

            // ── Guardar flag SOLO si todo fue bien ─────────────────────────────
            dataStore.edit { prefs -> prefs[MIGRATION_FLAG] = true }
            Log.i(TAG, "Flag de migración guardado ✅")

            SyncMigrationReport(
                reeferUnitsMarked    = reeferCount,
                usersMarked          = usersCount,
                announcementsPushed  = announcements.size,
                manufacturersPushed  = manufacturers.size,
                catalogEntriesPushed = catalogEntries.size,
            ).also { Log.i(TAG, it.toString()) }
        }
    }

    /**
     * Resetea el flag de migración para permitir un nuevo re-run.
     *
     * ⚠️ Solo para uso en desarrollo / QA. No llamar en producción.
     */
    suspend fun resetMigrationFlag() {
        dataStore.edit { prefs -> prefs.remove(MIGRATION_FLAG) }
        Log.w(TAG, "Flag de migración reseteado — el siguiente markAllExistingDataForSync volverá a ejecutar la migración")
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    /**
     * Envía [items] a Sheets en lotes de [BATCH_SIZE] con una pausa de
     * [BATCH_DELAY_MS] entre lotes para respetar el cupo de la Sheets API.
     *
     * Los fallos individuales se loguean pero no interrumpen el proceso;
     * la responsabilidad de reintento queda en la próxima migración o en el Worker.
     */
    private suspend fun <T : Any> pushDirectBatch(
        tableName: String,
        items: List<T>,
        toSyncMap: (T) -> Map<String, Any?>,
    ) {
        if (items.isEmpty()) return
        val batches = items.chunked(BATCH_SIZE)
        batches.forEachIndexed { idx, batch ->
            batch.forEach { item ->
                sheetsService.pushDataToSheet(tableName, toSyncMap(item))
                    .onFailure { ex ->
                        Log.w(TAG, "[$tableName] fila falló (se continuará): ${ex.message}")
                    }
            }
            if (idx < batches.lastIndex) delay(BATCH_DELAY_MS)
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG            = "SyncManager"
        private const val BATCH_SIZE     = 10
        private const val BATCH_DELAY_MS = 500L

        /**
         * Clave en DataStore que indica si la migración ya se ejecutó.
         * Cambia el sufijo de versión ("_v1") si en el futuro necesitas
         * forzar una re-ejecución sin tocar el código.
         */
        private val MIGRATION_FLAG = booleanPreferencesKey("initial_sync_migration_v1_done")
    }
}
