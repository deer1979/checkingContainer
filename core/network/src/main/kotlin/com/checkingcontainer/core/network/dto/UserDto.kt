package com.checkingcontainer.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON DTO for the `users` Supabase table.
 *
 * [nick] acts as the business key for upsert operations — matches the
 * unique index that Room also enforces.
 */
@Serializable
data class UserDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("nick") val nick: String,
    @SerialName("pin") val pin: String,
    @SerialName("job_title") val jobTitle: String,
    @SerialName("role") val role: String,
    @SerialName("company") val company: String,
    @SerialName("location") val location: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("local_id") val localId: Long? = null,
)
