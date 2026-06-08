package com.checkingcontainer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.domain.PendingAttachment
import com.checkingcontainer.core.model.Announcement
import com.checkingcontainer.core.model.Attachment
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class AnnouncementsRepositoryImpl @Inject constructor(
    private val dao: AnnouncementDao,
    private val firestoreService: FirestoreService,
    private val storageService: StorageService,
    private val dataStore: DataStore<Preferences>,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AnnouncementsRepository {

    override fun observeAll(): Flow<List<Announcement>> =
        dao.observeAll()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getById(id: String): Announcement? =
        withContext(ioDispatcher) { dao.findById(id)?.toDomain() }

    // ── Leídos / no leídos (por usuario, local) ─────────────────────────────────

    override fun unreadCount(userId: Long): Flow<Int> =
        combine(observeAll(), lastSeen(userId)) { items, seen ->
            items.count { it.publishedAt > seen }
        }

    override suspend fun markAllSeen(userId: Long) {
        dataStore.edit { it[lastSeenKey(userId)] = System.currentTimeMillis() }
    }

    private fun lastSeen(userId: Long): Flow<Long> =
        dataStore.data.map { it[lastSeenKey(userId)] ?: 0L }

    private fun lastSeenKey(userId: Long) =
        longPreferencesKey("announcements_last_seen_$userId")

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun publish(
        title: String,
        summary: String,
        body: String,
        authorName: String,
        attachments: List<PendingAttachment>,
    ) = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        val uploaded = attachments.map { p ->
            val url = storageService.upload(id, p.name, p.bytes, p.contentType)
            Attachment(url = url, name = p.name, contentType = p.contentType, sizeBytes = p.sizeBytes)
        }
        val entity = Announcement(
            id          = id,
            title       = title,
            summary     = summary.ifBlank { title },
            body        = body,
            authorName  = authorName,
            publishedAt = System.currentTimeMillis(),
            attachments = uploaded,
        ).toEntity()
        dao.insert(entity)
        firestoreService.upsertAnnouncement(entity)
    }

    override suspend fun refreshFromRemote() = withContext(ioDispatcher) {
        val remote = firestoreService.fetchAllAnnouncements()
        if (remote.isNotEmpty()) dao.replaceAll(remote)
    }

    override suspend fun delete(id: String): Unit = withContext(ioDispatcher) {
        val urls = dao.findById(id)?.toDomain()?.attachments?.map { it.url }.orEmpty()
        dao.deleteById(id)
        firestoreService.deleteAnnouncement(id)
        urls.forEach { storageService.deleteByUrl(it) }
    }
}
