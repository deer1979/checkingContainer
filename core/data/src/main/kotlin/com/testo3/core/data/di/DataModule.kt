package com.testo3.core.data.di

import com.testo3.core.data.AnnouncementsRepositoryImpl
import com.testo3.core.data.AuthRepositoryImpl
import com.testo3.core.data.TaskRepositoryImpl
import com.testo3.core.domain.AnnouncementsRepository
import com.testo3.core.domain.AuthRepository
import com.testo3.core.domain.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Single place that binds repository contracts to concrete implementations.
 * To migrate any of these to a cloud source later, replace the impl class
 * here — feature modules don't change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAnnouncementsRepository(
        impl: AnnouncementsRepositoryImpl,
    ): AnnouncementsRepository
}
