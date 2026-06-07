package com.checkingcontainer.feature.units

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class DetectedCharacter(
    val text: String,
    val boundingBox: RectF, // relativo al frame completo (0f..1f)
)

class TextRecognitionAnalyzer(
    private val mode: ScannerMode,
    private val isVerticalMode: () -> Boolean = { false },
    private val onTrackingUpdated: (List<DetectedCharacter>) -> Unit = {},
    // NOTA: el ID de contenedor es referencial — la PK para historial de reparaciones
    // es el Número de Estimativo, no este ID.
    private val onValidContainerIdFound: (String) -> Unit = {},
    private val onSuccess: (Map<String, String>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val captureRequested = AtomicBoolean(false)
    private val done = AtomicBoolean(false)
    private val lastFrameTimestamp = AtomicLong(0L)

    fun triggerCapture() { captureRequested.set(true) }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (done.get()) { imageProxy.close(); return }

        val now = System.currentTimeMillis()
        if (now - lastFrameTimestamp.get() < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastFrameTimestamp.set(now)

        val rawBitmap = imageProxy.toBitmap()
        val rotation  = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        val bitmap   = rawBitmap.rotate(rotation)
        val vertical = isVerticalMode()
        val crop     = bitmap.cropRoi(vertical)

        val roiLeft = (bitmap.width  - crop.width)  / 2f
        val roiTop  = (bitmap.height - crop.height) / 2f
        val frameW  = bitmap.width.toFloat()
        val frameH  = bitmap.height.toFloat()
        val cropW   = crop.width.toFloat()

        // ── DETECCIÓN (WHERE) ────────────────────────────────────────────────────
        // Projection analysis: encuentra las Y de cada carácter de forma independiente
        // a cómo ML Kit agrupa el texto. Análogo a face-detection encontrando caras
        // antes de identificarlas. Se ejecuta sincrónicamente aquí (rápido, ~3ms).
        val projSegments: List<ProjectionCharDetector.Segment> =
            if (vertical) ProjectionCharDetector.detect(crop) else emptyList()

        // ── RECONOCIMIENTO (WHAT) ────────────────────────────────────────────────
        // ML Kit lee qué dice el texto. Es confiable para el contenido aunque falla
        // en las posiciones cuando el texto es vertical.
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { visionText ->

                if (vertical) {
                    // Texto de ML Kit: correcto en contenido aunque incorrecto en posiciones
                    val allChars = visionText.textBlocks
                        .flatMap { it.lines }
                        .sortedBy { it.boundingBox?.top ?: 0 }
                        .joinToString("") { line ->
                            line.text.filter { c -> c.isUpperCase() || c.isDigit() }
                        }

                    // Posiciones de proyección: correctas en Y, independientes del texto
                    val segs = projSegments

                    if (allChars.isNotEmpty() && segs.isNotEmpty()) {
                        // Parear: texto[i] → posición segs[i]
                        val count = minOf(allChars.length, segs.size)
                        val tracked = (0 until count).map { i ->
                            val seg = segs[i]
                            DetectedCharacter(
                                text = allChars[i].toString(),
                                boundingBox = RectF(
                                    roiLeft            / frameW,
                                    (roiTop + seg.top) / frameH,
                                    (roiLeft + cropW)  / frameW,
                                    (roiTop + seg.bottom) / frameH,
                                ),
                            )
                        }
                        onTrackingUpdated(tracked)

                        // Auto-detect vertical: corrección por posición + dígito verificador
                        if (allChars.length == 11) {
                            val corrected = correctContainerChars(allChars)
                            if (Iso6346.isValid(corrected) && done.compareAndSet(false, true)) {
                                onValidContainerIdFound(corrected)
                                return@addOnSuccessListener
                            }
                        }
                    } else {
                        onTrackingUpdated(emptyList())
                    }

                } else {
                    // ─── MODO HORIZONTAL ─────────────────────────────────────────
                    // Para texto horizontal los Symbol.boundingBox son correctos.
                    val candidates: List<Pair<String, Rect>> = visionText.textBlocks
                        .flatMap { it.lines }
                        .flatMap { it.elements }
                        .flatMap { element ->
                            val syms = element.symbols
                            when {
                                syms.isNotEmpty() ->
                                    syms.mapNotNull { sym ->
                                        if (sym.confidence >= CONFIDENCE_THRESHOLD)
                                            sym.boundingBox?.let { sym.text to it }
                                        else null
                                    }
                                element.text.length == 1 ->
                                    element.boundingBox?.let {
                                        listOf(element.text to it)
                                    } ?: emptyList()
                                else -> emptyList()
                            }
                        }
                        .filter { (text, _) -> text.matches(CHAR_REGEX) }

                    val column = groupByColumn(candidates).sortedBy { it.second.top }

                    val tracked = column.map { (text, box) ->
                        DetectedCharacter(
                            text = text,
                            boundingBox = RectF(
                                (roiLeft + box.left)   / frameW,
                                (roiTop  + box.top)    / frameH,
                                (roiLeft + box.right)  / frameW,
                                (roiTop  + box.bottom) / frameH,
                            ),
                        )
                    }
                    onTrackingUpdated(tracked)

                    // Auto-detect horizontal: busca 11 chars consecutivos en el texto OCR,
                    // aplica corrección por posición y valida dígito verificador ISO 6346
                    if (mode == ScannerMode.CONTAINER && !done.get()) {
                        val raw = visionText.textBlocks
                            .flatMap { it.lines }
                            .joinToString("") { it.text.filter { c -> c.isLetter() || c.isDigit() } }
                            .uppercase()
                        Regex("[A-Z0-9]{11}").findAll(raw).forEach { match ->
                            val corrected = correctContainerChars(match.value)
                            if (Iso6346.isValid(corrected) && done.compareAndSet(false, true)) {
                                onValidContainerIdFound(corrected)
                                return@addOnSuccessListener
                            }
                        }
                    }
                }

                // Captura manual (botón disparador, ambos modos)
                if (!captureRequested.get()) return@addOnSuccessListener
                val result = when (mode) {
                    ScannerMode.CONTAINER  -> parseContainer(visionText.text)
                    ScannerMode.DATA_PLATE -> parseDataPlate(visionText.text)
                }
                if (result != null && done.compareAndSet(false, true)) {
                    onSuccess(result)
                } else {
                    captureRequested.set(false)
                }
            }
    }

    private fun groupByColumn(candidates: List<Pair<String, Rect>>): List<Pair<String, Rect>> {
        if (candidates.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Pair<String, Rect>>>()
        for (candidate in candidates) {
            val cx = candidate.second.exactCenterX()
            val match = groups.find { group ->
                val groupCx = group.sumOf { it.second.exactCenterX().toDouble() } / group.size
                abs(groupCx - cx) <= COLUMN_TOLERANCE_PX
            }
            if (match != null) match.add(candidate) else groups.add(mutableListOf(candidate))
        }
        return groups.maxByOrNull { it.size } ?: emptyList()
    }

    companion object {

        private const val FRAME_INTERVAL_MS    = 250L
        private const val COLUMN_TOLERANCE_PX = 35f
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private val CHAR_REGEX                = Regex("[A-Z0-9]")
        private val CONTAINER_REGEX           = Regex("^[A-Z]{4}\\d{7}$")

        /**
         * Corrige confusiones comunes de OCR según la posición ISO 6346:
         * - Posiciones 0-3: deben ser letras (O→0, I→1, B→8 en sentido contrario)
         * - Posiciones 4-10: deben ser dígitos (0→O, 1→I, 8→B en sentido contrario)
         */
        internal fun correctContainerChars(raw: String): String {
            if (raw.length != 11) return raw
            return buildString(11) {
                for (i in raw.indices) {
                    val c = raw[i].uppercaseChar()
                    if (i < 4) {
                        append(when (c) { '0' -> 'O'; '1' -> 'I'; '8' -> 'B'; '5' -> 'S'; else -> c })
                    } else {
                        append(when (c) { 'O' -> '0'; 'I' -> '1'; 'B' -> '8'; 'S' -> '5'; 'G' -> '6'; 'Z' -> '2'; else -> c })
                    }
                }
            }
        }

        private const val ROI_VERTICAL_WIDTH  = 0.15f
        private const val ROI_VERTICAL_HEIGHT = 0.80f
        private const val ROI_HORIZ_WIDTH     = 0.80f
        private const val ROI_HORIZ_HEIGHT    = 0.35f

        private fun Bitmap.rotate(degrees: Int): Bitmap {
            if (degrees == 0) return this
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
                .also { if (it !== this) recycle() }
        }

        private fun Bitmap.cropRoi(vertical: Boolean): Bitmap {
            val roiW: Int
            val roiH: Int
            if (vertical) {
                roiW = (width  * ROI_VERTICAL_WIDTH).toInt().coerceIn(1, width)
                roiH = (height * ROI_VERTICAL_HEIGHT).toInt().coerceIn(1, height)
            } else {
                roiW = (width  * ROI_HORIZ_WIDTH).toInt().coerceIn(1, width)
                roiH = (height * ROI_HORIZ_HEIGHT).toInt().coerceIn(1, height)
            }
            val x = (width  - roiW) / 2
            val y = (height - roiH) / 2
            return Bitmap.createBitmap(this, x, y, roiW, roiH)
        }

        fun parseContainer(text: String): Map<String, String>? {
            val upper = text.uppercase()
            val noSpaces = upper.replace(Regex("\\s+"), "")
            // Intentar match de 11 chars alfanuméricos con corrección y check digit
            Regex("[A-Z0-9]{11}").findAll(noSpaces).forEach { m ->
                val corrected = correctContainerChars(m.value)
                if (Iso6346.isValid(corrected)) return mapOf("Container No." to corrected)
            }
            // Fallback: 4 letras + 7 dígitos exactos
            Regex("[A-Z]{4}[0-9]{7}").find(noSpaces)?.let { m ->
                return mapOf("Container No." to m.value)
            }
            // Fallback: código de propietario en una línea, serial en la siguiente
            val lines = upper.lines()
                .map { it.replace(Regex("[^A-Z0-9]"), "") }
                .filter { it.isNotEmpty() }
            val ownerIdx = lines.indexOfFirst { it.matches(Regex("[A-Z]{4}")) }
            if (ownerIdx >= 0) {
                val owner = lines[ownerIdx]
                for (j in (ownerIdx + 1)..minOf(ownerIdx + 3, lines.lastIndex)) {
                    Regex("[0-9]{7}").find(lines[j])?.let { serial ->
                        return mapOf("Container No." to "$owner${serial.value}")
                    }
                }
            }
            // Fallback: 10 chars → computar dígito verificador
            Regex("[A-Z]{4}[0-9]{6}").find(noSpaces)?.let { m ->
                val first10 = m.value
                val check = Iso6346.computeCheckDigit(first10)
                if (check >= 0) return mapOf("Container No." to "$first10$check")
            }
            return null
        }

        fun parseDataPlate(text: String): Map<String, String>? {
            val result = mutableMapOf<String, String>()
            Regex("""(69NT40[-\s]*\d{3}[-\s]*\d{3}|SCI-\d{2}-[A-Z]{2})""")
                .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { result["Unit Model"] = it.replace(Regex("\\s+"), "") }
            Regex("""([A-Z]{3}[\s]?\d{8}|[A-Z]{2}\d{2}-\d{5})""")
                .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { result["Unit Serial No."] = it.replace(Regex("\\s+"), "") }
            Regex("""(?:0[1-9]|1[0-2])/((?:19|20)\d{2})""")
                .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { result["Year of Built"] = it }
            return result.takeIf { it.isNotEmpty() }
        }
    }
}