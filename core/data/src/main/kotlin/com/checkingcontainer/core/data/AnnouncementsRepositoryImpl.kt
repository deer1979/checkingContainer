package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.model.Announcement
import com.checkingcontainer.core.network.RemoteDataSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repositorio de anuncios.
 *
 * Estado actual: **local-only** — Room es la única fuente de verdad.
 *
 * TODO: Inyectar [RemoteDataSource] (Google Sheets) y sincronizar con
 *   una hoja "Anuncios" de solo lectura para distribución de comunicados.
 */
@Singleton
class AnnouncementsRepositoryImpl @Inject constructor(
    private val dao: AnnouncementDao,
    private val remoteDataSource: RemoteDataSource,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AnnouncementsRepository {

    private val syncScope = CoroutineScope(ioDispatcher + SupervisorJob())

    // ── Reads ─────────────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<Announcement>> =
        dao.observeAll()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getById(id: String): Announcement? =
        withContext(ioDispatcher) { dao.findById(id)?.toDomain() }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun publish(
        title: String,
        summary: String,
        body: String,
        authorName: String,
    ) = withContext(ioDispatcher) {
        val entity = AnnouncementEntity(
            id          = UUID.randomUUID().toString(),
            title       = title,
            summary     = summary.ifBlank { title },
            body        = body,
            authorName  = authorName,
            publishedAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
        // TODO: pushCreate(entity) con Google Sheets API
        Unit
    }

    override suspend fun refreshFromRemote() {
        // TODO: pull desde Google Sheets API y hacer upsert en Room
    }

    override suspend fun delete(id: String): Unit = withContext(ioDispatcher) {
        dao.deleteById(id)
        syncScope.launch {
            remoteDataSource.deleteRow("announcements", id)
                .onFailure { Log.w("AnnouncementsRepo", "deleteRow Sheets falló", it) }
        }
        Unit
    }
}

// ── Mapping helpers ───────────────────────────────────────────────────────────

private fun AnnouncementEntity.toDomain() = Announcement(
    id          = id,
    title       = title,
    summary     = summary,
    body        = body,
    authorName  = authorName,
    publishedAt = publishedAt,
)
