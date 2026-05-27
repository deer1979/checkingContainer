package com.checkingcontainer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.checkingcontainer.core.data.sync.RemoteSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Encola sincronización inmediata y periódica con el backend remoto.
        // Actualmente es un no-op hasta que se implemente Google Sheets/Drive API.
        RemoteSyncWorker.enqueueImmediate(this)
        RemoteSyncWorker.schedulePeriodicSync(this)
    }
}
