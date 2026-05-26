package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.checkingcontainer.core.database.entity.CatalogEntryEntity
import com.checkingcontainer.core.database.entity.ManufacturerEntity

@Dao
interface CatalogDao {

    @Query("SELECT * FROM manufacturers ORDER BY name ASC")
    suspend fun getAllManufacturers(): List<ManufacturerEntity>

    @Query("SELECT * FROM manufacturers WHERE id = :id LIMIT 1")
    suspend fun getManufacturerById(id: Long): ManufacturerEntity?

    @Query("SELECT * FROM catalog_entries WHERE manufacturerId = :manufacturerId ORDER BY modelFamily ASC")
    suspend fun getEntriesForManufacturer(manufacturerId: Long): List<CatalogEntryEntity>

    @Query("SELECT COUNT(*) FROM manufacturers")
    suspend fun manufacturerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManufacturer(manufacturer: ManufacturerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogEntry(entry: CatalogEntryEntity): Long

    @Update
    suspend fun updateManufacturer(manufacturer: ManufacturerEntity)

    @Update
    suspend fun updateCatalogEntry(entry: CatalogEntryEntity)

    @Query("DELETE FROM catalog_entries WHERE id = :id")
    suspend fun deleteCatalogEntry(id: Long)

    @Query("DELETE FROM manufacturers WHERE id = :id")
    suspend fun deleteManufacturer(id: Long)
}
