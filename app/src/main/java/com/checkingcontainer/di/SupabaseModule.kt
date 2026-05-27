package com.checkingcontainer.di

import com.checkingcontainer.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Named("supabase_url")
    fun provideSupabaseUrl(): String = BuildConfig.SUPABASE_URL

    @Provides
    @Named("supabase_anon_key")
    fun provideSupabaseAnonKey(): String = BuildConfig.SUPABASE_ANON_KEY
}
