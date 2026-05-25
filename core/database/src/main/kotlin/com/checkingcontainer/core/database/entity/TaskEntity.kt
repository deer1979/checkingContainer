package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Task

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isDone: Boolean,
    val createdAt: Long,
) {
    fun toDomain(): Task = Task(
        id = id,
        title = title,
        isDone = isDone,
        createdAt = createdAt,
    )
}
