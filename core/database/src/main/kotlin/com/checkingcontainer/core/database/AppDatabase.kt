package com.checkingcontainer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.checkingcontainer.core.database.converters.EnumConverters
import com.checkingcontainer.core.database.dao.CatalogDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.TaskDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.CatalogEntryEntity
import com.checkingcontainer.core.database.entity.ManufacturerEntity
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.TaskEntity
import com.checkingcontainer.core.database.entity.UserEntity

@Database(
    entities = [
        TaskEntity::class,
        UserEntity::class,
        ReeferUnitEntity::class,
        ManufacturerEntity::class,
        CatalogEntryEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun userDao(): UserDao
    abstract fun reeferUnitDao(): ReeferUnitDao
    abstract fun catalogDao(): CatalogDao
}
