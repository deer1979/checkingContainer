package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.common.di.ApplicationScope
import com.checkingcontainer.core.domain.BootstrapRepository
import com.checkingcontainer.core.model.User
import com.checkingcontainer.core.model.UserRole
import com.checkingcontainer.core.network.AnonymousAuth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val BOOT_TAG = "BootstrapSync"

@Singleton
class BootstrapRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val estimadoDao: EstimadoDao,
    private val inspectionDao: InspectionDao,
    private val reeferUnitDao: ReeferUnitDao,
    private val firestoreService: FirestoreService,
    private val anonymousAuth: AnonymousAuth,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : BootstrapRepository {

    override suspend fun syncIfNeeded(): Unit = withContext(ioDispatcher) {
        // Reintento de la sesión anónima en cada login: las reglas de
        // Firestore/Storage exigen auth y el arranque pudo estar offline.
        anonymousAuth.ensureSignedIn()

        if (userDao.count() > 0) return@withContext  // ya hay datos locales

        Log.i(BOOT_TAG, "Primera instalación — descargando datos de Firestore...")

        // 1. Usuarios (necesarios para el login)
        val users = firestoreService.fetchAllUsers()
        users.forEach { runCatching { userDao.upsert(it) } }
        Log.i(BOOT_TAG, "Usuarios: ${users.size}")

        // 2. Equipos (reefer_units) — FK requerida por inspecciones
        val units = firestoreService.fetchAllReeferUnits()
        units.forEach { runCatching { reeferUnitDao.upsert(it) } }
        Log.i(BOOT_TAG, "Equipos: ${units.size}")

        // 3. Inspecciones (después de equipos para respetar FK)
        val inspections = firestoreService.fetchAllInspections()
        inspections.forEach { runCatching { inspectionDao.upsert(it) } }
        Log.i(BOOT_TAG, "Inspecciones: ${inspections.size}")

        // 4. Estimados
        val estimados = firestoreService.fetchAllEstimados()
        estimados.forEach { runCatching { estimadoDao.upsert(it) } }
        Log.i(BOOT_TAG, "Estimados: ${estimados.size}")

        Log.i(BOOT_TAG, "Sincronización completa.")
    }

    override fun syncRecentAsync(user: User) {
        applicationScope.launch(ioDispatcher) {
            runCatching { syncRecent(user) }
                .onFailure { Log.w(BOOT_TAG, "syncRecent falló (se reintenta en el próximo login): ${it.message}") }
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
        incoming.forEach { runCatching { estimadoDao.upsert(it) } }

        // Reconciliar cierres viejos: abiertos locales que ya no aparecen abiertos
        // en remoto ni entre los recientes → se consultan uno a uno (suelen ser 0-2).
        // Si el doc no existe en remoto (creado offline aquí, aún sin subir) no se toca.
        val remoteOpenIds = remoteOpen.mapTo(HashSet()) { it.id }
        val incomingIds = incoming.mapTo(HashSet()) { it.id }
        val staleOpenIds = estimadoDao.findOpenIds()
            .filter { it !in remoteOpenIds && it !in incomingIds }
        staleOpenIds.forEach { id ->
            firestoreService.fetchEstimadoById(id)?.let { runCatching { estimadoDao.upsert(it) } }
        }

        Log.i(
            BOOT_TAG,
            "Sync post-login: ${incoming.size} estimados (abiertos+24h), " +
                "${staleOpenIds.size} reconciliados.",
        )
    }

    private companion object {
        const val RECENT_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
