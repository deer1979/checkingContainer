package com.checkingcontainer.feature.units

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Detecta el recuadro de cada carácter mediante **componentes conectados** (CCA).
 *
 * Arquitectura: la DETECCIÓN (¿dónde están los caracteres?) se separa del
 * RECONOCIMIENTO (¿qué dicen?). ML Kit hace el reconocimiento; esto la detección.
 *
 * Por qué CCA y no projection profile: en el poste real hay bordes verticales, juntas
 * y sombras que rellenan TODAS las filas → la proyección horizontal fusionaba los
 * caracteres en uno o dos bloques. Los componentes conectados son robustos a eso:
 * cada carácter es una mancha; los bordes/juntas verticales salen como un componente
 * altísimo que se filtra por tamaño.
 *
 * Cómo funciona:
 *  1. Luminancia + umbral de Otsu.
 *  2. Polaridad FIJA: texto oscuro sobre fondo claro (caso de los contenedores).
 *  3. Flood-fill (8-conexión) → un componente por mancha, con su bounding box.
 *  4. Filtra: bordes/juntas (casi toda la altura), líneas horizontales (casi todo el
 *     ancho), ruido pequeño y outliers por altura mediana (agujeros/pernos).
 *  5. Fusiona fragmentos de un mismo carácter (misma banda vertical).
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
        // Polaridad FIJA: texto OSCURO sobre fondo CLARO. Auto-detectar la polaridad
        // causaba que, con mucho fondo oscuro (junta/sombra), se tomara todo el poste
        // como "carácter" → una sola caja gigante ("todo verde").
        val fg = BooleanArray(w * h) { i -> lum[i] < threshold }

        // ── Componentes conectados (flood-fill 8-conexión) ───────────────────────
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val comps = mutableListOf<Glyph>()
        for (start in 0 until w * h) {
            if (!fg[start] || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var minX = w; var minY = h; var maxX = 0; var maxY = 0; var area = 0
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                area++
                var dy = -1
                while (dy <= 1) {
                    var dx = -1
                    while (dx <= 1) {
                        if (!(dx == 0 && dy == 0)) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until w && ny in 0 until h) {
                                val nidx = ny * w + nx
                                if (fg[nidx] && !visited[nidx]) {
                                    visited[nidx] = true
                                    stack[sp++] = nidx
                                }
                            }
                        }
                        dx++
                    }
                    dy++
                }
            }
            // Filtros de forma/tamaño
            val cw = maxX - minX + 1
            val ch = maxY - minY + 1
            if (ch > h * 0.5f) continue          // borde/junta vertical del poste
            if (cw > w * 0.9f) continue           // línea horizontal
            if (ch < MIN_GLYPH_HEIGHT) continue   // ruido bajo
            if (area < MIN_GLYPH_AREA) continue   // ruido pequeño
            comps.add(Glyph(minY, maxY + 1, minX, maxX + 1))
        }
        if (comps.isEmpty()) return emptyList()

        // Descarta outliers por altura mediana (agujeros/pernos atípicos)
        val sortedH = comps.map { it.height }.sorted()
        val median = sortedH[sortedH.size / 2]
        val lo = (median * 0.5f).toInt()
        val hi = (median * 1.8f).toInt()
        val kept = comps.filter { it.height in lo..hi }.ifEmpty { comps }

        // Fusiona componentes en la misma banda vertical (fragmentos del mismo carácter)
        val sorted = kept.sortedBy { it.top }
        val merged = mutableListOf<Glyph>()
        for (g in sorted) {
            val last = merged.lastOrNull()
            if (last != null && overlapY(last, g) > 0.5f) {
                merged[merged.lastIndex] = Glyph(
                    minOf(last.top, g.top),
                    maxOf(last.bottom, g.bottom),
                    minOf(last.left, g.left),
                    maxOf(last.right, g.right),
                )
            } else {
                merged.add(g)
            }
        }
        if (merged.size <= 1) return merged

        // Filtro de COLUMNA: los caracteres del número comparten un centro-X (están
        // apilados en columna). Descarta componentes desviados a los lados —remaches,
        // bordes, óxido, texto del poste vecino— que ensucian la tira y rompen el conteo.
        val centersX = merged.map { (it.left + it.right) / 2 }.sorted()
        val medianCx = centersX[centersX.size / 2]
        val band = w * COLUMN_BAND_FRACTION
        val col = merged.filter { abs((it.left + it.right) / 2 - medianCx) <= band }
        return col.ifEmpty { merged }
    }

    /**
     * Devuelve el glifo binarizado (negro sobre blanco) con un Otsu LOCAL — robusto al
     * glare. Devuelve **null** si la región tiene poco contraste o el resultado sale
     * degenerado (casi todo un color): en ese caso el llamador usa el recorte original,
     * que es lo que demostró funcionar en campo. Evita "blanquear" glifos de bajo
     * contraste (pantallas, códigos desgastados) y dejar a ML Kit sin nada que leer.
     */
    fun binarizedGlyph(crop: Bitmap, g: Glyph): Bitmap? {
        val gw = g.width.coerceAtLeast(1)
        val gh = g.height.coerceAtLeast(1)
        val px = IntArray(gw * gh)
        crop.getPixels(px, 0, gw, g.left, g.top, gw, gh)
        val lum = IntArray(gw * gh) { i ->
            val p = px[i]
            ((p shr 16 and 0xFF) * 77 +
                (p shr 8 and 0xFF) * 150 +
                (p and 0xFF) * 29) ushr 8
        }
        var mn = 255
        var mx = 0
        for (v in lum) { if (v < mn) mn = v; if (v > mx) mx = v }
        if (mx - mn < MIN_GLYPH_CONTRAST) return null // sin contraste claro → no binarizar

        val t = otsuThreshold(lum)
        var fg = 0
        for (v in lum) if (v < t) fg++
        val frac = fg.toFloat() / lum.size
        if (frac < 0.03f || frac > 0.97f) return null // degenerado → no binarizar

        val out = IntArray(gw * gh) { i ->
            if (lum[i] < t) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(out, gw, gh, Bitmap.Config.ARGB_8888)
    }

    /** Fracción de solapamiento vertical respecto al glifo más bajo (0..1). */
    private fun overlapY(a: Glyph, b: Glyph): Float {
        val top = maxOf(a.top, b.top)
        val bottom = minOf(a.bottom, b.bottom)
        val inter = (bottom - top).coerceAtLeast(0)
        val minH = minOf(a.height, b.height).coerceAtLeast(1)
        return inter.toFloat() / minH
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

    private const val MIN_GLYPH_HEIGHT  = 10 // ignora componentes más bajos que esto (px)
    private const val MIN_GLYPH_AREA    = 20 // ignora manchas con menos píxeles que esto
    private const val MIN_GLYPH_CONTRAST = 40 // rango de luminancia mínimo para binarizar
    private const val COLUMN_BAND_FRACTION = 0.30f // ancho de banda (frac. del ROI) en torno al centro de la columna
}
