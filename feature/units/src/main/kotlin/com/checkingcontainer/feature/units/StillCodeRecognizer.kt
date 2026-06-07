package com.checkingcontainer.feature.units

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Reconoce el código de contenedor sobre una FOTO estática de alta resolución.
 *
 * Sustituye al análisis continuo: con una sola captura full-res hay píxeles suficientes
 * (ML Kit necesita ≥16-24px por carácter) y tiempo para deskew + binarizado + varias
 * tuberías, eligiendo la que valide ISO 6346.
 *
 * `recognize()` es BLOQUEANTE (usa Tasks.await sobre ML Kit) → llamar en hilo de fondo.
 * Devuelve el mejor esfuerzo (cadena editable) o null si no sacó nada.
 */
internal object StillCodeRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // ── TAMAÑOS DEL ROI (única fuente de verdad; ScannerViewfinder los usa) ───────
    // Fracción del frame (0..1), centrado. Vertical estrecho y alto; horizontal ancho.
    const val ROI_VERTICAL_WIDTH  = 0.18f
    const val ROI_VERTICAL_HEIGHT = 0.85f
    const val ROI_HORIZ_WIDTH     = 0.80f
    const val ROI_HORIZ_HEIGHT    = 0.20f

    fun recognize(imageProxy: ImageProxy, mode: ScannerMode, vertical: Boolean): Map<String, String>? {
        val raw = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val cropRect = imageProxy.cropRect
        // ViewPort recortó la foto al mismo FOV que el preview (cropRect). Recortamos
        // antes de rotar para alinear con lo que vio el usuario.
        val frame = raw.cropTo(cropRect).rotate(rotation)
        val isVertical = vertical && mode == ScannerMode.CONTAINER
        val roi = frame.cropRoi(isVertical)
        return if (isVertical) recognizeVertical(roi) else recognizeHorizontal(roi, mode)
    }

    private fun recognizeHorizontal(roi: Bitmap, mode: ScannerMode): Map<String, String>? {
        val text = readText(roi) ?: return null
        return when (mode) {
            ScannerMode.CONTAINER -> parseContainer(text) ?: bestEffortContainer(text)
            ScannerMode.DATA_PLATE -> parseDataPlate(text)
        }
    }

    private fun recognizeVertical(roi: Bitmap): Map<String, String>? {
        // 1) Enderezar el ángulo (deskew) con los centroides de los glifos.
        val deskewed = deskew(roi)
        val glyphs = ProjectionCharDetector.detectGlyphs(deskewed)
        if (glyphs.size in PLAUSIBLE_GLYPHS) return recognizeFromGlyphs(deskewed, glyphs)
        // Respaldo: probar sin deskew por si el enderezado ensució la detección.
        val plain = ProjectionCharDetector.detectGlyphs(roi)
        if (plain.size in PLAUSIBLE_GLYPHS) return recognizeFromGlyphs(roi, plain)
        return null
    }

    /** Ensemble: tira sintética (binarizada y original) + per-carácter; elige la mejor. */
    private fun recognizeFromGlyphs(roi: Bitmap, glyphs: List<ProjectionCharDetector.Glyph>): Map<String, String>? {
        val candidates = mutableListOf<String>()
        composeStrip(roi, glyphs, binarize = true)?.let { strip ->
            readText(strip)?.let(candidates::add); strip.recycle()
        }
        composeStrip(roi, glyphs, binarize = false)?.let { strip ->
            readText(strip)?.let(candidates::add); strip.recycle()
        }
        perCharRead(roi, glyphs)?.let(candidates::add)
        return pickBest(candidates)
    }

    private fun pickBest(candidates: List<String>): Map<String, String>? {
        val cleaned = candidates
            .map { it.filter(Char::isLetterOrDigit).uppercase() }
            .filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null

        // 1) ¿algún candidato valida ISO tras corrección? → ganador seguro.
        for (c in cleaned) {
            if (c.length == 11) {
                val corrected = correctContainerChars(c)
                if (Iso6346.isValid(corrected)) return mapOf("Container No." to corrected)
            }
        }
        // 2) mejor esfuerzo (editable): el de mejor puntaje de estructura/longitud.
        val best = cleaned.maxByOrNull(::structureScore) ?: return null
        val out = if (best.length == 11) correctContainerChars(best) else best
        return mapOf("Container No." to out)
    }

    /** Más cerca de 11 mejor; bonus por estructura (0-3 letras, 4-9 dígitos). */
    private fun structureScore(s: String): Int {
        var score = 20 - abs(11 - s.length)
        for (i in s.indices) {
            when {
                i < 4 && s[i].isLetter() -> score++
                i in 4..9 && s[i].isDigit() -> score++
            }
        }
        return score
    }

    private fun perCharRead(roi: Bitmap, glyphs: List<ProjectionCharDetector.Glyph>): String? {
        val sb = StringBuilder()
        for (g in glyphs) {
            val bmp = ProjectionCharDetector.binarizedGlyph(roi, g) ?: continue
            val up = if (bmp.height < PERCHAR_HEIGHT) {
                val w = ((bmp.width.toFloat() * PERCHAR_HEIGHT) / bmp.height).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bmp, w, PERCHAR_HEIGHT, true)
            } else {
                bmp
            }
            readText(up)?.let { sb.append(it.filter(Char::isLetterOrDigit)) }
            if (up !== bmp) up.recycle()
            bmp.recycle()
        }
        return sb.toString().ifEmpty { null }
    }

    /** Lectura ML Kit BLOQUEANTE (Tasks.await). Devuelve el texto o null. */
    private fun readText(bmp: Bitmap): String? = try {
        val result: Text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
        result.text.ifBlank { null }
    } catch (_: Exception) {
        null
    }

    /** Endereza el ROI rotándolo según la inclinación de la columna de caracteres. */
    private fun deskew(roi: Bitmap): Bitmap {
        val glyphs = ProjectionCharDetector.detectGlyphs(roi)
        if (glyphs.size < 3) return roi
        val xs = glyphs.map { (it.left + it.right) / 2f }
        val ys = glyphs.map { (it.top + it.bottom) / 2f }
        val mx = xs.average().toFloat()
        val my = ys.average().toFloat()
        var sxy = 0f
        var syy = 0f
        for (i in glyphs.indices) {
            val dy = ys[i] - my
            sxy += (xs[i] - mx) * dy
            syy += dy * dy
        }
        if (syy == 0f) return roi
        val slope = sxy / syy // dx/dy de la columna (≈0 si está vertical)
        val angleDeg = Math.toDegrees(atan2(slope.toDouble(), 1.0)).toFloat()
        if (abs(angleDeg) < 2f || abs(angleDeg) > 20f) return roi // ya recto, o ángulo absurdo
        val m = Matrix().apply { postRotate(-angleDeg, roi.width / 2f, roi.height / 2f) }
        return Bitmap.createBitmap(roi, 0, 0, roi.width, roi.height, m, true)
    }

    /**
     * Compone una línea horizontal pegando los glifos en fila. `binarize` usa el Otsu
     * local por glifo (limpio, robusto al glare); si no hay contraste cae al recorte
     * original. Con `binarize=false` siempre usa el recorte original.
     */
    private fun composeStrip(
        crop: Bitmap,
        glyphs: List<ProjectionCharDetector.Glyph>,
        binarize: Boolean,
    ): Bitmap? {
        if (glyphs.isEmpty()) return null
        val targetH = STRIP_GLYPH_HEIGHT
        val widths = glyphs.map { g ->
            ((g.width.toFloat() * targetH) / g.height.coerceAtLeast(1)).toInt().coerceIn(1, targetH * 3)
        }
        val totalW = STRIP_PADDING * 2 + widths.sum() + STRIP_GAP * (glyphs.size - 1)
        val strip = Bitmap.createBitmap(totalW.coerceAtLeast(1), targetH + STRIP_PADDING * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var x = STRIP_PADDING
        for (i in glyphs.indices) {
            val g = glyphs[i]
            val dst = Rect(x, STRIP_PADDING, x + widths[i], STRIP_PADDING + targetH)
            val bin = if (binarize) ProjectionCharDetector.binarizedGlyph(crop, g) else null
            if (bin != null) {
                canvas.drawBitmap(bin, null, dst, paint)
                bin.recycle()
            } else {
                canvas.drawBitmap(crop, Rect(g.left, g.top, g.right, g.bottom), dst, paint)
            }
            x += widths[i] + STRIP_GAP
        }
        return strip
    }

    private fun bestEffortContainer(text: String): Map<String, String>? {
        val s = text.filter(Char::isLetterOrDigit).uppercase()
        return if (s.isEmpty()) null else mapOf("Container No." to s.take(11))
    }

    // ── Corrección por posición + parsers (reutilizados; antes en TextRecognitionAnalyzer) ──

    private val CATEGORY_LETTERS = setOf('U', 'J', 'Z')

    /**
     * Corrige confusiones de OCR por posición ISO 6346. La 4ª letra (categoría) en
     * reefers es siempre U: si no es U/J/Z se fuerza U (isValid revalida el dígito).
     */
    fun correctContainerChars(raw: String): String {
        if (raw.length != 11) return raw
        return buildString(11) {
            for (i in raw.indices) {
                val c = raw[i].uppercaseChar()
                when {
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

    fun parseContainer(text: String): Map<String, String>? {
        val upper = text.uppercase()
        val noSpaces = upper.replace(Regex("\\s+"), "")
        Regex("[A-Z0-9]{11}").findAll(noSpaces).forEach { m ->
            val corrected = correctContainerChars(m.value)
            if (Iso6346.isValid(corrected)) return mapOf("Container No." to corrected)
        }
        Regex("[A-Z]{4}[0-9]{7}").find(noSpaces)?.let { m ->
            return mapOf("Container No." to m.value)
        }
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

    // ── Utilidades de bitmap ─────────────────────────────────────────────────────

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
            roiW = (width * ROI_VERTICAL_WIDTH).toInt().coerceIn(1, width)
            roiH = (height * ROI_VERTICAL_HEIGHT).toInt().coerceIn(1, height)
        } else {
            roiW = (width * ROI_HORIZ_WIDTH).toInt().coerceIn(1, width)
            roiH = (height * ROI_HORIZ_HEIGHT).toInt().coerceIn(1, height)
        }
        val x = (width - roiW) / 2
        val y = (height - roiH) / 2
        return Bitmap.createBitmap(this, x, y, roiW, roiH)
    }

    private val PLAUSIBLE_GLYPHS = 8..14 // nº de glifos esperado (~11)
    private const val STRIP_GLYPH_HEIGHT = 96
    private const val STRIP_PADDING = 16
    private const val STRIP_GAP = 16
    private const val PERCHAR_HEIGHT = 96
}
