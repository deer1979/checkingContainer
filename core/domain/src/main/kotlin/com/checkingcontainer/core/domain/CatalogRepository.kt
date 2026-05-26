package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.CatalogEntry
import com.checkingcontainer.core.model.Manufacturer

interface CatalogRepository {
    suspend fun getManufacturers(): List<Manufacturer>
    suspend fun getEntriesForManufacturer(manufacturerId: Long): List<CatalogEntry>
    suspend fun insertManufacturer(manufacturer: Manufacturer): Long
    suspend fun insertCatalogEntry(entry: CatalogEntry): Long
    suspend fun deleteCatalogEntry(id: Long)
    suspend fun deleteManufacturer(id: Long)
}
