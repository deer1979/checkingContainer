package com.checkingcontainer.core.database.converters

import androidx.room.TypeConverter
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.JobTitle
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.UnitType
import com.checkingcontainer.core.model.UserRole

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

    @TypeConverter
    fun inspStatusToString(value: InspStatus?): String? = value?.name

    @TypeConverter
    fun stringToInspStatus(value: String?): InspStatus? =
        value?.let { runCatching { InspStatus.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun ptiInstructionToString(value: PtiInstruction?): String? = value?.name

    @TypeConverter
    fun stringToPtiInstruction(value: String?): PtiInstruction? =
        value?.let { runCatching { PtiInstruction.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun unitTypeToString(value: UnitType?): String? = value?.name

    @TypeConverter
    fun stringToUnitType(value: String?): UnitType? =
        value?.let { runCatching { UnitType.valueOf(it) }.getOrNull() }
}
