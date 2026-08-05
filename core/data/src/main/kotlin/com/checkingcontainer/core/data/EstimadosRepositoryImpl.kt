package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.EstimadoDao
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.core.model.MedicionSnapshot
import com.checkingcontainer.core.model.ResultadoFusion
import com.checkingcontainer.core.model.ResultadoGuardado
import com.checkingcontainer.core.model.fusionarEstimado
import com.checkingcontainer.core.model.mejorCopia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EstimadosRepositoryImpl @Inject constructor(
    private val dao: EstimadoDao,
    private val firestoreService: FirestoreService,
    private val storageService: StorageService,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EstimadosRepository {

    override suspend fun addMedicion(containerNo: String, medicion: MedicionSnapshot): Boolean =
        withContext(ioDispatcher) {
            val abierto = dao.findByContainerNo(containerNo)
                .firstOrNull { it.status == EstimadoStatus.ABIERTO }
                ?.toDomain()
                ?: return@withContext false
            save(abierto.copy(mediciones = abierto.mediciones + medicion))
            true
        }

    /**
     * Guarda fusionando con la nube y SIEMPRE en el teléfono.
     *
     * Antes de escribir se consulta la copia remota. Si otra persona la tocó
     * desde que yo abrí la pantalla, se fusiona campo por campo en vez de pisar
     * su trabajo: él agregó una foto, yo puse el valor, y quedan las dos cosas.
     * Solo cuando los dos cambiamos el MISMO campo se reporta el conflicto.
     *
     * Room nunca falla; Firestore sí cuando no hay cobertura. Si la nube no
     * confirma, la fila queda pendiente, que es lo que alimenta el aviso al
     * usuario y el reintento posterior.
     */
    override suspend fun save(estimado: Estimado, base: Estimado?): ResultadoGuardado =
        withContext(ioDispatcher) {
            val fusion = fusionarConRemoto(estimado, base)
            val ahora = System.currentTimeMillis()
            val marcado = fusion.estimado.copy(updatedAt = ahora)
            val entity = marcado.toEntity()
            val savedId = dao.upsert(entity)
            val persisted = entity.copy(id = if (marcado.id == 0L) savedId else marcado.id)

            val subido = firestoreService.upsertEstimado(persisted)
            if (subido) dao.upsert(persisted.copy(subidoEn = ahora))

            ResultadoGuardado(
                estimado = persisted.copy(subidoEn = if (subido) ahora else persisted.subidoEn).toDomain(),
                pendienteDeSubir = !subido,
                huboCambiosDelOtro = fusion.huboCambiosDelOtro,
                camposEnConflicto = fusion.camposEnConflicto,
            )
        }

    /**
     * Trae la copia remota y la fusiona con la mía. Si no hay remota, o es la
     * misma que cargué, no hay nada que fusionar.
     */
    private suspend fun fusionarConRemoto(mio: Estimado, base: Estimado?): ResultadoFusion {
        if (mio.id == 0L) return ResultadoFusion(mio, huboCambiosDelOtro = false, camposEnConflicto = emptyList())

        val remoto = firestoreService.fetchEstimadoById(mio.id)?.toDomain()
            ?: return ResultadoFusion(mio, huboCambiosDelOtro = false, camposEnConflicto = emptyList())

        // La nube no ha cambiado desde que abrí: no hace falta fusionar nada.
        if (base != null && remoto.updatedAt <= base.updatedAt) {
            return ResultadoFusion(mio, huboCambiosDelOtro = false, camposEnConflicto = emptyList())
        }
        return fusionarEstimado(base, mio, remoto)
    }

    /**
     * Cambios en vivo del estimado abierto. Emite solo cuando la versión remota
     * es más nueva que [desdeUpdatedAt], es decir, cuando otra persona guardó.
     */
    override fun observeCambiosRemotos(id: Long, desdeUpdatedAt: Long): Flow<Estimado> =
        firestoreService.observeEstimado(id)
            .mapNotNull { it?.toDomain() }
            .filter { it.updatedAt > desdeUpdatedAt }

    override suspend fun findById(id: Long): Estimado? =
        withContext(ioDispatcher) { dao.findById(id)?.toDomain() }

    override suspend fun subirPendientes(): Int = withContext(ioDispatcher) {
        val pendientes = dao.findPendientesDeSubir()
        var subidos = 0
        pendientes.forEach { entity ->
            val ahora = System.currentTimeMillis()
            if (firestoreService.upsertEstimado(entity)) {
                dao.upsert(entity.copy(subidoEn = ahora))
                subidos += 1
            }
        }
        subidos
    }

    override fun observePendientesDeSubir(): Flow<Int> = dao.observeCountPendientes()

    override fun observeByInspectionId(inspectionId: Long): Flow<Estimado?> =
        dao.observeByInspectionId(inspectionId).map { it?.toDomain() }

    /**
     * Carga el estimado de una inspección con prioridad estricta:
     *
     * 1. Copia local **pendiente de subir** → siempre gana: es trabajo que aún
     *    no viajó y ninguna versión de la nube puede pisarlo.
     * 2. Copia local ya subida → se compara con la nube y se toma la más nueva.
     * 3. Sin copia local → **se busca en Firestore** antes de darlo por nuevo.
     *    Omitir este paso era lo que hacía que un teléfono recién instalado
     *    creara un duplicado en blanco y lo subiera encima del bueno.
     *
     * Si en la nube hay varias copias de la misma inspección se conserva la que
     * más información tiene y **las vacías se eliminan**.
     */
    override suspend fun findByInspectionId(inspectionId: Long): Estimado? =
        withContext(ioDispatcher) {
            val local = dao.findByInspectionId(inspectionId)?.toDomain()
            if (local != null && local.pendienteDeSubir) return@withContext local

            val remotas = firestoreService.fetchEstimadosByInspectionId(inspectionId)
                .map { it.toDomain() }
            if (remotas.isEmpty()) return@withContext local

            val mejorRemota = limpiarDuplicados(remotas) ?: return@withContext local
            if (local == null) {
                // Llega de la nube: se guarda en local ya marcada como subida.
                dao.upsert(mejorRemota.toEntity().copy(subidoEn = System.currentTimeMillis()))
                return@withContext mejorRemota
            }

            // Ambas subidas: manda la modificación más reciente.
            val ganadora = if (mejorRemota.updatedAt > local.updatedAt) mejorRemota else local
            if (ganadora !== local) {
                dao.upsert(ganadora.toEntity().copy(id = local.id, subidoEn = System.currentTimeMillis()))
            }
            ganadora
        }

    /**
     * Con varias copias remotas de la misma inspección conserva la más completa
     * y borra las demás, que son los duplicados en blanco. Devuelve la buena.
     */
    private suspend fun limpiarDuplicados(remotas: List<Estimado>): Estimado? {
        val mejor = mejorCopia(remotas) ?: return null
        remotas.filter { it.id != mejor.id && it.riqueza == 0 }.forEach { vacio ->
            firestoreService.deleteEstimado(vacio.id)
            dao.deleteById(vacio.id)
        }
        return mejor
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        dao.deleteById(id)
        firestoreService.deleteEstimado(id)
    }

    override fun observeOpen(): Flow<List<Estimado>> =
        dao.observeOpen().map { list -> list.map { it.toDomain() } }

    override fun observeClosed(): Flow<List<Estimado>> =
        dao.observeClosed().map { list -> list.map { it.toDomain() } }

    override fun countOpen(): Flow<Int> = dao.countOpen()

    override suspend fun uploadItemPhoto(
        inspectionId: Long,
        itemId: String,
        isDano: Boolean,
        bytes: ByteArray,
    ): String = withContext(ioDispatcher) {
        val phase = if (isDano) "dano" else "reparacion"
        // Sufijo único: cada ítem admite varias fotos por fase; un nombre fijo
        // sobrescribiría la anterior.
        val unico = java.util.UUID.randomUUID().toString().take(8)
        storageService.uploadToPath("estimados/$inspectionId/items/$itemId/$phase-$unico.jpg", bytes)
    }

    override suspend fun deletePhoto(url: String): Unit = withContext(ioDispatcher) {
        storageService.deleteByUrl(url)
    }

    override suspend fun uploadPdf(inspectionId: Long, bytes: ByteArray): String =
        withContext(ioDispatcher) {
            storageService.uploadToPath(
                "estimados/$inspectionId/reporte.pdf",
                bytes,
                contentType = "application/pdf",
            )
        }

    override suspend fun searchByContainerNo(containerNo: String): List<Estimado> =
        withContext(ioDispatcher) {
            val local = dao.findByContainerNo(containerNo)
            if (local.isNotEmpty()) return@withContext local.map { it.toDomain() }
            val remote = firestoreService.fetchEstimadosByContainerNo(containerNo)
            remote.forEach { dao.upsert(it) }
            remote.map { it.toDomain() }
        }
}
