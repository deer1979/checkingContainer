package com.checkingcontainer.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

internal fun DocumentSnapshot.safeLong(field: String): Long? =
    when (val v = get(field)) {
        null -> null
        is Long -> v
        is Number -> v.toLong()
        is Timestamp -> v.toDate().time
        else -> null
    }

internal fun DocumentSnapshot.safeInt(field: String): Int? =
    when (val v = get(field)) {
        null -> null
        is Int -> v
        is Number -> v.toInt()
        else -> null
    }

internal fun DocumentSnapshot.safeString(field: String): String? =
    when (val v = get(field)) {
        null -> null
        is String -> v
        else -> v.toString()
    }

internal data class DigitacionUpdate(
    val id: Long,
    val idDigitador: String?,
    val timestampDigitador: Long?,
    val statusDigitacion: String?,
    val noteDigitacion: String?,
    val avisoDigitacion: String?,
    val diasPendiente: Int?,
)
