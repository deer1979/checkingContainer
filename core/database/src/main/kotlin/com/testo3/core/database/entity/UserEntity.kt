package com.testo3.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.testo3.core.model.JobTitle
import com.testo3.core.model.User
import com.testo3.core.model.UserRole

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val email: String,
    val pin: String,
    val jobTitle: JobTitle,
    val role: UserRole,
    val company: String,
    val location: String,
    val isActive: Boolean = true,
) {
    fun toDomain(): User = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
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
    email = email,
    pin = pin,
    jobTitle = jobTitle,
    role = role,
    company = company,
    location = location,
    isActive = isActive,
)
