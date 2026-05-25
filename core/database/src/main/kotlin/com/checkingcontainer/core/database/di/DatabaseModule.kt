package com.checkingcontainer.core.database.di

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.checkingcontainer.core.database.AppDatabase
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.TaskDao
import com.checkingcontainer.core.database.dao.UserDao
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
        .addCallback(seedFirstSuperAdminCallback)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun providesTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun providesUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun providesReeferUnitDao(db: AppDatabase): ReeferUnitDao = db.reeferUnitDao()

    /** Seeds SuperAdmin on first install. Login: nick = sadmin, PIN = 000000. */
    private val seedFirstSuperAdminCallback = object : RoomDatabase.Callback() {
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
        }
    }
}