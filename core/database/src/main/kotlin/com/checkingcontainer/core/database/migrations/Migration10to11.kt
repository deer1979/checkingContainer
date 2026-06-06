package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Paso 1: guardar datos de inspección antes de tocar reefer_units ──────
        db.execSQL("""
            CREATE TABLE inspections_staging (
                id INTEGER NOT NULL,
                containerNo TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'INSP',
                ptiInstruction TEXT,
                deployedAs TEXT,
                technicianId INTEGER NOT NULL DEFAULT 0,
                technicianName TEXT NOT NULL DEFAULT '',
                observations TEXT NOT NULL DEFAULT '',
                idDigitador TEXT,
                timestampDigitador INTEGER,
                statusDigitacion TEXT,
                noteDigitacion TEXT,
                avisoDigitacion TEXT,
                diasPendiente INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO inspections_staging
                (id, containerNo, createdAt, status, ptiInstruction, deployedAs,
                 technicianId, technicianName, observations,
                 idDigitador, timestampDigitador, statusDigitacion,
                 noteDigitacion, avisoDigitacion, diasPendiente)
            SELECT id, containerNo, createdAt, status, ptiInstruction, deployedAs,
                   technicianId, technicianName, observations,
                   idDigitador, timestampDigitador, statusDigitacion,
                   noteDigitacion, avisoDigitacion, diasPendiente
            FROM reefer_units
        """.trimIndent())

        // ── Paso 2: construir nueva tabla de equipos ──────────────────────────────
        db.execSQL("""
            CREATE TABLE reefer_units_new (
                containerNo TEXT NOT NULL PRIMARY KEY,
                manufacturer TEXT NOT NULL DEFAULT '',
                unitModel TEXT NOT NULL DEFAULT '',
                unitModelNo TEXT NOT NULL DEFAULT '',
                unitSerialNo TEXT NOT NULL DEFAULT '',
                yearOfBuilt TEXT NOT NULL DEFAULT '',
                brand TEXT NOT NULL DEFAULT 'CARRIER',
                unitType TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())

        db.execSQL("""
            INSERT OR IGNORE INTO reefer_units_new
                (containerNo, manufacturer, unitModel, unitModelNo, unitSerialNo, yearOfBuilt, brand, unitType)
            SELECT containerNo, manufacturer, unitModel, unitModelNo, unitSerialNo, yearOfBuilt, brand, unitType
            FROM reefer_units
            GROUP BY containerNo
        """.trimIndent())

        // ── Paso 3: reemplazar tabla vieja y renombrar ────────────────────────────
        db.execSQL("DROP TABLE reefer_units")
        db.execSQL("ALTER TABLE reefer_units_new RENAME TO reefer_units")

        // ── Paso 4: crear inspections con FK a reefer_units (ya renombrada) ──────
        db.execSQL("""
            CREATE TABLE inspections (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                containerNo TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'INSP',
                ptiInstruction TEXT,
                deployedAs TEXT,
                technicianId INTEGER NOT NULL DEFAULT 0,
                technicianName TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                observations TEXT NOT NULL DEFAULT '',
                idDigitador TEXT,
                timestampDigitador INTEGER,
                statusDigitacion TEXT,
                noteDigitacion TEXT,
                avisoDigitacion TEXT,
                diasPendiente INTEGER,
                FOREIGN KEY (containerNo) REFERENCES reefer_units(containerNo) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX index_inspections_containerNo ON inspections (containerNo)")

        // ── Paso 5: restaurar inspecciones desde staging ──────────────────────────
        db.execSQL("""
            INSERT INTO inspections
                (id, containerNo, createdAt, status, ptiInstruction, deployedAs,
                 technicianId, technicianName, location, observations,
                 idDigitador, timestampDigitador, statusDigitacion,
                 noteDigitacion, avisoDigitacion, diasPendiente)
            SELECT id, containerNo, createdAt, status, ptiInstruction, deployedAs,
                   technicianId, technicianName, '', observations,
                   idDigitador, timestampDigitador, statusDigitacion,
                   noteDigitacion, avisoDigitacion, diasPendiente
            FROM inspections_staging
        """.trimIndent())

        db.execSQL("DROP TABLE inspections_staging")
    }
}
