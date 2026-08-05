package com.checkingcontainer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.checkingcontainer.core.database.dao.ClientDao
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.network.AnonymousAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre las dos reglas que protegen el trabajo hecho en campo:
 *
 *  1. El bootstrap NO debe volver a bajar Firestore sobre una instalación que ya
 *     tiene datos locales — sobreescribiría lo capturado sin cobertura y aún no
 *     subido (la app es offline-first).
 *  2. Una fila remota corrupta debe saltarse, no abortar el bootstrap: si aborta,
 *     el marcador nunca se guarda y el reintento falla igual en cada arranque.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BootstrapRepositoryImplTest {

    private val userDao: UserDao = mockk(relaxed = true)
    private val estimadoDao: EstimadoDao = mockk(relaxed = true)
    private val clientDao: ClientDao = mockk(relaxed = true)
    private val inspectionDao: InspectionDao = mockk(relaxed = true)
    private val reeferUnitDao: ReeferUnitDao = mockk(relaxed = true)
    private val firestoreService: FirestoreService = mockk(relaxed = true)
    private val anonymousAuth: AnonymousAuth = mockk(relaxed = true)
    private val dataStore = FakePreferencesDataStore()

    private fun repositorio(dispatcher: TestDispatcher) = BootstrapRepositoryImpl(
        userDao = userDao,
        estimadoDao = estimadoDao,
        clientDao = clientDao,
        inspectionDao = inspectionDao,
        reeferUnitDao = reeferUnitDao,
        firestoreService = firestoreService,
        anonymousAuth = anonymousAuth,
        dataStore = dataStore,
        ioDispatcher = dispatcher,
        applicationScope = TestScope(dispatcher),
    )

    private suspend fun bootstrapMarcado(): Boolean =
        dataStore.data.first()[KEY_BOOTSTRAP_COMPLETED] == true

    @Test
    fun `instalacion con datos locales no vuelve a descargar y queda marcada`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { userDao.count() } returns 7

        repositorio(dispatcher).syncIfNeeded()

        // Lo esencial: no se toca Firestore, así que nada remoto pisa lo local.
        coVerify(exactly = 0) { firestoreService.fetchAllUsers() }
        coVerify(exactly = 0) { firestoreService.fetchAllEstimados() }
        coVerify(exactly = 0) { userDao.upsert(any()) }
        assertTrue(bootstrapMarcado())
    }

    @Test
    fun `con el marcador puesto no hace nada`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        dataStore.set(KEY_BOOTSTRAP_COMPLETED, true)

        repositorio(dispatcher).syncIfNeeded()

        coVerify(exactly = 0) { firestoreService.fetchAllUsers() }
        coVerify(exactly = 0) { userDao.count() }
    }

    @Test
    fun `instalacion nueva descarga todo y marca completado`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { userDao.count() } returns 0
        coEvery { firestoreService.fetchAllUsers() } returns listOf(mockk(), mockk())

        repositorio(dispatcher).syncIfNeeded()

        coVerify(exactly = 2) { userDao.upsert(any()) }
        coVerify(exactly = 1) { firestoreService.fetchAllReeferUnits() }
        coVerify(exactly = 1) { firestoreService.fetchAllEstimados() }
        coVerify(exactly = 1) { firestoreService.fetchAllClients() }
        assertTrue(bootstrapMarcado())
    }

    @Test
    fun `una fila corrupta se salta y el bootstrap igual completa`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val buena1: UserEntity = mockk()
        val mala: UserEntity = mockk()
        val buena2: UserEntity = mockk()
        coEvery { userDao.count() } returns 0
        coEvery { firestoreService.fetchAllUsers() } returns listOf(buena1, mala, buena2)
        coEvery { userDao.upsert(mala) } throws IllegalStateException("fila corrupta")

        repositorio(dispatcher).syncIfNeeded()

        // Las filas sanas entran aunque una falle…
        coVerify(exactly = 1) { userDao.upsert(buena1) }
        coVerify(exactly = 1) { userDao.upsert(buena2) }
        // …y el proceso NO queda atascado reintentando para siempre.
        assertTrue(bootstrapMarcado())
    }

    @Test
    fun `si Firestore no devuelve usuarios no se marca y se reintenta luego`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { userDao.count() } returns 0
        coEvery { firestoreService.fetchAllUsers() } returns emptyList()

        repositorio(dispatcher).syncIfNeeded()

        coVerify(exactly = 0) { firestoreService.fetchAllEstimados() }
        assertFalse(bootstrapMarcado())
    }

    @Test
    fun `un fallo de red deja el bootstrap pendiente sin propagar la excepcion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { userDao.count() } returns 0
        coEvery { firestoreService.fetchAllUsers() } throws RuntimeException("sin red")

        repositorio(dispatcher).syncIfNeeded()

        assertFalse(bootstrapMarcado())
    }

    private companion object {
        val KEY_BOOTSTRAP_COMPLETED = booleanPreferencesKey("bootstrap_completed_v1")
    }
}

/** DataStore en memoria: `edit {}` funciona sobre `updateData` sin mocks estáticos. */
private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        state.value = mutablePreferencesOf().apply { this[key] = value }
    }
}
