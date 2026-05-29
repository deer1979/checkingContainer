package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.model.Announcement
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class AnnouncementsRepositoryImpl @Inject constructor(
    private val dao: AnnouncementDao,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AnnouncementsRepository {

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
    }
}
