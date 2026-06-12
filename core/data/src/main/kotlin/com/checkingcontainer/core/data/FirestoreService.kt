package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.entity.AnnouncementEntity
import com.checkingcontainer.core.database.entity.EstimadoEntity
import com.checkingcontainer.core.database.entity.InspectionEntity
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.ReeferEquipment
import com.checkingcontainer.core.domain.SyncStatusRepository
import com.checkingcontainer.core.network.FirestoreDataSource
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirestoreService"
private const val COL_ANNOUNCEMENTS = "announcements"
private const val COL_REEFER_UNITS = "reefer_units"
private const val COL_INSPECTIONS = "inspections"
private const val COL_USERS = "users"
private const val COL_ESTIMADOS = "estimados"

@Singleton
class FirestoreService @Inject constructor(
    dataSource: FirestoreDataSource,
    private val syncStatus: SyncStatusRepository,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val firestore: FirebaseFirestore = dataSource.firestore

    /**
     * Ejecuta un write con timeout del ack del servidor y registra el resultado
     * en [SyncStatusRepository] (visible en Ajustes). Importante: sin conexión,
     * `set()/delete()` ya quedan en la caché local del SDK y se re-envían solos
     * al volver la red — el timeout evita que el guardado offline se cuelgue
     * esperando un ack que no va a llegar (bug previo: spinner infinito).
     */
    private suspend fun write(op: String, block: suspend () -> Unit): Unit = withContext(ioDispatcher) {
        try {
            withTimeout(WRITE_ACK_TIMEOUT_MS) { block() }
            syncStatus.recordOk()
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "$op pendiente de sync (sin conexión)")
            syncStatus.recordPending(op)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$op falló: ${e.message}")
            syncStatus.recordError(op, e.message)
        }
    }

    // ── Announcements ────────────────────────────────────────────────────────

    suspend fun upsertAnnouncement(entity: AnnouncementEntity): Unit = write("upsertAnnouncement") {
        firestore.collection(COL_ANNOUNCEMENTS)
            .document(entity.id)
            .set(entity.toFirestoreMap())
            .await()
    }

    suspend fun deleteAnnouncement(id: String): Unit = write("deleteAnnouncement") {
        firestore.collection(COL_ANNOUNCEMENTS)
            .document(id)
            .delete()
            .await()
    }

    suspend fun fetchAllAnnouncements(): List<AnnouncementEntity> = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_ANNOUNCEMENTS)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val body = doc.getString("body") ?: return@mapNotNull null
                    AnnouncementEntity(
                        id = doc.id,
                        title = title,
                        summary = doc.getString("summary") ?: title,
                        body = body,
                        authorName = doc.getString("authorName") ?: "",
                        publishedAt = doc.getLong("publishedAt") ?: 0L,
                        attachments = doc.getString("attachments") ?: "[]",
                    )
                }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllAnnouncements deferred (offline?): ${e.message}")
            emptyList()
        }
    }

    // ── Equipment (reefer_units/{containerNo}) ───────────────────────────────

    suspend fun upsertEquipment(entity: ReeferUnitEntity): Unit = write("upsertEquipment") {
        firestore.collection(COL_REEFER_UNITS)
            .document(entity.containerNo)
            .set(entity.toFirestoreMap())
            .await()
    }

    suspend fun fetchEquipment(containerNo: String): ReeferEquipment? = withContext(ioDispatcher) {
        try {
            val doc = firestore.collection(COL_REEFER_UNITS)
                .document(containerNo)
                .get()
                .await()
            if (!doc.exists()) return@withContext null
            ReeferEquipment(
                containerNo = doc.getString("containerNo") ?: containerNo,
                manufacturer = doc.getString("manufacturer") ?: "",
                unitModel = doc.getString("unitModel") ?: "",
                unitModelNo = doc.getString("unitModelNo") ?: "",
                unitSerialNo = doc.getString("unitSerialNo") ?: "",
                yearOfBuilt = doc.getString("yearOfBuilt") ?: "",
                brand = doc.safeString("brand")
                    ?.let { runCatching { Brand.valueOf(it) }.getOrDefault(Brand.CARRIER) }
                    ?: Brand.CARRIER,
                unitType = doc.getString("unitType") ?: "",
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchEquipment deferred (offline?): ${e.message}")
            null
        }
    }

    suspend fun deleteEquipment(containerNo: String): Unit = write("deleteEquipment") {
        firestore.collection(COL_REEFER_UNITS)
            .document(containerNo)
            .delete()
            .await()
    }

    // ── Inspections (reefer_units/{containerNo}/inspections/{id}) ────────────

    suspend fun upsertInspection(entity: InspectionEntity): Unit = write("upsertInspection") {
        firestore.collection(COL_REEFER_UNITS)
            .document(entity.containerNo)
            .collection(COL_INSPECTIONS)
            .document(entity.id.toString())
            .set(entity.toFirestoreMap())
            .await()
    }

    suspend fun deleteInspection(containerNo: String, id: Long): Unit = write("deleteInspection") {
        firestore.collection(COL_REEFER_UNITS)
            .document(containerNo)
            .collection(COL_INSPECTIONS)
            .document(id.toString())
            .delete()
            .await()
    }

    /** Escucha cambios de digitación en todas las inspecciones vía collectionGroup. */
    internal fun observeInspectionsDigitacion(): Flow<List<DigitacionUpdate>> = callbackFlow {
        val listener = firestore.collectionGroup(COL_INSPECTIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "digitacion listener error: ${error.message}")
                    return@addSnapshotListener
                }
                val updates = snapshot?.documentChanges
                    ?.filter { it.type != DocumentChange.Type.REMOVED }
                    ?.mapNotNull { change ->
                        val doc = change.document
                        val id = doc.getLong("id") ?: return@mapNotNull null
                        DigitacionUpdate(
                            id = id,
                            idDigitador = doc.safeString("idDigitador"),
                            timestampDigitador = doc.safeLong("timestampDigitador"),
                            statusDigitacion = doc.safeString("statusDigitacion"),
                            noteDigitacion = doc.safeString("noteDigitacion"),
                            avisoDigitacion = doc.safeString("avisoDigitacion"),
                            diasPendiente = doc.safeInt("diasPendiente"),
                        )
                    } ?: emptyList()
                trySend(updates)
            }
        awaitClose { listener.remove() }
    }

    // ── Estimados ────────────────────────────────────────────────────────────

    suspend fun upsertEstimado(entity: EstimadoEntity): Unit = write("upsertEstimado") {
        firestore.collection(COL_ESTIMADOS)
            .document(entity.id.toString())
            .set(entity.toFirestoreMap())
            .await()
    }

    suspend fun deleteEstimado(id: Long): Unit = write("deleteEstimado") {
        firestore.collection(COL_ESTIMADOS)
            .document(id.toString())
            .delete()
            .await()
    }

    // ── Users ────────────────────────────────────────────────────────────────

    suspend fun upsertUser(entity: UserEntity): Unit = write("upsertUser") {
        firestore.collection(COL_USERS)
            .document(entity.id.toString())
            .set(entity.toFirestoreMap())
            .await()
    }

    suspend fun deleteUser(id: Long): Unit = write("deleteUser") {
        firestore.collection(COL_USERS)
            .document(id.toString())
            .delete()
            .await()
    }
}

