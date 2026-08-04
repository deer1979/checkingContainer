package com.checkingcontainer.core.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Identidad Firebase activa en el dispositivo. */
data class FirebaseIdentity(
    val uid: String,
    val isAnonymous: Boolean,
) {
    val isTrusted: Boolean get() = !isAnonymous
}

/**
 * Gestiona la identidad técnica de Firebase durante la migración.
 *
 * El modo compatible conserva la sesión anónima para no bloquear APK ni datos
 * existentes. Cuando el backend confiable entregue un custom token, la misma
 * clase eleva la sesión a un UID estable sin cambiar los consumidores de
 * Firestore y Storage.
 */
@Singleton
class FirebaseSession @Inject constructor(
    private val auth: FirebaseAuth,
) {

    fun currentIdentity(): FirebaseIdentity? = auth.currentUser?.toIdentity()

    fun hasTrustedIdentity(): Boolean = currentIdentity()?.isTrusted == true

    /**
     * Garantiza que exista alguna sesión Firebase.
     *
     * Nunca reemplaza una identidad confiable por una anónima. La autenticación
     * anónima solo se usa como compatibilidad mientras finaliza la migración.
     */
    suspend fun ensureSignedIn(): Boolean {
        if (auth.currentUser != null) return true
        return runCatching { auth.signInAnonymously().await() }
            .onFailure { error ->
                Log.w(TAG, "Sin sesión Firebase aún (se reintentará): ${error.message}")
            }
            .isSuccess
    }

    /** Variante no bloqueante para el arranque de la aplicación. */
    fun ensureSignedInAsync() {
        if (auth.currentUser != null) return
        auth.signInAnonymously().addOnFailureListener { error ->
            Log.w(TAG, "Sin sesión Firebase aún (se reintentará): ${error.message}")
        }
    }

    /**
     * Sustituye la sesión compatible por una identidad estable emitida por un
     * backend confiable. El token no se persiste ni se registra en logs.
     */
    suspend fun signInWithCustomToken(customToken: String): Result<FirebaseIdentity> {
        val token = customToken.trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Custom token vacío"))
        }

        return runCatching {
            val user = auth.signInWithCustomToken(token).await().user
                ?: error("Firebase no devolvió una identidad")
            user.toIdentity().also { identity ->
                check(identity.isTrusted) { "El custom token produjo una sesión anónima" }
            }
        }.onFailure { error ->
            Log.w(TAG, "No se pudo elevar la identidad Firebase: ${error.message}")
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun FirebaseUser.toIdentity(): FirebaseIdentity = FirebaseIdentity(
        uid = uid,
        isAnonymous = isAnonymous,
    )

    private companion object {
        const val TAG = "FirebaseSession"
    }
}
