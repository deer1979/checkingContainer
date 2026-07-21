package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import android.util.Log
import com.checkingcontainer.core.model.CampoFicha
import com.checkingcontainer.core.model.TipoEquipo
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class ResultadoPlaca(
    /** Campos clave mapeados al formulario (canal OcrResult). */
    val fields: Map<String, String>,
    /** TODOS los datos de la placa (ficha técnica del equipo). */
    val ficha: List<CampoFicha>,
    /** Con qué se leyó: "OCR + IA" / "OCR". */
    val metodo: String = "",
)

/**
 * Lee la placa de datos de un equipo genérico desde una foto y devuelve la
 * FICHA COMPLETA (lista abierta etiqueta→valor) más los campos clave derivados
 * (marca/modelo/serie/año + código sugerido).
 *
 * Estrategia (recomendada por Google para documentos): **OCR primero, IA
 * después**. ML Kit OCR lee los caracteres; Gemini Nano ORGANIZA ese texto en
 * pares. Clave del diseño: Nano on-device tiene un tope duro de ~512 tokens de
 * salida — por eso se le pide texto plano "ETIQUETA: VALOR" (compacto), NO JSON
 * estructurado (que reventaba el límite en placas densas y fallaba en silencio).
 * Sin IA, un lector determinista de tablas de dos columnas hace el trabajo.
 * Solo PRE-LLENA: el usuario revisa, corrige o borra antes de guardar.
 */
internal object PlacaEquipoExtractor {

    private const val TAG = "PlacaEquipoExtractor"

    private const val REGLAS =
        "You read identification data plates of refrigeration and air conditioning " +
            "equipment. Use ONLY text explicitly present. Never guess or invent values."

    suspend fun desdeImagen(context: Context, uri: Uri, tipo: TipoEquipo): ResultadoPlaca {
        val textoOcr = runCatching { ocrTexto(context, uri) }.getOrDefault("")

        // 1. OCR + IA: Nano reorganiza el texto del OCR en pares (texto plano).
        if (textoOcr.isNotBlank()) {
            runCatching { nanoOrganiza(textoOcr) }.getOrNull()?.let { ficha ->
                Log.i(TAG, "Placa: OCR + IA, ${ficha.size} pares")
                return ResultadoPlaca(derivarCampos(ficha, tipo), ficha, "OCR + IA")
            }
        }
        // 2. Sin IA (o si falló): lector determinista de tablas de dos columnas.
        val ficha = parsearFicha(textoOcr)
        Log.i(TAG, "Placa: solo OCR, ${ficha.size} pares")
        return ResultadoPlaca(derivarCampos(ficha, tipo), ficha, "OCR")
    }

    // ── IA: organiza el texto del OCR en pares (SALIDA DE TEXTO PLANO) ───────

    private suspend fun nanoOrganiza(texto: String): List<CampoFicha>? {
        if (!GeminiNanoOcr.isAvailable()) return null
        val model = Generation.getClient()
        return try {
            val base = generateContentRequest(
                TextPart(
                    "Raw OCR text from an equipment data plate follows. Rewrite it as " +
                        "one line per field, exactly in the format LABEL: VALUE. Join each " +
                        "label with its value even if OCR split them across separate lines. " +
                        "If one line holds several fields, split them. Keep values exactly " +
                        "as printed. Output ONLY the LABEL: VALUE lines, nothing else.\n\n" +
                        texto,
                ),
            ) {
                temperature = 0f; topK = 1; maxOutputTokens = 512
                systemInstruction = SystemInstruction(REGLAS)
            }
            val salida = model.generateContent(base).candidates.firstOrNull()?.text.orEmpty()
            parsearLineasEtiquetaValor(salida).ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Nano organizador falló (se usa lector OCR): ${e.message}")
            null
        } finally {
            runCatching { model.close() }
        }
    }

    // ── OCR ─────────────────────────────────────────────────────────────────

