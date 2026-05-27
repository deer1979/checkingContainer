package com.checkingcontainer

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.checkingcontainer.core.data.sync.RemoteSyncWorker
import com.checkingcontainer.core.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MyApplication"

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: SyncManager

    /**
     * Scope de vida de la aplicación.
     * Usa [SupervisorJob] para que el fallo de una corrutina no cancele las demás.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // ── Migración inicial Room → Google Sheets ────────────────────────────
        // El DataStore guard dentro de SyncManager garantiza que solo se ejecuta
        // una vez; en arranques posteriores retorna inmediatamente sin hacer nada.
        applicationScope.launch {
            syncManager.markAllExistingDataForSync()
                .onSuccess { report ->
                    Log.i(TAG, "Migración inicial completada: $report")
                }
                .onFailure { ex ->
                    // IllegalStateException = ya ejecutada anteriormente (esperado)
                    // Cualquier otro error = problema real de red o DB
                    if (ex is IllegalStateException) {
                        Log.d(TAG, "Migración ya realizada — omitida: ${ex.message}")
                    } else {
                        Log.e(TAG, "Error en migración inicial: ${ex.message}", ex)
                    }
                }
        }

        // ── Sync periódico y de arranque para ítems creados tras la migración ─
        RemoteSyncWorker.enqueueImmediate(this)
        RemoteSyncWorker.schedulePeriodicSync(this)
    }
}
