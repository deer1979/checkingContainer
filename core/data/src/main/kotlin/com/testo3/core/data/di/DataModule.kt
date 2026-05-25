package com.testo3.core.data.di

import com.testo3.core.data.TaskRepositoryImpl
import com.testo3.core.domain.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /**
     * Bind the Room-backed implementation as the singleton TaskRepository.
     * Swap this single line to switch to a cloud/remote implementation later.
     */
    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository
}
