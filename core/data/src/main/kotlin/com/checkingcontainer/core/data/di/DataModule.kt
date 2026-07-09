package com.checkingcontainer.core.data.di

import com.checkingcontainer.core.data.BootstrapRepositoryImpl
import com.checkingcontainer.core.data.ClientsRepositoryImpl
import com.checkingcontainer.core.data.AnnouncementsRepositoryImpl
import com.checkingcontainer.core.data.CatalogRepositoryImpl
import com.checkingcontainer.core.data.EstimadosRepositoryImpl
import com.checkingcontainer.core.data.InspectionRepositoryImpl
import com.checkingcontainer.core.data.ReeferEquipmentRepositoryImpl
import com.checkingcontainer.core.data.ThemeRepositoryImpl
import com.checkingcontainer.core.data.AuthRepositoryImpl
import com.checkingcontainer.core.data.SyncStatusRepositoryImpl
import com.checkingcontainer.core.data.UsersRepositoryImpl
import com.checkingcontainer.core.domain.BootstrapRepository
import com.checkingcontainer.core.domain.ClientsRepository
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.domain.CatalogRepository
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import com.checkingcontainer.core.domain.ThemeRepository
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.SyncStatusRepository
import com.checkingcontainer.core.domain.UsersRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindBootstrapRepository(impl: BootstrapRepositoryImpl): BootstrapRepository

    @Binds
    @Singleton
    abstract fun bindClientsRepository(impl: ClientsRepositoryImpl): ClientsRepository

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
    abstract fun bindReeferEquipmentRepository(
        impl: ReeferEquipmentRepositoryImpl,
    ): ReeferEquipmentRepository

    @Binds
    @Singleton
    abstract fun bindInspectionRepository(impl: InspectionRepositoryImpl): InspectionRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository

    @Binds
    @Singleton
    abstract fun bindEstimadosRepository(impl: EstimadosRepositoryImpl): EstimadosRepository

    @Binds
    @Singleton
    abstract fun bindSyncStatusRepository(impl: SyncStatusRepositoryImpl): SyncStatusRepository
}
