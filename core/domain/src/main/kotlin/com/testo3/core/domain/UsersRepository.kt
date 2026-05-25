package com.testo3.core.domain

import com.testo3.core.model.User
import kotlinx.coroutines.flow.Flow

interface UsersRepository {
    fun observeAll(): Flow<List<User>>
    suspend fun getById(id: Long): User?
    suspend fun create(user: User): Result<Long>
    suspend fun update(user: User): Result<Unit>
    suspend fun setActive(id: Long, isActive: Boolean)
    suspend fun delete(id: Long)
    suspend fun count(): Int
}
