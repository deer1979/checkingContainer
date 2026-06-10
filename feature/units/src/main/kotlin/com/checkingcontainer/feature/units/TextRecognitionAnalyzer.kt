package com.checkingcontainer.feature.units

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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
    // Candidato de 11 chars que NO pasó Iso6346 tras corrección posicional.
    // OcrScannerBottomSheet lo reintenta con Gemini Nano si está disponible.
    private val onRawCandidateAvailable: ((String) -> Unit)? = null,
    private val onSuccess: (Map<String, String>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val captureRequested = AtomicBoolean(false)
    private val done = AtomicBoolean(false)
    private val lastFrameTimestamp = AtomicLong(0L)

    // Lecturas verticales recientes (solo de 11 chars) para el voto por posición.
    // Se accede únicamente desde el listener de ML Kit (hilo principal) → sin sincronizar.
    private val recentReads = ArrayDeque<String>()

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
        val cropRect  = imageProxy.cropRect
        imageProxy.close()

        // ViewPort recorta el frame al mismo FOV que el preview (cropRect). Recortamos
        // ANTES de rotar para que el bitmap quede alineado 1:1 con lo que ve el usuario,
        // eliminando la corrección FILL_CENTER manual del overlay.
        val bitmap   = rawBitmap.cropTo(cropRect).rotate(rotation)
        val vertical = isVerticalMode()
        val crop     = bitmap.cropRoi(vertical)

        val roiLeft = (bitmap.width  - crop.width)  / 2f
        val roiTop  = (bitmap.height - crop.height) / 2f
        val frameW  = bitmap.width.toFloat()
        val frameH  = bitmap.height.toFloat()

        if (vertical) processVertical(crop, roiLeft, roiTop, frameW, frameH)
        else processHorizontal(crop, roiLeft, roiTop, frameW, frameH)
    }

    /**
     * Vertical (Caso A: caracteres derechos apilados). ML Kit Latin no sabe leer texto
     * vertical, así que NO le damos la columna. En su lugar: la proyección detecta cada
     * glifo (DÓNDE), recortamos cada uno y los pegamos en una LÍNEA HORIZONTAL sintética
     * (composeStrip); a ML Kit le damos esa "palabra" normal (QUÉ). Esto endereza el skew
     * y juega a favor de ML Kit. Los agujeros/pernos se reconocen como nada y se filtran.
     */
    private fun processVertical(crop: Bitmap, roiLeft: Float, roiTop: Float, frameW: Float, frameH: Float) {
        val glyphs = ProjectionCharDetector.detectGlyphs(crop)

        // Overlay de depuración: una caja real por glifo detectado (sin texto aún).
        onTrackingUpdated(
            glyphs.map { g ->
                DetectedCharacter(
                    text = "",
                    boundingBox = RectF(
                        (roiLeft + g.left)   / frameW,
                        (roiTop  + g.top)    / frameH,
                        (roiLeft + g.right)  / frameW,
                        (roiTop  + g.bottom) / frameH,
                    ),
                )
            },
        )

        // Compuerta: solo intentamos reconocer si el nº de glifos es plausible (~11).
        // Si hay mucha basura (20+ cajas) no perseguimos ruido ni hacemos esperar al
        // usuario; las cajas igual se dibujan arriba (debug) para ver qué detectó.
        if (glyphs.size !in PLAUSIBLE_GLYPH_RANGE) return

        val strip = composeStrip(crop, glyphs) ?: return
        recognizer.process(InputImage.fromBitmap(strip, 0))
            .addOnSuccessListener { visionText ->
                val raw = visionText.text.filter { it.isLetterOrDigit() }.uppercase()

                // Acumula lecturas de 11 chars para el voto temporal por posición.
                if (raw.length == 11) {
                    recentReads.addLast(raw)
                    while (recentReads.size > VOTE_WINDOW) recentReads.removeFirst()
                }

                // Candidatos a validar: el VOTO de varios frames (más fiable que uno solo)
                // y la lectura cruda actual. Cada uno pasa por corrección + dígito ISO.
                val candidates = buildList {
                    if (recentReads.size >= VOTE_MIN) add(majorityVote(recentReads))
                    if (raw.length == 11) add(raw)
                }
                for (cand in candidates) {
                    val corrected = correctContainerChars(cand)
                    if (Iso6346.isValid(corrected) && done.compareAndSet(false, true)) {
                        onValidContainerIdFound(corrected)
                        return@addOnSuccessListener
                    }
                }

                // Ningún candidato pasó ISO — ofrecer el raw al corrector Gemini Nano.
                if (raw.length == 11 && !done.get()) {
                    onRawCandidateAvailable?.invoke(raw)
                }

                // MODO PRUEBA: al pulsar el disparador devuelve el texto CRUDO de la tira
                // sintética, para medir la precisión real antes de exigir validación.
                if (captureRequested.get() && raw.isNotEmpty() && done.compareAndSet(false, true)) {
                    onValidContainerIdFound(raw)
                }
            }
    }

    /** Horizontal: ML Kit lee la línea normalmente; los Symbol.boundingBox son correctos. */
    private fun processHorizontal(crop: Bitmap, roiLeft: Float, roiTop: Float, frameW: Float, frameH: Float) {
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { visionText ->
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

                onTrackingUpdated(
                    column.map { (text, box) ->
                        DetectedCharacter(
                            text = text,
                            boundingBox = RectF(
                                (roiLeft + box.left)   / frameW,
                                (roiTop  + box.top)    / frameH,
                                (roiLeft + box.right)  / frameW,
                                (roiTop  + box.bottom) / frameH,
                            ),
                        )
                    },
                )

                // Auto-detect horizontal: 11 chars consecutivos, corrección + dígito ISO 6346
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
                    // Ningún match pasó ISO — reportar el primer candidato a Gemini Nano.
                    if (!done.get()) {
                        Regex("[A-Z0-9]{11}").find(raw)?.let { match ->
                            onRawCandidateAvailable?.invoke(match.value)
                        }
                    }
                }

                // Captura manual (botón disparador)
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
        private val CATEGORY_LETTERS = setOf('U', 'J', 'Z')

        internal fun correctContainerChars(raw: String): String {
            if (raw.length != 11) return raw
            return buildString(11) {
                for (i in raw.indices) {
                    val c = raw[i].uppercaseChar()
                    when {
                        // 4ª letra = identificador de categoría. En reefers es siempre U,
                        // así que tras la corrección dígito→letra, si no es U/J/Z forzamos
                        // U. isValid luego revalida el dígito: si la U no cuadra, se rechaza.
                        i == 3 -> {
                            val letter = when (c) { '0' -> 'O'; '1' -> 'I'; '8' -> 'B'; '5' -> 'S'; else -> c }
                            append(if (letter in CATEGORY_LETTERS) letter else 'U')
                        }
                        i < 4 -> append(when (c) { '0' -> 'O'; '1' -> 'I'; '8' -> 'B'; '5' -> 'S'; else -> c })
                        else -> append(when (c) { 'O' -> '0'; 'I' -> '1'; 'B' -> '8'; 'S' -> '5'; 'G' -> '6'; 'Z' -> '2'; else -> c })
                    }
                }
            }
        }

        // ── TAMAÑOS DEL ROI (única fuente de verdad) ─────────────────────────────
        // Fracción del frame (0..1). El recorte del OCR (cropRoi) y el recuadro que
        // dibuja ScannerViewfinder usan ESTOS mismos valores, así que ajusta aquí y
        // ambos cambian juntos. Todos los ROI están centrados.
        internal const val ROI_VERTICAL_WIDTH  = 0.18f
        internal const val ROI_VERTICAL_HEIGHT = 0.85f
        internal const val ROI_HORIZ_WIDTH     = 0.80f
        internal const val ROI_HORIZ_HEIGHT    = 0.20f

        // Tira sintética: cada glifo se normaliza a esta altura y se pegan en fila.
        private const val STRIP_GLYPH_HEIGHT = 96
        private const val STRIP_PADDING      = 16
        private const val STRIP_GAP          = 16

        // Voto temporal: cuántas lecturas de 11 chars guardar y mínimo para votar.
        private const val VOTE_WINDOW = 12
        private const val VOTE_MIN    = 3

        // Rango plausible de glifos detectados para intentar reconocer (ISO = 11 chars).
        private val PLAUSIBLE_GLYPH_RANGE = 8..14

        /**
         * Voto por posición: para cada una de las 11 posiciones toma el carácter más
         * frecuente entre las lecturas recientes. Reconstruye el número correcto aunque
         * ningún frame individual lo haya leído entero bien (cada uno falla un char
         * distinto). Asume que todas las lecturas tienen longitud 11.
         */
        private fun majorityVote(reads: List<String>): String {
            val sb = StringBuilder(11)
            for (i in 0 until 11) {
                val counts = HashMap<Char, Int>()
                for (r in reads) {
                    val c = r[i]
                    counts[c] = (counts[c] ?: 0) + 1
                }
                sb.append(counts.maxByOrNull { it.value }!!.key)
            }
            return sb.toString()
        }

        /**
         * Compone una LÍNEA HORIZONTAL a partir de los glifos verticales: recorta cada
         * uno del crop, lo escala a una altura común y los pega de izquierda a derecha
         * sobre fondo blanco. Así ML Kit recibe una "palabra" horizontal normal — su
         * caso fuerte — en vez de la columna vertical que no sabe leer. Endereza el skew
         * (cada glifo va recto) y los agujeros del cangrejo se vuelven ruido inocuo.
         * Nota: los números de contenedor son oscuro-sobre-claro, que es lo que asume
         * este pegado sobre blanco (caso real de los reefer).
         */
        private fun composeStrip(crop: Bitmap, glyphs: List<ProjectionCharDetector.Glyph>): Bitmap? {
            if (glyphs.isEmpty()) return null
            val targetH = STRIP_GLYPH_HEIGHT
            val widths = glyphs.map { g ->
                ((g.width.toFloat() * targetH) / g.height.coerceAtLeast(1))
                    .toInt().coerceIn(1, targetH * 3)
            }
            val totalW = STRIP_PADDING * 2 + widths.sum() + STRIP_GAP * (glyphs.size - 1)
            val strip = Bitmap.createBitmap(
                totalW.coerceAtLeast(1),
                targetH + STRIP_PADDING * 2,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(strip)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var x = STRIP_PADDING
            for (i in glyphs.indices) {
                val g = glyphs[i]
                val dst = Rect(x, STRIP_PADDING, x + widths[i], STRIP_PADDING + targetH)
                // Binariza con Otsu local (negro sobre blanco). Si la región tiene poco
                // contraste devuelve null → usamos el recorte original (camino probado).
                val glyphBmp = ProjectionCharDetector.binarizedGlyph(crop, g)
                if (glyphBmp != null) {
                    canvas.drawBitmap(glyphBmp, null, dst, paint)
                    glyphBmp.recycle()
                } else {
                    canvas.drawBitmap(crop, Rect(g.left, g.top, g.right, g.bottom), dst, paint)
                }
                x += widths[i] + STRIP_GAP
            }
            return strip
        }

        private fun Bitmap.cropTo(rect: Rect): Bitmap {
            val x = rect.left.coerceIn(0, width - 1)
            val y = rect.top.coerceIn(0, height - 1)
            val w = rect.width().coerceIn(1, width - x)
            val h = rect.height().coerceIn(1, height - y)
            if (x == 0 && y == 0 && w == width && h == height) return this
            return Bitmap.createBitmap(this, x, y, w, h)
        }

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