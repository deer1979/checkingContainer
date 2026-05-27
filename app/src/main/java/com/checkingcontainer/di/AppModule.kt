package com.checkingcontainer.di

import com.checkingcontainer.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * Provides app-level singletons that require access to [BuildConfig].
 *
 * Supabase credentials are read from BuildConfig fields that are injected
 * at build time from `local.properties` (local dev) or GitHub Actions secrets
 * (CI).  When both strings are blank the app silently operates in local-only
 * mode — see [SupabaseClientHolder].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Named("supabase_url")
    fun provideSupabaseUrl(): String = BuildConfig.SUPABASE_URL

    @Provides
    @Named("supabase_anon_key")
    fun provideSupabaseAnonKey(): String = BuildConfig.SUPABASE_ANON_KEY
}
