package com.checkingcontainer.core.model

data class Inspection(
    val id: Long = 0,
    val containerNo: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: InspStatus = InspStatus.INSP,
    val ptiInstruction: PtiInstruction? = null,
    val deployedAs: String? = null,
    val technicianId: Long = 0,
    val technicianName: String = "",
    val location: String = "",
    val observations: String = "",
    // Campos de digitación — poblados por la web app vía Firestore
    val idDigitador: String? = null,
    val timestampDigitador: Long? = null,
    val statusDigitacion: String? = null,
    val noteDigitacion: String? = null,
    val avisoDigitacion: String? = null,
    val diasPendiente: Int? = null,
)
