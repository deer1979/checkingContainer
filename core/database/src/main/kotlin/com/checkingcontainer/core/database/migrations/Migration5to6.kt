package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS announcements (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                body TEXT NOT NULL,
                authorName TEXT NOT NULL,
                publishedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        seedAnnouncements(db)
        seedExtraCatalog(db)
    }
}

fun seedAnnouncements(db: SupportSQLiteDatabase) {
    val now = System.currentTimeMillis()
    val announcements = listOf(
        Triple("a-001", "Bienvenido a CheckingContainer",
            "Una app offline para inspección de unidades reefer.") to
            "Esta versión integra Material 3, Jetpack Compose, Hilt y Room. " +
            "El OCR reconoce placas de datos de Carrier, Star Cool, Thermo King y Daikin.",
        Triple("a-002", "Cero red, cero costo",
            "Todo el procesamiento se hace en el dispositivo.") to
            "Sin facturación, sin API keys, sin servicios en la nube. " +
            "Aprovechamos la cámara y el procesador del propio teléfono.",
        Triple("a-003", "Arquitectura por capas",
            "Multi-módulo · Hilt · Room · Compose.") to
            "Cada feature vive en su módulo. La capa de datos está aislada por interfaces; " +
            "cuando migremos a la nube cambiaremos una sola línea de DI.",
    )
    announcements.forEachIndexed { index, (meta, body) ->
        val (id, title, summary) = meta
        val publishedAt = now - (announcements.size - index) * 3_600_000L
        db.execSQL(
            "INSERT OR IGNORE INTO announcements (id, title, summary, body, authorName, publishedAt) VALUES (?, ?, ?, ?, 'Equipo CheckingContainer', ?)",
            arrayOf(id, title, summary, body, publishedAt),
        )
    }
}

fun seedExtraCatalog(db: SupportSQLiteDatabase) {
    val starCoolFamilies = listOf(
        "SCI-40" to "Star Cool Integrated 40ft (estándar)",
        "SCI-40-CA" to "Star Cool Integrated 40ft (Atmósfera Controlada)",
        "SCI-20" to "Star Cool Integrated 20ft",
    )
    starCoolFamilies.forEach { (family, desc) ->
        db.execSQL(
            "INSERT OR IGNORE INTO catalog_entries (manufacturerId, modelFamily, description, serialRangeStart, serialRangeEnd) VALUES ((SELECT id FROM manufacturers WHERE name = 'Star Cool' LIMIT 1), ?, ?, NULL, NULL)",
            arrayOf(family, desc),
        )
    }

    val thermoKingFamilies = listOf(
        "SL-100" to "Thermo King SL-100",
        "SL-200" to "Thermo King SL-200",
        "SL-400" to "Thermo King SL-400",
        "SLX-i" to "Thermo King SLX Integra",
        "T-1200R" to "Thermo King T-1200R",
        "Magnum Plus" to "Thermo King Magnum Plus",
    )
    thermoKingFamilies.forEach { (family, desc) ->
        db.execSQL(
            "INSERT OR IGNORE INTO catalog_entries (manufacturerId, modelFamily, description, serialRangeStart, serialRangeEnd) VALUES ((SELECT id FROM manufacturers WHERE name = 'Thermo King' LIMIT 1), ?, ?, NULL, NULL)",
            arrayOf(family, desc),
        )
    }

    val daikinFamilies = listOf(
        "NaturaLINE" to "Daikin NaturaLINE (CO2 natural refrigerant)",
        "Maverick II" to "Daikin Maverick II",
        "Enviroline" to "Daikin Enviroline",
    )
    daikinFamilies.forEach { (family, desc) ->
        db.execSQL(
            "INSERT OR IGNORE INTO catalog_entries (manufacturerId, modelFamily, description, serialRangeStart, serialRangeEnd) VALUES ((SELECT id FROM manufacturers WHERE name = 'Daikin' LIMIT 1), ?, ?, NULL, NULL)",
            arrayOf(family, desc),
        )
    }
}
