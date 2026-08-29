package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Fotos de las observaciones: la evidencia de lo que se le advierte al cliente
 * y no se cobra en este trabajo. Se guardan como array JSON de URLs, igual que
 * las fotos de los ítems.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE estimados ADD COLUMN observacionesFotos TEXT NOT NULL DEFAULT '[]'")
    }
}
