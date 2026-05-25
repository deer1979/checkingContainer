package com.checkingcontainer.core.data.di

import com.checkingcontainer.core.data.AnnouncementsRepositoryImpl
import com.checkingcontainer.core.data.ThemeRepositoryImpl
import com.checkingcontainer.core.data.AuthRepositoryImpl
import com.checkingcontainer.core.data.ReeferUnitRepositoryImpl
import com.checkingcontainer.core.data.TaskRepositoryImpl
import com.checkingcontainer.core.data.UsersRepositoryImpl
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.domain.ThemeRepository
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.ReeferUnitRepository
import com.checkingcontainer.core.domain.TaskRepository
import com.checkingcontainer.core.domain.UsersRepository
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

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository
}
