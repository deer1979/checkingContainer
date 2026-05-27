package com.checkingcontainer.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Holds an optional [SupabaseClient].
 *
 * The client is created only when both [supabaseUrl] and [anonKey] are non-blank.
 * If either is missing (e.g. the developer hasn't yet added them to `local.properties`)
 * the app works in **local-only mode**: Room is the single source of truth and no
 * network calls are attempted.
 */
@Singleton
class SupabaseClientHolder @Inject constructor(
    @param:Named("supabase_url") private val supabaseUrl: String,
    @param:Named("supabase_anon_key") private val anonKey: String,
) {
    val client: SupabaseClient? by lazy {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            null
        } else {
            runCatching {
                createSupabaseClient(
                    supabaseUrl = supabaseUrl,
                    supabaseKey = anonKey,
                ) {
                    install(Postgrest)
                }
            }.getOrNull()
        }
    }

    val isConfigured: Boolean get() = client != null
}
