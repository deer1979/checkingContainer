package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Estimado: sitio del trabajo (cliente final) + nº de orden de trabajo del
 * contratante. Equipo: URL de la foto de su placa de datos.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE estimados ADD COLUMN sitioClienteId INTEGER")
        db.execSQL("ALTER TABLE estimados ADD COLUMN sitioNombre TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN ordenTrabajo TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN fotoPlacaUrl TEXT")
    }
}
