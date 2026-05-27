package com.checkingcontainer.core.network

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "SupabaseClientHolder"

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
        when {
            supabaseUrl.isBlank() -> {
                Log.e(TAG, "SUPABASE_URL está vacío — modo local activado. Verifica los GitHub Secrets.")
                null
            }
            anonKey.isBlank() -> {
                Log.e(TAG, "SUPABASE_ANON_KEY está vacío — modo local activado. Verifica los GitHub Secrets.")
                null
            }
            else -> {
                Log.d(TAG, "Creando cliente Supabase para: $supabaseUrl")
                runCatching {
                    createSupabaseClient(
                        supabaseUrl = supabaseUrl,
                        supabaseKey = anonKey,
                    ) {
                        install(Postgrest)
                        install(Realtime)
                    }.also { Log.d(TAG, "Cliente Supabase creado correctamente") }
                }.onFailure { e ->
                    Log.e(TAG, "Error creando cliente Supabase: ${e.message}", e)
                }.getOrNull()
            }
        }
    }

    val isConfigured: Boolean get() = client != null

    /** Host visible para mostrar en UI (sin revelar la key). */
    val displayHost: String get() = supabaseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .ifBlank { "no configurado" }
}
