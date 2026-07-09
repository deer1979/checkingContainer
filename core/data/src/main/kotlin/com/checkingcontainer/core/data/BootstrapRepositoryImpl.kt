package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.dao.InspectionDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.domain.BootstrapRepository
import com.checkingcontainer.core.network.AnonymousAuth
import kotlinx.coroutines.CoroutineDispatcher
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
}
