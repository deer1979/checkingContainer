package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN idDigitador TEXT")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN timestampDigitador INTEGER")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN statusDigitacion TEXT")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN noteDigitacion TEXT")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN avisoDigitacion TEXT")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN diasPendiente INTEGER")
    }
}
