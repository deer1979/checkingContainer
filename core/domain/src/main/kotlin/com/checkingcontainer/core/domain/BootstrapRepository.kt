package com.checkingcontainer.core.domain

interface BootstrapRepository {
    /** Descarga usuarios, equipos, inspecciones y estimados de Firestore si Room está vacío. */
    suspend fun syncIfNeeded()
}
