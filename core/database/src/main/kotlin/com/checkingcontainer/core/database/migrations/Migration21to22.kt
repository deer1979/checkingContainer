package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Observaciones y recomendaciones del estimado: lo que el técnico vio pero no
 * cobra en este trabajo. Nace vacío en los estimados existentes.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE estimados ADD COLUMN observaciones TEXT NOT NULL DEFAULT ''")
    }
}
