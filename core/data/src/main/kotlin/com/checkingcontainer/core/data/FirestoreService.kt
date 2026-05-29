package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.network.FirestoreDataSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirestoreService"
private const val COL_REEFER_UNITS = "reefer_units"
private const val COL_USERS = "users"

@Singleton
class FirestoreService @Inject constructor(
    dataSource: FirestoreDataSource,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val firestore: FirebaseFirestore = dataSource.firestore

    suspend fun upsertReeferUnit(entity: ReeferUnitEntity): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_REEFER_UNITS)
                .document(entity.id.toString())
                .set(entity.toFirestoreMap())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "upsertReeferUnit deferred (offline?): ${e.message}")
        }
    }

    suspend fun deleteReeferUnit(id: Long): Unit = withContext(ioDispatcher) {
        try {
            firestore.collection(COL_REEFER_UNITS)
                .document(id.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteReeferUnit deferred (offline?): ${e.message}")
        }
    }

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

private fun ReeferUnitEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id"            to id,
    "containerNo"   to containerNo,
    "manufacturer"  to manufacturer,
    "unitModel"     to unitModel,
    "unitModelNo"   to unitModelNo,
    "unitSerialNo"  to unitSerialNo,
    "yearOfBuilt"   to yearOfBuilt,
    "createdAt"     to createdAt,
    "status"        to status.name,
    "ptiInstruction" to ptiInstruction?.name,
    "brand"         to brand.name,
    "unitType"      to unitType,
    "deployedAs"    to deployedAs,
    "technicianId"  to technicianId,
    "technicianName" to technicianName,
    "observations"  to observations,
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
