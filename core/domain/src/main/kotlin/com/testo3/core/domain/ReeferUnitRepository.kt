package com.testo3.core.domain

import com.testo3.core.model.ReeferUnit
import kotlinx.coroutines.flow.Flow

interface ReeferUnitRepository {
    fun observeAll(): Flow<List<ReeferUnit>>
    suspend fun getById(id: Long): ReeferUnit?
    suspend fun create(unit: ReeferUnit): Result<Long>
    suspend fun update(unit: ReeferUnit): Result<Unit>
    suspend fun delete(id: Long)
}