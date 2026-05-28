package com.checkingcontainer.core.network

import javax.inject.Inject

/**
 * Contrato del backend remoto de la aplicación.
 *
 * Esta interfaz abstrae el transporte de datos remoto, permitiendo cambiar
 * la implementación (Supabase → Google Sheets/Drive) sin tocar los repositorios.
 *
 * ## Estado actual
 * Implementación en progreso. Los repositorios operan en modo **local-only** (Room)
 * hasta que se conecte una implementación concreta de Google Sheets/Drive API.
 *
 * ## Próximos pasos
 * 1. Agregar dependencias en `core/network/build.gradle.kts`:
 *    ```
 *    implementation("com.google.api-client:google-api-client-android:2.x.x")
 *    implementation("com.google.apis:google-api-services-sheets:v4-rev...")
 *    ```
 * 2. Crear `GoogleSheetsDataSource : RemoteDataSource` en este módulo.
 * 3. Enlazar vía Hilt en un `NetworkModule` nuevo.
 * 4. Inyectar [RemoteDataSource] en cada repositorio en lugar de [RemoteDataSource].
 */
interface RemoteDataSource {

    /** true si hay credenciales válidas y la conexión al backend remoto está activa. */
    val isConnected: Boolean

    /** Nombre legible del backend para mostrar en la UI de ajustes. */
    val backendDescription: String

    suspend fun deleteRow(tableName: String, keyValue: String): Result<Unit>
}

/**
 * Implementación vacía (no-op) que devuelve siempre [isConnected] = false.
 * Usada por Hilt mientras no exista la implementación de Google API.
 * Reemplazar por `GoogleSheetsDataSource @Inject constructor(...)` cuando esté lista.
 */
class NoOpRemoteDataSource @Inject constructor() : RemoteDataSource {
    override val isConnected: Boolean = false
    override val backendDescription: String = "Sin backend remoto configurado"
    override suspend fun deleteRow(tableName: String, keyValue: String): Result<Unit> = Result.success(Unit)
}
