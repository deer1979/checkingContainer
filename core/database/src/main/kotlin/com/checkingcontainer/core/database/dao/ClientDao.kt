package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.checkingcontainer.core.database.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients WHERE isActive = 1 ORDER BY razonSocial COLLATE NOCASE")
    fun observeActive(): Flow<List<ClientEntity>>

    @Query(
        """SELECT * FROM clients WHERE isActive = 1
           AND (razonSocial LIKE '%' || :query || '%' OR idNumber LIKE '%' || :query || '%')
           ORDER BY razonSocial COLLATE NOCASE""",
    )
    suspend fun search(query: String): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ClientEntity?

    @Query("SELECT * FROM clients WHERE idNumber = :idNumber AND idNumber != '' LIMIT 1")
    suspend fun findByIdNumber(idNumber: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClientEntity): Long
}
