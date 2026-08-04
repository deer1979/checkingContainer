package com.checkingcontainer.core.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.ApplicationScope
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.ClientDao
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.domain.BootstrapRepository
import com.checkingcontainer.core.model.User
import com.checkingcontainer.core.model.UserRole
import com.checkingcontainer.core.network.AnonymousAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BOOT_TAG = "BootstrapSync"

@Singleton
class BootstrapRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val estimadoDao: EstimadoDao,
    private val clientDao: ClientDao,
    private val inspectionDao: InspectionDao,
    private val reeferUnitDao: ReeferUnitDao,
    private val firestoreService: FirestoreService,
    private val anonymousAuth: AnonymousAuth,
    private val dataStore: DataStore<Preferences>,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : BootstrapRepository {

    override suspend fun syncIfNeeded(): Unit = withContext(ioDispatcher) {
        try {
            if (dataStore.data.first()[KEY_BOOTSTRAP_COMPLETED] == true) {
                return@withContext
            }

            // Reintento de la sesión anónima en cada arranque pendiente. Las
            // reglas de Firestore/Storage exigen auth y el primer inicio puede
            // haber ocurrido sin conexión.
            anonymousAuth.ensureSignedIn()

            Log.i(BOOT_TAG, "Bootstrap pendiente — descargando datos de Firestore...")

            // Usuarios se consultan primero porque son necesarios para iniciar
            // sesión. Una respuesta vacía se considera intento incompleto: el
            // marcador no se guarda y se reintentará en el próximo arranque.
            val users = firestoreService.fetchAllUsers()
            if (users.isEmpty()) {
                Log.w(BOOT_TAG, "Bootstrap aplazado: Firestore no devolvió usuarios")
                return@withContext
            }
            users.forEach { userDao.upsert(it) }
            Log.i(BOOT_TAG, "Usuarios: ${users.size}")

            // Equipos antes de inspecciones para respetar la relación local.
            val units = firestoreService.fetchAllReeferUnits()
            units.forEach { reeferUnitDao.upsert(it) }
            Log.i(BOOT_TAG, "Equipos: ${units.size}")

            val inspections = firestoreService.fetchAllInspections()
            inspections.forEach { inspectionDao.upsert(it) }
            Log.i(BOOT_TAG, "Inspecciones: ${inspections.size}")

            val estimados = firestoreService.fetchAllEstimados()
            estimados.forEach { estimadoDao.upsert(it) }
            Log.i(BOOT_TAG, "Estimados: ${estimados.size}")

            val clients = firestoreService.fetchAllClients()
            clients.forEach { clientDao.upsert(it) }
            Log.i(BOOT_TAG, "Clientes: ${clients.size}")

            dataStore.edit { preferences ->
                preferences[KEY_BOOTSTRAP_COMPLETED] = true
            }
            Log.i(BOOT_TAG, "Bootstrap completo.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // No se marca como completo. La base local sigue disponible y el
            // proceso se reintentará en el próximo arranque de login.
            Log.w(BOOT_TAG, "Bootstrap pendiente: ${error.message}")
        }
    }

    override fun syncRecentAsync(user: User) {
        applicationScope.launch(ioDispatcher) {
            try {
                syncRecent(user)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(
                    BOOT_TAG,
                    "syncRecent falló (se reintenta en el próximo login): ${error.message}",
                )
            }
        }
    }

    private suspend fun syncRecent(user: User) {
        anonymousAuth.ensureSignedIn()

        val isAdmin = user.role == UserRole.SuperAdmin || user.role == UserRole.Admin
        val since = System.currentTimeMillis() - RECENT_WINDOW_MS

        // Dos consultas baratas: todos los abiertos + creados en las últimas 24h
        // (la segunda trae también los recién cerrados, para reflejar cierres).
        val remoteOpen = firestoreService.fetchOpenEstimados()
        val recent = firestoreService.fetchEstimadosCreatedSince(since)

        val incoming = (remoteOpen + recent)
            .distinctBy { it.id }
            .filter { isAdmin || it.technicianId == user.id }
        incoming.forEach { estimadoDao.upsert(it) }

        // Reconciliar cierres viejos: abiertos locales que ya no aparecen abiertos
        // en remoto ni entre los recientes → se consultan uno a uno (suelen ser 0-2).
        // Si el doc no existe en remoto (creado offline aquí, aún sin subir) no se toca.
        val remoteOpenIds = remoteOpen.mapTo(HashSet()) { it.id }
        val incomingIds = incoming.mapTo(HashSet()) { it.id }
        val staleOpenIds = estimadoDao.findOpenIds()
            .filter { it !in remoteOpenIds && it !in incomingIds }
        staleOpenIds.forEach { id ->
            firestoreService.fetchEstimadoById(id)?.let { estimadoDao.upsert(it) }
        }

        Log.i(
            BOOT_TAG,
            "Sync post-login: ${incoming.size} estimados (abiertos+24h), " +
                "${staleOpenIds.size} reconciliados.",
        )
    }

    private companion object {
        val KEY_BOOTSTRAP_COMPLETED = booleanPreferencesKey("bootstrap_completed_v1")
        const val RECENT_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
