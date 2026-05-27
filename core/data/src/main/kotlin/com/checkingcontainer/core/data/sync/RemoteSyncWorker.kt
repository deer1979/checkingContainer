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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val TAG = "RemoteSyncWorker"
private const val WORK_NAME_PERIODIC  = "remote_sync_periodic"
private const val WORK_NAME_IMMEDIATE = "remote_sync_immediate"

/**
 * Worker de sincronización con el backend remoto.
 *
 * ## Estado actual
 * Stub vacío — devuelve [Result.success] sin hacer nada.
 * La infraestructura de WorkManager (nombre del trabajo, constraints, backoff)
 * está lista para conectar la lógica de Google Sheets/Drive API.
 *
 * ## Próximos pasos
 * 1. Inyectar el [RemoteDataSource] de Google cuando esté implementado.
 * 2. En [doWork], llamar a los métodos de push de cada repositorio:
 *    ```kotlin
 *    reeferUnitRepo.pushPendingToRemote()
 *    usersRepo.pushPendingToRemote()
 *    announcementsRepo.pushPendingToRemote()
 *    ```
 */
@HiltWorker
class RemoteSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "RemoteSyncWorker ejecutado — implementación pendiente (Google API)")
        // TODO: sincronizar Room → Google Sheets/Drive
        return Result.success()
    }

    companion object {
        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Sincronización inmediata en cuanto haya red (p.ej. al arrancar la app). */
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
        }
    }
}
