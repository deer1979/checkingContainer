package com.checkingcontainer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.checkingcontainer.core.data.sync.SupabaseSyncWorker
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
        // Enqueue an immediate sync (runs when network is available) and a
        // periodic sync every 15 min. Both are no-ops when Supabase is not
        // configured (empty URL / key).
        SupabaseSyncWorker.enqueueImmediate(this)
        SupabaseSyncWorker.schedulePeriodicSync(this)
    }
}
