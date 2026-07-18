package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import android.util.Log
import com.checkingcontainer.core.model.CampoFicha
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

/** Un dato leído de la placa: etiqueta y valor tal como aparecen impresos. */
@Generable(description = "Un dato individual impreso en la placa: su etiqueta y su valor")
data class CampoPlacaLeido(
    @param:Guide(description = "Etiqueta o nombre del dato tal como aparece, por ejemplo MODEL, SERIAL, REFRIGERANT, VOLTAGE")
    val etiqueta: String,
    @param:Guide(description = "Valor del dato tal como aparece impreso")
    val valor: String,
)

/** Esquema de lista ABIERTA: todos los datos que la placa traiga, sin encasillar. */
@Generable(description = "Todos los datos legibles impresos en la placa de identificación del equipo")
data class FichaPlacaLeida(
    @param:Guide(description = "Lista de todos los pares etiqueta-valor legibles en la placa, en el orden en que aparecen")
    val campos: List<CampoPlacaLeido>,
)

internal data class ResultadoPlaca(
    /** Campos clave mapeados al formulario (canal OcrResult). */
    val fields: Map<String, String>,
    /** TODOS los datos de la placa (ficha técnica del equipo). */
    val ficha: List<CampoFicha>,
)

/**
 * Lee la placa de datos de un equipo genérico desde una foto y devuelve la
 * FICHA COMPLETA (lista abierta etiqueta→valor — cada placa trae lo suyo) más
 * los campos clave derivados (marca/modelo/serie/año + código sugerido).
 * Gemini Nano con salida estructurada; sin IA, OCR por líneas. Solo
 * PRE-LLENA: el usuario revisa, borra o corrige antes de guardar.
 */
internal object PlacaEquipoExtractor {

    private const val TAG = "PlacaEquipoExtractor"

    private const val REGLAS =
        "You read identification data plates of refrigeration and air conditioning " +
            "equipment. Extract ONLY values explicitly printed on the plate, " +
            "exactly as printed. Never guess, complete or invent values."

    private const val PROMPT =
        "List every readable label-value pair printed on this equipment data plate."

    suspend fun desdeImagen(context: Context, uri: Uri, tipo: TipoEquipo): ResultadoPlaca {
        val ficha = runCatching { nano(context, uri) }.getOrNull()
            ?: runCatching { nanoTexto(context, uri) }.getOrNull()
            ?: runCatching { porOcr(context, uri) }.getOrDefault(emptyList())
        Log.i(TAG, "Placa leída: ${ficha.size} pares")
        return ResultadoPlaca(fields = derivarCampos(ficha, tipo), ficha = ficha)
    }

    // ── Gemini Nano: lista abierta con salida estructurada ──────────────────

    private suspend fun nano(context: Context, uri: Uri): List<CampoFicha>? {
        if (!GeminiNanoOcr.isAvailable()) return null
        val bitmap = decodeBitmapForIa(context, uri) ?: return null
        val model = Generation.getClient()
        return try {
            val base = generateContentRequest(ImagePart(bitmap), TextPart(PROMPT)) {
                temperature = 0f; topK = 1; maxOutputTokens = 512
                systemInstruction = SystemInstruction(REGLAS)
            }
            val typed = generateTypedContentRequest(base, FichaPlacaLeida::class)
            model.generateContent(typed).candidates.firstOrNull()?.response?.campos
                ?.filter { it.etiqueta.isNotBlank() && it.valor.isNotBlank() }
                ?.map { CampoFicha(it.etiqueta.trim(), it.valor.trim()) }
                ?.ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Salida tipada de placa falló (se usa OCR): ${e.message}")
            null
        } finally {
            runCatching { model.close() }
        }
    }

