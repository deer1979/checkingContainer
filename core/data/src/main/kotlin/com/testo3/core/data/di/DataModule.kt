package com.testo3.core.data.di

import com.testo3.core.data.AnnouncementsRepositoryImpl
import com.testo3.core.data.AuthRepositoryImpl
import com.testo3.core.data.ReeferUnitRepositoryImpl
import com.testo3.core.data.TaskRepositoryImpl
import com.testo3.core.data.UsersRepositoryImpl
import com.testo3.core.domain.AnnouncementsRepository
import com.testo3.core.domain.AuthRepository
import com.testo3.core.domain.ReeferUnitRepository
import com.testo3.core.domain.TaskRepository
import com.testo3.core.domain.UsersRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository contract → concrete impl bindings. Migrating any of these to a
 * cloud source later is a one-line change on that single binding.
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

    @Binds
    @Singleton
    abstract fun bindUsersRepository(impl: UsersRepositoryImpl): UsersRepository

    @Binds
    @Singleton
    abstract fun bindReeferUnitRepository(impl: ReeferUnitRepositoryImpl): ReeferUnitRepository
}
