package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import android.util.Log
import com.checkingcontainer.core.model.TipoEquipo
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Esquema tipado para leer placas de datos de CUALQUIER equipo de frío. */
@Generable(description = "Datos leídos de la placa de identificación de un equipo de refrigeración o climatización")
data class DatosPlacaEquipo(
    @Guide(description = "Marca o fabricante del equipo; cadena vacía si no aparece")
    val marca: String,
    @Guide(description = "Número o nombre de modelo tal como aparece impreso; cadena vacía si no aparece")
    val modelo: String,
    @Guide(description = "Número de serie tal como aparece impreso; cadena vacía si no aparece")
    val serie: String,
    @Guide(description = "Refrigerante, por ejemplo R-410A o R-134a; cadena vacía si no aparece")
    val refrigerante: String,
    @Guide(description = "Capacidad (BTU, kW o toneladas) tal como aparece; cadena vacía si no aparece")
    val capacidad: String,
    @Guide(description = "Voltaje de alimentación, por ejemplo 220V; cadena vacía si no aparece")
    val voltaje: String,
    @Guide(description = "Año de fabricación de 4 dígitos; cadena vacía si no aparece")
    val anio: String,
)

/**
 * Lee la placa de datos de un equipo genérico (A/C, cámara fría, chiller…)
 * desde una foto. Gemini Nano con salida estructurada cuando hay; sin IA,
 * OCR + patrones por palabras clave. El resultado solo PRE-LLENA el
 * formulario (claves del canal OcrResult) — el usuario revisa y corrige.
 */
internal object PlacaEquipoExtractor {

    private const val TAG = "PlacaEquipoExtractor"

    private const val REGLAS =
        "You read identification data plates of refrigeration and air conditioning " +
            "equipment. Extract ONLY values explicitly printed on the plate. " +
            "Never guess, complete or invent values."

    private const val PROMPT =
        "Extract the data from this equipment data plate photo."

    /** Devuelve campos con las claves del canal OcrResult del formulario. */
    suspend fun desdeImagen(context: Context, uri: Uri, tipo: TipoEquipo): Map<String, String> {
        val ai = runCatching { nano(context, uri) }.getOrNull()
        val datos = ai ?: runCatching { porOcr(context, uri) }.getOrDefault(emptyMap())

        val out = mutableMapOf<String, String>()
        datos["marca"]?.let { out["Manufacturer"] = it }
        datos["modelo"]?.let { out["Unit Model"] = it }
        datos["serie"]?.let { out["Unit Serial No."] = it.uppercase() }
        datos["anio"]?.let { out["Year of Built"] = it }
        val extras = listOfNotNull(datos["refrigerante"], datos["capacidad"], datos["voltaje"])
        if (extras.isNotEmpty()) out["Observaciones"] = extras.joinToString(" · ")

        // Código de equipo sugerido desde el serial: identidad estable para
        // reencontrar el MISMO aparato en futuras visitas (historial).
        datos["serie"]?.let { serie ->
            val limpio = serie.uppercase().replace(Regex("[^A-Z0-9-]"), "")
            if (limpio.length >= 3) out["Container No."] = "${tipo.prefijoCodigo()}-$limpio"
        }
        return out
    }

    private fun TipoEquipo.prefijoCodigo(): String = when (this) {
        TipoEquipo.AIRE_ACONDICIONADO -> "AC"
        TipoEquipo.CAMARA_FRIA -> "CF"
        TipoEquipo.CHILLER -> "CH"
        else -> "EQ"
    }

    private suspend fun nano(context: Context, uri: Uri): Map<String, String>? {
        if (!GeminiNanoOcr.isAvailable()) return null
        val bitmap = runCatching { InputImage.fromFilePath(context, uri).bitmapInternal }.getOrNull()
            ?: return null
        val model = Generation.getClient()
        return try {
            val base = generateContentRequest(ImagePart(bitmap), TextPart(PROMPT)) {
                temperature = 0f; topK = 1; maxOutputTokens = 256
                systemInstruction = SystemInstruction(REGLAS)
            }
            val typed = generateTypedContentRequest(base, DatosPlacaEquipo::class)
            val r = model.generateContent(typed).candidates.firstOrNull()?.response ?: return null
            buildMap {
                if (r.marca.isNotBlank()) put("marca", r.marca.trim())
                if (r.modelo.isNotBlank()) put("modelo", r.modelo.trim())
                if (r.serie.isNotBlank()) put("serie", r.serie.trim())
                if (r.refrigerante.isNotBlank()) put("refrigerante", r.refrigerante.trim())
                if (r.capacidad.isNotBlank()) put("capacidad", r.capacidad.trim())
                if (r.voltaje.isNotBlank()) put("voltaje", r.voltaje.trim())
                if (r.anio.isNotBlank()) put("anio", r.anio.trim())
            }.ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Salida tipada falló (se usa OCR): ${e.message}")
            null
        } finally {
            runCatching { model.close() }
        }
    }

    /** Camino sin IA: OCR + patrones por palabras clave (mejor esfuerzo). */
    private suspend fun porOcr(context: Context, uri: Uri): Map<String, String> {
        val texto = suspendCancellableCoroutine { cont ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromFilePath(context, uri))
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
        if (texto.isBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        Regex("(?i)(?:model|mod|modelo)\\s*[:.#]?\\s*([A-Z0-9][A-Z0-9/-]{2,})")
            .find(texto)?.groupValues?.get(1)?.let { out["modelo"] = it }
        Regex("(?i)(?:serial|s/n|serie|ser)\\s*(?:no|n°|nº)?\\s*[:.#]?\\s*([A-Z0-9][A-Z0-9-]{4,})")
            .find(texto)?.groupValues?.get(1)?.let { out["serie"] = it }
        Regex("\\bR-?\\d{2,3}[A-Za-z]{0,2}\\b").find(texto)?.let { out["refrigerante"] = it.value }
        Regex("\\b\\d{3}\\s?V\\b").find(texto)?.let { out["voltaje"] = it.value }
        Regex("\\b(19|20)\\d{2}\\b").find(texto)?.let { out["anio"] = it.value }
        return out
    }
}
