package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.JobTitle
import com.checkingcontainer.core.model.User
import com.checkingcontainer.core.model.UserRole
import java.util.UUID

@Entity(
    tableName = "users",
    indices = [Index(value = ["nick"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val nick: String,
    val pin: String,
    val jobTitle: JobTitle,
    val role: UserRole,
    val company: String,
    val location: String,
    val isActive: Boolean = true,
    val syncId: String = UUID.randomUUID().toString(),
    val syncPending: Boolean = true,
) {
    fun toDomain(): User = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        nick = nick,
        pin = pin,
        jobTitle = jobTitle,
        role = role,
        company = company,
        location = location,
        isActive = isActive,
    )
}

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    firstName = firstName,
    lastName = lastName,
    nick = nick,
    pin = pin,
    jobTitle = jobTitle,
    role = role,
    company = company,
    location = location,
    isActive = isActive,
)