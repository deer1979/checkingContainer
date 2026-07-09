package com.checkingcontainer.feature.settings

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

/** Estado de la IA local — Gemini Nano vía ML Kit GenAI — en el dispositivo. */
internal data class AiCoreInfo(
    val available: Boolean,
    val statusLabel: String,
    val description: String,
)

internal val AI_CORE_CHECKING = AiCoreInfo(
    available = false,
    statusLabel = "Comprobando…",
    description = "Consultando la disponibilidad de Gemini Nano en este equipo.",
)

/**
 * Consulta REAL de disponibilidad: pregunta a la Prompt API de ML Kit GenAI
 * (beta) el estado de Gemini Nano en este equipo. A diferencia del chequeo
 * anterior (presencia del paquete AICore), esto confirma que el modelo puede
 * ejecutarse: AVAILABLE (listo), DOWNLOADABLE/DOWNLOADING (compatible, el
 * modelo se descarga al primer uso) o UNAVAILABLE (equipo no soportado).
 *
 * En equipos sin AICore la llamada puede fallar con excepción → se trata
 * como no disponible.
 */
internal suspend fun aiCoreInfo(): AiCoreInfo {
    val model = Generation.getClient()
    val status = try {
        model.checkStatus()
    } catch (_: Exception) {
        FeatureStatus.UNAVAILABLE
    } finally {
        runCatching { model.close() }
    }

    return when (status) {
        FeatureStatus.AVAILABLE -> AiCoreInfo(
            available = true,
            statusLabel = "Disponible",
            description = "Gemini Nano está listo en este equipo: se pueden usar funciones de IA local sin internet.",
        )
        FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> AiCoreInfo(
            available = true,
            statusLabel = "Compatible",
            description = "Este equipo soporta Gemini Nano; el modelo se descargará la primera vez que se use.",
        )
        else -> AiCoreInfo(
            available = false,
            statusLabel = "No disponible",
            description = "Este equipo no soporta IA local (Gemini Nano). El OCR usa el modelo estándar.",
        )
    }
}
