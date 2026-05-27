package com.checkingcontainer.core.data.remote.dto

import com.checkingcontainer.core.database.entity.AnnouncementEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String,
    @SerialName("body") val body: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("published_at") val publishedAt: Long,
)

fun AnnouncementDto.toEntity() = AnnouncementEntity(
    id = id,
    title = title,
    summary = summary,
    body = body,
    authorName = authorName,
    publishedAt = publishedAt,
)

fun AnnouncementEntity.toDto() = AnnouncementDto(
    id = id,
    title = title,
    summary = summary,
    body = body,
    authorName = authorName,
    publishedAt = publishedAt,
)
