package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.checkingcontainer.core.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY lastName, firstName")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE nick = :nick LIMIT 1")
    suspend fun findByNick(nick: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("UPDATE users SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM users WHERE syncPending = 1")
    suspend fun getPending(): List<UserEntity>

    @Query("UPDATE users SET syncPending = 0 WHERE id = :id")
    suspend fun markSynced(id: Long)
}
