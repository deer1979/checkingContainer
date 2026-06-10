package com.checkingcontainer.feature.units

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.checkingcontainer.core.model.Estimado
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EstimadoPdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val usd = NumberFormat.getCurrencyInstance(Locale("es", "US"))
        .apply { maximumFractionDigits = 2 }

    suspend fun generate(estimado: Estimado): ByteArray {
        val loader = SingletonImageLoader.get(context)

        // Pre-cargar todas las fotos antes de empezar a dibujar
        val photos = mutableMapOf<String, Bitmap?>()
        estimado.damages.forEach { item ->
            item.damagePhoto?.let { url -> photos.getOrPut(url) { loadBitmap(loader, url) } }
            item.repairPhoto?.let { url -> photos.getOrPut(url) { loadBitmap(loader, url) } }
        }

        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val contentW = pageW - 2 * margin

        // ── Paints ──────────────────────────────────────────────────────────
        val pTitle = TextPaint().apply {
            color = Color.BLACK; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val pSection = TextPaint().apply {
            color = 0xFF1565C0.toInt(); textSize = 11f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val pSubheader = TextPaint().apply {
            color = Color.DKGRAY; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val pBody = TextPaint().apply {
            color = 0xFF333333.toInt(); textSize = 10f; isAntiAlias = true
        }
        val pBold = TextPaint().apply {
            color = Color.BLACK; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val pLabel = TextPaint().apply {
            color = Color.GRAY; textSize = 9f; isAntiAlias = true
        }
        val pHline = Paint().apply {
            color = Color.LTGRAY; strokeWidth = 0.5f; isAntiAlias = true
        }

        // ── Estado mutable del contexto de dibujo ──────────────────────────
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var canvas: Canvas = page.canvas
        var y = margin

        fun hLine() = canvas.drawLine(margin, y, pageW - margin, y, pHline)

        fun checkBreak(needed: Float) {
            if (y + needed > pageH - margin) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                canvas = page.canvas
                y = margin
            }
        }

        fun drawMultiline(text: String, paint: TextPaint, width: Float, x: Float = margin): Float {
            val sl = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()
            canvas.save()
            canvas.translate(x, y)
            sl.draw(canvas)
            canvas.restore()
            return sl.height.toFloat()
        }

        fun infoRow(label: String, value: String) {
            canvas.drawText(label, margin, y, pLabel)
            canvas.drawText(value, margin + 110f, y, pBody)
            y += 13f
        }

        fun photoBox(bmp: Bitmap?, px: Float, py: Float, size: Float, caption: String) {
            val dst = RectF(px, py, px + size, py + size)
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, dst, null)
            } else {
                val bg = Paint().apply { color = 0xFFEEEEEE.toInt(); style = Paint.Style.FILL }
                canvas.drawRect(dst, bg)
                canvas.drawText(
                    "Sin foto",
                    px + (size - pLabel.measureText("Sin foto")) / 2,
                    py + size / 2 + 4f,
                    pLabel,
                )
            }
            canvas.drawText(
                caption,
                px + (size - pLabel.measureText(caption)) / 2,
                py + size + 11f,
                pLabel,
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // HEADER
        // ════════════════════════════════════════════════════════════════════
        canvas.drawText("CHECKING CONTAINER", margin, y, pTitle)
        val subtitle = "ESTIMADO DE REPARACIÓN"
        canvas.drawText(
            subtitle,
            pageW - margin - pSubheader.measureText(subtitle),
            y,
            pSubheader,
        )
        y += 6f; hLine(); y += 12f

        infoRow("Contenedor:", estimado.containerNo)
        if (estimado.clientName.isNotEmpty()) infoRow("Cliente:", estimado.clientName)
        if (estimado.location.isNotEmpty()) infoRow("Ubicación:", estimado.location)
        infoRow("Técnico:", estimado.technicianName.ifEmpty { "—" })
        infoRow("Fecha:", sdf.format(Date(estimado.createdAt)))
        estimado.approvedAt?.let { infoRow("Aprobado:", sdf.format(Date(it))) }

        y += 4f; hLine(); y += 12f

        // ════════════════════════════════════════════════════════════════════
        // EQUIPO
        // ════════════════════════════════════════════════════════════════════
        if (estimado.manufacturer.isNotEmpty() || estimado.unitSerialNo.isNotEmpty()) {
            canvas.drawText("DATOS DEL EQUIPO", margin, y, pSection); y += 12f
            val c2 = margin + contentW / 2 + 8f

            fun equipRow(l1: String, v1: String, l2: String = "", v2: String = "") {
                canvas.drawText(l1, margin, y, pLabel)
                canvas.drawText(v1, margin + 72f, y, pBody)
                if (l2.isNotEmpty()) {
                    canvas.drawText(l2, c2, y, pLabel)
                    canvas.drawText(v2, c2 + 72f, y, pBody)
                }
                y += 13f
            }

            if (estimado.manufacturer.isNotEmpty())
                equipRow("Fabricante:", estimado.manufacturer, "Modelo:", estimado.unitModel)
            if (estimado.unitSerialNo.isNotEmpty())
                equipRow("No. Serie:", estimado.unitSerialNo, "No. Modelo:", estimado.unitModelNo)
            if (estimado.yearOfBuilt.isNotEmpty())
                equipRow("Año:", estimado.yearOfBuilt, "Tipo:", estimado.unitType)

            y += 4f; hLine(); y += 12f
        }

        // ════════════════════════════════════════════════════════════════════
        // ÍTEMS DE DAÑO
        // ════════════════════════════════════════════════════════════════════
        estimado.damages.forEachIndexed { idx, item ->
            checkBreak(50f)

            canvas.drawText("ÍTEM ${idx + 1}", margin, y, pSection); y += 4f

            y += drawMultiline(item.damageDescription, pBody, contentW); y += 8f

            // Fotos lado a lado
            val photoSz = 175f
            val gap = (contentW - 2 * photoSz) / 3
            val lx = margin + gap
            val rx = lx + photoSz + gap

            val hasDano = item.damagePhoto != null
            val hasRep = item.repairPhoto != null
            if (hasDano || hasRep) {
                checkBreak(photoSz + 24f)
                val py = y
                photoBox(item.damagePhoto?.let { photos[it] }, lx, py, photoSz, "ANTES")
                photoBox(item.repairPhoto?.let { photos[it] }, rx, py, photoSz, "DESPUÉS")
                y += photoSz + 18f
            }

            // Acción de reparación
            if (item.repairAction.isNotEmpty()) {
                checkBreak(24f)
                val lw = pBold.measureText("Reparación: ")
                canvas.drawText("Reparación: ", margin, y, pBold)
                val rh = drawMultiline(item.repairAction, pBody, contentW - lw, margin + lw)
                y += maxOf(rh, 13f) + 4f
            }

            // Costos
            val lab = item.laborCost ?: 0.0
            val mat = item.materialCost ?: 0.0
            if (lab > 0 || mat > 0) {
                checkBreak(14f)
                canvas.drawText(
                    "Mano de obra: ${usd.format(lab)}    Material: ${usd.format(mat)}",
                    margin, y, pBody,
                )
                y += 14f
            }

            y += 8f; hLine(); y += 12f
        }

        // ════════════════════════════════════════════════════════════════════
        // RESUMEN DE VALORES
        // ════════════════════════════════════════════════════════════════════
        checkBreak(90f)
        canvas.drawText("RESUMEN DE VALORES", margin, y, pSection); y += 14f

        val labTotal = estimado.damages.sumOf { it.laborCost ?: 0.0 }
        val matTotal = estimado.damages.sumOf { it.materialCost ?: 0.0 }
        val subtotal = labTotal + matTotal
        val ivaAmt = if (estimado.hasIva) subtotal * 0.12 else 0.0
        val total = subtotal + ivaAmt

        fun totalRow(label: String, amount: Double, bold: Boolean = false) {
            val p = if (bold) pBold else pBody
            canvas.drawText(label, margin + 20f, y, p)
            val amtStr = usd.format(amount)
            canvas.drawText(amtStr, pageW - margin - p.measureText(amtStr), y, p)
            y += 13f
        }

        totalRow("Mano de obra total:", labTotal)
        totalRow("Materiales total:", matTotal)
        if (estimado.hasIva) totalRow("IVA 12%:", ivaAmt)
        y += 4f; hLine(); y += 10f
        totalRow("TOTAL:", total, bold = true)

        doc.finishPage(page)

        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    private suspend fun loadBitmap(loader: coil3.ImageLoader, url: String): Bitmap? =
        runCatching {
            val req = ImageRequest.Builder(context)
                .data(url)
                .build()
            val result = loader.execute(req)
            (result as? SuccessResult)?.image?.let { (it as? BitmapImage)?.bitmap }
        }.getOrNull()
}
