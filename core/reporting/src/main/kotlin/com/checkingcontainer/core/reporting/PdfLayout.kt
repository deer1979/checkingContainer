package com.checkingcontainer.core.reporting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Geometría de la hoja A4 a 72 dpi. */
object Hoja {
    const val ANCHO = 595
    const val ALTO = 842
    const val MARGEN = 40f
    /** Alto reservado para el pie (numeración) en cada página. */
    const val PIE = 22f

    val contenidoAncho: Float get() = ANCHO - 2 * MARGEN
    /** Y máxima utilizable antes de invadir el pie de página. */
    val limiteInferior: Float get() = ALTO - MARGEN - PIE
}

/** Paleta del documento, en un solo sitio para mantener coherencia. */
object Tinta {
    val AZUL = 0xFF1565C0.toInt()
    val TEXTO = 0xFF333333.toInt()
    val TENUE = 0xFF888888.toInt()
    val LINEA = 0xFFDDDDDD.toInt()

    val ROJO = 0xFFC62828.toInt()
    val ROJO_SUAVE = 0xFFFCE9E7.toInt()
    val AZUL_FRIO = 0xFF0277BD.toInt()
    val AZUL_SUAVE = 0xFFE3F2FD.toInt()
    val VERDE = 0xFF2E7D32.toInt()
    val VERDE_SUAVE = 0xFFE8F5E9.toInt()
    val AMBAR_SUAVE = 0xFFFFF3E0.toInt()
    val GRIS_FILA = 0xFFF5F5F5.toInt()
    val MARCO = 0xFFEEEEEE.toInt()
}

/** Juego de pinceles del documento. */
class Pinceles {
    val titulo = texto(18f, Color.BLACK, negrita = true)
    val subtitulo = texto(12f, Color.DKGRAY, negrita = true)
    val seccion = texto(11f, Tinta.AZUL, negrita = true)
    val cuerpo = texto(10f, Tinta.TEXTO)
    val negrita = texto(10f, Color.BLACK, negrita = true)
    val etiqueta = texto(9f, Tinta.TENUE)
    val pie = texto(8f, Tinta.TENUE)
    val itemNumero = texto(13f, Tinta.AZUL, negrita = true)
    val itemNombre = texto(12f, Color.BLACK, negrita = true)
    val columnaHdr = texto(8.5f, Color.WHITE, negrita = true)
    val celdaEtiqueta = texto(9f, 0xFF555555.toInt(), negrita = true)
    val celdaValor = texto(9.5f, 0xFF222222.toInt())
    val chip = texto(8f, Color.WHITE, negrita = true)

    val linea = Paint().apply {
        color = Tinta.LINEA
        strokeWidth = 0.6f
        isAntiAlias = true
    }

    private fun texto(size: Float, color: Int, negrita: Boolean = false) = TextPaint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        if (negrita) typeface = Typeface.DEFAULT_BOLD
    }
}

/**
 * Lienzo paginado: lleva la posición vertical, abre páginas nuevas cuando hace
 * falta y numera el pie al cerrar.
 *
 * El truco para numerar "Página 2 de 5" sin saber el total de antemano es
 * guardar las páginas terminadas y estampar el pie al final, en [finalizar].
 */
class LienzoPdf(private val doc: PdfDocument, private val p: Pinceles) {

    private var numero = 1
    private var pagina = nuevaPagina(1)
    private val terminadas = mutableListOf<PdfDocument.Page>()

    var canvas: Canvas = pagina.canvas
        private set
    var y: Float = Hoja.MARGEN

    /** Se dibuja al abrir cada página después de la primera (encabezado repetido). */
    var encabezadoContinuacion: (() -> Unit)? = null

    private fun nuevaPagina(n: Int) =
        doc.startPage(PdfDocument.PageInfo.Builder(Hoja.ANCHO, Hoja.ALTO, n).create())

    val espacioLibre: Float get() = Hoja.limiteInferior - y

    /** Abre página nueva si [alto] no cabe en lo que queda. Devuelve true si saltó. */
    fun asegurar(alto: Float): Boolean {
        if (y + alto <= Hoja.limiteInferior) return false
        saltarPagina()
        return true
    }

