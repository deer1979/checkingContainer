package com.checkingcontainer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.checkingcontainer.core.database.converters.EnumConverters
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.dao.CatalogDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.database.entity.CatalogEntryEntity
import com.checkingcontainer.core.database.entity.ManufacturerEntity
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ReeferUnitEntity::class,
        ManufacturerEntity::class,
        CatalogEntryEntity::class,
        AnnouncementEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun reeferUnitDao(): ReeferUnitDao
    abstract fun catalogDao(): CatalogDao
    abstract fun announcementDao(): AnnouncementDao
}
