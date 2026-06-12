package com.checkingcontainer.core.domain

import kotlinx.coroutines.flow.Flow

/** Último estado conocido de la sincronización con la nube. */
data class SyncStatus(
    val lastOkAt: Long? = null,
    val lastPendingAt: Long? = null,
    val lastErrorAt: Long? = null,
    val lastErrorMessage: String? = null,
)

interface SyncStatusRepository {
    val status: Flow<SyncStatus>
    suspend fun recordOk()
    suspend fun recordPending(operation: String)
    suspend fun recordError(operation: String, message: String?)
}