    fun saltarPagina() {
        terminadas += pagina
        numero++
        pagina = nuevaPagina(numero)
        canvas = pagina.canvas
        y = Hoja.MARGEN
        encabezadoContinuacion?.invoke()
    }

    fun linea(alto: Float = 0f) {
        y += alto
        canvas.drawLine(Hoja.MARGEN, y, Hoja.ANCHO - Hoja.MARGEN, y, p.linea)
    }

    fun texto(t: String, x: Float, paint: TextPaint) = canvas.drawText(t, x, y, paint)

    fun textoDerecha(t: String, xDerecha: Float, paint: TextPaint) =
        canvas.drawText(t, xDerecha - paint.measureText(t), y, paint)

    fun textoCentrado(t: String, centroX: Float, paint: TextPaint) =
        canvas.drawText(t, centroX - paint.measureText(t) / 2f, y, paint)

    fun relleno(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, radio: Float = 0f) {
        val pintura = Paint().apply { this.color = color; style = Paint.Style.FILL; isAntiAlias = true }
        if (radio > 0f) canvas.drawRoundRect(RectF(x0, y0, x1, y1), radio, radio, pintura)
        else canvas.drawRect(RectF(x0, y0, x1, y1), pintura)
    }

    fun borde(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, radio: Float = 0f) {
        val pintura = Paint().apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true
        }
        if (radio > 0f) canvas.drawRoundRect(RectF(x0, y0, x1, y1), radio, radio, pintura)
        else canvas.drawRect(RectF(x0, y0, x1, y1), pintura)
    }

    /** Dibuja texto con salto de línea y devuelve el alto ocupado. */
    fun parrafo(t: String, paint: TextPaint, ancho: Float, x: Float = Hoja.MARGEN): Float {
        if (t.isEmpty()) return 0f
        val layout = construir(t, paint, ancho)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Cierra el documento estampando "Página N de M" en todas las hojas. */
    fun finalizar() {
        terminadas += pagina
        val total = terminadas.size
        terminadas.forEachIndexed { indice, hoja ->
            val etiqueta = "Página ${indice + 1} de $total"
            hoja.canvas.drawText(
                etiqueta,
                Hoja.ANCHO - Hoja.MARGEN - p.pie.measureText(etiqueta),
                Hoja.ALTO - Hoja.MARGEN + 8f,
                p.pie,
            )
            doc.finishPage(hoja)
        }
    }

    companion object {
        /** Alto que ocuparía [t] sin dibujarlo (para decidir saltos de página). */
        fun medir(t: String, paint: TextPaint, ancho: Float): Float =
            if (t.isEmpty()) 0f else construir(t, paint, ancho).height.toFloat()

        private fun construir(t: String, paint: TextPaint, ancho: Float): StaticLayout =
            StaticLayout.Builder
                .obtain(t, 0, t.length, paint, ancho.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()
    }
}

/**
 * Dibuja [bmp] dentro del cuadro conservando su proporción (recorte centrado).
 *
 * Antes se estiraba la foto hasta llenar un cuadrado, lo que deformaba las
 * imágenes de la cámara (4:3 o 16:9) justo en el documento que sirve para
 * evidenciar un daño ante el cliente.
 */
fun Canvas.dibujarFotoProporcional(bmp: Bitmap, destino: RectF) {
    val escala = maxOf(destino.width() / bmp.width, destino.height() / bmp.height)
    val anchoVisible = (destino.width() / escala).coerceAtMost(bmp.width.toFloat())
    val altoVisible = (destino.height() / escala).coerceAtMost(bmp.height.toFloat())
    val origen = Rect(
        ((bmp.width - anchoVisible) / 2f).toInt(),
        ((bmp.height - altoVisible) / 2f).toInt(),
        ((bmp.width + anchoVisible) / 2f).toInt(),
        ((bmp.height + altoVisible) / 2f).toInt(),
    )
    drawBitmap(bmp, origen, destino, null)
}
