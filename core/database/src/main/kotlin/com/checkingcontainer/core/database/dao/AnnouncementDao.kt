package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AnnouncementEntity?

    @Query("SELECT COUNT(*) FROM announcements WHERE publishedAt > :seen")
    fun observeUnreadCount(seen: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(announcement: AnnouncementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(announcements: List<AnnouncementEntity>)

    @Query("DELETE FROM announcements WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM announcements")
    suspend fun deleteAll()

    @Query("DELETE FROM announcements WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Transaction
    suspend fun replaceAll(announcements: List<AnnouncementEntity>) {
        deleteAll()
        insertAll(announcements)
    }

    /**
     * Sincroniza la tabla con el snapshot remoto sin vaciarla primero:
     * evita la invalidación "tabla vacía → tabla llena" que hace parpadear la UI.
     */
    @Transaction
    suspend fun replaceAllDiff(announcements: List<AnnouncementEntity>) {
        insertAll(announcements)
        deleteNotIn(announcements.map { it.id })
    }
}
