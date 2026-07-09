package com.checkingcontainer.core.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sesión anónima de Firebase. Las reglas de Firestore/Storage exigen
 * `request.auth != null`, así que la app obtiene una credencial técnica
 * (sin correo ni contraseña, invisible para el usuario) antes de hablar
 * con el backend.
 *
 * La credencial persiste entre arranques: solo el primer arranque (o tras
 * borrar datos de la app) necesita red. Si falla, se reintenta en cada
 * arranque y en cada apertura de la pantalla de login.
 */
@Singleton
class AnonymousAuth @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Garantiza la sesión y espera el resultado. true si hay sesión. */
    suspend fun ensureSignedIn(): Boolean {
        if (auth.currentUser != null) return true
        return runCatching { auth.signInAnonymously().await() }
            .onFailure { Log.w(TAG, "Sin sesión anónima aún (se reintentará): ${it.message}") }
            .isSuccess
    }

    /** Variante sin espera, para el arranque de la app. */
    fun ensureSignedInAsync() {
        if (auth.currentUser != null) return
        auth.signInAnonymously().addOnFailureListener {
            Log.w(TAG, "Sin sesión anónima aún (se reintentará): ${it.message}")
        }
    }

    private companion object {
        const val TAG = "AnonymousAuth"
    }
}
