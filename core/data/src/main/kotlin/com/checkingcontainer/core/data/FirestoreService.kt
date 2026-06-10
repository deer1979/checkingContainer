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
import com.checkingcontainer.core.network.FirestoreDataSource
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val firestore: FirebaseFirestore = dataSource.firestore

    // ── Announcements ────────────────────────────────────────────────────────

    suspend fun upsertAnnouncement(entity: AnnouncementEntity): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_ANNOUNCEMENTS)
                .document(entity.id)
                .set(entity.toFirestoreMap())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "upsertAnnouncement deferred (offline?): ${e.message}")
        }
    }

    suspend fun deleteAnnouncement(id: String): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_ANNOUNCEMENTS)
                .document(id)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteAnnouncement deferred (offline?): ${e.message}")
        }
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

    suspend fun upsertEquipment(entity: ReeferUnitEntity): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_REEFER_UNITS)
                .document(entity.containerNo)
                .set(entity.toFirestoreMap())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "upsertEquipment deferred (offline?): ${e.message}")
        }
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

    suspend fun deleteEquipment(containerNo: String): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_REEFER_UNITS)
                .document(containerNo)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteEquipment deferred (offline?): ${e.message}")
        }
    }

    // ── Inspections (reefer_units/{containerNo}/inspections/{id}) ────────────

    suspend fun upsertInspection(entity: InspectionEntity): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_REEFER_UNITS)
                .document(entity.containerNo)
                .collection(COL_INSPECTIONS)
                .document(entity.id.toString())
                .set(entity.toFirestoreMap())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "upsertInspection deferred (offline?): ${e.message}")
        }
    }

    suspend fun deleteInspection(containerNo: String, id: Long): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_REEFER_UNITS)
                .document(containerNo)
                .collection(COL_INSPECTIONS)
                .document(id.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteInspection deferred (offline?): ${e.message}")
        }
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

    suspend fun upsertEstimado(entity: EstimadoEntity): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_ESTIMADOS)
                .document(entity.id.toString())
                .set(entity.toFirestoreMap())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "upsertEstimado deferred (offline?): ${e.message}")
        }
    }

    suspend fun deleteEstimado(id: Long): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_ESTIMADOS)
                .document(id.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteEstimado deferred (offline?): ${e.message}")
        }
    }

    // ── Users ────────────────────────────────────────────────────────────────

    suspend fun upsertUser(entity: UserEntity): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_USERS)
                .document(entity.id.toString())
                .set(entity.toFirestoreMap())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "upsertUser deferred (offline?): ${e.message}")
        }
    }

    suspend fun deleteUser(id: Long): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_USERS)
                .document(id.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteUser deferred (offline?): ${e.message}")
        }
    }
}

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
    "id"                to id,
    "inspectionId"      to inspectionId,
    "containerNo"       to containerNo,
    "clientName"        to clientName,
    "technicianId"      to technicianId,
    "technicianName"    to technicianName,
    "location"          to location,
    "createdAt"         to createdAt,
    "closedAt"          to closedAt,
    "status"            to status.name,
    "damageDescription" to damageDescription,
    "damagePhotos"      to damagePhotos,
    "repairDescription" to repairDescription,
    "repairPhotos"      to repairPhotos,
    "reportUrl"         to reportUrl,
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
