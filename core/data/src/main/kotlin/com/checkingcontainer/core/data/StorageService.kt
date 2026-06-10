package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StorageService"

/**
 * Sube y borra archivos en Firebase Storage. Los adjuntos de anuncios se guardan
 * bajo `announcements/{announcementId}/{nombre}` y se referencian por su URL de
 * descarga (que es lo que se persiste en el anuncio).
 */
@Singleton
class StorageService @Inject constructor(
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    /** Sube [bytes] y devuelve la URL de descarga pública (token incluido). */
    suspend fun upload(
        announcementId: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String,
    ): String = withContext(ioDispatcher) {
        val ref = storage.reference.child("announcements/$announcementId/$fileName")
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()
        ref.putBytes(bytes, metadata).await()
        ref.downloadUrl.await().toString()
    }

    /** Sube [bytes] a [storagePath] y devuelve la URL de descarga pública. */
    suspend fun uploadToPath(
        storagePath: String,
        bytes: ByteArray,
        contentType: String = "image/jpeg",
    ): String = withContext(ioDispatcher) {
        val ref = storage.reference.child(storagePath)
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()
        ref.putBytes(bytes, metadata).await()
        ref.downloadUrl.await().toString()
    }

    /** Borra un archivo a partir de su URL de descarga. No lanza si ya no existe. */
    suspend fun deleteByUrl(url: String): Unit = withContext(ioDispatcher) {
        runCatching { storage.getReferenceFromUrl(url).delete().await() }
            .onFailure { Log.w(TAG, "deleteByUrl ignorado: ${it.message}") }
        Unit
    }
}
