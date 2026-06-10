package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Crear tabla nueva con esquema correcto (sin columnas obsoletas)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS estimados_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                inspectionId INTEGER NOT NULL,
                containerNo TEXT NOT NULL,
                manufacturer TEXT NOT NULL DEFAULT '',
                unitModel TEXT NOT NULL DEFAULT '',
                unitModelNo TEXT NOT NULL DEFAULT '',
                unitSerialNo TEXT NOT NULL DEFAULT '',
                yearOfBuilt TEXT NOT NULL DEFAULT '',
                unitType TEXT NOT NULL DEFAULT '',
                clientName TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                technicianId INTEGER NOT NULL DEFAULT 0,
                technicianName TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                approvedAt INTEGER,
                closedAt INTEGER,
                status TEXT NOT NULL DEFAULT 'ABIERTO',
                damages TEXT NOT NULL DEFAULT '[]',
                hasIva INTEGER NOT NULL DEFAULT 0,
                reportUrl TEXT
            )
            """.trimIndent(),
        )
        // Migrar datos existentes (campos compatibles)
        db.execSQL(
            """
            INSERT INTO estimados_new
                (id, inspectionId, containerNo, clientName, technicianId, technicianName,
                 location, createdAt, closedAt, status, reportUrl)
            SELECT
                id, inspectionId, containerNo, clientName, technicianId, technicianName,
                location, createdAt, closedAt, status, reportUrl
            FROM estimados
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE estimados")
        db.execSQL("ALTER TABLE estimados_new RENAME TO estimados")
    }
}
