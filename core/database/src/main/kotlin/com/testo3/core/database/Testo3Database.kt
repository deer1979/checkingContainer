package com.testo3.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.testo3.core.database.dao.TaskDao
import com.testo3.core.database.entity.TaskEntity

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false, // turn on (with ksp.arg("room.schemaLocation", ...)) when we add migrations
)
abstract class Testo3Database : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
