package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Trazabilidad de sincronización del estimado.
 *
 * - `updatedAt`: última modificación. Sin esto, cuando la copia local y la de la
 *   nube diferían no había forma de saber cuál era la buena y se elegía a ciegas.
 * - `subidoEn`: cuándo confirmó Firestore la subida. Si es nulo o anterior a
 *   `updatedAt`, el estimado **solo existe en el teléfono** y hay que avisarlo.
 *
 * Las filas existentes arrancan con `updatedAt = createdAt` (es lo único que se
 * sabe de ellas) y `subidoEn = createdAt`, dándolas por subidas: se guardaron
 * cuando el write-through era la única vía, así que asumir lo contrario marcaría
 * como pendiente todo el historial.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE estimados ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE estimados ADD COLUMN subidoEn INTEGER")
        db.execSQL("UPDATE estimados SET updatedAt = createdAt, subidoEn = createdAt")
    }
}
