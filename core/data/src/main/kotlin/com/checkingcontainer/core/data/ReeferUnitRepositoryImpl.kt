package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.ReeferUnitRepository
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.ReeferUnit
import com.checkingcontainer.core.network.SupabaseClientHolder
import com.checkingcontainer.core.network.dto.ReeferUnitDto
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ReeferUnitRepo"
private const val TABLE = "reefer_units"

@Singleton
class ReeferUnitRepositoryImpl @Inject constructor(
    private val dao: ReeferUnitDao,
    private val supabase: SupabaseClientHolder,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ReeferUnitRepository {

    /** Long-lived scope tied to the singleton lifetime for fire-and-forget syncs. */
    private val syncScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        // Pull remote rows into Room on first instantiation (startup sync).
        syncScope.launch { pullFromSupabase() }
        // Realtime: subscribe to live changes from other devices / dashboard.
        supabase.client?.let { setupRealtime(it) }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<ReeferUnit>> =
        dao.observeAll()
            .map { rows -> rows.map(ReeferUnitEntity::toDomain) }
            .flowOn(ioDispatcher)

    override fun observeLast24h(): Flow<List<ReeferUnit>> {
        val since = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        return dao.observeLast24h(since)
            .map { rows -> rows.map(ReeferUnitEntity::toDomain) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getById(id: Long): ReeferUnit? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun getLatestByContainerNo(containerNo: String): ReeferUnit? =
        withContext(ioDispatcher) { dao.getLatestByContainerNo(containerNo)?.toDomain() }

    override suspend fun getAllByContainerNo(containerNo: String): List<ReeferUnit> =
        withContext(ioDispatcher) { dao.getAllByContainerNo(containerNo).map { it.toDomain() } }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun create(unit: ReeferUnit): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val id = dao.insert(unit.toEntity())
            // Sync to Supabase in background; failures are logged, not surfaced
            syncScope.launch { pushCreate(unit.copy(id = id)) }
            id
        }
    }

    override suspend fun update(unit: ReeferUnit): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            dao.update(unit.toEntity())
            syncScope.launch { pushUpdate(unit) }
            Unit
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        dao.delete(id)
        syncScope.launch { pushDelete(id) }
    }

    // ── Supabase helpers ─────────────────────────────────────────────────────

    /**
     * Opens a Realtime channel for the `reefer_units` table.
     * INSERT → insert locally if not already present (avoids re-inserting our own writes).
     * UPDATE → update locally, preserving local-only fields (brand, syncId).
     * DELETE → delete locally (requires REPLICA IDENTITY FULL on the table in Supabase).
     */
    private fun setupRealtime(client: io.github.jan.supabase.SupabaseClient) {
        val json = Json { ignoreUnknownKeys = true }
        val channel = client.channel("realtime:reefer_units")
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
        }.onEach { action ->
            when (action) {
                is PostgresAction.Insert -> runCatching {
                    val dto = json.decodeFromJsonElement<ReeferUnitDto>(action.record)
                    val localId = dto.localId
                    if (localId != null && dao.findById(localId) != null) return@runCatching
                    dao.insert(dto.toEntity().copy(syncPending = false))
                    Log.d(TAG, "Realtime INSERT — containerNo=${dto.containerNo}")
                }.onFailure { Log.w(TAG, "Realtime INSERT error", it) }

                is PostgresAction.Update -> runCatching {
                    val dto = json.decodeFromJsonElement<ReeferUnitDto>(action.record)
                    val localId = dto.localId ?: return@runCatching
                    val existing = dao.findById(localId) ?: return@runCatching
                    dao.update(dto.toEntity().copy(
                        brand       = existing.brand,
                        syncId      = existing.syncId,
                        syncPending = false,
                    ))
                    Log.d(TAG, "Realtime UPDATE — containerNo=${dto.containerNo}")
                }.onFailure { Log.w(TAG, "Realtime UPDATE error", it) }

                is PostgresAction.Delete -> runCatching {
                    val localId = action.oldRecord["local_id"]?.jsonPrimitive?.longOrNull
                        ?: return@runCatching
                    dao.delete(localId)
                    Log.d(TAG, "Realtime DELETE — localId=$localId")
                }.onFailure { Log.w(TAG, "Realtime DELETE error", it) }

                else -> Unit
            }
        }.launchIn(syncScope)
        syncScope.launch { channel.subscribe() }
    }

    private suspend fun pullFromSupabase() {
        val client = supabase.client ?: return
        runCatching {
            val remote = client.from(TABLE).select().decodeList<ReeferUnitDto>()
            // Only insert records that don't already exist locally (by local_id).
            remote.forEach { dto ->
                val localId = dto.localId ?: return@forEach
                if (dao.findById(localId) == null) {
                    dao.insert(dto.toEntity())
                }
            }
            Log.d(TAG, "Pulled ${remote.size} rows from Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase pull failed — running in local-only mode", e)
        }
    }

    private suspend fun pushCreate(unit: ReeferUnit) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).insert(unit.toDto())
            Log.d(TAG, "Pushed new unit ${unit.containerNo} to Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (create) failed for ${unit.containerNo}", e)
        }
    }

    private suspend fun pushUpdate(unit: ReeferUnit) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).update({
                set("container_no", unit.containerNo)
                set("manufacturer", unit.manufacturer)
                set("unit_model", unit.unitModel)
                set("unit_model_no", unit.unitModelNo)
                set("unit_serial_no", unit.unitSerialNo)
                set("year_of_built", unit.yearOfBuilt)
                set("created_at_ms", unit.createdAt)
                set("status", unit.status.name)
                set("pti_instruction", unit.ptiInstruction?.name)
                set("unit_type", unit.unitType)
                set("deployed_as", unit.deployedAs)
                set("technician_id", unit.technicianId)
                set("technician_name", unit.technicianName)
                set("observations", unit.observations)
            }) {
                filter { eq("local_id", unit.id) }
            }
            Log.d(TAG, "Updated unit ${unit.containerNo} on Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (update) failed for ${unit.containerNo}", e)
        }
    }

    private suspend fun pushDelete(id: Long) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).delete { filter { eq("local_id", id) } }
            Log.d(TAG, "Deleted unit id=$id from Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (delete) failed for id=$id", e)
        }
    }
}

// ── Mapping helpers ───────────────────────────────────────────────────────────

private fun ReeferUnit.toDto() = ReeferUnitDto(
    containerNo = containerNo,
    manufacturer = manufacturer,
    unitModel = unitModel,
    unitModelNo = unitModelNo,
    unitSerialNo = unitSerialNo,
    yearOfBuilt = yearOfBuilt,
    createdAtMs = createdAt,
    status = status.name,
    ptiInstruction = ptiInstruction?.name,
    unitType = unitType,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    observations = observations,
    localId = id.takeIf { it != 0L },
)

private fun ReeferUnitDto.toEntity() = ReeferUnitEntity(
    id = localId ?: 0L,
    containerNo = containerNo,
    manufacturer = manufacturer,
    unitModel = unitModel,
    unitModelNo = unitModelNo,
    unitSerialNo = unitSerialNo,
    yearOfBuilt = yearOfBuilt,
    createdAt = createdAtMs,
    status = runCatching { InspStatus.valueOf(status) }.getOrDefault(InspStatus.INSP),
    ptiInstruction = ptiInstruction?.let { runCatching { PtiInstruction.valueOf(it) }.getOrNull() },
    unitType = unitType,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    observations = observations,
)
