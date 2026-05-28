package com.checkingcontainer.di

import android.content.Context
import com.checkingcontainer.BuildConfig
import com.checkingcontainer.core.network.GoogleAuthManager
import com.checkingcontainer.core.network.GoogleSheetsSyncService
import com.checkingcontainer.core.network.RemoteDataSource
import com.checkingcontainer.core.network.ServiceAccountCredentials
import com.checkingcontainer.core.network.SheetsMappingConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Módulo Hilt de nivel de aplicación.
 *
 * ## Grafo de dependencias (cadena de construcción)
 * ```
 * credentials.json (res/raw) ──► ServiceAccountCredentials
 *                             ──► GoogleAuthManager ──────────────────────────┐
 * sheets_structure.json (res/raw) ──► SheetsMappingConfig ────────────────────┤
 * OkHttpClient ─────────────────────────────────────────────────────────────┤ ├──► GoogleSheetsSyncService ──► RemoteDataSource
 * BuildConfig.SHEETS_SPREADSHEET_ID ────────────────────────────────────────┘
 * ```
 *
 * ## Para deshabilitar Google Sheets temporalmente
 * Cambia el `@Binds` de abajo para apuntar a `NoOpRemoteDataSource`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /** Enlaza [GoogleSheetsSyncService] como implementación de [RemoteDataSource]. */
    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(impl: GoogleSheetsSyncService): RemoteDataSource

    companion object {

        // ── Raw resources ────────────────────────────────────────────────────

        /**
         * Contenido de `app/src/main/res/raw/credentials.json` (Service Account).
         * ⚠️ NO LOGUEAR este valor — contiene la clave privada RSA.
         */
        @Provides
        @Singleton
        @Named("google_credentials_json")
        fun provideCredentialsJson(@ApplicationContext ctx: Context): String {
            val resId = ctx.resources.getIdentifier("credentials", "raw", ctx.packageName)
            if (resId == 0) return "{}"
            return ctx.resources.openRawResource(resId)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        /**
         * Contenido de `app/src/main/res/raw/sheets_structure.json`.
         * Define qué columnas de cada tabla Room se sincronizan y en qué orden.
         */
        @Provides
        @Singleton
        @Named("sheets_structure_json")
        fun provideSheetsStructureJson(@ApplicationContext ctx: Context): String =
            ctx.resources.openRawResource(R.raw.sheets_structure)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

        // ── BuildConfig ──────────────────────────────────────────────────────

        /**
         * ID de la hoja de cálculo de Google.
         * Configurar en `local.properties`:
         * ```
         * SHEETS_SPREADSHEET_ID=tu_id_aqui
         * ```
         */
        @Provides
        @Singleton
        @Named("sheets_spreadsheet_id")
        fun provideSpreadsheetId(): String = BuildConfig.SHEETS_SPREADSHEET_ID

        // ── Infraestructura HTTP ─────────────────────────────────────────────

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

        // ── Google Sheets / Auth ─────────────────────────────────────────────

        @Provides
        @Singleton
        fun provideServiceAccountCredentials(
            @Named("google_credentials_json") credJson: String,
        ): ServiceAccountCredentials =
            Json { ignoreUnknownKeys = true }.decodeFromString(credJson)

        @Provides
        @Singleton
        fun provideSheetsMappingConfig(
            @Named("sheets_structure_json") structureJson: String,
        ): SheetsMappingConfig = SheetsMappingConfig(structureJson)

        @Provides
        @Singleton
        fun provideGoogleAuthManager(
            credentials: ServiceAccountCredentials,
            httpClient: OkHttpClient,
        ): GoogleAuthManager = GoogleAuthManager(credentials, httpClient)
    }
}
