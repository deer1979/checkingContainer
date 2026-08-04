package com.checkingcontainer

import android.app.Application
import android.util.Log
import androidx.appfunctions.service.AppFunctionConfiguration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.checkingcontainer.appfunctions.ContainerFunctions
import com.checkingcontainer.core.network.FirebaseSession
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApplication :
    Application(),
    SingletonImageLoader.Factory,
    AppFunctionConfiguration.Provider {

    @Inject
    lateinit var containerFunctions: ContainerFunctions

    @Inject
    lateinit var firebaseSession: FirebaseSession

    override fun onCreate() {
        super.onCreate()

        // App Check se instala antes de cualquier operación explícita de
        // Firebase. En esta fase solo se envían tokens; enforcement permanece
        // desactivado en consola hasta validar métricas y dispositivos de campo.
        runCatching {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }.onFailure { error ->
            Log.w(APP_CHECK_TAG, "App Check no disponible aún: ${error.message}")
        }

        // Crashlytics captura los fallos no controlados automáticamente (instala
        // su propio handler). Activo también en debug: el propietario usa el APK
        // debug publicado por el CI, así que ahí es donde hay que ver los crashes.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true

        // Durante la migración se conserva una sesión compatible. Si el usuario
        // ya obtuvo un UID confiable mediante custom token, ensureSignedInAsync
        // no lo reemplaza por una identidad anónima.
        firebaseSession.ensureSignedInAsync()
    }

    // AppFunctions: las funciones se construyen vía Hilt para reutilizar los
    // repositorios reales (offline-first) cuando un agente las invoca.
    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(ContainerFunctions::class.java) { containerFunctions }
            .build()

    /**
     * ImageLoader global de Coil. La caché de disco es clave para el flujo
     * offline-first: las fotos de Firebase Storage ya vistas (daños, anuncios)
     * se muestran sin conexión y al instante al volver a una pantalla.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val APP_CHECK_TAG = "FirebaseAppCheck"
    }
}
