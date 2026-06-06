package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.ReeferEquipment

interface ReeferEquipmentRepository {
    /** Busca equipo solo en Room. Gratis, sin lecturas Firestore. */
    suspend fun findByContainerNo(containerNo: String): ReeferEquipment?

    /** Busca equipo en Firestore (match exacto). Cuesta 1 lectura si existe, 0 si no. */
    suspend fun fetchFromFirestore(containerNo: String): ReeferEquipment?

    /** Guarda en Room (IGNORE si ya existe) y sube a Firestore. */
    suspend fun upsert(equipment: ReeferEquipment)
}
