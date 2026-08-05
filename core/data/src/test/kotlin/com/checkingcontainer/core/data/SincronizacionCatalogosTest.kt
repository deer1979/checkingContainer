package com.checkingcontainer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.checkingcontainer.core.database.dao.ClientDao
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.EstimadoEntity
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
    fun `el login NO repone inspecciones ni toca su tabla`() = runTest {
        // Los ids de inspección son una secuencia local de cada teléfono y la
        // consulta remota mezcla todos los contenedores: reponer por id metía la
        // inspección de OTRO contenedor en el hueco de la borrada, y el estimado
        // acababa mostrando una unidad que nunca le perteneció.
        val dispatcher = StandardTestDispatcher(testScheduler)

        repositorio(dispatcher).syncRecentPublico(tecnico)

        coVerify(exactly = 0) { firestoreService.fetchAllInspections() }
        coVerify(exactly = 0) { inspectionDao.upsert(any()) }
    }

    @Test
    fun `el login nunca reescribe el contenedor de un estimado`() = runTest {
        // Deducir el contenedor a partir de una inspección no es fiable, y un
        // documento que va al cliente no puede llevar datos adivinados.
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { firestoreService.fetchOpenEstimados() } returns emptyList()
        coEvery { firestoreService.fetchEstimadosCreatedSince(any()) } returns emptyList()
        coEvery { estimadoDao.findOpenIds() } returns emptyList()

        repositorio(dispatcher).syncRecentPublico(tecnico)

        coVerify(exactly = 0) { estimadoDao.findSinContenedor() }
    }

    @Test
    fun `un estimado bajado de la nube no queda marcado como sin subir`() = runTest {
        // subidoEn es solo-local: los bajados llegan con null y el upsert directo
        // marcaba TODOS los estimados como "Sin subir" en cada login.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val remoto = EstimadoEntity(id = 9L, inspectionId = 1L, containerNo = "MSCU1234567", updatedAt = 100L)
        coEvery { firestoreService.fetchOpenEstimados() } returns listOf(remoto)
        coEvery { estimadoDao.findById(9L) } returns null

        repositorio(dispatcher).syncRecentPublico(tecnico)

        coVerify {
            estimadoDao.upsert(
                match { it.id == 9L && it.subidoEn != null && it.subidoEn!! >= it.updatedAt },
            )
        }
    }

    @Test
    fun `una copia remota no pisa trabajo local pendiente de subir`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val remoto = EstimadoEntity(id = 9L, inspectionId = 1L, containerNo = "", updatedAt = 500L)
        val localPendiente = EstimadoEntity(
            id = 9L, inspectionId = 1L, containerNo = "MSCU1234567",
            updatedAt = 400L, subidoEn = null,
        )
        coEvery { firestoreService.fetchOpenEstimados() } returns listOf(remoto)
        coEvery { estimadoDao.findById(9L) } returns localPendiente

        repositorio(dispatcher).syncRecentPublico(tecnico)

        coVerify(exactly = 0) { estimadoDao.upsert(match { it.id == 9L }) }
    }
}

private class FakeDataStoreCatalogos : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }
}
