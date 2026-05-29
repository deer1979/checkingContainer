package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateReeferUnits(db)
        migrateCatalogEntries(db)
        seedFullCatalog(db)
    }
}

private fun migrateReeferUnits(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE reefer_units_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            containerNo TEXT NOT NULL,
            manufacturer TEXT NOT NULL DEFAULT '',
            unitModel TEXT NOT NULL DEFAULT '',
            unitModelNo TEXT NOT NULL DEFAULT '',
            unitSerialNo TEXT NOT NULL,
            yearOfBuilt TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'INSP',
            ptiInstruction TEXT,
            brand TEXT NOT NULL DEFAULT 'CARRIER',
            unitType TEXT NOT NULL DEFAULT '',
            deployedAs TEXT,
            technicianId INTEGER NOT NULL DEFAULT 0,
            technicianName TEXT NOT NULL DEFAULT '',
            observations TEXT NOT NULL DEFAULT ''
        )
        """.trimIndent(),
    )
    // unitModel (old) = model number → goes to unitModelNo
    // unitType (old)  = Brand enum  → goes to brand
    // unitModel (new) = commercial family, starts empty
    // unitType (new)  = service code, starts empty
    db.execSQL(
        """
        INSERT INTO reefer_units_new
            (id, containerNo, manufacturer, unitModel, unitModelNo, unitSerialNo, yearOfBuilt,
             createdAt, status, ptiInstruction, brand, unitType, deployedAs, technicianId,
             technicianName, observations)
        SELECT
            id, containerNo, manufacturer, '', unitModel, unitSerialNo, yearOfBuilt,
            createdAt, status, ptiInstruction, unitType, '', deployedAs, technicianId,
            technicianName, observations
        FROM reefer_units
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE reefer_units")
    db.execSQL("ALTER TABLE reefer_units_new RENAME TO reefer_units")
}

private fun migrateCatalogEntries(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS catalog_entries")
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS catalog_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            manufacturerId INTEGER NOT NULL,
            serie TEXT NOT NULL,
            rangeStart INTEGER NOT NULL DEFAULT 0,
            rangeEnd INTEGER NOT NULL DEFAULT 9999,
            unitModel TEXT NOT NULL,
            unitType TEXT NOT NULL DEFAULT ''
        )
        """.trimIndent(),
    )
}

fun seedManufacturers(db: SupportSQLiteDatabase) {
    db.execSQL("INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Carrier Transicold', '69NT40,69NT20,X2')")
    db.execSQL("INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Star Cool', 'SCI-')")
    db.execSQL("INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Thermo King', 'SL-,T-,SLX,Mag')")
    db.execSQL("INSERT OR IGNORE INTO manufacturers (name, modelPrefixes) VALUES ('Daikin', 'NaturaLINE,Maverick')")
}

fun seedFullCatalog(db: SupportSQLiteDatabase) {
    // Carrier ThinLINE — series 489, 511, 521, 541
    val carrierEntries = listOf(
        // 489 series
        Triple("489", 0, 999) to ("ThinLINE" to "T297PL"),
        Triple("489", 100, 100) to ("ThinLINE" to "T305PL"),
        // 511 series
        Triple("511", 0, 999) to ("ThinLINE" to "T268PL"),
        Triple("511", 0, 999) to ("ThinLINE" to "T297PL"),
        Triple("511", 200, 299) to ("ThinLINE" to "T294PL"),
        Triple("511", 300, 399) to ("ThinLINE" to "T285PL"),
        Triple("511", 310, 310) to ("ThinLINE" to "T296PL"),
        Triple("511", 344, 344) to ("ThinLINE" to "T296PL"),
        Triple("511", 359, 359) to ("ThinLINE" to "T296PL"),
        // 521 series
        Triple("521", 0, 999) to ("ThinLINE" to "T268PL"),
        // 541 series
        Triple("541", 1, 199) to ("ThinLINE" to "T316PL"),
        Triple("541", 1, 199) to ("ThinLINE" to "T325PL"),
        Triple("541", 200, 299) to ("ThinLINE" to "T317PL"),
        Triple("541", 300, 499) to ("ThinLINE" to "T318PL"),
        Triple("541", 306, 306) to ("ThinLINE" to "T354PL"),
        Triple("541", 314, 314) to ("ThinLINE" to "T354PL"),
        Triple("541", 328, 328) to ("ThinLINE" to "T354PL"),
        Triple("541", 500, 599) to ("ThinLINE" to "T363PL"),
        Triple("541", 505, 505) to ("ThinLINE" to "T368PL"),
        Triple("541", 508, 508) to ("ThinLINE" to "T368PL"),
        Triple("541", 509, 509) to ("ThinLINE" to "T368PL"),
        // EliteLINE — series 531, 551
        Triple("531", 1, 199) to ("EliteLINE" to "T292PL"),
        Triple("531", 300, 399) to ("EliteLINE" to "T309PL"),
        Triple("551", 1, 199) to ("EliteLINE" to "T320PL"),
        Triple("551", 300, 399) to ("EliteLINE" to "T322PL"),
        Triple("551", 400, 425) to ("EliteLINE" to "T327PL"),
        Triple("551", 500, 599) to ("EliteLINE" to "T334PL"),
        // PrimeLINE — series 561, 565
        Triple("561", 1, 199) to ("PrimeLINE" to "T340PL"),
        Triple("561", 19, 19) to ("PrimeLINE" to "T359PL"),
        Triple("561", 200, 299) to ("PrimeLINE" to "T362PL"),
        Triple("561", 201, 201) to ("PrimeLINE" to "T364PL"),
        Triple("561", 300, 399) to ("PrimeLINE EDGE" to "T365PL"),
        Triple("561", 500, 599) to ("PrimeLINE" to "T362PL"),
        Triple("565", 200, 299) to ("PrimeLINE" to "T362PL"),
        Triple("565", 500, 599) to ("PrimeLINE" to "T362PL"),
        // PrimeLINE EDGE — series 571
        Triple("571", 100, 199) to ("PrimeLINE EDGE" to "T372PL"),
        Triple("571", 200, 299) to ("PrimeLINE EDGE" to "T380PL"),
        Triple("571", 300, 399) to ("PrimeLINE EDGE" to "T372PL"),
        Triple("571", 500, 599) to ("PrimeLINE EDGE" to "T380PL"),
        // NaturaLINE — series 601
        Triple("601", 1, 99) to ("NaturaLINE" to "T349PL"),
        Triple("601", 100, 199) to ("NaturaLINE" to "T370PL"),
        // OptimaLINE — series 701
        Triple("701", 1, 99) to ("OptimaLINE" to "T383PL"),
        Triple("701", 100, 199) to ("OptimaLINE" to "T384PL"),
    )

    carrierEntries.forEach { (serieRange, familyCode) ->
        val (serie, start, end) = serieRange
        val (unitModel, unitType) = familyCode
        db.execSQL(
            "INSERT INTO catalog_entries (manufacturerId, serie, rangeStart, rangeEnd, unitModel, unitType) VALUES ((SELECT id FROM manufacturers WHERE name = 'Carrier Transicold' LIMIT 1), ?, ?, ?, ?, ?)",
            arrayOf(serie, start, end, unitModel, unitType),
        )
    }
}
