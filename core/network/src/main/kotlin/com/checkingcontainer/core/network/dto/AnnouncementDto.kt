package com.checkingcontainer.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON DTO for the `announcements` Supabase table.
 *
 * The app generates a UUID [id] before inserting, so the same value is used
 * in both Room and Supabase — enabling exact-match upserts.
 */
@Serializable
data class AnnouncementDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String,
    @SerialName("body") val body: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("published_at_ms") val publishedAtMs: Long,
)
