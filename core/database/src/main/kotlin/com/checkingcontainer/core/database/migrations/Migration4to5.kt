package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN status TEXT NOT NULL DEFAULT 'INSP'")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN ptiInstruction TEXT")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN unitType TEXT NOT NULL DEFAULT 'CARRIER'")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN deployedAs TEXT")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN technicianId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN technicianName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN observations TEXT NOT NULL DEFAULT ''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS manufacturers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                modelPrefixes TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalog_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                manufacturerId INTEGER NOT NULL,
                modelFamily TEXT NOT NULL,
                description TEXT NOT NULL,
                serialRangeStart INTEGER,
                serialRangeEnd INTEGER
            )
            """.trimIndent(),
        )

        seedCatalog(db)
    }
}

fun seedCatalog(db: SupportSQLiteDatabase) {
    db.execSQL(
        "INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Carrier', '69NT40,X2')",
    )
    db.execSQL(
        "INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Star Cool', 'SCI-')",
    )
    db.execSQL(
        "INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Thermo King', 'SL-,T-,SLX,Mag')",
    )
    db.execSQL(
        "INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Daikin', 'NaturaLINE,Maverick')",
    )

    // Carrier model families — ranges to be populated by admin
    val carrierFamilies = listOf(
        "69NT40-489" to "ThinLINE 40 (489 series)",
        "69NT40-511" to "ThinLINE 40 (511 series)",
        "69NT40-521" to "ThinLINE 40 (521 series)",
        "69NT40-531" to "ThinLINE 40 (531 series)",
        "69NT40-541" to "ThinLINE 40 (541 series)",
        "69NT40-551" to "ThinLINE 40 (551 series)",
    )
    carrierFamilies.forEach { (family, desc) ->
        db.execSQL(
            """
            INSERT OR IGNORE INTO catalog_entries
                (manufacturerId, modelFamily, description, serialRangeStart, serialRangeEnd)
            VALUES (
                (SELECT id FROM manufacturers WHERE name = 'Carrier' LIMIT 1),
                '$family', '$desc', NULL, NULL
            )
            """.trimIndent(),
        )
    }
}
