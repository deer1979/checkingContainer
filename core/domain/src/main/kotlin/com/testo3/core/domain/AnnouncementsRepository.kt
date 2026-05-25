package com.testo3.core.domain

import com.testo3.core.model.Announcement
import kotlinx.coroutines.flow.Flow

interface AnnouncementsRepository {
    fun observeAll(): Flow<List<Announcement>>
    suspend fun getById(id: String): Announcement?
    suspend fun publish(title: String, summary: String, body: String, authorName: String)
}
