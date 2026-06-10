package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS estimados (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                inspectionId INTEGER NOT NULL,
                containerNo TEXT NOT NULL,
                clientName TEXT NOT NULL DEFAULT '',
                technicianId INTEGER NOT NULL DEFAULT 0,
                technicianName TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                closedAt INTEGER,
                status TEXT NOT NULL DEFAULT 'ABIERTO',
                damageDescription TEXT NOT NULL DEFAULT '',
                damagePhotos TEXT NOT NULL DEFAULT '[]',
                repairDescription TEXT NOT NULL DEFAULT '',
                repairPhotos TEXT NOT NULL DEFAULT '[]',
                reportUrl TEXT
            )
            """.trimIndent(),
        )
    }
}