    private suspend fun ocrTexto(context: Context, uri: Uri): String =
        suspendCancellableCoroutine { cont ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromFilePath(context, uri))
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }

    /** Parsea líneas "ETIQUETA: VALOR" limpias (salida de la IA). */
    private fun parsearLineasEtiquetaValor(texto: String): List<CampoFicha> =
        texto.lines().mapNotNull { raw ->
            val line = raw.trim().removePrefix("-").removePrefix("*").trim()
            val idx = line.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val etiqueta = line.take(idx).trim()
            val valor = line.substring(idx + 1).trim()
            if (etiqueta.length in 1..40 && valor.isNotEmpty()) CampoFicha(etiqueta, valor) else null
        }

    private val ETIQUETAS_CONOCIDAS = listOf(
        "MODEL", "MODELO", "MOD", "SERIAL", "SERIE", "S/N", "SN", "TYPE", "TIPO",
        "REFRIGERANT", "REFRIGERANTE", "GAS", "VOLTS", "VOLTAJE", "VOLTAGE",
        "TENSION", "TENSIÓN", "PHASE", "FASE", "HZ", "FRECUENCIA", "AMPS", "AMP",
        "RLA", "LRA", "FLA", "MCA", "MOP", "CORRIENTE", "POTENCIA", "POWER",
        "CAPACITY", "CAPACIDAD", "BTU", "KW", "HP", "VOLUMEN", "CONSUMO", "CARGA",
        "YEAR", "AÑO", "DATE", "FECHA", "CLASE", "CLASS", "PRESION", "PRESSURE",
        "PSI", "MADE", "PART", "P/N", "CHARGE", "COLOR", "CONGELADOR", "REFRIGERADOR",
    )

    /**
     * Lector determinista para cuando no hay IA. Maneja tres formas comunes en
     * placas: "ETIQUETA: valor", "ETIQUETA:" con el valor en la línea siguiente
     * (tablas de dos columnas que el OCR separa), y "ETIQUETA valor" cuando la
     * primera palabra es una etiqueta conocida.
     */
    private fun parsearFicha(texto: String): List<CampoFicha> {
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<CampoFicha>()
        var i = 0
        while (i < lineas.size) {
            val line = lineas[i]
            val idx = line.indexOf(':')
            when {
                // "ETIQUETA: valor" en la misma línea
                idx > 0 && line.substring(idx + 1).isNotBlank() -> {
                    val et = line.take(idx).trim()
                    val v = line.substring(idx + 1).trim()
                    if (et.length in 1..40) out += CampoFicha(et, v)
                }
                // "ETIQUETA:" y el valor en la línea siguiente (dos columnas)
                idx > 0 && line.substring(idx + 1).isBlank() && i + 1 < lineas.size -> {
                    val et = line.take(idx).trim()
                    val v = lineas[i + 1].trim()
                    if (et.length in 1..40 && v.isNotEmpty() && !v.contains(':')) {
                        out += CampoFicha(et, v); i++
                    }
                }
                // "ETIQUETA valor" sin dos puntos, con etiqueta conocida
                else -> {
                    val partes = line.split(Regex("\\s+"), limit = 2)
                    if (partes.size == 2) {
                        val et = partes[0].trim('.', '#')
                        if (ETIQUETAS_CONOCIDAS.any { it.equals(et, ignoreCase = true) }) {
                            out += CampoFicha(et.uppercase(), partes[1].trim())
                        }
                    }
                }
            }
            i++
        }
        return out
    }

    // ── Campos clave derivados de la ficha ──────────────────────────────────

    // Marcas comunes de línea blanca / climatización en Ecuador y la región.
    private val MARCAS_CONOCIDAS = listOf(
        "INDURAMA", "MABE", "LG", "SAMSUNG", "WHIRLPOOL", "ELECTROLUX", "DUREX",
        "GLOBAL", "OSTER", "HACEB", "CARRIER", "YORK", "TRANE", "DAIKIN", "MIDEA",
        "GREE", "PANASONIC", "TCL", "HISENSE", "BOSCH", "GENERAL ELECTRIC", "GE",
    )

    private fun derivarCampos(ficha: List<CampoFicha>, tipo: TipoEquipo): Map<String, String> {
        fun buscar(vararg claves: String): String? = ficha.firstOrNull { c ->
            claves.any { c.etiqueta.contains(it, ignoreCase = true) }
        }?.valor

        val out = mutableMapOf<String, String>()

        // Marca: por etiqueta (incluye tipo de equipo como etiqueta portadora:
        // "Congelador: Indurama") o, si no, por una marca conocida en cualquier valor.
        val marca = buscar("marca", "brand", "fabricante", "manufacturer", "maker",
            "congelador", "refrigerador", "nevera", "frigorifico", "frigorífico", "equipo")
            ?: ficha.firstOrNull { c ->
                MARCAS_CONOCIDAS.any { m -> c.valor.uppercase().contains(m) }
            }?.let { c -> MARCAS_CONOCIDAS.first { m -> c.valor.uppercase().contains(m) } }
        marca?.let { out["Manufacturer"] = it }

        buscar("model", "modelo", "mod.")?.let { out["Unit Model"] = it }

        // Serie: primer token (no arrastrar REV ni fechas del mismo renglón).
        val serie = buscar("serial", "serie", "s/n", "ser no", "s.n", "número de serie")
            ?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            ?.filter { it.isLetterOrDigit() || it == '-' }
        serie?.takeIf { it.length >= 3 }?.let { out["Unit Serial No."] = it.uppercase() }

        // Año: acepta 4 dígitos (2024) y también fechas con año de 2 dígitos
        // (01/02/24 → 2024), muy común en placas.
        buscar("year", "año", "anio", "fecha de fab", "fecha fab", "mfg date", "fabricacion", "date")
            ?.let { extraerAnio(it) }
            ?.let { out["Year of Built"] = it }

        // Código de equipo sugerido desde el serial.
        serie?.takeIf { it.length >= 3 }?.let {
            val limpio = it.uppercase().replace(Regex("[^A-Z0-9-]"), "")
            if (limpio.length >= 3) out["Container No."] = "${tipo.prefijoCodigo()}-$limpio"
        }
        return out
    }

    /** Año de 4 dígitos directo, o el año de una fecha dd/mm/yy(yy). */
    private fun extraerAnio(texto: String): String? {
        Regex("(19|20)\\d{2}").find(texto)?.let { return it.value }
        // dd/mm/yy o dd-mm-yy → toma el último grupo de 2 dígitos como año.
        Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-](\\d{2})\\b").find(texto)?.let {
            return "20${it.groupValues[1]}"
        }
        return null
    }

    private fun TipoEquipo.prefijoCodigo(): String = when (this) {
        TipoEquipo.AIRE_ACONDICIONADO -> "AC"
        TipoEquipo.CAMARA_FRIA -> "CF"
        TipoEquipo.CHILLER -> "CH"
        else -> "EQ"
    }
}
