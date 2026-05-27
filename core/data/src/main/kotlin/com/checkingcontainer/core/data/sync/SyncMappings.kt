package com.checkingcontainer.core.data.sync

import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.database.entity.CatalogEntryEntity
import com.checkingcontainer.core.database.entity.ManufacturerEntity
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.UserEntity

/**
 * Extensiones que convierten entidades Room en mapas [columnName → value]
 * listos para ser enviados a Google Sheets vía [GoogleSheetsSyncService.pushDataToSheet].
 *
 * ## Reglas
 * - Solo se incluyen columnas con `sync_to_sheets = true` (definido en sheets_structure.json).
 * - Las columnas LOCAL_ONLY (`syncId`, `syncPending`, `brand`) **no** se incluyen.
 * - Los enums se serializan como `.name` (String).
 * - Los valores nullable se incluyen como null; [SheetsMappingConfig.buildRow] los convierte a "".
 */

/** Mapea [ReeferUnitEntity] → Map para la tabla `reefer_units`. */
fun ReeferUnitEntity.toSyncMap(): Map<String, Any?> = mapOf(
    "id"             to id,
    "containerNo"    to containerNo,
    "manufacturer"   to manufacturer,
    "unitModel"      to unitModel,
    "unitModelNo"    to unitModelNo,
    "unitSerialNo"   to unitSerialNo,
    "yearOfBuilt"    to yearOfBuilt,
    "createdAt"      to createdAt,
    "status"         to status.name,
    "ptiInstruction" to ptiInstruction?.name,  // null → "" en Sheets
    "unitType"       to unitType,
    "deployedAs"     to deployedAs,             // null → "" en Sheets
    "technicianId"   to technicianId,
    "technicianName" to technicianName,
    "observations"   to observations,
    // brand    → LOCAL_ONLY (logo visual en app)
    // syncId   → LOCAL_ONLY
    // syncPending → LOCAL_ONLY
)

/**
 * Mapea [UserEntity] → Map para la tabla `users`.
 *
 * ⚠️ El campo `pin` se sincroniza tal cual (plaintext).
 * Considera cifrar el valor antes de escribirlo en Sheets si el acceso
 * a la hoja no está restringido por IAM.
 */
fun UserEntity.toSyncMap(): Map<String, Any?> = mapOf(
    "id"        to id,
    "firstName" to firstName,
    "lastName"  to lastName,
    "nick"      to nick,
    "pin"       to pin,          // ⚠️ plaintext — ver KDoc
    "jobTitle"  to jobTitle.name,
    "role"      to role.name,
    "company"   to company,
    "location"  to location,
    "isActive"  to isActive,
    // syncId      → LOCAL_ONLY
    // syncPending → LOCAL_ONLY
)

// ── Tablas sin syncPending (push directo en migración) ───────────────────────

/**
 * Mapea [AnnouncementEntity] → Map para la tabla `announcements`.
 * Los anuncios son inmutables (solo INSERT, nunca UPDATE), por lo que no
 * tienen campo syncPending — se sincronizan directamente desde [SyncManager].
 */
fun AnnouncementEntity.toSyncMap(): Map<String, Any?> = mapOf(
    "id"          to id,
    "title"       to title,
    "summary"     to summary,
    "body"        to body,
    "authorName"  to authorName,
    "publishedAt" to publishedAt,
)

/** Mapea [CatalogEntryEntity] → Map para la tabla `catalog_entries`. */
fun CatalogEntryEntity.toSyncMap(): Map<String, Any?> = mapOf(
    "id"             to id,
    "manufacturerId" to manufacturerId,
    "serie"          to serie,
    "rangeStart"     to rangeStart,
    "rangeEnd"       to rangeEnd,
    "unitModel"      to unitModel,
    "unitType"       to unitType,
)

/**
 * Mapea [ManufacturerEntity] → Map para la tabla `manufacturers`.
 * [modelPrefixes] ya está almacenado como CSV (ej. "ARSKL,ARST,SKU");
 * se envía tal cual a Sheets — al leer, hacer split(',').
 */
fun ManufacturerEntity.toSyncMap(): Map<String, Any?> = mapOf(
    "id"            to id,
    "name"          to name,
    "modelPrefixes" to modelPrefixes,
)
