package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Announcement

@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val authorName: String,
    val publishedAt: Long,
    val attachments: String = "[]",
) {
    fun toDomain() = Announcement(
        id = id,
        title = title,
        summary = summary,
        body = body,
        authorName = authorName,
        publishedAt = publishedAt,
        attachments = AttachmentJson.decode(attachments),
    )
}

fun Announcement.toEntity() = AnnouncementEntity(
    id = id,
    title = title,
    summary = summary,
    body = body,
    authorName = authorName,
    publishedAt = publishedAt,
    attachments = AttachmentJson.encode(attachments),
)
