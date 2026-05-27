package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.model.Announcement
import com.checkingcontainer.core.network.SupabaseClientHolder
import com.checkingcontainer.core.network.dto.AnnouncementDto
import io.github.jan.supabase.postgrest.from
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

private const val TAG = "AnnouncementsRepo"
private const val TABLE = "announcements"

@Singleton
class AnnouncementsRepositoryImpl @Inject constructor(
    private val dao: AnnouncementDao,
    private val supabase: SupabaseClientHolder,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AnnouncementsRepository {

    private val syncScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        syncScope.launch { pullFromSupabase() }
    }

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
            id = UUID.randomUUID().toString(),
            title = title,
            summary = summary.ifBlank { title },
            body = body,
            authorName = authorName,
            publishedAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
        // UUID id is shared between Room and Supabase — perfect dedup key
        syncScope.launch { pushCreate(entity) }
    }

    // ── Supabase helpers ─────────────────────────────────────────────────────

    private suspend fun pullFromSupabase() {
        val client = supabase.client ?: return
        runCatching {
            val remote = client.from(TABLE).select().decodeList<AnnouncementDto>()
            // AnnouncementEntity already uses UUID string PKs — safe to upsert by id
            remote.forEach { dto ->
                dao.insert(dto.toEntity()) // OnConflictStrategy.REPLACE handles duplicates
            }
            Log.d(TAG, "Pulled ${remote.size} announcements from Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase pull failed (local-only mode)", e)
        }
    }

    private suspend fun pushCreate(entity: AnnouncementEntity) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).insert(entity.toDto())
            Log.d(TAG, "Pushed announcement '${entity.title}' to Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (create) failed for '${entity.title}'", e)
        }
    }
}

// ── Mapping helpers ───────────────────────────────────────────────────────────

private fun AnnouncementEntity.toDto() = AnnouncementDto(
    id = id,
    title = title,
    summary = summary,
    body = body,
    authorName = authorName,
    publishedAtMs = publishedAt,
)

private fun AnnouncementDto.toEntity() = AnnouncementEntity(
    id = id,
    title = title,
    summary = summary,
    body = body,
    authorName = authorName,
    publishedAt = publishedAtMs,
)
