package com.checkingcontainer.feature.settings

import android.content.Context

/** Estado (aproximado) de la IA local — AICore / Gemini Nano — en el dispositivo. */
internal data class AiCoreInfo(
    val available: Boolean,
    val statusLabel: String,
    val description: String,
)

/**
 * Detecta si el dispositivo tiene **AICore** (el servicio del sistema que ejecuta Gemini
 * Nano). Comprueba la presencia del paquete del sistema; requiere el `<queries>` de
 * `com.google.android.aicore` en el manifest para que sea visible en Android 11+.
 *
 * Es un AVISO informativo: la presencia del paquete indica que el equipo SOPORTA la IA
 * local, aunque no garantiza por sí sola que esté operativa para todo uso. Sirve para
 * explicar por qué en unos equipos (gama alta) el OCR podrá afinarse con IA y en otros no.
 */
internal fun aiCoreInfo(context: Context): AiCoreInfo {
    val present = runCatching {
        context.packageManager.getApplicationInfo("com.google.android.aicore", 0).enabled
    }.getOrDefault(false)

    return if (present) {
        AiCoreInfo(
            available = true,
            statusLabel = "Disponible",
            description = "Este equipo soporta IA local: a futuro podrá afinar el reconocimiento del número.",
        )
    } else {
        AiCoreInfo(
            available = false,
            statusLabel = "No disponible",
            description = "Este equipo no tiene IA local (AICore). El OCR usa el modelo estándar.",
        )
    }
}
