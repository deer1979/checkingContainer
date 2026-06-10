package com.checkingcontainer.feature.units

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.GenerationConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GeminiNanoCorrectorEntryPoint {
    fun geminiNanoCorrector(): GeminiNanoCorrector
}

/**
 * Corrector de último recurso para números de contenedor: usa Gemini Nano (AICore)
 * para interpretar texto OCR ambiguo que ML Kit no pudo validar como ISO 6346.
 *
 * - Solo activo en dispositivos con AICore (Pixel 8+ y equivalentes, Android 14+).
 * - Si el dispositivo no lo soporta, [correct] devuelve null en microsegundos.
 * - API en Alpha → toda la lógica está en try/catch; cualquier fallo devuelve null.
 * - El llamador SIEMPRE valida el resultado con [Iso6346.isValid] antes de usarlo.
 */
@Singleton
class GeminiNanoCorrector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Lazy: el modelo solo se crea si el dispositivo tiene AICore y Android 14+.
    // La creación del GenerativeModel puede tomar ~200ms la primera vez.
    private val model: GenerativeModel? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@lazy null
        if (!isAiCorePresent()) return@lazy null
        runCatching {
            GenerativeModel(
                generationConfig = GenerationConfig.builder()
                    .setMaxOutputTokens(24)
                    .setTemperature(0.1f)
                    .build(),
            )
        }.getOrNull()
    }

    /**
     * Intenta corregir [rawOcr] (texto OCR con posibles confusiones de caracteres)
     * al formato ISO 6346. Devuelve los 11 caracteres corregidos o null si:
     * - El dispositivo no tiene AICore / Android < 14
     * - Gemini Nano tarda más de 5 segundos
     * - El modelo lanza cualquier excepción
     * - La respuesta no tiene exactamente 11 caracteres alfanuméricos
     */
    suspend fun correct(rawOcr: String): String? {
        val m = model ?: return null
        val prompt = buildPrompt(rawOcr)
        return runCatching {
            withTimeoutOrNull(5_000L) {
                val response = m.generateContent(prompt)
                response.text
                    ?.filter { it.isLetterOrDigit() }
                    ?.uppercase()
                    ?.takeIf { it.length == 11 }
            }
        }.getOrNull()
    }

    private fun isAiCorePresent(): Boolean = runCatching {
        context.packageManager.getApplicationInfo("com.google.android.aicore", 0).enabled
    }.getOrDefault(false)

    private fun buildPrompt(rawOcr: String): String =
        "Número de contenedor ISO 6346 leído por OCR con posibles errores.\n" +
        "Formato: 4 letras mayúsculas + 6 dígitos + 1 dígito verificador (11 caracteres).\n" +
        "Confusiones comunes: O↔0, I↔1, B↔8, S↔5, G↔6, Z↔2.\n" +
        "OCR: $rawOcr\n" +
        "Devuelve SOLO los 11 caracteres corregidos, sin espacios ni explicación."
}
