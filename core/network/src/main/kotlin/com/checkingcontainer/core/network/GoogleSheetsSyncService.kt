package com.checkingcontainer.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Sincroniza datos entre Room y Google Sheets a través de la Sheets REST API v4.
 *
 * ## Operaciones disponibles
 * - [pushDataToSheet]: agrega una fila al final de la pestaña correspondiente.
 * - [pullSheetData]: lee todas las filas de una pestaña como lista de mapas.
 *
 * ## Autenticación
 * Delega en [GoogleAuthManager] para obtener el Bearer token OAuth2 del Service Account.
 *
 * ## Mapeo de columnas
 * Usa [SheetsMappingConfig] (derivado de `sheets_structure.json`) para determinar:
 * - El nombre de la pestaña en la hoja.
 * - El orden y filtro de columnas (solo `sync_to_sheets = true`).
 *
 * ## Inyección Hilt
 * Todas las dependencias se proveen en `AppModule`:
 * - [authManager]: Service Account JWT + token caching.
 * - [mappingConfig]: estructura de columnas desde `res/raw/sheets_structure.json`.
 * - [httpClient]: OkHttpClient singleton.
 * - [spreadsheetId]: desde `BuildConfig.SHEETS_SPREADSHEET_ID` (vía `local.properties`).
 */
