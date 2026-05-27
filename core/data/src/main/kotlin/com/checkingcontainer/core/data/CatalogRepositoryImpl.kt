package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.CatalogDao
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.CatalogRepository
import com.checkingcontainer.core.model.CatalogEntry
import com.checkingcontainer.core.model.Manufacturer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val dao: CatalogDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CatalogRepository {

    override suspend fun getManufacturers(): List<Manufacturer> = withContext(ioDispatcher) {
        dao.getAllManufacturers().map { it.toDomain() }
    }

    override suspend fun getEntriesByManufacturerAndSerie(
        manufacturerId: Long,
        serie: String,
    ): List<CatalogEntry> = withContext(ioDispatcher) {
        dao.getEntriesByManufacturerAndSerie(manufacturerId, serie).map { it.toDomain() }
    }

    override suspend fun getEntriesForManufacturer(manufacturerId: Long): List<CatalogEntry> =
        withContext(ioDispatcher) {
            dao.getEntriesForManufacturer(manufacturerId).map { it.toDomain() }
        }

    override suspend fun insertManufacturer(manufacturer: Manufacturer): Long =
        withContext(ioDispatcher) { dao.insertManufacturer(manufacturer.toEntity()) }

    override suspend fun insertCatalogEntry(entry: CatalogEntry): Long =
        withContext(ioDispatcher) { dao.insertCatalogEntry(entry.toEntity()) }

    override suspend fun deleteCatalogEntry(id: Long) =
        withContext(ioDispatcher) { dao.deleteCatalogEntry(id) }

    override suspend fun deleteManufacturer(id: Long) =
        withContext(ioDispatcher) { dao.deleteManufacturer(id) }
}
