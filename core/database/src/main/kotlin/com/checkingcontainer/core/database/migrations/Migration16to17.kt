package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Tipo de equipo (reefer / A/C / cámara fría / chiller / otro). */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN tipoEquipo TEXT NOT NULL DEFAULT 'REEFER'")
    }
}
