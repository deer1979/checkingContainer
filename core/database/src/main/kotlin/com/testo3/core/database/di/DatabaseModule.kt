package com.testo3.core.database.di

import android.content.Context
import androidx.room.Room
import com.testo3.core.database.Testo3Database
import com.testo3.core.database.dao.TaskDao
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
    ): Testo3Database = Room.databaseBuilder(
        context,
        Testo3Database::class.java,
        "testo3.db",
    ).build()

    @Provides
    fun providesTaskDao(db: Testo3Database): TaskDao = db.taskDao()
}
