package com.checkingcontainer.feature.units

import android.graphics.Bitmap

/**
 * Detecta el recuadro de cada carácter mediante Projection Profile.
 *
 * Arquitectura: la DETECCIÓN (¿dónde están los caracteres?) se separa del
 * RECONOCIMIENTO (¿qué dicen?). ML Kit hace el reconocimiento; esto la detección
 * — como un detector de caras localiza caras antes de identificarlas.
 *
 * Cómo funciona:
 *  1. Luminancia por píxel
 *  2. Umbral de Otsu → binariza (maneja oscuro-sobre-claro Y claro-sobre-oscuro)
 *  3. Proyección horizontal (píxeles de carácter por FILA) → rangos Y de cada glifo
 *  4. Para cada rango Y, proyección vertical (por COLUMNA) → extent X del glifo
 *  5. Fusiona segmentos muy cercanos (letras con huecos internos: '8','B','i')
 *  6. Filtra por altura para descartar agujeros/pernos de la esquina
 */
internal object ProjectionCharDetector {

    /** Recuadro de un carácter dentro del crop (coordenadas en píxeles del crop). */
    data class Glyph(val top: Int, val bottom: Int, val left: Int, val right: Int) {
        val height: Int get() = bottom - top
        val width: Int get() = right - left
    }

    /** Devuelve los glifos detectados, ordenados de arriba a abajo. */
    fun detectGlyphs(crop: Bitmap): List<Glyph> {
        val w = crop.width
        val h = crop.height
        if (w == 0 || h == 0) return emptyList()

        val pixels = IntArray(w * h)
        crop.getPixels(pixels, 0, w, 0, 0, w, h)

        // Luminancia entera: (77R + 150G + 29B) >> 8 ≈ 0.299R + 0.587G + 0.114B
        val lum = IntArray(w * h) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xFF) * 77 +
                (p shr 8 and 0xFF) * 150 +
                (p and 0xFF) * 29) ushr 8
        }

        val threshold = otsuThreshold(lum)
        // ¿Los caracteres son los píxeles oscuros o los claros? La clase minoritaria.
        val darkCount = lum.count { it < threshold }
        val charIsDark = darkCount <= (lum.size - darkCount)

        fun isFg(idx: Int): Boolean {
            val dark = lum[idx] < threshold
            return if (charIsDark) dark else !dark
        }

        // Proyección horizontal: píxeles de carácter por fila
        val rowProj = IntArray(h) { y ->
            var count = 0
            for (x in 0 until w) if (isFg(y * w + x)) count++
            count
        }
        val avgActivity = rowProj.average().toFloat()
        val rowThreshold = (avgActivity * 0.4f).toInt().coerceAtLeast(1)

        // Rangos Y de cada carácter
        val rawSegs = mutableListOf<Pair<Int, Int>>()
        var inChar = false
        var startY = 0
        for (y in 0 until h) {
            val active = rowProj[y] >= rowThreshold
            if (!inChar && active) {
                inChar = true
                startY = y
            } else if (inChar && !active) {
                inChar = false
                if (y - startY >= MIN_SEG_HEIGHT) rawSegs.add(startY to y)
            }
        }
        if (inChar && h - startY >= MIN_SEG_HEIGHT) rawSegs.add(startY to h)
        if (rawSegs.isEmpty()) return emptyList()

        // Fusiona segmentos cercanos (huecos internos de letras)
        val expectedCharH = h / EXPECTED_CHARS
        val mergeGap = (expectedCharH / 3).coerceAtLeast(MIN_MERGE_GAP)
        val merged = mutableListOf(rawSegs[0])
        for (i in 1 until rawSegs.size) {
            val prev = merged.last()
            val curr = rawSegs[i]
            if (curr.first - prev.second < mergeGap) {
                merged[merged.lastIndex] = prev.first to curr.second
            } else {
                merged.add(curr)
            }
        }

        // Para cada rango Y, extent en X (proyección vertical dentro del segmento)
        val glyphs = merged.mapNotNull { (top, bottom) ->
            val segH = bottom - top
            val colThreshold = (segH * 0.08f).toInt().coerceAtLeast(1)
            var left = w
            var right = -1
            for (x in 0 until w) {
                var count = 0
                for (y in top until bottom) if (isFg(y * w + x)) count++
                if (count >= colThreshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
            if (right < left) return@mapNotNull null
            Glyph(top, bottom, (left - 2).coerceAtLeast(0), (right + 3).coerceAtMost(w))
        }
        if (glyphs.isEmpty()) return emptyList()

        // Filtra por altura: descarta agujeros/pernos del cangrejo (outliers)
        val sortedH = glyphs.map { it.height }.sorted()
        val median = sortedH[sortedH.size / 2]
        val lo = (median * 0.4f).toInt()
        val hi = (median * 2.5f).toInt()
        val filtered = glyphs.filter { it.height in lo..hi }
        return filtered.ifEmpty { glyphs }
    }

    // Método de Otsu: maximiza la varianza entre clases para el umbral óptimo
    private fun otsuThreshold(lum: IntArray): Int {
        val hist = IntArray(256)
        for (v in lum) hist[v]++

        val total = lum.size.toDouble()
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * hist[i]

        var sumB = 0.0
        var wB = 0
        var bestVar = 0.0
        var thresh = 128

        for (i in 0 until 256) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val variance = wB * wF * (mB - mF) * (mB - mF)
            if (variance > bestVar) {
                bestVar = variance
                thresh = i
            }
        }
        return thresh
    }

    private const val EXPECTED_CHARS = 11 // longitud del ID ISO 6346
    private const val MIN_SEG_HEIGHT = 6  // ignora ruido más bajo que esto (px)
    private const val MIN_MERGE_GAP  = 4  // nunca fusiona segmentos más lejos que esto (px)
}