private const val WRITE_ACK_TIMEOUT_MS = 10_000L

// ── Firestore map extensions ─────────────────────────────────────────────────

private fun AnnouncementEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title"       to title,
    "summary"     to summary,
    "body"        to body,
    "authorName"  to authorName,
    "publishedAt" to publishedAt,
    "attachments" to attachments,
)

private fun ReeferUnitEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "containerNo" to containerNo,
    "manufacturer" to manufacturer,
    "unitModel"   to unitModel,
    "unitModelNo" to unitModelNo,
    "unitSerialNo" to unitSerialNo,
    "yearOfBuilt" to yearOfBuilt,
    "brand"       to brand.name,
    "unitType"    to unitType,
)

private fun InspectionEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id"                 to id,
    "containerNo"        to containerNo,
    "createdAt"          to createdAt,
    "status"             to status.name,
    "ptiInstruction"     to ptiInstruction?.name,
    "deployedAs"         to deployedAs,
    "technicianId"       to technicianId,
    "technicianName"     to technicianName,
    "location"           to location,
    "observations"       to observations,
    "idDigitador"        to idDigitador,
    "timestampDigitador" to timestampDigitador,
    "statusDigitacion"   to statusDigitacion,
    "noteDigitacion"     to noteDigitacion,
    "avisoDigitacion"    to avisoDigitacion,
    "diasPendiente"      to diasPendiente,
)

private fun EstimadoEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id"            to id,
    "inspectionId"  to inspectionId,
    "containerNo"   to containerNo,
    "manufacturer"  to manufacturer,
    "unitModel"     to unitModel,
    "unitModelNo"   to unitModelNo,
    "unitSerialNo"  to unitSerialNo,
    "yearOfBuilt"   to yearOfBuilt,
    "unitType"      to unitType,
    "clientName"    to clientName,
    "location"      to location,
    "technicianId"  to technicianId,
    "technicianName" to technicianName,
    "createdAt"     to createdAt,
    "approvedAt"    to approvedAt,
    "closedAt"      to closedAt,
    "status"        to status.name,
    "damages"       to damages,
    "hasIva"        to hasIva,
    "reportUrl"     to reportUrl,
)

private fun UserEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id"        to id,
    "firstName" to firstName,
    "lastName"  to lastName,
    "nick"      to nick,
    "pin"       to pin,
    "jobTitle"  to jobTitle.name,
    "role"      to role.name,
    "company"   to company,
    "location"  to location,
    "isActive"  to isActive,
)
