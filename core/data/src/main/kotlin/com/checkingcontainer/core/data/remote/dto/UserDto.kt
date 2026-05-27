package com.checkingcontainer.core.data.remote.dto

import com.checkingcontainer.core.database.entity.UserEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("sync_id") val syncId: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("nick") val nick: String,
    @SerialName("pin") val pin: String,
    @SerialName("job_title") val jobTitle: String,
    @SerialName("role") val role: String,
    @SerialName("company") val company: String,
    @SerialName("location") val location: String,
    @SerialName("is_active") val isActive: Boolean,
)

fun UserEntity.toDto() = UserDto(
    syncId = syncId,
    firstName = firstName,
    lastName = lastName,
    nick = nick,
    pin = pin,
    jobTitle = jobTitle.name,
    role = role.name,
    company = company,
    location = location,
    isActive = isActive,
)
