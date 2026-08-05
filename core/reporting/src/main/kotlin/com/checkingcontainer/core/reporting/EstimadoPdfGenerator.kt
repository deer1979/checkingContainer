package com.checkingcontainer.core.reporting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.checkingcontainer.core.model.CampoFicha
import com.checkingcontainer.core.model.DiagnosticoRefrigeracion
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoTotals
import com.checkingcontainer.core.model.Iso6346
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
    private val usd = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 2 }

    // Todo el dibujo (Canvas, StaticLayout, bitmaps) fuera del main thread:
    // con varios ítems y fotos, hacerlo en Main congelaba la UI 2-3 segundos.
    suspend fun generate(
        estimado: Estimado,
        fichaTecnica: List<CampoFicha> = emptyList(),
    ): ByteArray = withContext(Dispatchers.Default) {
        val loader = SingletonImageLoader.get(context)
        val fotos = mutableMapOf<String, Bitmap?>()
        estimado.damages.forEach { item ->
            (item.damagePhotos + item.repairPhotos).forEach { url ->
                fotos.getOrPut(url) { loadBitmap(loader, url) }
            }
        }

        val p = Pinceles()
        val esContenedor = Iso6346.isValid(estimado.containerNo)
        val referencia = "Estimado N° ${numeroEstimado(estimado)}"

        // Todo el documento se dibuja con esta función, que se ejecuta DOS veces.
        val dibujarTodo: (LienzoPdf) -> Unit = { l ->
            // A partir de la página 2 se repite quién es quién: si una hoja se
            // separa del juego, sigue identificada.
            l.encabezadoContinuacion = {
                l.texto(referencia, Hoja.MARGEN, p.etiqueta)
                l.textoDerecha(
                    "${if (esContenedor) "Contenedor" else "Equipo"} ${estimado.containerNo}" +
                        if (estimado.clientName.isNotEmpty()) "  ·  ${estimado.clientName}" else "",
                    Hoja.ANCHO - Hoja.MARGEN, p.etiqueta,
                )
                l.linea(4f)
                l.y += 14f
            }
            dibujarEncabezado(l, p, estimado, referencia, esContenedor)
            dibujarEquipo(l, p, estimado, fichaTecnica)
            dibujarMediciones(l, p, estimado)
            dibujarItems(l, p, estimado, fotos)
            dibujarValores(l, p, estimado)
        }

        // Primera pasada: solo para saber cuántas hojas salen. El pie necesita el
        // total ("Página 2 de 5") y no hay forma de saberlo sin maquetar antes;
        // PdfDocument no deja volver atrás a retocar una hoja ya cerrada.
        val conteo = PdfDocument()
        val totalPaginas = try {
            LienzoPdf(conteo, p).let { dibujarTodo(it); it.finalizar() }
        } finally {
            conteo.close()
        }

        // Segunda pasada: la de verdad, ya con la numeración completa. Las fotos
        // están decodificadas en memoria, así que repetirla sale barato.
        val doc = PdfDocument()
        LienzoPdf(doc, p, totalPaginas).also { dibujarTodo(it) }.finalizar()

        ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    /** Sin folio propio todavía: el id de la base sirve de número estable. */
    private fun numeroEstimado(e: Estimado): String =
        e.id.takeIf { it > 0 }?.toString()?.padStart(5, '0') ?: "—"

    // ── Encabezado ──────────────────────────────────────────────────────────

    private fun dibujarEncabezado(
        l: LienzoPdf,
        p: Pinceles,
        e: Estimado,
        referencia: String,
        esContenedor: Boolean,
    ) {
        l.texto("CHECKING CONTAINER", Hoja.MARGEN, p.titulo)
        l.textoDerecha("ESTIMADO DE REPARACIÓN", Hoja.ANCHO - Hoja.MARGEN, p.subtitulo)
        l.y += 13f
        l.textoDerecha(referencia, Hoja.ANCHO - Hoja.MARGEN, p.etiqueta)
        l.linea(4f)
        l.y += 14f

        // Dos columnas: identificación del trabajo a la izquierda, cliente a la
        // derecha. Los valores se recortan al ancho de su columna para que una
        // dirección larga no se salga de la hoja (antes se desbordaba).
        val anchoCol = (Hoja.contenidoAncho - 20f) / 2f
        val xDer = Hoja.MARGEN + anchoCol + 20f
        val izquierda = buildList {
            add((if (esContenedor) "Contenedor:" else "Equipo:") to e.containerNo)
            if (e.ordenTrabajo.isNotEmpty()) add("Orden de trabajo:" to e.ordenTrabajo)
            if (e.sitioNombre.isNotEmpty()) add("Trabajo en:" to e.sitioNombre)
            if (e.location.isNotEmpty()) add("Ubicación:" to e.location)
            add("Técnico:" to e.technicianName.ifEmpty { "—" })
            add("Fecha:" to sdf.format(Date(e.createdAt)))
            e.approvedAt?.let { add("Aprobado:" to sdf.format(Date(it))) }
        }
        val derecha = buildList {
            if (e.clientName.isNotEmpty()) add("Cliente:" to e.clientName)
            if (e.clientIdNumber.isNotEmpty()) add("RUC / CI:" to e.clientIdNumber)
            if (e.clientDireccion.isNotEmpty()) add("Dirección:" to e.clientDireccion)
            if (e.clientTelefono.isNotEmpty()) add("Teléfono:" to e.clientTelefono)
            if (e.clientEmail.isNotEmpty()) add("Correo:" to e.clientEmail)
        }

        val yInicio = l.y
        izquierda.forEach { (etiqueta, valor) -> fila(l, p, etiqueta, valor, Hoja.MARGEN, anchoCol) }
        val yIzq = l.y
        l.y = yInicio
        derecha.forEach { (etiqueta, valor) -> fila(l, p, etiqueta, valor, xDer, anchoCol) }
        l.y = maxOf(yIzq, l.y)

        l.linea(6f)
        l.y += 14f
    }

    /** Etiqueta + valor ajustados al ancho de la columna (el valor puede envolver). */
    private fun fila(l: LienzoPdf, p: Pinceles, etiqueta: String, valor: String, x: Float, ancho: Float) {
        val anchoEtiqueta = 72f
        l.texto(etiqueta, x, p.etiqueta)
        val alto = l.parrafo(valor, p.cuerpo, ancho - anchoEtiqueta, x + anchoEtiqueta)
        l.y += maxOf(alto, 12f) + 1f
    }

    // ── Equipo y ficha técnica ──────────────────────────────────────────────

    private fun dibujarEquipo(l: LienzoPdf, p: Pinceles, e: Estimado, ficha: List<CampoFicha>) {
        if (e.manufacturer.isEmpty() && e.unitSerialNo.isEmpty() && ficha.isEmpty()) return
        l.asegurar(60f)
        l.texto("DATOS DEL EQUIPO", Hoja.MARGEN, p.seccion)
        l.y += 14f

        val anchoCol = (Hoja.contenidoAncho - 20f) / 2f
        val xDer = Hoja.MARGEN + anchoCol + 20f
        val campos = buildList {
            if (e.manufacturer.isNotEmpty()) add("Fabricante:" to e.manufacturer)
            if (e.unitModel.isNotEmpty()) add("Modelo:" to e.unitModel)
            if (e.unitSerialNo.isNotEmpty()) add("No. Serie:" to e.unitSerialNo)
            if (e.unitModelNo.isNotEmpty()) add("No. Modelo:" to e.unitModelNo)
            if (e.yearOfBuilt.isNotEmpty()) add("Año:" to e.yearOfBuilt)
            if (e.unitType.isNotEmpty()) add("Tipo:" to e.unitType)
            ficha.forEach { add("${it.etiqueta}:" to it.valor) }
        }
        var i = 0
        while (i < campos.size) {
            l.asegurar(16f)
            val y0 = l.y
            fila(l, p, campos[i].first, campos[i].second, Hoja.MARGEN, anchoCol)
            val yIzq = l.y
            campos.getOrNull(i + 1)?.let {
                l.y = y0
                fila(l, p, it.first, it.second, xDer, anchoCol)
            }
            l.y = maxOf(yIzq, l.y)
            i += 2
        }
        l.linea(6f)
        l.y += 14f
    }

    // ── Mediciones (tabla alta/baja + diagnóstico) ──────────────────────────

    private fun dibujarMediciones(l: LienzoPdf, p: Pinceles, e: Estimado) {
        if (e.mediciones.isEmpty()) return
        fun num(v: Double?, dec: Int = 1): String =
            v?.let { String.format(Locale.US, "%.${dec}f", it) } ?: "—"

        val anchoEtiqueta = 96f
        val anchoCelda = (Hoja.contenidoAncho - anchoEtiqueta) / 2f
        val xEtq = Hoja.MARGEN
        val xAlta = xEtq + anchoEtiqueta
        val xBaja = xAlta + anchoCelda
        val xFin = xBaja + anchoCelda
        val altoFila = 15f

        l.asegurar(30f)
        l.texto("MEDICIONES DEL EQUIPO", Hoja.MARGEN, p.seccion)
        l.y += 14f

        e.mediciones.forEach { m ->
            val objetivos = ObjetivoRefrigeracion.efectivos(m.tipoExpansion, m.objetivoManual())
            val obs = DiagnosticoRefrigeracion.evaluar(m)
            val altoDiag = LienzoPdf.medir(obs.texto, p.cuerpo, Hoja.contenidoAncho - 16f)
            l.asegurar(14f + altoFila * 6 + altoDiag + 24f)

            l.texto(
                sdfHora.format(Date(m.timestamp)) +
                    (if (m.refrigerante.isNotEmpty()) "  ·  ${m.refrigerante}" else "") +
                    (if (m.tipoExpansion != TipoExpansion.NO_ESPECIFICADO) "  ·  ${m.tipoExpansion.abreviatura}" else "") +
                    (if (m.dispositivos.isNotEmpty()) "  ·  ${m.dispositivos.joinToString(", ")}" else ""),
                Hoja.MARGEN, p.etiqueta,
            )
            l.y += 13f

            l.relleno(xAlta, l.y - altoFila + 4f, xBaja, l.y + 4f, Tinta.ROJO)
            l.relleno(xBaja, l.y - altoFila + 4f, xFin, l.y + 4f, Tinta.AZUL_FRIO)
            l.textoCentrado("ALTA / Descarga", (xAlta + xBaja) / 2f, p.columnaHdr)
            l.textoCentrado("BAJA / Succión", (xBaja + xFin) / 2f, p.columnaHdr)
            l.y += altoFila

            fun filaDatos(etiqueta: String, alta: String, baja: String, guia: Boolean = false) {
                l.relleno(xEtq, l.y - altoFila + 4f, xAlta, l.y + 4f, Tinta.GRIS_FILA)
                l.relleno(xAlta, l.y - altoFila + 4f, xBaja, l.y + 4f, Tinta.ROJO_SUAVE)
                l.relleno(xBaja, l.y - altoFila + 4f, xFin, l.y + 4f, Tinta.AZUL_SUAVE)
                l.texto(etiqueta + if (guia) "  ★" else "", xEtq + 4f, p.celdaEtiqueta)
                l.textoCentrado(alta, (xAlta + xBaja) / 2f, p.celdaValor)
                l.textoCentrado(baja, (xBaja + xFin) / 2f, p.celdaValor)
                l.y += altoFila
            }

            val guia = objetivos.guia != ParametroGuia.NINGUNO
            filaDatos("Presión", "${num(m.presionAltaPsig, 0)} psi", "${num(m.presionBajaPsig, 0)} psi")
            filaDatos("Temperatura", "${num(m.tempDescargaC)} °C", "${num(m.tempSuccionC)} °C")
            filaDatos("Saturación", "${num(m.satLiquidoC)} °C", "${num(m.satVaporC)} °C")
            filaDatos("Subcool / Superheat", "${num(m.subcoolingC)} °C", "${num(m.superheatC)} °C", guia)

            val nombreGuia = when (objetivos.guia) {
                ParametroGuia.SUBENFRIAMIENTO -> "Subcooling"
                ParametroGuia.RECALENTAMIENTO -> "Superheat"
                ParametroGuia.NINGUNO -> ""
            }
            l.y += 11f
            l.texto("Corriente  ${num(m.corrienteA)} A", xEtq + 4f, p.cuerpo)
            l.textoDerecha(
                objetivos.rangoGuia()?.let { "Objetivo $nombreGuia: ${it.etiqueta()}" }
                    ?: "Objetivo: seleccione dispositivo de expansión",
                Hoja.ANCHO - Hoja.MARGEN, p.cuerpo,
            )
            l.y += 8f

            val fondo = when (obs.severidad) {
                Severidad.OK -> Tinta.VERDE_SUAVE
                Severidad.ALERTA -> Tinta.AMBAR_SUAVE
                Severidad.INFO -> Tinta.GRIS_FILA
            }
            val prefijo = when (obs.severidad) {
                Severidad.OK -> "OK  "
                Severidad.ALERTA -> "!  "
                Severidad.INFO -> "i  "
            }
            val texto = prefijo + obs.texto
            val alto = LienzoPdf.medir(texto, p.cuerpo, Hoja.contenidoAncho - 16f)
            val arriba = l.y
            l.relleno(Hoja.MARGEN, arriba, Hoja.ANCHO - Hoja.MARGEN, arriba + alto + 14f, fondo, radio = 4f)
            l.y = arriba + 10f
            l.parrafo(texto, p.cuerpo, Hoja.contenidoAncho - 16f, Hoja.MARGEN + 8f)
            l.y = arriba + alto + 14f + 10f
        }
        l.linea(2f)
        l.y += 14f
    }

    // ── Ítems ───────────────────────────────────────────────────────────────

    private fun dibujarItems(l: LienzoPdf, p: Pinceles, e: Estimado, fotos: Map<String, Bitmap?>) {
        if (e.damages.isEmpty()) return
        val renderer = ItemRenderer(l, p, fotos)
        l.asegurar(30f)
        l.texto("DETALLE DE TRABAJOS", Hoja.MARGEN, p.seccion)
        l.y += 16f

        e.damages.forEachIndexed { indice, item ->
            val alto = renderer.medir(item, indice)
            // Se mide el ítem ENTERO antes de dibujarlo: así nunca queda el
            // número y el nombre solos al pie con las fotos en la hoja siguiente.
            // Si es más alto que una hoja completa no hay salto que lo arregle,
            // y se dibuja donde esté para no provocar una página en blanco.
            val cabeEnHoja = alto <= Hoja.limiteInferior - Hoja.MARGEN
            if (cabeEnHoja) l.asegurar(alto)
            renderer.dibujar(item, indice)
            l.y += 6f
        }
        l.y += 4f
    }

    // ── Tabla de valores ────────────────────────────────────────────────────

    private fun dibujarValores(l: LienzoPdf, p: Pinceles, e: Estimado) {
        val totales = EstimadoTotals.calcular(e.damages, e.hasIva, e.manoDeObraTotal)
        val filas = e.damages.count { it.precioUnitario != null }
        l.asegurar(60f + filas * 15f + 80f)

        l.texto("DETALLE DE VALORES", Hoja.MARGEN, p.seccion)
        l.y += 16f

        val xNum = Hoja.MARGEN + 4f
        val xDesc = Hoja.MARGEN + 26f
        val xCant = Hoja.MARGEN + 320f
        val xUnit = Hoja.MARGEN + 400f
        val xTotal = Hoja.ANCHO - Hoja.MARGEN - 4f
        val altoFila = 15f

        l.relleno(Hoja.MARGEN, l.y - 10f, Hoja.ANCHO - Hoja.MARGEN, l.y + 4f, Tinta.GRIS_FILA)
        l.texto("N°", xNum, p.celdaEtiqueta)
        l.texto("Descripción", xDesc, p.celdaEtiqueta)
        l.textoDerecha("Cant.", xCant + 30f, p.celdaEtiqueta)
        l.textoDerecha("V. Unit.", xUnit + 50f, p.celdaEtiqueta)
        l.textoDerecha("Total", xTotal, p.celdaEtiqueta)
        l.y += altoFila

        e.damages.forEachIndexed { indice, item ->
            if (item.precioUnitario == null) return@forEachIndexed
            l.asegurar(altoFila)
            l.texto("${indice + 1}", xNum, p.cuerpo)
            // El nombre no debe invadir la columna de cantidad.
            var nombre = item.nombreParaMostrar(indice)
            while (nombre.isNotEmpty() && p.cuerpo.measureText(nombre) > xCant - xDesc - 12f) {
                nombre = nombre.dropLast(1)
            }
            l.texto(nombre, xDesc, p.cuerpo)
            l.textoDerecha("${item.cantidad}", xCant + 30f, p.cuerpo)
            l.textoDerecha(usd.format(item.precioUnitario), xUnit + 50f, p.cuerpo)
            l.textoDerecha(usd.format(item.totalLinea), xTotal, p.cuerpo)
            l.y += altoFila
        }

        l.linea(2f)
        l.y += 14f

        fun totalRow(etiqueta: String, monto: Double, negrita: Boolean = false) {
            val pintura = if (negrita) p.negrita else p.cuerpo
            l.textoDerecha(etiqueta, xUnit + 50f, pintura)
            l.textoDerecha(usd.format(monto), xTotal, pintura)
            l.y += 14f
        }

        totalRow("Subtotal ítems:", totales.itemsTotal)
        totalRow("Mano de obra:", totales.laborTotal)
        if (e.hasIva) {
            totalRow("Subtotal:", totales.subtotal)
            totalRow("${EstimadoTotals.ivaLabel}:", totales.ivaAmount)
        }
        l.linea(2f)
        l.y += 14f
        totalRow("TOTAL:", totales.total, negrita = true)

        dibujarFirma(l, p)
    }

    /** Espacio de aceptación: un estimado aprobado normalmente se firma. */
    private fun dibujarFirma(l: LienzoPdf, p: Pinceles) {
        l.asegurar(70f)
        l.y += 30f
        val ancho = 200f
        val x = Hoja.MARGEN
        l.canvas.drawLine(x, l.y, x + ancho, l.y, p.linea)
        l.y += 11f
        l.texto("Aceptado por el cliente (nombre y firma)", x, p.etiqueta)
        l.y += 12f
        l.texto("Fecha: ____ / ____ / ________", x, p.etiqueta)
    }

    // Un fallo puntual de red dejaba "Sin foto" en el PDF de forma definitiva:
    // se reintenta una vez antes de rendirse.
    private suspend fun loadBitmap(loader: coil3.ImageLoader, url: String): Bitmap? =
        loadBitmapOnce(loader, url) ?: loadBitmapOnce(loader, url)

    private suspend fun loadBitmapOnce(loader: coil3.ImageLoader, url: String): Bitmap? =
        runCatching {
            // Las fotos se dibujan a ~125pt: decodificar a 700px en vez de la
            // resolución completa de la cámara baja ~10x el pico de memoria.
            val req = ImageRequest.Builder(context).data(url).size(700).build()
            val bmp = (loader.execute(req) as? SuccessResult)?.image?.let { (it as? BitmapImage)?.bitmap }
            // El canvas del PDF es software: los bitmaps de hardware hay que copiarlos.
            bmp?.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        }.getOrNull()
}
