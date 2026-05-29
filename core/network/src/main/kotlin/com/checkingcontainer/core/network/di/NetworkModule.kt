package com.checkingcontainer.core.network.di

import com.checkingcontainer.core.network.FirestoreDataSource
import com.checkingcontainer.core.network.RemoteDataSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(impl: FirestoreDataSource): RemoteDataSource

    companion object {

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()
            return FirebaseFirestore.getInstance().also { it.firestoreSettings = settings }
        }
    }
}
