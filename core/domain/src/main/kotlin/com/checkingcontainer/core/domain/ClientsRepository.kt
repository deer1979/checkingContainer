package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.Client
import kotlinx.coroutines.flow.Flow

interface ClientsRepository {
    fun observeActive(): Flow<List<Client>>
    suspend fun search(query: String): List<Client>
    suspend fun findById(id: Long): Client?
    /** Guarda (crea o edita) y sincroniza a la nube. Devuelve el id. */
    suspend fun save(client: Client): Long
    /** Baja lógica: el cliente deja de aparecer, sus estimados no se tocan. */
    suspend fun deactivate(id: Long)
}
