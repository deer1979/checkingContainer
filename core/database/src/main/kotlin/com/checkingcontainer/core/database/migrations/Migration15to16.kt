package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Catálogo de clientes (datos SRI para facturar) + referencia/snapshot del
 * cliente en el estimado (clientId + identificación/dirección/teléfono/email
 * congelados al momento de asignarlo, para que el PDF no cambie si el
 * cliente se edita después).
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS clients (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                razonSocial TEXT NOT NULL,
                idType TEXT NOT NULL DEFAULT 'RUC',
                idNumber TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                direccion TEXT NOT NULL DEFAULT '',
                telefono TEXT NOT NULL DEFAULT '',
                contacto TEXT NOT NULL DEFAULT '',
                notas TEXT NOT NULL DEFAULT '',
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE estimados ADD COLUMN clientId INTEGER")
        db.execSQL("ALTER TABLE estimados ADD COLUMN clientIdNumber TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN clientDireccion TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN clientTelefono TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN clientEmail TEXT NOT NULL DEFAULT ''")
    }
}