    /** Respaldo con Nano SIN esquema (equipos donde la salida tipada falla). */
    private suspend fun nanoTexto(context: Context, uri: Uri): List<CampoFicha>? {
        if (!GeminiNanoOcr.isAvailable()) return null
        val bitmap = decodeBitmapForIa(context, uri) ?: return null
        val model = Generation.getClient()
        return try {
            val base = generateContentRequest(
                ImagePart(bitmap),
                TextPart("$PROMPT Reply ONLY with one line per pair, in the format LABEL: VALUE. No other text."),
            ) {
                temperature = 0f; topK = 1; maxOutputTokens = 512
                systemInstruction = SystemInstruction(REGLAS)
            }
            val texto = model.generateContent(base).candidates.firstOrNull()?.text.orEmpty()
            parsearPares(texto).ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Nano texto falló: ${e.message}")
            null
        } finally {
            runCatching { model.close() }
        }
    }

    // ── OCR sin IA: pares por líneas "ETIQUETA: valor" ──────────────────────

    private suspend fun porOcr(context: Context, uri: Uri): List<CampoFicha> {
        val texto = suspendCancellableCoroutine { cont ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromFilePath(context, uri))
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
        return parsearPares(texto)
    }

    /**
     * Convierte texto en pares etiqueta→valor. Acepta "ETIQUETA: valor" y
     * también líneas sin dos puntos donde la primera palabra es una etiqueta
     * conocida de placas ("MODEL ZR72KC-TF5") — común en placas de compresor.
     */
    private val ETIQUETAS_CONOCIDAS = listOf(
        "MODEL", "MODELO", "MOD", "SERIAL", "SERIE", "S/N", "SN", "TYPE", "TIPO",
        "REFRIGERANT", "REFRIGERANTE", "GAS", "VOLTS", "VOLTAJE", "VOLTAGE", "V",
        "PHASE", "FASE", "HZ", "AMPS", "AMP", "RLA", "LRA", "FLA", "MCA", "MOP",
        "CAPACITY", "CAPACIDAD", "BTU", "KW", "HP", "YEAR", "AÑO", "DATE", "FECHA",
        "PRESION", "PRESSURE", "PSI", "MADE", "PART", "P/N", "CHARGE", "CARGA",
    )

    private fun parsearPares(texto: String): List<CampoFicha> =
        texto.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.length < 3) return@mapNotNull null
            val idx = line.indexOf(':')
            if (idx > 0) {
                val etiqueta = line.take(idx).trim()
                val valor = line.substring(idx + 1).trim()
                return@mapNotNull if (etiqueta.length in 1..30 && valor.isNotEmpty()) {
                    CampoFicha(etiqueta, valor)
                } else null
            }
            // Sin dos puntos: "MODEL ZR72KC-TF5-522" → etiqueta conocida + resto
            val partes = line.split(Regex("\\s+"), limit = 2)
            if (partes.size == 2) {
                val etiqueta = partes[0].trim('.', '#')
                if (ETIQUETAS_CONOCIDAS.any { it.equals(etiqueta, ignoreCase = true) }) {
                    return@mapNotNull CampoFicha(etiqueta.uppercase(), partes[1].trim())
                }
            }
            null
        }

    // ── Campos clave derivados de la ficha (por etiqueta) ───────────────────

    private fun derivarCampos(ficha: List<CampoFicha>, tipo: TipoEquipo): Map<String, String> {
        fun buscar(vararg claves: String): String? = ficha.firstOrNull { c ->
            claves.any { c.etiqueta.contains(it, ignoreCase = true) }
        }?.valor

        val out = mutableMapOf<String, String>()
        buscar("marca", "brand", "fabricante", "manufacturer", "maker")
            ?.let { out["Manufacturer"] = it }
        buscar("model", "modelo", "mod.")?.let { out["Unit Model"] = it }
        val serie = buscar("serial", "serie", "s/n", "ser no", "s.n")
        serie?.let { out["Unit Serial No."] = it.uppercase() }
        (buscar("year", "año", "fecha fab", "mfg date", "date")
            ?.let { Regex("(19|20)\\d{2}").find(it)?.value })
            ?.let { out["Year of Built"] = it }

        // Código de equipo sugerido: identidad estable desde el serial.
        serie?.let {
            val limpio = it.uppercase().replace(Regex("[^A-Z0-9-]"), "")
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
}
