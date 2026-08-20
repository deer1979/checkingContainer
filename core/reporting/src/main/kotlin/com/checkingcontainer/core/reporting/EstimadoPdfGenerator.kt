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

/**
 * Los dos papeles que salen del mismo trabajo.
 *
 * Antes de reparar se cotiza (con precios); después se entrega constancia de lo
 * hecho, y ahí los valores sobran. El título cambia con el tipo porque un
 * documento sin precios que se llame "estimado" confunde a quien lo recibe.
 */
enum class TipoDocumento(val titulo: String, val piePara: String) {
    ESTIMADO("ESTIMADO DE REPARACIÓN", "Aceptado por el cliente (nombre y firma)"),
    INFORME("INFORME DE REPARACIÓN", "Recibido conforme (nombre y firma)"),
}

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
        tipo: TipoDocumento = TipoDocumento.ESTIMADO,
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
        val referencia = "${if (tipo == TipoDocumento.INFORME) "Informe" else "Estimado"} N.º ${numeroEstimado(estimado)}"

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
            dibujarEncabezado(l, p, estimado, referencia, esContenedor, tipo)
            dibujarEquipo(l, p, estimado, fichaTecnica)
            dibujarMediciones(l, p, estimado)
            dibujarItems(l, p, estimado, fotos)
            // El informe de reparación NO lleva valores: es constancia del
            // trabajo hecho, no una cotización.
            if (tipo == TipoDocumento.ESTIMADO) dibujarValores(l, p, estimado)
            dibujarFirma(l, p, tipo)
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

    /** Alto del rótulo de un bloque enmarcado (título + aire). */
    private val ALTO_TITULO_BLOQUE = 30f

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
        tipo: TipoDocumento,
    ) {
        // Cabecera: quién emite a la izquierda, qué documento es a la derecha.
        l.texto("CHECKING CONTAINER", Hoja.MARGEN, p.titulo)
        l.textoDerecha(tipo.titulo, Hoja.ANCHO - Hoja.MARGEN, p.subtitulo)
        l.y += 14f
        l.textoDerecha(
            "$referencia  ·  ${sdf.format(Date(e.createdAt))}",
            Hoja.ANCHO - Hoja.MARGEN, p.etiqueta,
        )
        l.y += 18f

        // Dos bloques enmarcados: a quién se le factura y sobre qué. El nombre del
        // cliente va destacado, no como una fila más — es lo primero que busca
        // quien recibe el documento.
        val anchoBloque = (Hoja.contenidoAncho - 16f) / 2f
        val xDer = Hoja.MARGEN + anchoBloque + 16f

        // Cada dato con su etiqueta: una dirección o un teléfono sueltos no se
        // distinguen entre sí, y quien recibe el documento no tiene por qué
        // adivinar qué es cada línea.
        val datosCliente = buildList {
            if (e.clientIdNumber.isNotEmpty()) add("RUC / CI" to e.clientIdNumber)
            if (e.clientDireccion.isNotEmpty()) add("Dirección" to e.clientDireccion)
            if (e.clientTelefono.isNotEmpty()) add("Teléfono" to e.clientTelefono)
            if (e.clientEmail.isNotEmpty()) add("Correo" to e.clientEmail)
        }
        val datosTrabajo = buildList {
            add((if (esContenedor) "Contenedor" else "Equipo") to e.containerNo)
            if (e.ordenTrabajo.isNotEmpty()) add("Orden de trabajo" to e.ordenTrabajo)
            if (e.sitioNombre.isNotEmpty()) add("Trabajo en" to e.sitioNombre)
            if (e.location.isNotEmpty()) add("Ubicación" to e.location)
            add("Técnico" to e.technicianName.ifEmpty { "—" })
            e.approvedAt?.let { add("Aprobado" to sdf.format(Date(it))) }
        }

        val altoCliente = altoBloqueCliente(p, e, datosCliente, anchoBloque)
        val altoTrabajo = ALTO_TITULO_BLOQUE + altoCampos(p, datosTrabajo, anchoBloque) + 12f
        val alto = maxOf(altoCliente, altoTrabajo)
        l.asegurar(alto + 20f)

        val yBloques = l.y
        bloqueCliente(l, p, e, datosCliente, Hoja.MARGEN, anchoBloque, alto)
        l.y = yBloques
        bloqueTrabajo(l, p, datosTrabajo, xDer, anchoBloque, alto)

        l.y = yBloques + alto + 16f
    }

    /** Ancho de la columna de etiquetas, ajustado a la etiqueta más larga. */
    private fun anchoEtiquetas(p: Pinceles, campos: List<Pair<String, String>>): Float =
        (campos.maxOfOrNull { p.etiqueta.measureText(it.first) } ?: 0f) + 10f

    /**
     * Alto real de las filas etiqueta/valor, contando los valores que envuelven.
     * Medir y dibujar usan el MISMO cálculo para que el recuadro nunca corte el
     * contenido (una dirección larga ocupa dos líneas).
     */
    private fun altoCampos(p: Pinceles, campos: List<Pair<String, String>>, anchoBloque: Float): Float {
        val anchoValor = anchoBloque - 16f - anchoEtiquetas(p, campos)
        return campos.sumOf {
            (maxOf(LienzoPdf.medir(it.second, p.cuerpo, anchoValor), 12f) + 2f).toDouble()
        }.toFloat()
    }

    private fun dibujarCampos(
        l: LienzoPdf,
        p: Pinceles,
        campos: List<Pair<String, String>>,
        x: Float,
        ancho: Float,
    ) {
        val anchoEtq = anchoEtiquetas(p, campos)
        val anchoValor = ancho - 16f - anchoEtq
        campos.forEach { (etiqueta, valor) ->
            l.texto(etiqueta, x + 8f, p.etiqueta)
            val alto = l.parrafoEnLineaBase(valor, p.cuerpo, anchoValor, x + 8f + anchoEtq)
            l.y += maxOf(alto, 12f) + 2f
        }
    }

    private fun altoBloqueCliente(
        p: Pinceles,
        e: Estimado,
        campos: List<Pair<String, String>>,
        ancho: Float,
    ): Float {
        val nombre = e.clientName.ifBlank { "Sin cliente asignado" }
        val altoNombre = LienzoPdf.medir(nombre, p.itemNombre, ancho - 16f)
        return ALTO_TITULO_BLOQUE + altoNombre + 6f + altoCampos(p, campos, ancho) + 12f
    }

    /** Bloque "a quién se le factura": nombre destacado y contacto etiquetado. */
    private fun bloqueCliente(
        l: LienzoPdf,
        p: Pinceles,
        e: Estimado,
        campos: List<Pair<String, String>>,
        x: Float,
        ancho: Float,
        alto: Float,
    ) {
        val arriba = l.y
        l.relleno(x, arriba, x + ancho, arriba + alto, Tinta.MARCO, radio = 5f)
        l.y = arriba + 14f
        l.texto("CLIENTE", x + 8f, p.seccion)
        l.y += 16f
        // El nombre va sin etiqueta y destacado: es el titular del bloque.
        l.y += l.parrafoEnLineaBase(
            e.clientName.ifBlank { "Sin cliente asignado" },
            p.itemNombre, ancho - 16f, x + 8f,
        )
        l.y += 6f
        dibujarCampos(l, p, campos, x, ancho)
    }

    /** Bloque "sobre qué": equipo, orden, sitio y técnico. */
    private fun bloqueTrabajo(
        l: LienzoPdf,
        p: Pinceles,
        campos: List<Pair<String, String>>,
        x: Float,
        ancho: Float,
        alto: Float,
    ) {
        val arriba = l.y
        l.relleno(x, arriba, x + ancho, arriba + alto, Tinta.MARCO, radio = 5f)
        l.y = arriba + 14f
        l.texto("DATOS DEL TRABAJO", x + 8f, p.seccion)
        l.y += 16f
        dibujarCampos(l, p, campos, x, ancho)
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
        // Etiqueta y valor en la MISMA línea base; mezclar drawText con
        // StaticLayout desalineaba cada valor una línea respecto a su etiqueta.
        fun filaEquipo(etiqueta: String, valor: String, x: Float): Float {
            l.texto(etiqueta, x, p.etiqueta)
            val alto = l.parrafoEnLineaBase(valor, p.cuerpo, anchoCol - 78f, x + 78f)
            return maxOf(alto, 12f) + 1f
        }

        var i = 0
        while (i < campos.size) {
            l.asegurar(16f)
            val y0 = l.y
            val altoIzq = filaEquipo(campos[i].first, campos[i].second, Hoja.MARGEN)
            val altoDer = campos.getOrNull(i + 1)?.let { filaEquipo(it.first, it.second, xDer) } ?: 0f
            l.y = y0 + maxOf(altoIzq, altoDer)
            i += 2
        }
        l.linea(6f)
        l.y += 14f
    }

    // ── Mediciones (tabla alta/baja + diagnóstico) ──────────────────────────

    private fun dibujarMediciones(l: LienzoPdf, p: Pinceles, e: Estimado) {
        if (e.mediciones.isEmpty()) return
        fun num(v: Double?, dec: Int = 1): String {
            v ?: return "—"
            val s = String.format(Locale.US, "%.${dec}f", v)
            // Un -0.3 psig redondeado a entero imprimía "-0": cero no lleva signo.
            return if (s.trimStart('-').all { it == '0' || it == '.' }) s.trimStart('-') else s
        }

        // Columnas del informe de servicio: parámetro, lado de alta, lado de baja
        // y el rango objetivo. Los números van alineados a la derecha para que las
        // cifras se lean en columna, como en cualquier informe técnico.
        val xParam = Hoja.MARGEN + 6f
        val anchoObjetivo = 96f
        val anchoDato = (Hoja.contenidoAncho - 130f - anchoObjetivo) / 2f
        val xAlta = Hoja.MARGEN + 130f + anchoDato
        val xBaja = xAlta + anchoDato
        val xObjetivo = Hoja.ANCHO - Hoja.MARGEN - 6f
        val altoFila = 15f

        // Título reservado junto con la primera tabla, para no dejarlo huérfano.
        l.asegurar(34f + 16f + altoFila * 6)
        l.texto("MEDICIONES DEL EQUIPO", Hoja.MARGEN, p.seccion)
        l.y += 16f

        e.mediciones.forEach { m ->
            val objetivos = ObjetivoRefrigeracion.efectivos(m.tipoExpansion, m.objetivoManual())
            val obs = DiagnosticoRefrigeracion.evaluar(m)
            val altoDiag = LienzoPdf.medir(obs.texto, p.cuerpo, Hoja.contenidoAncho - 20f)
            l.asegurar(16f + altoFila * 6 + altoDiag + 50f)

            // Contexto de la toma: cuándo, con qué gas y con qué expansión.
            l.texto(
                sdfHora.format(Date(m.timestamp)) +
                    (if (m.refrigerante.isNotEmpty()) "  ·  ${m.refrigerante}" else "") +
                    (if (m.tipoExpansion != TipoExpansion.NO_ESPECIFICADO) "  ·  ${m.tipoExpansion.abreviatura}" else ""),
                Hoja.MARGEN, p.etiqueta,
            )
            if (m.dispositivos.isNotEmpty()) {
                l.textoDerecha(m.dispositivos.joinToString(", "), Hoja.ANCHO - Hoja.MARGEN, p.etiqueta)
            }
            l.y += 14f

            // Encabezado de columnas.
            l.relleno(Hoja.MARGEN, l.y - altoFila + 4f, Hoja.ANCHO - Hoja.MARGEN, l.y + 4f, Tinta.GRIS_FILA)
            l.texto("PARÁMETRO", xParam, p.celdaEtiqueta)
            l.textoDerecha("ALTA / Descarga", xAlta, p.celdaEtiqueta)
            l.textoDerecha("BAJA / Succión", xBaja, p.celdaEtiqueta)
            l.textoDerecha("OBJETIVO", xObjetivo, p.celdaEtiqueta)
            l.y += altoFila

            fun filaDato(
                etiqueta: String,
                alta: String,
                baja: String,
                objetivo: String = "",
                guia: Boolean = false,
                fondo: Int? = null,
            ) {
                fondo?.let { l.relleno(Hoja.MARGEN, l.y - altoFila + 4f, Hoja.ANCHO - Hoja.MARGEN, l.y + 4f, it) }
                l.texto(etiqueta + if (guia) "  ★" else "", xParam, if (guia) p.negrita else p.cuerpo)
                l.textoDerecha(alta, xAlta, p.celdaValor)
                l.textoDerecha(baja, xBaja, p.celdaValor)
                if (objetivo.isNotEmpty()) l.textoDerecha(objetivo, xObjetivo, p.etiqueta)
                l.y += altoFila
            }

            val guiaSC = objetivos.guia == ParametroGuia.SUBENFRIAMIENTO
            val guiaSH = objetivos.guia == ParametroGuia.RECALENTAMIENTO
            // PSIG es lo que marca el manómetro y lo que espera leer un técnico.
            filaDato("Presión", "${num(m.presionAltaPsig, 0)} PSIG", "${num(m.presionBajaPsig, 0)} PSIG")
            filaDato("Temperatura de línea", "${num(m.tempDescargaC)} °C", "${num(m.tempSuccionC)} °C", fondo = Tinta.GRIS_FILA)
            filaDato("Temperatura de saturación", "${num(m.satLiquidoC)} °C", "${num(m.satVaporC)} °C")
            filaDato(
                "Subenfriamiento / Recalentamiento",
                "${num(m.subcoolingC)} °C",
                "${num(m.superheatC)} °C",
                objetivo = objetivos.rangoGuia()?.etiqueta() ?: "—",
                guia = guiaSC || guiaSH,
                fondo = Tinta.GRIS_FILA,
            )
            if (m.corrienteA != null) filaDato("Corriente", "${num(m.corrienteA)} A", "")
            l.y += 4f

            // Diagnóstico: la conclusión del técnico, destacada por severidad.
            val fondo = when (obs.severidad) {
                Severidad.OK -> Tinta.VERDE_SUAVE
                Severidad.ALERTA -> Tinta.AMBAR_SUAVE
                Severidad.INFO -> Tinta.GRIS_FILA
            }
            val altoCaja = altoDiag + 34f
            val arriba = l.y
            l.relleno(Hoja.MARGEN, arriba, Hoja.ANCHO - Hoja.MARGEN, arriba + altoCaja, fondo, radio = 4f)
            l.y = arriba + 13f
            l.texto("Diagnóstico", Hoja.MARGEN + 10f, p.etiqueta)
            l.y += 13f
            l.parrafoEnLineaBase(obs.texto, p.cuerpo, Hoja.contenidoAncho - 20f, Hoja.MARGEN + 10f)
            l.y = arriba + altoCaja + 14f
        }
        l.linea(2f)
        l.y += 16f
    }

    // ── Ítems ───────────────────────────────────────────────────────────────

    private fun dibujarItems(l: LienzoPdf, p: Pinceles, e: Estimado, fotos: Map<String, Bitmap?>) {
        if (e.damages.isEmpty()) return
        val renderer = ItemRenderer(l, p, fotos)
        // El título se reserva JUNTO con el primer ítem: si no caben los dos, se
        // salta de página ANTES de dibujar el rótulo. Sin esto quedaba
        // "DETALLE DE TRABAJOS" huérfano al pie con toda la hoja vacía debajo.
        val altoPrimero = e.damages.firstOrNull()?.let { renderer.medir(it, 0) } ?: 0f
        val altoUtil = Hoja.limiteInferior - Hoja.MARGEN - 30f
        l.asegurar(30f + minOf(altoPrimero, altoUtil))
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

        // Una tabla con encabezados y ninguna fila queda mal en un documento que
        // va al cliente: se dice explícitamente que aún no hay valores.
        if (e.damages.none { it.precioUnitario != null }) {
            l.relleno(Hoja.MARGEN, l.y - 10f, Hoja.ANCHO - Hoja.MARGEN, l.y + 6f, Tinta.GRIS_FILA)
            l.textoCentrado("Sin valores asignados todavía", Hoja.ANCHO / 2f, p.etiqueta)
            l.y += altoFila + 4f
        }

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
    }

    /**
     * Espacio de firma. El texto cambia con el documento: un estimado se
     * **acepta** antes de trabajar; un informe se **recibe conforme** después.
     */
    private fun dibujarFirma(l: LienzoPdf, p: Pinceles, tipo: TipoDocumento) {
        l.asegurar(70f)
        l.y += 30f
        val ancho = 220f
        val x = Hoja.MARGEN
        l.canvas.drawLine(x, l.y, x + ancho, l.y, p.linea)
        l.y += 11f
        l.texto(tipo.piePara, x, p.etiqueta)
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
