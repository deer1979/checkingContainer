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
import com.checkingcontainer.core.domain.EstimadosRepository
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
    private val estimadosRepo: EstimadosRepository,
    private val dataStore: DataStore<Preferences>,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : BootstrapRepository {

    override suspend fun syncIfNeeded(): Unit = withContext(ioDispatcher) {
        try {
            // La sesión anónima se renueva SIEMPRE, antes de cualquier atajo: las
            // reglas de Firestore/Storage la exigen y puede haberse perdido desde
            // el último arranque. Comprobarlo después de los return dejaba a la
            // app sin credencial cuando el bootstrap ya estaba hecho.
            anonymousAuth.ensureSignedIn()

            if (dataStore.data.first()[KEY_BOOTSTRAP_COMPLETED] == true) {
                return@withContext
            }

            // Instalación con trabajo propio: el bootstrap NO debe volver a
            // correr, porque bajar Firestore encima sobreescribiría lo capturado
            // en campo sin red y aún no subido.
            //
            // OJO: se mira el TRABAJO (estimados, inspecciones, equipos), no los
            // usuarios. La primera instalación siembra un SuperAdmin local, así
            // que contar usuarios daba 1 en un teléfono recién instalado y el
            // bootstrap se saltaba la descarga entera: el equipo nuevo se quedaba
            // sin los usuarios reales (nadie podía iniciar sesión), sin clientes
            // y sin estimados.
            if (hayTrabajoLocal()) {
                marcarCompletado()
                Log.i(BOOT_TAG, "Bootstrap omitido: la instalación ya tenía trabajo local.")
                return@withContext
            }

            Log.i(BOOT_TAG, "Bootstrap pendiente — descargando datos de Firestore...")

            // Usuarios se consultan primero porque son necesarios para iniciar
            // sesión. Una respuesta vacía se considera intento incompleto: el
            // marcador no se guarda y se reintentará en el próximo arranque.
            val users = firestoreService.fetchAllUsers()
            if (users.isEmpty()) {
                Log.w(BOOT_TAG, "Bootstrap aplazado: Firestore no devolvió usuarios")
                return@withContext
            }

            // Cada fila se inserta aislada: un registro remoto corrupto se salta
            // y se cuenta, en vez de abortar el bootstrap entero. Sin esto una
            // sola fila mala bloquea la primera instalación para siempre, porque
            // el reintento vuelve a fallar en el mismo punto en cada arranque.
            var fallos = 0
            suspend fun <T> insertar(filas: List<T>, etiqueta: String, bloque: suspend (T) -> Unit): Int {
                var ok = 0
                filas.forEach { fila ->
                    runCatching { bloque(fila) }
                        .onSuccess { ok += 1 }
                        .onFailure {
                            fallos += 1
                            Log.w(BOOT_TAG, "$etiqueta: fila descartada (${it.message})")
                        }
                }
                return ok
            }

            Log.i(BOOT_TAG, "Usuarios: ${insertar(users, "usuarios") { userDao.upsert(it) }}")

            // Equipos antes de inspecciones para respetar la relación local.
            val units = firestoreService.fetchAllReeferUnits()
            Log.i(BOOT_TAG, "Equipos: ${insertar(units, "equipos") { reeferUnitDao.upsert(it) }}")

            // Los ids de inspección son una secuencia LOCAL de cada teléfono, y
            // esta consulta trae las de todos los contenedores mezcladas: dos
            // equipos distintos pueden traer, cada uno, una inspección con id 5.
            // Insertarlas a ciegas hacía que una pisara a la otra y el estimado
            // acabara apuntando a un contenedor ajeno. Ante un id repetido con
            // contenedor distinto se conserva la primera y se registra el choque.
            val inspections = firestoreService.fetchAllInspections()
            val idsUsados = mutableMapOf<Long, String>()
            var choques = 0
            val inspOk = insertar(inspections, "inspecciones") { inspeccion ->
                val yaUsado = idsUsados[inspeccion.id]
                if (yaUsado != null && yaUsado != inspeccion.containerNo) {
                    choques += 1
                    Log.w(
                        BOOT_TAG,
                        "id de inspección ${inspeccion.id} repetido: '$yaUsado' vs " +
                            "'${inspeccion.containerNo}'. Se omite la segunda.",
                    )
                } else {
                    idsUsados[inspeccion.id] = inspeccion.containerNo
                    inspectionDao.upsert(inspeccion)
                }
            }
            Log.i(BOOT_TAG, "Inspecciones: $inspOk (choques de id omitidos: $choques)")

            val estimados = firestoreService.fetchAllEstimados()
            Log.i(BOOT_TAG, "Estimados: ${insertar(estimados, "estimados") { guardarBajadoDeLaNube(it) }}")

            val clients = firestoreService.fetchAllClients()
            Log.i(BOOT_TAG, "Clientes: ${insertar(clients, "clientes") { clientDao.upsert(it) }}")

            // Se marca completo aunque se hayan descartado filas sueltas: los
            // usuarios entraron y la app es utilizable. Repetir el bootstrap no
            // arreglaría un registro que está mal en origen.
            marcarCompletado()
            Log.i(BOOT_TAG, "Bootstrap completo. Filas descartadas: $fallos")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // No se marca como completo. La base local sigue disponible y el
            // proceso se reintentará en el próximo arranque de login.
            Log.w(BOOT_TAG, "Bootstrap pendiente: ${error.message}")
        }
    }

    /**
     * Vuelve a bajar los catálogos compartidos (clientes y equipos). Se hace fila
     * a fila y sin abortar: un registro corrupto no debe impedir el resto.
     */
    private suspend fun refrescarCatalogos() {
        val clientes = firestoreService.fetchAllClients()
        var ok = 0
        clientes.forEach { fila ->
            runCatching { clientDao.upsert(fila) }
                .onSuccess { ok += 1 }
                .onFailure { Log.w(BOOT_TAG, "cliente descartado: ${it.message}") }
        }
        if (clientes.isNotEmpty()) Log.i(BOOT_TAG, "Clientes sincronizados: $ok/${clientes.size}")

        val equipos = firestoreService.fetchAllReeferUnits()
        var okEquipos = 0
        equipos.forEach { fila ->
            runCatching { reeferUnitDao.upsert(fila) }
                .onSuccess { okEquipos += 1 }
                .onFailure { Log.w(BOOT_TAG, "equipo descartado: ${it.message}") }
        }
        if (equipos.isNotEmpty()) Log.i(BOOT_TAG, "Equipos sincronizados: $okEquipos/${equipos.size}")

    }

    /**
     * Guarda en Room un estimado que VIENE de la nube.
     *
     * `subidoEn` es un campo solo-local (la nube no lo guarda), así que los
     * documentos bajados llegan con null y el upsert directo marcaba TODOS los
     * estimados como "Sin subir" en cada login — la advertencia no se quitaba
     * nunca. Si está en la nube, por definición está subido.
     *
     * Además, una copia remota nunca pisa trabajo local pendiente de subir ni
     * uno editado más recientemente aquí.
     */
    private suspend fun guardarBajadoDeLaNube(remoto: com.checkingcontainer.core.database.entity.EstimadoEntity) {
        val local = estimadoDao.findById(remoto.id)
        if (local != null) {
            val subidoLocal = local.subidoEn
            val localPendiente = subidoLocal == null || subidoLocal < local.updatedAt
            if (localPendiente || local.updatedAt > remoto.updatedAt) return
        }
        estimadoDao.upsert(
            remoto.copy(subidoEn = maxOf(System.currentTimeMillis(), remoto.updatedAt)),
        )
    }

    /**
     * NO se reponen inspecciones ni se rellena el contenedor de un estimado a
     * partir de ellas, y esto no se puede reactivar sin cambiar antes el esquema.
     *
     * El id de `inspections` es `autoGenerate`, es decir, **una secuencia local de
     * cada teléfono**. En Firestore las inspecciones viven bajo
     * `reefer_units/{contenedor}/inspections/{id}`, así que el contenedor forma
     * parte de la ruta y dos equipos distintos pueden tener, cada uno, una
     * inspección con id 5 para contenedores diferentes.
     *
     * `fetchAllInspections()` es una consulta de grupo: devuelve las de TODOS los
     * contenedores mezcladas. Reponer por id significaba meter en el hueco de una
     * inspección borrada la de otro contenedor cualquiera, y la reparación
     * posterior escribía ESE contenedor en el estimado y lo subía a Firestore:
     * un estimado acababa mostrando una unidad que nunca le perteneció.
     *
     * No hay forma fiable de deducir el contenedor correcto a posteriori, así que
     * se deja el campo vacío antes que inventarlo: un documento que va al cliente
     * no puede llevar datos adivinados. La corrección definitiva es dar a las
     * inspecciones un id único global (ver PLAN_DEUDA_TECNICA.md).
     */

    /**
     * Trabajo real hecho en este teléfono. El usuario sembrado en la primera
     * instalación no cuenta: no es trabajo, es semilla.
     */
    private suspend fun hayTrabajoLocal(): Boolean =
        estimadoDao.findOpenIds().isNotEmpty() ||
            inspectionDao.count() > 0 ||
            reeferUnitDao.count() > 0

    private suspend fun marcarCompletado() {
        dataStore.edit { preferences ->
            preferences[KEY_BOOTSTRAP_COMPLETED] = true
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

    /** Punto de entrada directo para los tests: [syncRecentAsync] es fire-and-forget. */
    @androidx.annotation.VisibleForTesting
    internal suspend fun syncRecentPublico(user: User) = syncRecent(user)

    private suspend fun syncRecent(user: User) {
        anonymousAuth.ensureSignedIn()

        // Antes de traer nada, empujar lo que quedó guardado solo en el teléfono
        // (se guardó sin cobertura). Así el trabajo del técnico llega a la nube
        // en cuanto hay señal, sin depender de que vuelva a tocar Guardar.
        val subidos = estimadosRepo.subirPendientes()
        if (subidos > 0) Log.i(BOOT_TAG, "Subidos $subidos estimados que estaban pendientes.")

        // Clientes y equipos: hasta ahora solo se descargaban en la PRIMERA
        // instalación, así que un cliente creado por un compañero en otro teléfono
        // no llegaba nunca — no aparecía en el buscador por más que existiera en
        // la nube. Son catálogos pequeños y de cambio lento, así que refrescarlos
        // en cada login es barato.
        refrescarCatalogos()

        val isAdmin = user.role == UserRole.SuperAdmin || user.role == UserRole.Admin
        val since = System.currentTimeMillis() - RECENT_WINDOW_MS

        // Dos consultas baratas: todos los abiertos + creados en las últimas 24h
        // (la segunda trae también los recién cerrados, para reflejar cierres).
        val remoteOpen = firestoreService.fetchOpenEstimados()
        val recent = firestoreService.fetchEstimadosCreatedSince(since)

        val incoming = (remoteOpen + recent)
            .distinctBy { it.id }
            .filter { isAdmin || it.technicianId == user.id }
        // Aislado por fila: un estimado remoto corrupto no debe impedir que se
        // sincronicen los demás en cada login.
        incoming.forEach { estimado ->
            runCatching { guardarBajadoDeLaNube(estimado) }
                .onFailure { Log.w(BOOT_TAG, "syncRecent: estimado ${estimado.id} descartado (${it.message})") }
        }

        // Reconciliar cierres viejos: abiertos locales que ya no aparecen abiertos
        // en remoto ni entre los recientes → se consultan uno a uno (suelen ser 0-2).
        // Si el doc no existe en remoto (creado offline aquí, aún sin subir) no se toca.
        val remoteOpenIds = remoteOpen.mapTo(HashSet()) { it.id }
        val incomingIds = incoming.mapTo(HashSet()) { it.id }
        val staleOpenIds = estimadoDao.findOpenIds()
            .filter { it !in remoteOpenIds && it !in incomingIds }
        staleOpenIds.forEach { id ->
            runCatching { firestoreService.fetchEstimadoById(id)?.let { guardarBajadoDeLaNube(it) } }
                .onFailure { Log.w(BOOT_TAG, "syncRecent: reconciliación de $id falló (${it.message})") }
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
