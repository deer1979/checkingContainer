package com.checkingcontainer.feature.units

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Detects individual character bounding boxes using Horizontal Projection Profile.
 *
 * Architecture: DETECTION (where are the chars?) is separate from RECOGNITION (what do they say?).
 * ML Kit handles recognition; this handles detection — like face detection finds faces
 * before the system reads who they are.
 *
 * How it works:
 *  1. Compute luminance per pixel
 *  2. Otsu threshold → binarize (handles dark-on-light AND light-on-dark)
 *  3. Count foreground pixels per row → projection profile
 *  4. Rows above threshold = character rows; rows below = gaps
 *  5. Merge very close segments (handles letters with internal gaps: '8', 'B', 'i')
 */
internal object ProjectionCharDetector {

    data class Segment(val top: Int, val bottom: Int) {
        val height: Int get() = bottom - top
        val centerY: Int get() = (top + bottom) / 2
    }

    /**
     * Returns character segments sorted top-to-bottom.
     * Each segment gives the Y range of one detected character in the crop.
     */
    fun detect(crop: Bitmap): List<Segment> {
        val w = crop.width
        val h = crop.height
        if (w == 0 || h == 0) return emptyList()

        // Read all pixels in a single call (much faster than per-pixel getPixel)
        val pixels = IntArray(w * h)
        crop.getPixels(pixels, 0, w, 0, 0, w, h)

        // Integer luminance: (77R + 150G + 29B) >> 8  ≈  0.299R + 0.587G + 0.114B
        val lum = IntArray(w * h) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xFF) * 77 +
             (p shr 8  and 0xFF) * 150 +
             (p        and 0xFF) * 29) ushr 8
        }

        // Otsu's threshold: optimal split between foreground and background
        val threshold = otsuThreshold(lum)

        // Determine polarity: are characters the DARK or the LIGHT pixels?
        // Whichever class is smaller (minority) = characters
        val darkCount = lum.count { it < threshold }
        val charIsDark = darkCount <= (lum.size - darkCount)

        // Horizontal projection: count character pixels per row
        val projection = IntArray(h) { y ->
            var count = 0
            for (x in 0 until w) {
                val isDark = lum[y * w + x] < threshold
                if (if (charIsDark) isDark else !isDark) count++
            }
            count
        }

        // Adaptive row threshold: a row "has a character" if it has at least
        // half the average character-pixel count across the whole crop
        val avgActivity = projection.average().toFloat()
        val rowThreshold = (avgActivity * 0.4f).toInt().coerceAtLeast(1)

        // Find runs of active rows → raw character segments
        val raw = mutableListOf<Segment>()
        var inChar = false
        var startY = 0
        for (y in 0 until h) {
            val active = projection[y] >= rowThreshold
            if (!inChar && active) {
                inChar = true
                startY = y
            } else if (inChar && !active) {
                inChar = false
                val seg = Segment(startY, y)
                if (seg.height >= MIN_SEG_HEIGHT) raw.add(seg)
            }
        }
        if (inChar) {
            val seg = Segment(startY, h)
            if (seg.height >= MIN_SEG_HEIGHT) raw.add(seg)
        }

        if (raw.isEmpty()) return emptyList()

        // Merge segments that are too close together.
        // Gap threshold ≈ 1/3 of the expected character height.
        // This handles letters with internal gaps ('8', 'B', 'R', dot of 'i').
        val expectedCharH = h / EXPECTED_CHARS
        val mergeGap = (expectedCharH / 3).coerceAtLeast(MIN_MERGE_GAP)
        return mergeClose(raw, mergeGap)
    }

    // Otsu's method: maximizes between-class variance to find the optimal threshold
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

    private fun mergeClose(segments: List<Segment>, minGap: Int): List<Segment> {
        if (segments.size <= 1) return segments
        val result = mutableListOf(segments[0])
        for (i in 1 until segments.size) {
            val prev = result.last()
            val curr = segments[i]
            if (curr.top - prev.bottom < minGap) {
                result[result.lastIndex] = Segment(prev.top, curr.bottom)
            } else {
                result.add(curr)
            }
        }
        return result
    }

    private const val EXPECTED_CHARS   = 11   // ISO 6346 container ID length
    private const val MIN_SEG_HEIGHT   = 6    // ignore noise segments shorter than this (px)
    private const val MIN_MERGE_GAP    = 4    // never merge segments farther apart than this (px)
}