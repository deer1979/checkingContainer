package com.checkingcontainer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.checkingcontainer.core.database.dao.ClientDao
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.InspectionEntity
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.User
import com.checkingcontainer.core.network.AnonymousAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Los catálogos compartidos (clientes y equipos) se refrescan en cada inicio de
 * sesión porque antes solo se descargaban en la primera instalación: un cliente
 * creado por un compañero en otro teléfono no llegaba nunca.
 *
 * Ese refresco provocó una pérdida de datos en campo: `reefer_units` se
 * reinsertaba con REPLACE, que en SQLite **borra** la fila antes de insertarla, y
 * `inspections` cuelga de ella con ON DELETE CASCADE. Cada login borraba todas
 * las inspecciones y el estimado se quedaba sin contenedor ni datos de placa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SincronizacionCatalogosTest {

    private val userDao: UserDao = mockk(relaxed = true)
    private val estimadoDao: EstimadoDao = mockk(relaxed = true)
    private val clientDao: ClientDao = mockk(relaxed = true)
    private val inspectionDao: InspectionDao = mockk(relaxed = true)
    private val reeferUnitDao: ReeferUnitDao = mockk(relaxed = true)
    private val firestoreService: FirestoreService = mockk(relaxed = true)
    private val anonymousAuth: AnonymousAuth = mockk(relaxed = true)
    private val estimadosRepo: EstimadosRepository = mockk(relaxed = true)
    private val dataStore = FakeDataStoreCatalogos()

    private val tecnico: User = mockk(relaxed = true)

    private fun repositorio(dispatcher: TestDispatcher) = BootstrapRepositoryImpl(
        userDao = userDao,
        estimadoDao = estimadoDao,
        clientDao = clientDao,
        inspectionDao = inspectionDao,
        reeferUnitDao = reeferUnitDao,
        firestoreService = firestoreService,
        anonymousAuth = anonymousAuth,
        estimadosRepo = estimadosRepo,
        dataStore = dataStore,
        ioDispatcher = dispatcher,
        applicationScope = TestScope(dispatcher),
    )

    @Test
    fun `el login baja los clientes para que aparezcan los del companero`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { firestoreService.fetchAllClients() } returns listOf(mockk(), mockk())
        coEvery { inspectionDao.count() } returns 5

        repositorio(dispatcher).syncRecentPublico(tecnico)

        coVerify(exactly = 2) { clientDao.upsert(any()) }
    }

    @Test
    fun `si no quedan inspecciones locales se reponen desde la nube`() = runTest {
        // Es el caso de los teléfonos donde la cascada ya se llevó las inspecciones.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val remotas: List<InspectionEntity> = listOf(mockk(), mockk(), mockk())
        coEvery { inspectionDao.count() } returns 0
        coEvery { firestoreService.fetchAllInspections() } returns remotas

        repositorio(dispatcher).syncRecentPublico(tecnico)

        coVerify(exactly = 3) { inspectionDao.upsert(any()) }
    }

    @Test
    fun `con inspecciones locales no se toca nada`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { inspectionDao.count() } returns 12

        repositorio(dispatcher).syncRecentPublico(tecnico)

        coVerify(exactly = 0) { firestoreService.fetchAllInspections() }
        coVerify(exactly = 0) { inspectionDao.upsert(any()) }
    }
}

private class FakeDataStoreCatalogos : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }
}
