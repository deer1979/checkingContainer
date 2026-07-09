package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.User

interface BootstrapRepository {
    /** Descarga usuarios, equipos, inspecciones y estimados de Firestore si Room está vacío. */
    suspend fun syncIfNeeded()

    /**
     * Sync ligero al autenticarse: estimados ABIERTOS + creados en las últimas 24h
     * (admin: todos; técnico: solo los suyos) y reconciliación de cierres remotos.
     * Corre en un scope de aplicación (fire-and-forget): no bloquea el login y
     * sobrevive a la navegación.
     */
    fun syncRecentAsync(user: User)
}
