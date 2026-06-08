package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v12: adjuntos de anuncios (imágenes/archivos) guardados como JSON en una columna. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE announcements ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
    }
}
