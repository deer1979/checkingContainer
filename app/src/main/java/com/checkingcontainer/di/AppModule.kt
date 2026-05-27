package com.checkingcontainer.di

import com.checkingcontainer.core.network.NoOpRemoteDataSource
import com.checkingcontainer.core.network.RemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de DI de nivel de aplicación.
 *
 * ## Estado actual
 * Enlaza [RemoteDataSource] con [NoOpRemoteDataSource] (sin backend remoto).
 *
 * ## Al implementar Google Sheets/Drive API
 * Reemplazar [NoOpRemoteDataSource] por `GoogleSheetsDataSource`:
 * ```kotlin
 * @Binds @Singleton
 * abstract fun bindRemoteDataSource(impl: GoogleSheetsDataSource): RemoteDataSource
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(impl: NoOpRemoteDataSource): RemoteDataSource
}
