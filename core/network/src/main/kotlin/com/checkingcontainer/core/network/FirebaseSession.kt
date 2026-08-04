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
     * Garantiza que exista alguna sesión Firebase durante una sesión local
     * activa. Nunca degrada un UID confiable ya obtenido por custom token.
     */
    suspend fun ensureSignedIn(): Boolean {
        if (auth.currentUser != null) return true
        return runCatching { auth.signInAnonymously().await() }
            .onFailure { error ->
                warn("Sin sesión Firebase aún (se reintentará): ${error.message}")
            }
            .isSuccess
    }

    /** Garantiza una sesión compatible sin bloquear el hilo de arranque. */
    fun ensureSignedInAsync() {
        if (auth.currentUser != null) return
        auth.signInAnonymously().addOnFailureListener { error ->
            warn("Sin sesión Firebase aún (se reintentará): ${error.message}")
        }
    }

    /**
     * Borra cualquier identidad confiable anterior y vuelve al modo compatible.
     * Se usa al iniciar el proceso y al cerrar sesión local para impedir que los
     * claims de un usuario queden disponibles para el siguiente.
     */
    fun resetToCompatibilitySessionAsync() {
        if (auth.currentUser?.isAnonymous == false) {
            auth.signOut()
        }
        ensureSignedInAsync()
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
            warn("No se pudo elevar la identidad Firebase: ${error.message}")
        }
    }

    private fun FirebaseUser.toIdentity(): FirebaseIdentity = FirebaseIdentity(
        uid = uid,
        isAnonymous = isAnonymous,
    )

    /** El logging nunca debe alterar el resultado de una operación de auth. */
    private fun warn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private companion object {
        const val TAG = "FirebaseSession"
    }
}
