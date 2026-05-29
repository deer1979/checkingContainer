package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite no soporta DROP COLUMN directamente — se recrea la tabla sin los campos.

        db.execSQL("""
            CREATE TABLE reefer_units_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                containerNo TEXT NOT NULL,
                manufacturer TEXT NOT NULL,
                unitModel TEXT NOT NULL,
                unitModelNo TEXT NOT NULL,
                unitSerialNo TEXT NOT NULL,
                yearOfBuilt TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                status TEXT NOT NULL,
                ptiInstruction TEXT,
                brand TEXT NOT NULL,
                unitType TEXT NOT NULL DEFAULT '',
                deployedAs TEXT,
                technicianId INTEGER NOT NULL DEFAULT 0,
                technicianName TEXT NOT NULL DEFAULT '',
                observations TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO reefer_units_new
            SELECT id, containerNo, manufacturer, unitModel, unitModelNo, unitSerialNo,
                   yearOfBuilt, createdAt, status, ptiInstruction, brand, unitType,
                   deployedAs, technicianId, technicianName, observations
            FROM reefer_units
        """.trimIndent())
        db.execSQL("DROP TABLE reefer_units")
        db.execSQL("ALTER TABLE reefer_units_new RENAME TO reefer_units")

        db.execSQL("""
            CREATE TABLE users_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                firstName TEXT NOT NULL,
                lastName TEXT NOT NULL,
                nick TEXT NOT NULL,
                pin TEXT NOT NULL,
                jobTitle TEXT NOT NULL,
                role TEXT NOT NULL,
                company TEXT NOT NULL,
                location TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO users_new
            SELECT id, firstName, lastName, nick, pin, jobTitle, role, company, location, isActive
            FROM users
        """.trimIndent())
        db.execSQL("DROP TABLE users")
        db.execSQL("ALTER TABLE users_new RENAME TO users")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_nick ON users (nick)")

        // Actualiza nombre de Carrier para que coincida con CatalogLookupUseCase
        db.execSQL("UPDATE manufacturers SET name = 'Carrier Transicold' WHERE name = 'Carrier'")
        // Inserta fabricantes faltantes si la tabla está incompleta
        seedManufacturers(db)
    }
}
