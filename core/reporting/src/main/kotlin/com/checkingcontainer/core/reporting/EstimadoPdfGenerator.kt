package com.checkingcontainer.core.reporting

import com.checkingcontainer.core.model.Iso6346
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
import com.checkingcontainer.core.model.CampoFicha
import com.checkingcontainer.core.model.DiagnosticoRefrigeracion
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoTotals
import com.checkingcontainer.core.model.ObjetivoRefrigeracion
import com.checkingcontainer.core.model.ParametroGuia
import com.checkingcontainer.core.model.Severidad
import com.checkingcontainer.core.model.TipoExpansion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EstimadoPdfGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val sdfHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val usd = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-US"))
        .apply { maximumFractionDigits = 2 }

    // Todo el dibujo (Canvas, StaticLayout, bitmaps) fuera del main thread:
    // con varios ítems y fotos, hacerlo en Main congelaba la UI 2-3 segundos.
    suspend fun generate(
        estimado: Estimado,
        fichaTecnica: List<CampoFicha> = emptyList(),
    ): ByteArray = withContext(Dispatchers.Default) {
        val loader = SingletonImageLoader.get(context)

        // Pre-cargar todas las fotos antes de empezar a dibujar
        val photos = mutableMapOf<String, Bitmap?>()
        estimado.damages.forEach { item ->
            (item.damagePhotos + item.repairPhotos).forEach { url ->
                photos.getOrPut(url) { loadBitmap(loader, url) }
            }
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

        // Alto que ocuparía un texto multilínea (sin dibujarlo): se usa para decidir
        // saltos de página antes de empezar a escribir.
        fun measureHeight(text: String, paint: TextPaint, width: Float): Float {
            if (text.isEmpty()) return 0f
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build().height.toFloat()
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
            if (caption.isNotEmpty()) {
                canvas.drawText(
                    caption,
                    px + (size - pLabel.measureText(caption)) / 2,
                    py + size + 11f,
                    pLabel,
                )
            }
        }

        // Dibuja un grupo de fotos en cuadrícula (3 por fila), precedido de su
        // etiqueta ("Daño" / "Reparación"). Salta de página si no caben.
        fun photoGrid(label: String, urls: List<String>) {
            if (urls.isEmpty()) return
            val perRow = 3
            val gap = 8f
            val size = (contentW - (perRow - 1) * gap) / perRow
            // Reserva la etiqueta + la primera fila JUNTAS: si no caben, salta de
            // página antes de dibujar el rótulo. Así la imagen queda pegada debajo
            // de su etiqueta y no se estorba un rótulo huérfano al pie de página.
            checkBreak(14f + size + 10f)
            canvas.drawText(label, margin, y, pLabel); y += 14f
            var i = 0
            while (i < urls.size) {
                val rowCount = minOf(perRow, urls.size - i)
                checkBreak(size + 10f)
                val rowY = y
                for (c in 0 until rowCount) {
                    val px = margin + c * (size + gap)
                    photoBox(photos[urls[i + c]], px, rowY, size, "")
                }
                y += size + 10f
                i += rowCount
            }
            y += 4f
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

        // Heurística sin cambio de esquema: los reefer siempre pasan ISO 6346;
        // los demás equipos usan código libre.
        infoRow(if (Iso6346.isValid(estimado.containerNo)) "Contenedor:" else "Equipo:", estimado.containerNo)
        if (estimado.clientName.isNotEmpty()) infoRow("Cliente:", estimado.clientName)
        if (estimado.clientIdNumber.isNotEmpty()) infoRow("RUC/CI:", estimado.clientIdNumber)
        if (estimado.clientDireccion.isNotEmpty()) infoRow("Dirección:", estimado.clientDireccion)
        if (estimado.clientTelefono.isNotEmpty()) infoRow("Teléfono:", estimado.clientTelefono)
        if (estimado.ordenTrabajo.isNotEmpty()) infoRow("Orden de trabajo:", estimado.ordenTrabajo)
        if (estimado.sitioNombre.isNotEmpty()) infoRow("Trabajo en:", estimado.sitioNombre)
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

            // Ficha técnica completa (placa) — pares en dos columnas.
            if (fichaTecnica.isNotEmpty()) {
                y += 4f
                checkBreak(14f)
                canvas.drawText("FICHA TÉCNICA (PLACA)", margin, y, pSection); y += 12f
                var i = 0
                while (i < fichaTecnica.size) {
                    checkBreak(13f)
                    val a = fichaTecnica[i]
                    val b = fichaTecnica.getOrNull(i + 1)
                    equipRow("${a.etiqueta}:", a.valor, b?.let { "${it.etiqueta}:" } ?: "", b?.valor ?: "")
                    i += 2
                }
            }

            y += 4f; hLine(); y += 12f
        }

        // ════════════════════════════════════════════════════════════════════
        // MEDICIONES DEL EQUIPO (capturas BLE: presiones, SH/SC, corriente)
        // ════════════════════════════════════════════════════════════════════
        if (estimado.mediciones.isNotEmpty()) {
            fun num(v: Double?, dec: Int = 1): String =
                v?.let { String.format(Locale.US, "%.${dec}f", it) } ?: "—"

            // ── Paints y colores de la tabla de mediciones ──────────────────
            val cRojo = 0xFFC62828.toInt()      // ALTA / descarga / líquido (cálido)
            val cAzul = 0xFF0277BD.toInt()      // BAJA / succión / vapor (frío)
            val cRojoTenue = 0xFFFCE9E7.toInt()
            val cAzulTenue = 0xFFE3F2FD.toInt()
            val cGrisFila = 0xFFF5F5F5.toInt()
            val pCellHdr = TextPaint().apply { color = Color.WHITE; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
            val pCellLbl = TextPaint().apply { color = 0xFF555555.toInt(); textSize = 9f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
            val pCellVal = TextPaint().apply { color = 0xFF222222.toInt(); textSize = 9.5f; isAntiAlias = true }

            fun fillRect(x0: Float, y0: Float, x1: Float, y1: Float, c: Int) =
                canvas.drawRect(RectF(x0, y0, x1, y1), Paint().apply { color = c; style = Paint.Style.FILL })

            fun centrado(text: String, cx: Float, baseY: Float, p: TextPaint) =
                canvas.drawText(text, cx - p.measureText(text) / 2f, baseY, p)

            // Geometría de la tabla: columna de etiqueta + dos columnas (ALTA/BAJA).
            val labelW = 96f
            val cellW = (contentW - labelW) / 2f
            val xLbl = margin
            val xAlta = margin + labelW
            val xBaja = xAlta + cellW
            val xEnd = xBaja + cellW
            val rowH = 15f

            canvas.drawText("MEDICIONES DEL EQUIPO", margin, y, pSection); y += 12f

            estimado.mediciones.forEach { m ->
                val objetivos = ObjetivoRefrigeracion.efectivos(m.tipoExpansion, m.objetivoManual())
                val obs = DiagnosticoRefrigeracion.evaluar(m)
                val diagH = measureHeight(obs.texto, pBody, contentW - 24f) + 8f
                // Todo el bloque de una captura se mantiene junto (cabecera + tabla + diagnóstico).
                checkBreak(12f + rowH * 6 + diagH + 14f)

                // Cabecera: fecha · gas · dispositivo de expansión · medidores.
                val cab = sdfHora.format(Date(m.timestamp)) +
                    (if (m.refrigerante.isNotEmpty()) "  ·  ${m.refrigerante}" else "") +
                    (if (m.tipoExpansion != TipoExpansion.NO_ESPECIFICADO) "  ·  ${m.tipoExpansion.abreviatura}" else "") +
                    (if (m.dispositivos.isNotEmpty()) "  ·  ${m.dispositivos.joinToString(", ")}" else "")
                canvas.drawText(cab, margin, y, pLabel); y += 12f

                // Fila de encabezado con las dos columnas coloreadas.
                fillRect(xAlta, y - rowH + 4f, xBaja, y + 4f, cRojo)
                fillRect(xBaja, y - rowH + 4f, xEnd, y + 4f, cAzul)
                centrado("ALTA / Descarga", (xAlta + xBaja) / 2f, y, pCellHdr)
                centrado("BAJA / Succión", (xBaja + xEnd) / 2f, y, pCellHdr)
                y += rowH

                // Una fila de datos: etiqueta + valor ALTA + valor BAJA, con tinte de columna.
                fun filaDatos(etiqueta: String, valAlta: String, valBaja: String, guia: Boolean = false) {
                    fillRect(xLbl, y - rowH + 4f, xAlta, y + 4f, cGrisFila)
                    fillRect(xAlta, y - rowH + 4f, xBaja, y + 4f, cRojoTenue)
                    fillRect(xBaja, y - rowH + 4f, xEnd, y + 4f, cAzulTenue)
                    canvas.drawText(etiqueta + if (guia) "  ★" else "", xLbl + 4f, y, pCellLbl)
                    centrado(valAlta, (xAlta + xBaja) / 2f, y, pCellVal)
                    centrado(valBaja, (xBaja + xEnd) / 2f, y, pCellVal)
                    y += rowH
                }

                val guiaSH = objetivos.guia == ParametroGuia.RECALENTAMIENTO
                val guiaSC = objetivos.guia == ParametroGuia.SUBENFRIAMIENTO
                filaDatos("Presión", "${num(m.presionAltaPsig, 0)} psi", "${num(m.presionBajaPsig, 0)} psi")
                filaDatos("Temperatura", "${num(m.tempDescargaC)} °C", "${num(m.tempSuccionC)} °C")
                filaDatos("Saturación", "${num(m.satLiquidoC)} °C", "${num(m.satVaporC)} °C")
                filaDatos("Subcool / Superheat", "${num(m.subcoolingC)} °C", "${num(m.superheatC)} °C", guia = guiaSH || guiaSC)

                // Fila corriente + objetivo (ancho completo bajo la tabla).
                val guiaNombre = when (objetivos.guia) {
                    ParametroGuia.SUBENFRIAMIENTO -> "Subcooling"
                    ParametroGuia.RECALENTAMIENTO -> "Superheat"
                    ParametroGuia.NINGUNO -> ""
                }
                val objTxt = objetivos.rangoGuia()?.let { "Objetivo $guiaNombre: ${it.etiqueta()}" }
                    ?: "Objetivo: seleccione dispositivo de expansión"
                canvas.drawText("Corriente  ${num(m.corrienteA)} A", xLbl + 4f, y + rowH - 4f, pBody)
                canvas.drawText(objTxt, xBaja - 40f, y + rowH - 4f, pBody)
                y += rowH + 2f

                // Diagnóstico técnico: caja coloreada por severidad con la observación.
                val bgDiag = when (obs.severidad) {
                    Severidad.OK -> 0xFFE8F5E9.toInt()
                    Severidad.ALERTA -> 0xFFFFF3E0.toInt()
                    Severidad.INFO -> 0xFFF5F5F5.toInt()
                }
                val icono = when (obs.severidad) {
                    Severidad.OK -> "OK  "
                    Severidad.ALERTA -> "! "
                    Severidad.INFO -> "i  "
                }
                val txtDiag = icono + obs.texto
                val hDiag = measureHeight(txtDiag, pBody, contentW - 16f)
                val topDiag = y
                fillRect(margin, topDiag, pageW - margin, topDiag + hDiag + 14f, bgDiag)
                y += 9f
                drawMultiline(txtDiag, pBody, contentW - 16f, margin + 8f)
                y = topDiag + hDiag + 14f + 6f
            }

            y += 4f; hLine(); y += 12f
        }

        // ════════════════════════════════════════════════════════════════════
        // ÍTEMS DE DAÑO
        // ════════════════════════════════════════════════════════════════════
        estimado.damages.forEachIndexed { idx, item ->
            // Mantener encabezado + descripción juntos. Las fotos se gestionan
            // solas dentro de photoGrid (que también evita rótulos huérfanos).
            val descH = measureHeight(item.damageDescription, pBody, contentW)
            checkBreak((16f + descH + 8f).coerceAtMost(pageH - 2 * margin))

            canvas.drawText("ÍTEM ${idx + 1}", margin, y, pSection); y += 4f

            y += drawMultiline(item.damageDescription, pBody, contentW); y += 8f

            // Fotos: grupo de daño (antes) y grupo de reparación (después).
            photoGrid("Daño (antes):", item.damagePhotos)
            photoGrid("Reparación (después):", item.repairPhotos)

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

        val totals = EstimadoTotals.calcular(estimado.damages, estimado.hasIva)
        val labTotal = totals.laborTotal
        val matTotal = totals.materialTotal
        val ivaAmt = totals.ivaAmount
        val total = totals.total

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

        ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    // Un fallo puntual de red dejaba "Sin foto" en el PDF de forma definitiva:
    // se reintenta una vez antes de rendirse.
    private suspend fun loadBitmap(loader: coil3.ImageLoader, url: String): Bitmap? =
        loadBitmapOnce(loader, url) ?: loadBitmapOnce(loader, url)

    private suspend fun loadBitmapOnce(loader: coil3.ImageLoader, url: String): Bitmap? =
        runCatching {
            // Las fotos se dibujan a ~175pt en el PDF: decodificar a 700px en vez
            // de la resolución completa de la cámara baja ~10x el pico de memoria
            // (se retienen todas las fotos a la vez mientras se dibuja).
            val req = ImageRequest.Builder(context).data(url).size(700).build()
            val bmp = (loader.execute(req) as? SuccessResult)?.image?.let { (it as? BitmapImage)?.bitmap }
            // PDF canvas is software-rendered; hardware bitmaps must be copied to software
            bmp?.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        }.getOrNull()
}
