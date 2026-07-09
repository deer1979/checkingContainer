package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import android.util.Log
import com.checkingcontainer.core.model.Client
import com.checkingcontainer.core.model.ClientIdType
import com.checkingcontainer.core.model.IdentificacionEc
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Extrae datos de cliente desde texto pegado (WhatsApp) o una foto (factura).
 *
 * Anti-inventos, en capas:
 * 1. Gemini Nano solo si está disponible; instrucción estricta de no adivinar.
 * 2. RUC/cédula SIEMPRE se validan con [IdentificacionEc] (dígito verificador
 *    SRI): un número inventado o mal leído se descarta.
 * 3. En paralelo corre un extractor determinista por patrones (regex) que no
 *    depende de IA — es el camino completo en equipos sin Gemini Nano.
 * 4. El resultado solo PRE-LLENA el formulario: el usuario revisa y guarda.
 */
internal object ClientDataExtractor {

    private const val TAG = "ClientDataExtractor"

    private const val PROMPT =
        "The following is an Ecuadorian invoice or contact data of a business/person. " +
            "Extract ONLY information explicitly present (for invoices: the ISSUER at the top). " +
            "Reply EXACTLY with these lines, using - when a value is not present. No other text.\n" +
            "RAZON_SOCIAL: \nRUC: \nCEDULA: \nEMAIL: \nDIRECCION: \nTELEFONO: \n" +
            "Do not guess or invent any value."

    /** Texto pegado → cliente pre-llenado (nunca lanza; campos vacíos si nada). */
    suspend fun desdeTexto(texto: String): Client {
        val ai = if (GeminiNanoOcr.isAvailable()) {
            runCatching { nano(TextPart("$PROMPT\n\nDATA:\n$texto"), image = null) }.getOrNull()
        } else {
            null
        }
        return combinar(ai, porPatrones(texto))
    }

    /** Foto (galería) → cliente pre-llenado. Sin IA usa OCR + patrones. */
    suspend fun desdeImagen(context: Context, uri: Uri): Client {
        val bitmap = runCatching { InputImage.fromFilePath(context, uri).bitmapInternal }.getOrNull()
        val ai = if (bitmap != null && GeminiNanoOcr.isAvailable()) {
            runCatching { nano(TextPart(PROMPT), ImagePart(bitmap)) }.getOrNull()
        } else {
            null
        }
        val ocrTexto = runCatching { ocr(context, uri) }.getOrDefault("")
        return combinar(ai, porPatrones(ocrTexto))
    }

    // ── Gemini Nano ──────────────────────────────────────────────────────────

    private suspend fun nano(text: TextPart, image: ImagePart?): Map<String, String> {
        val model = Generation.getClient()
        return try {
            val request = if (image != null) {
                generateContentRequest(image, text) { temperature = 0f; topK = 1; maxOutputTokens = 256 }
            } else {
                generateContentRequest(text) { temperature = 0f; topK = 1; maxOutputTokens = 256 }
            }
            val respuesta = model.generateContent(request).candidates.firstOrNull()?.text.orEmpty()
            respuesta.lines().mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val clave = line.take(idx).trim().uppercase()
                val valor = line.substring(idx + 1).trim().takeUnless { it.isEmpty() || it == "-" }
                    ?: return@mapNotNull null
                clave to valor
            }.toMap()
        } finally {
            runCatching { model.close() }
        }
    }

    // ── Extractor determinista (sin IA) ─────────────────────────────────────

    private fun porPatrones(texto: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        // Identificaciones: solo secuencias que PASAN el dígito verificador.
        Regex("\\d{13}").findAll(texto).firstOrNull { IdentificacionEc.rucValido(it.value) }
            ?.let { out["RUC"] = it.value }
        Regex("\\d{10}").findAll(texto)
            .firstOrNull { IdentificacionEc.cedulaValida(it.value) && it.value != out["RUC"]?.take(10) }
            ?.let { out["CEDULA"] = it.value }
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(texto)
            ?.let { out["EMAIL"] = it.value }
        // Teléfonos EC: celular 09xxxxxxxx, fijo 0Xxxxxxxx o +593...
        Regex("(\\+593\\s?\\d{8,9}|09\\d{8}|0[2-7]\\d{7})").findAll(texto)
            .firstOrNull { it.value != out["RUC"] && it.value != out["CEDULA"] }
            ?.let { out["TELEFONO"] = it.value }
        return out
    }

    // ── Fusión con validación ────────────────────────────────────────────────

    private fun combinar(ai: Map<String, String>?, patrones: Map<String, String>): Client {
        fun campo(clave: String): String = ai?.get(clave).orEmpty().ifEmpty { patrones[clave].orEmpty() }

        // Identificación: el primer número VÁLIDO gana (la IA se verifica igual).
        val rucCandidatos = listOfNotNull(ai?.get("RUC"), patrones["RUC"])
            .map { it.filter(Char::isDigit) }
        val cedulaCandidatos = listOfNotNull(ai?.get("CEDULA"), patrones["CEDULA"])
            .map { it.filter(Char::isDigit) }
        val ruc = rucCandidatos.firstOrNull { IdentificacionEc.rucValido(it) }
        val cedula = cedulaCandidatos.firstOrNull { IdentificacionEc.cedulaValida(it) }

        val descartadas = (rucCandidatos + cedulaCandidatos).size -
            listOfNotNull(ruc, cedula).size
        if (descartadas > 0) Log.i(TAG, "$descartadas identificaciones descartadas por verificador")

        return Client(
            razonSocial = ai?.get("RAZON_SOCIAL").orEmpty(),
            idType = if (ruc != null) ClientIdType.RUC else ClientIdType.CEDULA,
            idNumber = ruc ?: cedula.orEmpty(),
            email = campo("EMAIL"),
            direccion = campo("DIRECCION"),
            telefono = campo("TELEFONO"),
        )
    }

    // ── OCR (ML Kit) para el camino sin IA ──────────────────────────────────

    private suspend fun ocr(context: Context, uri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromFilePath(context, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
}
