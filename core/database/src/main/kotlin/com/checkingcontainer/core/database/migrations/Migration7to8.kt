package com.checkingcontainer.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reefer_units ADD COLUMN syncPending INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE users ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE users ADD COLUMN syncPending INTEGER NOT NULL DEFAULT 1")
        // Assign a unique hex ID to all existing records so they can be pushed to Supabase
        db.execSQL("UPDATE reefer_units SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
        db.execSQL("UPDATE users SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
    }
}
