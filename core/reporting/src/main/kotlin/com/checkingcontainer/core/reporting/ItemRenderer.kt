package com.checkingcontainer.core.reporting

import android.graphics.Bitmap
import android.graphics.RectF
import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.DamageItemStatus

/**
 * Dibuja un ítem de reparación con el daño y la reparación enfrentados.
 *
 * ```
 * 1.  Contactor C12 · P/N 3100-4520                    [ REPARADO ]
 * ┌────────── ANTES · Daño ─────────┬──── DESPUÉS · Reparación ────┐
 * │ Contactos carbonizados, no      │ Se reemplazó el contactor y  │
 * │ cortaba el circuito.            │ se probó con carga.          │
 * │   [foto]        [foto]          │   [foto]        [foto]       │
 * └─────────────────────────────────┴──────────────────────────────┘
 * ```
 *
 * Las dos descripciones van una al lado de la otra a propósito: comparten el
 * mismo espacio vertical (se paga el más largo, no la suma) y cada lado queda
 * pegado a sus fotos. Mientras no exista reparación, el daño ocupa el ancho
 * completo en vez de dejar media hoja vacía.
 */
internal class ItemRenderer(
    private val lienzo: LienzoPdf,
    private val p: Pinceles,
    private val fotos: Map<String, Bitmap?>,
) {

    /** Alto total del ítem, para decidir el salto de página antes de dibujar. */
    fun medir(item: DamageItem, indice: Int): Float {
        val d = distribucion(item)
        return ALTO_CABECERA + d.altoTextos + d.altoFotos + ALTO_CIERRE
    }

    fun dibujar(item: DamageItem, indice: Int) {
        val d = distribucion(item)
        val yInicio = lienzo.y
        val alto = ALTO_CABECERA + d.altoTextos + d.altoFotos + ALTO_CIERRE

        // Marco del ítem: el blanco que pueda quedar al pie de la hoja se lee
        // como margen del bloque y no como un hueco por mal armado.
        lienzo.relleno(
            Hoja.MARGEN - 6f, yInicio - 12f,
            Hoja.ANCHO - Hoja.MARGEN + 6f, yInicio + alto - 6f,
            Tinta.MARCO, radio = 6f,
        )

        dibujarCabecera(item, indice)
        if (d.dosColumnas) dibujarEnfrentado(item, d) else dibujarAnchoCompleto(item, d)
        lienzo.y = yInicio + alto
    }

    // ── Cabecera: número + nombre + estado ──────────────────────────────────

    private fun dibujarCabecera(item: DamageItem, indice: Int) {
        lienzo.y += 4f
        val numero = "${indice + 1}."
        lienzo.texto(numero, Hoja.MARGEN, p.itemNumero)
        val xNombre = Hoja.MARGEN + p.itemNumero.measureText(numero) + 8f

        val chip = if (item.status == DamageItemStatus.REPARADO) "REPARADO" else "PENDIENTE"
        val colorChip = if (item.status == DamageItemStatus.REPARADO) Tinta.VERDE else Tinta.TENUE
        val anchoChip = p.chip.measureText(chip) + 14f
        val xChip = Hoja.ANCHO - Hoja.MARGEN - anchoChip
        lienzo.relleno(xChip, lienzo.y - 9f, xChip + anchoChip, lienzo.y + 3f, colorChip, radio = 6f)
        lienzo.textoCentrado(chip, xChip + anchoChip / 2f, p.chip)

        // El nombre se recorta para no invadir el chip de estado.
        val disponible = xChip - xNombre - 10f
        lienzo.texto(recortar(item.nombreParaMostrar(indice), disponible), xNombre, p.itemNombre)
        lienzo.y += 16f
    }

    private fun recortar(texto: String, ancho: Float): String {
        if (p.itemNombre.measureText(texto) <= ancho) return texto
        var recortado = texto
        while (recortado.isNotEmpty() && p.itemNombre.measureText("$recortado…") > ancho) {
            recortado = recortado.dropLast(1)
        }
        return "$recortado…"
    }

    // ── Cuerpo enfrentado (hay reparación) ──────────────────────────────────

    private fun dibujarEnfrentado(item: DamageItem, d: Distribucion) {
        val xIzq = Hoja.MARGEN
        val xDer = Hoja.MARGEN + d.anchoColumna + SEPARACION

        rotuloColumna("ANTES · Daño", xIzq, d.anchoColumna, Tinta.ROJO)
        rotuloColumna("DESPUÉS · Reparación", xDer, d.anchoColumna, Tinta.VERDE)
        lienzo.y += ALTO_ROTULO

        // Ambos textos arrancan a la misma altura; las fotos también, tomando el
        // más alto de los dos, para que las cuadrículas queden a nivel.
        val yTexto = lienzo.y
        lienzo.parrafo(item.damageDescription, p.cuerpo, d.anchoColumna, xIzq)
        lienzo.y = yTexto
        lienzo.parrafo(item.repairAction, p.cuerpo, d.anchoColumna, xDer)
        lienzo.y = yTexto + d.altoTextosCuerpo

        val yFotos = lienzo.y
        cuadricula(item.damagePhotos, xIzq, d.anchoColumna, d.ladoFoto, FOTOS_POR_FILA_COLUMNA)
        lienzo.y = yFotos
        cuadricula(item.repairPhotos, xDer, d.anchoColumna, d.ladoFoto, FOTOS_POR_FILA_COLUMNA)
        lienzo.y = yFotos + d.altoFotos
    }

    // ── Cuerpo a ancho completo (aún sin reparar) ───────────────────────────

    private fun dibujarAnchoCompleto(item: DamageItem, d: Distribucion) {
        rotuloColumna("Daño", Hoja.MARGEN, Hoja.contenidoAncho, Tinta.ROJO)
        lienzo.y += ALTO_ROTULO
        lienzo.y += lienzo.parrafo(item.damageDescription, p.cuerpo, Hoja.contenidoAncho)
        lienzo.y += 6f
        cuadricula(item.damagePhotos, Hoja.MARGEN, Hoja.contenidoAncho, d.ladoFoto, FOTOS_POR_FILA_ANCHO)
    }

    private fun rotuloColumna(texto: String, x: Float, ancho: Float, color: Int) {
        lienzo.relleno(x, lienzo.y - 8f, x + ancho, lienzo.y + 4f, color, radio = 3f)
        lienzo.textoCentrado(texto, x + ancho / 2f, p.columnaHdr)
    }

    private fun cuadricula(urls: List<String>, x: Float, ancho: Float, lado: Float, porFila: Int) {
        if (urls.isEmpty()) return
        val hueco = (ancho - porFila * lado) / (porFila - 1).coerceAtLeast(1)
        urls.forEachIndexed { i, url ->
            val fila = i / porFila
            val col = i % porFila
            val px = x + col * (lado + hueco)
            val py = lienzo.y + fila * (lado + HUECO_FILA)
            val destino = RectF(px, py, px + lado, py + lado)
            val bmp = fotos[url]
            if (bmp != null) {
                lienzo.canvas.dibujarFotoProporcional(bmp, destino)
            } else {
                lienzo.relleno(destino.left, destino.top, destino.right, destino.bottom, Tinta.GRIS_FILA)
                lienzo.canvas.drawText(
                    "Sin foto",
                    destino.centerX() - p.etiqueta.measureText("Sin foto") / 2f,
                    destino.centerY(),
                    p.etiqueta,
                )
            }
            lienzo.borde(destino.left, destino.top, destino.right, destino.bottom, Tinta.LINEA)
        }
    }

    // ── Medidas ─────────────────────────────────────────────────────────────

    private class Distribucion(
        val dosColumnas: Boolean,
        val anchoColumna: Float,
        val ladoFoto: Float,
        val altoTextosCuerpo: Float,
        val altoFotos: Float,
    ) {
        val altoTextos: Float get() = ALTO_ROTULO + altoTextosCuerpo
    }

    private fun distribucion(item: DamageItem): Distribucion {
        val hayReparacion = item.repairPhotos.isNotEmpty() || item.repairAction.isNotBlank()

        if (!hayReparacion) {
            val lado = ladoFoto(Hoja.contenidoAncho, FOTOS_POR_FILA_ANCHO)
            val texto = LienzoPdf.medir(item.damageDescription, p.cuerpo, Hoja.contenidoAncho)
            return Distribucion(
                dosColumnas = false,
                anchoColumna = Hoja.contenidoAncho,
                ladoFoto = lado,
                altoTextosCuerpo = texto + 6f,
                altoFotos = altoCuadricula(item.damagePhotos.size, FOTOS_POR_FILA_ANCHO, lado),
            )
        }

        val anchoCol = (Hoja.contenidoAncho - SEPARACION) / 2f
        val lado = ladoFoto(anchoCol, FOTOS_POR_FILA_COLUMNA)
        // Los dos textos comparten franja: manda el más alto, no la suma.
        val alto = maxOf(
            LienzoPdf.medir(item.damageDescription, p.cuerpo, anchoCol),
            LienzoPdf.medir(item.repairAction, p.cuerpo, anchoCol),
        )
        val filas = maxOf(
            filasNecesarias(item.damagePhotos.size, FOTOS_POR_FILA_COLUMNA),
            filasNecesarias(item.repairPhotos.size, FOTOS_POR_FILA_COLUMNA),
        )
        return Distribucion(
            dosColumnas = true,
            anchoColumna = anchoCol,
            ladoFoto = lado,
            altoTextosCuerpo = alto + 6f,
            altoFotos = if (filas == 0) 0f else filas * lado + (filas - 1) * HUECO_FILA,
        )
    }

    private fun ladoFoto(ancho: Float, porFila: Int): Float =
        (ancho - (porFila - 1) * HUECO_COL) / porFila

    private fun filasNecesarias(cantidad: Int, porFila: Int): Int =
        if (cantidad == 0) 0 else (cantidad + porFila - 1) / porFila

    private fun altoCuadricula(cantidad: Int, porFila: Int, lado: Float): Float {
        val filas = filasNecesarias(cantidad, porFila)
        return if (filas == 0) 0f else filas * lado + (filas - 1) * HUECO_FILA
    }

    private companion object {
        const val FOTOS_POR_FILA_COLUMNA = 2
        const val FOTOS_POR_FILA_ANCHO = 3
        const val SEPARACION = 12f
        const val HUECO_COL = 6f
        const val HUECO_FILA = 6f
        const val ALTO_CABECERA = 20f
        const val ALTO_ROTULO = 14f
        const val ALTO_CIERRE = 16f
    }
}
