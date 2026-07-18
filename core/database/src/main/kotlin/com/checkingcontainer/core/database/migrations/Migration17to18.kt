package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Ficha técnica de la placa (JSON de pares etiqueta→valor). */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN fichaTecnica TEXT NOT NULL DEFAULT '[]'")
    }
}
