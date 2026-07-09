package com.checkingcontainer.core.database.di

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.checkingcontainer.core.database.AppDatabase
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.dao.CatalogDao
import com.checkingcontainer.core.database.dao.ClientDao
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.migrations.MIGRATION_4_5
import com.checkingcontainer.core.database.migrations.MIGRATION_5_6
import com.checkingcontainer.core.database.migrations.MIGRATION_6_7
import com.checkingcontainer.core.database.migrations.MIGRATION_7_8
import com.checkingcontainer.core.database.migrations.MIGRATION_8_9
import com.checkingcontainer.core.database.migrations.MIGRATION_9_10
import com.checkingcontainer.core.database.migrations.MIGRATION_10_11
import com.checkingcontainer.core.database.migrations.MIGRATION_11_12
import com.checkingcontainer.core.database.migrations.MIGRATION_12_13
import com.checkingcontainer.core.database.migrations.MIGRATION_13_14
import com.checkingcontainer.core.database.migrations.MIGRATION_14_15
import com.checkingcontainer.core.database.migrations.MIGRATION_15_16
import com.checkingcontainer.core.database.migrations.seedAnnouncements
import com.checkingcontainer.core.database.migrations.seedFullCatalog
import com.checkingcontainer.core.database.migrations.seedManufacturers
import com.checkingcontainer.core.model.JobTitle
import com.checkingcontainer.core.model.UserRole
import com.checkingcontainer.core.model.generateNick
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "checkingcontainer.db",
    )
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
        .addCallback(seedOnCreateCallback)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun providesUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun providesReeferUnitDao(db: AppDatabase): ReeferUnitDao = db.reeferUnitDao()

    @Provides
    fun providesInspectionDao(db: AppDatabase): InspectionDao = db.inspectionDao()

    @Provides
    fun providesCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun providesAnnouncementDao(db: AppDatabase): AnnouncementDao = db.announcementDao()

    @Provides
    fun providesEstimadoDao(db: AppDatabase): EstimadoDao = db.estimadoDao()

    @Provides
    fun providesClientDao(db: AppDatabase): ClientDao = db.clientDao()

    /** Seeds SuperAdmin + full catalog on first install. Login: nick = sadmin, PIN = 000000. */
    private val seedOnCreateCallback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val firstName = "Super"
            val lastName = "Admin"
            val values = ContentValues().apply {
                put("firstName", firstName)
                put("lastName", lastName)
                put("nick", generateNick(firstName, lastName))
                put("pin", "000000")
                put("jobTitle", JobTitle.Lider.name)
                put("role", UserRole.SuperAdmin.name)
                put("company", "CheckingContainer")
                put("location", "Principal")
                put("isActive", 1)
            }
            db.insert("users", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, values)
            seedManufacturers(db)
            seedFullCatalog(db)
            seedAnnouncements(db)
        }
    }
}
