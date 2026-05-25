package com.testo3.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.testo3.core.database.converters.EnumConverters
import com.testo3.core.database.dao.ReeferUnitDao
import com.testo3.core.database.dao.TaskDao
import com.testo3.core.database.dao.UserDao
import com.testo3.core.database.entity.ReeferUnitEntity
import com.testo3.core.database.entity.TaskEntity
import com.testo3.core.database.entity.UserEntity

@Database(
    entities = [TaskEntity::class, UserEntity::class, ReeferUnitEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(EnumConverters::class)
abstract class Testo3Database : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun userDao(): UserDao
    abstract fun reeferUnitDao(): ReeferUnitDao
}
