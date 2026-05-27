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
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.network.SupabaseClientHolder
import com.checkingcontainer.core.network.dto.AnnouncementDto
import com.checkingcontainer.core.network.dto.ReeferUnitDto
import com.checkingcontainer.core.network.dto.UserDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.util.concurrent.TimeUnit

private const val TAG = "SupabaseSyncWorker"
private const val WORK_NAME_PERIODIC = "supabase_sync_periodic"
private const val WORK_NAME_IMMEDIATE = "supabase_sync_immediate"

/**
 * WorkManager worker that pushes all local Room data to Supabase.
 *
 * Triggered:
 * - Immediately (one-time) on app start — runs as soon as the device
 *   has network connectivity, even if that's hours after the app was last used.
 * - Periodically every 15 minutes while network is connected.
 *
 * Offline flow: Field work with no internet → data stays in Room →
 * connectivity returns → WorkManager fires → all records uploaded to Supabase.
 */
@HiltWorker
class SupabaseSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userDao: UserDao,
    private val reeferUnitDao: ReeferUnitDao,
    private val announcementDao: AnnouncementDao,
    private val supabase: SupabaseClientHolder,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val client = supabase.client ?: run {
            Log.d(TAG, "Supabase not configured — skipping sync")
            return Result.success()
        }
        return try {
            syncUsers(client)
            syncReeferUnits(client)
            syncAnnouncements(client)
            Log.d(TAG, "Full sync completed")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Sync attempt $runAttemptCount failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun syncUsers(client: SupabaseClient) {
        val rows = userDao.getAllOnce()
        if (rows.isEmpty()) return
        client.from("users").upsert(rows.map { it.toDto() }) { onConflict = "nick" }
        Log.d(TAG, "Synced ${rows.size} users")
    }

    private suspend fun syncReeferUnits(client: SupabaseClient) {
        val rows = reeferUnitDao.getAllOnce()
        if (rows.isEmpty()) return
        client.from("reefer_units").upsert(rows.map { it.toDto() }) { onConflict = "local_id" }
        Log.d(TAG, "Synced ${rows.size} reefer units")
    }

    private suspend fun syncAnnouncements(client: SupabaseClient) {
        val rows = announcementDao.getAllOnce()
        if (rows.isEmpty()) return
        client.from("announcements").upsert(rows.map { it.toDto() }) { onConflict = "id" }
        Log.d(TAG, "Synced ${rows.size} announcements")
    }

    companion object {
        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Enqueue a one-time sync that runs as soon as the device has network.
         * [ExistingWorkPolicy.KEEP] prevents stacking multiple requests.
         */
        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Periodic sync every 15 min (WorkManager minimum interval) while connected. */
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SupabaseSyncWorker>(
                15, TimeUnit.MINUTES,
            )
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

// ── Mapping helpers ───────────────────────────────────────────────────────────

private fun UserEntity.toDto() = UserDto(
    firstName = firstName,
    lastName = lastName,
    nick = nick,
    pin = pin,
    jobTitle = jobTitle.name,
    role = role.name,
    company = company,
    location = location,
    isActive = isActive,
    localId = id.takeIf { it != 0L },
)

private fun ReeferUnitEntity.toDto() = ReeferUnitDto(
    containerNo = containerNo,
    manufacturer = manufacturer,
    unitModel = unitModel,
    unitModelNo = unitModelNo,
    unitSerialNo = unitSerialNo,
    yearOfBuilt = yearOfBuilt,
    createdAtMs = createdAt,
    status = status.name,
    ptiInstruction = ptiInstruction?.name,
    unitType = unitType,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    observations = observations,
    localId = id.takeIf { it != 0L },
)

private fun AnnouncementEntity.toDto() = AnnouncementDto(
    id = id,
    title = title,
    summary = summary,
    body = body,
    authorName = authorName,
    publishedAtMs = publishedAt,
)
