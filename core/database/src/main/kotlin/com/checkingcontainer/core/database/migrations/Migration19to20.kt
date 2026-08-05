package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Mano de obra única por estimado.
 *
 * Antes cada ítem llevaba su propia mano de obra y su propio material; ahora los
 * ítems se cobran como cantidad × precio unitario y la mano de obra de instalar
 * los repuestos es una sola línea del estimado.
 *
 * La columna nace nula a propósito: `migrarDesdeCostosPorItem` detecta ese nulo
 * al cargar y reparte los valores viejos (material → precio unitario con
 * cantidad 1, suma de manos de obra → este total) sin alterar el total final.
 * Los ítems viven dentro del JSON de `damages`, así que no hay más columnas que
 * tocar aquí.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE estimados ADD COLUMN manoDeObraTotal REAL")
    }
}
