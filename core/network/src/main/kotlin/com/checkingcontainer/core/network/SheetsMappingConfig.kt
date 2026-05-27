package com.checkingcontainer.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── DTOs internos para parsear sheets_structure.json ────────────────────────

@Serializable
private data class SheetsStructure(
    val tables: Map<String, TableDef>,
)

@Serializable
private data class TableDef(
    @SerialName("suggested_sheet_name") val suggestedSheetName: String,
    @SerialName("unique_key_column")    val uniqueKeyColumn: String,
    val columns: List<ColumnDef>,
)

@Serializable
private data class ColumnDef(
    val name: String,
    @SerialName("sync_to_sheets") val syncToSheets: Boolean,
    @SerialName("kotlin_type")    val kotlinType: String = "",
)

// ── API pública ──────────────────────────────────────────────────────────────

/**
 * Mapeo de una tabla Room a su pestaña en Google Sheets.
 *
 * @param sheetTabName Nombre de la pestaña en la hoja (p.ej. "ReeferUnits").
 * @param syncColumns  Columnas a sincronizar, en el mismo orden que en sheets_structure.json.
 *                     Solo incluye columnas con sync_to_sheets = true.
 * @param upsertKey    Columna recomendada como clave de upsert / deduplicación.
 */
data class TableMapping(
    val sheetTabName: String,
    val syncColumns: List<String>,
    val upsertKey: String,
)

/**
 * Parsea el JSON de [sheets_structure.json] y expone el mapeo
 * tabla-Room → pestaña de Sheets con sus columnas sincronizables.
 *
 * Se instancia una sola vez vía Hilt (`@Singleton`) a partir de la cadena
 * inyectada con `@Named("sheets_structure_json")`.
 */
class SheetsMappingConfig(structureJson: String) {

    private val parser = Json { ignoreUnknownKeys = true }

    /** Mapa: nombre de tabla Room → [TableMapping]. */
    private val tableMap: Map<String, TableMapping> = run {
        val structure = parser.decodeFromString<SheetsStructure>(structureJson)
        structure.tables.mapValues { (_, def) ->
            TableMapping(
                sheetTabName = def.suggestedSheetName,
                syncColumns  = def.columns.filter { it.syncToSheets }.map { it.name },
                upsertKey    = def.uniqueKeyColumn,
            )
        }
    }

    /** Devuelve el mapeo para [tableName], o null si no está registrada. */
    fun getTableMapping(tableName: String): TableMapping? = tableMap[tableName]

    /**
     * Convierte [rowData] (clave = nombre de columna Room, valor = dato) en una lista
     * ordenada según las columnas sincronizables definidas para [tableName].
     *
     * Las columnas LOCAL_ONLY (syncToSheets = false) son ignoradas automáticamente.
     * Los valores null se convierten en cadena vacía.
     *
     * @return Lista de valores en el orden de las columnas de Sheets, o null si la tabla
     *         no está registrada.
     */
    fun buildRow(tableName: String, rowData: Map<String, Any?>): List<Any?>? {
        val mapping = tableMap[tableName] ?: return null
        return mapping.syncColumns.map { col -> rowData[col]?.toString() ?: "" }
    }

    /** Todas las tablas registradas (para iteración / diagnóstico). */
    val registeredTables: Set<String> get() = tableMap.keys
}
