package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Mediciones BLE capturadas en el estimado (JSON, igual que damages). */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE estimados ADD COLUMN mediciones TEXT NOT NULL DEFAULT '[]'")
    }
}
