package com.checkingcontainer.feature.units

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/**
 * Respaldo de OCR con **Gemini Nano** (IA local, ML Kit GenAI Prompt API — beta).
 *
 * Principios de diseño (acordados):
 * 1. **Solo respaldo**: se invoca únicamente cuando el OCR normal (ML Kit) no pudo
 *    leer, nunca lo sustituye.
 * 2. **Sin inferencia**: el modelo solo TRANSCRIBE; su salida pasa por los mismos
 *    validadores duros que el OCR — dígito verificador ISO 6346 para el número de
 *    contenedor y las regex de [TextRecognitionAnalyzer.parseDataPlate] para la
 *    placa. Un dato inventado no puede pasar esos filtros → se descarta.
 * 3. **Nunca rompe**: en equipos sin Gemini Nano (o si la beta falla) todos los
 *    métodos devuelven `null` en silencio y el flujo actual sigue intacto.
 */
internal object GeminiNanoOcr {

    private const val TAG = "GeminiNanoOcr"

    /** Lado mayor máximo que se envía al modelo (latencia/memoria acotadas). */
    private const val MAX_IMAGE_SIDE = 1024

    private const val PROMPT_CONTAINER =
        "This photo shows a shipping container identification code: 4 letters followed by " +
            "7 digits, possibly printed vertically (one character below another). " +
            "Transcribe EXACTLY the characters you see, in reading order, without spaces. " +
            "Do not guess or invent characters. If the code is not clearly legible, " +
            "reply exactly: ILEGIBLE"

    private const val PROMPT_DATA_PLATE =
        "This photo shows the data plate of a container refrigeration unit. " +
            "Transcribe ALL legible printed text exactly as it appears, line by line, " +
            "including model numbers, serial numbers and dates. " +
            "Do not guess, complete or invent any value. If nothing is legible, " +
            "reply exactly: ILEGIBLE"

    /** Disponibilidad cacheada: una sola consulta al sistema por proceso. */
    @Volatile
    private var cachedAvailable: Boolean? = null

    suspend fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val available = runCatching {
            val model = Generation.getClient()
            try {
                model.checkStatus() == FeatureStatus.AVAILABLE
            } finally {
                runCatching { model.close() }
            }
        }.getOrDefault(false)
        cachedAvailable = available
        return available
    }

    /**
     * Intenta leer el número de contenedor. Devuelve el número SOLO si pasa la
     * corrección posicional + dígito verificador ISO 6346; si no, `null`.
     */
    suspend fun readContainerNumber(bitmap: Bitmap): String? {
        val transcript = transcribe(bitmap, PROMPT_CONTAINER, maxTokens = 64) ?: return null
        val cleaned = transcript.uppercase().replace(Regex("[^A-Z0-9]"), "")
        Regex("[A-Z0-9]{11}").findAll(cleaned).forEach { match ->
            val corrected = TextRecognitionAnalyzer.correctContainerChars(match.value)
            if (Iso6346.isValid(corrected)) return corrected
        }
        return null
    }

    /**
     * Intenta leer la placa de datos. La transcripción pasa por las MISMAS regex
     * del OCR normal ([TextRecognitionAnalyzer.parseDataPlate]): solo salen campos
     * con formato válido (modelo/serie/año); si ninguno cuadra, `null`.
     */
    suspend fun readDataPlate(bitmap: Bitmap): Map<String, String>? {
        val transcript = transcribe(bitmap, PROMPT_DATA_PLATE, maxTokens = 512) ?: return null
        return TextRecognitionAnalyzer.parseDataPlate(transcript)
    }

    /** Transcripción cruda. `null` si no disponible, ilegible o error (nunca lanza). */
    private suspend fun transcribe(bitmap: Bitmap, prompt: String, maxTokens: Int): String? {
        if (!isAvailable()) return null
        val scaled = bitmap.scaledDown()
        return runCatching {
            val model = Generation.getClient()
            try {
                val request = generateContentRequest(ImagePart(scaled), TextPart(prompt)) {
                    temperature = 0f
                    topK = 1
                    maxOutputTokens = maxTokens
                }
                model.generateContent(request).candidates.firstOrNull()?.text?.trim()
                    ?.takeUnless { it.isEmpty() || it.contains("ILEGIBLE", ignoreCase = true) }
            } finally {
                runCatching { model.close() }
            }
        }
            .onFailure { Log.w(TAG, "Fallo de Gemini Nano (se ignora, OCR sigue normal): ${it.message}") }
            .getOrNull()
            .also { if (scaled !== bitmap) scaled.recycle() }
    }

    private fun Bitmap.scaledDown(): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= MAX_IMAGE_SIDE) return this
        val scale = MAX_IMAGE_SIDE.toFloat() / largest
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
