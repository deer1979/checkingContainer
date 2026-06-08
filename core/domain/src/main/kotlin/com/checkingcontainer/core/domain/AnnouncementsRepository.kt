package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.Announcement
import kotlinx.coroutines.flow.Flow

interface AnnouncementsRepository {
    fun observeAll(): Flow<List<Announcement>>
    suspend fun getById(id: String): Announcement?
    suspend fun publish(title: String, summary: String, body: String, authorName: String)
    suspend fun refreshFromRemote()
    suspend fun delete(id: String)

    /** Cantidad de anuncios sin leer para [userId] (los publicados tras su última visita). */
    fun unreadCount(userId: Long): Flow<Int>

    /** Marca todos los anuncios actuales como leídos para [userId]. */
    suspend fun markAllSeen(userId: Long)
}