@Singleton
class GoogleSheetsSyncService @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val mappingConfig: SheetsMappingConfig,
    private val httpClient: OkHttpClient,
    @param:Named("sheets_spreadsheet_id") private val spreadsheetId: String,
) : RemoteDataSource {

    override val isConnected: Boolean
        get() = spreadsheetId.isNotEmpty()

    override val backendDescription: String
        get() = if (spreadsheetId.isNotEmpty())
            "Google Sheets (ID: …${spreadsheetId.takeLast(6)})"
        else
            "Google Sheets (sin configurar — agrega SHEETS_SPREADSHEET_ID en local.properties)"

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val baseUrl   = "https://sheets.googleapis.com/v4/spreadsheets"

    // ── Escritura ────────────────────────────────────────────────────────────

    /**
     * Agrega [rowData] como una nueva fila al final de la pestaña correspondiente a [tableName].
     *
     * Las columnas marcadas como `sync_to_sheets = false` (syncId, syncPending, brand) se
     * ignoran automáticamente mediante [SheetsMappingConfig.buildRow].
     *
     * @param tableName Nombre de la tabla Room (ej. `"reefer_units"`, `"users"`).
     * @param rowData   Mapa columna-Room → valor. Los valores null se convierten en "".
     * @return [Result.success] si la fila fue insertada; [Result.failure] con detalle del error.
     */
    suspend fun pushDataToSheet(
        tableName: String,
        rowData: Map<String, Any?>,
    ):Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mapping = requireNotNull(mappingConfig.getTableMapping(tableName)) {
                "Tabla '$tableName' no encontrada en sheets_structure.json. " +
                "Tablas registradas: ${mappingConfig.registeredTables}"
            }
            val row = requireNotNull(mappingConfig.buildRow(tableName, rowData)) {
                "buildRow devolvió null para '$tableName'"
            }

            val bodyJson = buildJsonObject {
                put("values", JsonArray(listOf(
                    JsonArray(row.map { JsonPrimitive(it?.toString() ?: "") })
                )))
            }.toString()

            val token = authManager.getAccessToken()
            val url   = "$baseUrl/$spreadsheetId/values/" +
                        "${mapping.sheetTabName}:append" +
                        "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(bodyJson.toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) {
                    "Sheets append FAILED [${resp.code}] ${mapping.sheetTabName}: " +
                    resp.body?.string()
                }
            }
            Log.d(TAG, "pushDataToSheet OK → ${mapping.sheetTabName} (${row.size} cols)")
        }.map {}   // coerce Result<Int> → Result<Unit>
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    /**
     * Lee todas las filas de la pestaña correspondiente a [tableName].
     *
     * Asume que la primera fila de la hoja contiene los encabezados.
     *
     * @return [Result.success] con una lista de mapas `columnName → value` por cada fila
     *         de datos; [Result.failure] con detalle del error.
     */
    suspend fun pullSheetData(
        tableName: String,
    ): Result<List<Map<String, String>>> = withContext(Dispatchers.IO) {
        runCatching {
            val mapping = requireNotNull(mappingConfig.getTableMapping(tableName)) {
                "Tabla '$tableName' no encontrada en sheets_structure.json"
            }

            val token = authManager.getAccessToken()
            val url   = "$baseUrl/$spreadsheetId/values/${mapping.sheetTabName}" +
                        "?majorDimension=ROWS"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val responseBody = httpClient.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) {
                    "Sheets get FAILED [${resp.code}] ${mapping.sheetTabName}: " +
                    resp.body?.string()
                }
                resp.body!!.string()
            }

            parseSheetResponse(responseBody).also {
                Log.d(TAG, "pullSheetData OK ← ${mapping.sheetTabName} (${it.size} rows)")
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Parsea la respuesta JSON de la API `values.get`.
     * La primera fila se usa como cabecera; las restantes como datos.
     */
    private fun parseSheetResponse(body: String): List<Map<String, String>> {
        val values = json.parseToJsonElement(body)
            .jsonObject["values"]
            ?.jsonArray
            ?: return emptyList()

        if (values.isEmpty()) return emptyList()

        val headers = values[0].jsonArray.map { it.jsonPrimitive.content }

        return values.drop(1).map { rowEl ->
            val row = rowEl.jsonArray
            headers.mapIndexed { idx, header ->
                header to (row.getOrNull(idx)?.jsonPrimitive?.content ?: "")
            }.toMap()
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    override suspend fun deleteRow(tableName: String, keyValue: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mapping = requireNotNull(mappingConfig.getTableMapping(tableName)) {
                "Tabla '$tableName' no encontrada en sheets_structure.json"
            }
            val rows = pullSheetData(tableName).getOrThrow()
            val rowIndex = rows.indexOfFirst { it[mapping.upsertKey] == keyValue }
            if (rowIndex == -1) {
                Log.w(TAG, "deleteRow: clave '$keyValue' no encontrada en ${mapping.sheetTabName} — se ignora")
                return@runCatching
            }
            val sheetRowIndex = rowIndex + 1  // +1 por la fila de encabezados (0-indexed)
            val numericId = getNumericSheetId(mapping.sheetTabName)
            val token = authManager.getAccessToken()
            val body = """{"requests":[{"deleteDimension":{"range":{"sheetId":$numericId,"dimension":"ROWS","startIndex":$sheetRowIndex,"endIndex":${sheetRowIndex + 1}}}}]}"""
            val request = Request.Builder()
                .url("$baseUrl/$spreadsheetId:batchUpdate")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toRequestBody(jsonMedia))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) {
                    "deleteRow FAILED [${resp.code}] ${mapping.sheetTabName}: ${resp.body?.string()}"
                }
            }
            Log.d(TAG, "deleteRow OK → ${mapping.sheetTabName} (key=$keyValue)")
        }
    }

    private val sheetIdCache = mutableMapOf<String, Int>()

    private suspend fun getNumericSheetId(tabName: String): Int {
        sheetIdCache[tabName]?.let { return it }
        val token = authManager.getAccessToken()
        val request = Request.Builder()
            .url("$baseUrl/$spreadsheetId?fields=sheets.properties")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        val body = httpClient.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "getSheetProperties FAILED [${resp.code}]: ${resp.body?.string()}" }
            resp.body!!.string()
        }
        json.parseToJsonElement(body).jsonObject["sheets"]?.jsonArray?.forEach { el ->
            val props = el.jsonObject["properties"]?.jsonObject ?: return@forEach
            val title = props["title"]?.jsonPrimitive?.content ?: return@forEach
            val id = props["sheetId"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
            sheetIdCache[title] = id
        }
        return sheetIdCache[tabName] ?: error("Tab '$tabName' no encontrado en la hoja")
    }

    private companion object {
        const val TAG = "GoogleSheetsSyncSvc"
    }
}
