package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.ReeferUnit
import kotlinx.coroutines.flow.Flow

interface ReeferUnitRepository {
    fun observeAll(): Flow<List<ReeferUnit>>
    fun observeLast24h(): Flow<List<ReeferUnit>>
    fun observeLatestByContainerNo(containerNo: String): Flow<ReeferUnit?>
    suspend fun getById(id: Long): ReeferUnit?
    suspend fun getLatestByContainerNo(containerNo: String): ReeferUnit?
    suspend fun findTodayByContainerNo(containerNo: String): ReeferUnit?
    suspend fun getAllByContainerNo(containerNo: String): List<ReeferUnit>
    suspend fun create(unit: ReeferUnit): Result<Long>
    suspend fun update(unit: ReeferUnit): Result<Unit>
    suspend fun delete(id: Long)
}
