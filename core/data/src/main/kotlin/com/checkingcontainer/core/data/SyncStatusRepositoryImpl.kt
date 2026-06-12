package com.checkingcontainer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.checkingcontainer.core.domain.SyncStatus
import com.checkingcontainer.core.domain.SyncStatusRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SyncStatusRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SyncStatusRepository {

    override val status: Flow<SyncStatus> = dataStore.data.map { prefs ->
        SyncStatus(
            lastOkAt = prefs[KEY_LAST_OK],
            lastPendingAt = prefs[KEY_LAST_PENDING],
            lastErrorAt = prefs[KEY_LAST_ERROR],
            lastErrorMessage = prefs[KEY_LAST_ERROR_MSG],
        )
    }

    override suspend fun recordOk() {
        dataStore.edit { it[KEY_LAST_OK] = System.currentTimeMillis() }
    }

    override suspend fun recordPending(operation: String) {
        dataStore.edit { it[KEY_LAST_PENDING] = System.currentTimeMillis() }
    }

    override suspend fun recordError(operation: String, message: String?) {
        dataStore.edit {
            it[KEY_LAST_ERROR] = System.currentTimeMillis()
            it[KEY_LAST_ERROR_MSG] = "$operation: ${message ?: "error desconocido"}"
        }
    }

    private companion object {
        val KEY_LAST_OK = longPreferencesKey("sync_last_ok_at")
        val KEY_LAST_PENDING = longPreferencesKey("sync_last_pending_at")
        val KEY_LAST_ERROR = longPreferencesKey("sync_last_error_at")
        val KEY_LAST_ERROR_MSG = stringPreferencesKey("sync_last_error_msg")
    }
}
