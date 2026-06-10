package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE estimados ADD COLUMN damages TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE estimados ADD COLUMN manufacturer TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN unitModel TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN unitModelNo TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN unitSerialNo TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN yearOfBuilt TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN unitType TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE estimados ADD COLUMN approvedAt INTEGER")
        db.execSQL("ALTER TABLE estimados ADD COLUMN hasIva INTEGER NOT NULL DEFAULT 0")
    }
}
