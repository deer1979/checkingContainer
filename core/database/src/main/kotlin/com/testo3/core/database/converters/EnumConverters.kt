package com.testo3.core.database.converters

import androidx.room.TypeConverter
import com.testo3.core.model.JobTitle
import com.testo3.core.model.UserRole

/**
 * Room needs explicit converters for enums — it stores the name() string.
 * Adding new enum values is backward-compatible; renames would require a
 * migration step to rewrite old rows.
 */
class EnumConverters {

    @TypeConverter
    fun userRoleToString(value: UserRole?): String? = value?.name

    @TypeConverter
    fun stringToUserRole(value: String?): UserRole? =
        value?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun jobTitleToString(value: JobTitle?): String? = value?.name

    @TypeConverter
    fun stringToJobTitle(value: String?): JobTitle? =
        value?.let { runCatching { JobTitle.valueOf(it) }.getOrNull() }
}
