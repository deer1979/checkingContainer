package com.checkingcontainer.core.model

/**
 * Resultado de fusionar mi versión con la que hay en la nube.
 *
 * [camposEnConflicto] solo trae los campos que **ambos** cambiamos a valores
 * distintos: son los únicos que hay que consultar al usuario. Todo lo demás se
 * resuelve solo.
 */
data class ResultadoFusion(
    val estimado: Estimado,
    val huboCambiosDelOtro: Boolean,
    val camposEnConflicto: List<String>,
)

/**
 * Fusiona por campo un estimado editado por dos personas **por turnos**.
 *
 * El caso real: uno hace el borrador, el otro le agrega una foto y le pone
 * valores. Como tocan cosas distintas, no hay motivo para que uno pise al otro
 * ni para molestar con preguntas — se juntan y ya.
 *
 * Regla de tres vías, campo por campo, partiendo de [base] (la versión que yo
 * cargué al abrir la pantalla):
 *
 * - Solo yo lo cambié     → gana el mío
 * - Solo el otro lo cambió → gana el suyo
 * - Los dos lo cambiamos   → gana el mío y se **reporta** el conflicto
 * - Nadie lo cambió        → queda como estaba
 *
 * Sin [base] no se puede saber quién cambió qué, así que se conserva lo mío y se
 * incorporan únicamente los ítems y mediciones que el otro haya añadido (nunca
 * se borra nada suyo).
 */
fun fusionarEstimado(base: Estimado?, mio: Estimado, remoto: Estimado): ResultadoFusion {
    val conflictos = mutableListOf<String>()

    /** Elige el valor de un campo escalar según quién lo tocó. */
    fun <T> campo(nombre: String, deBase: T?, deMio: T, deRemoto: T): T {
        if (deMio == deRemoto) return deMio
        if (base == null) return deMio
        val yoCambie = deMio != deBase
        val elCambio = deRemoto != deBase
        return when {
            yoCambie && elCambio -> { conflictos += nombre; deMio }
            elCambio -> deRemoto
            else -> deMio
        }
    }

    val damages = fusionarItems(base?.damages, mio.damages, remoto.damages, conflictos)

    // Las mediciones solo se agregan (son capturas con marca de tiempo): se unen
    // las de los dos y se ordenan. Nunca hay conflicto posible.
    val mediciones = (mio.mediciones + remoto.mediciones)
        .distinctBy { it.timestamp }
        .sortedBy { it.timestamp }

    val fusionado = mio.copy(
        clientName = campo("Cliente", base?.clientName, mio.clientName, remoto.clientName),
        clientId = campo("Cliente", base?.clientId, mio.clientId, remoto.clientId),
        clientIdNumber = campo("RUC / Cédula", base?.clientIdNumber, mio.clientIdNumber, remoto.clientIdNumber),
        clientDireccion = campo("Dirección", base?.clientDireccion, mio.clientDireccion, remoto.clientDireccion),
        clientTelefono = campo("Teléfono", base?.clientTelefono, mio.clientTelefono, remoto.clientTelefono),
        clientEmail = campo("Correo", base?.clientEmail, mio.clientEmail, remoto.clientEmail),
        sitioClienteId = campo("Sitio", base?.sitioClienteId, mio.sitioClienteId, remoto.sitioClienteId),
        sitioNombre = campo("Sitio", base?.sitioNombre, mio.sitioNombre, remoto.sitioNombre),
        ordenTrabajo = campo("Orden de trabajo", base?.ordenTrabajo, mio.ordenTrabajo, remoto.ordenTrabajo),
        location = campo("Ubicación", base?.location, mio.location, remoto.location),
        manoDeObraTotal = campo("Mano de obra", base?.manoDeObraTotal, mio.manoDeObraTotal, remoto.manoDeObraTotal),
        hasIva = campo("IVA", base?.hasIva, mio.hasIva, remoto.hasIva),
        approvedAt = campo("Aprobación", base?.approvedAt, mio.approvedAt, remoto.approvedAt),
        observaciones = campo("Observaciones", base?.observaciones, mio.observaciones, remoto.observaciones),
        damages = damages,
        mediciones = mediciones,
    ).let { fusion ->
        // Cerrar y reabrir es una decisión de persona, no un campo más: se
        // resuelve con la misma regla de tres vías, pero la fecha de cierre
        // sigue al estado para que no queden contradiciéndose.
        val estado = campo("Estado", base?.status, mio.status, remoto.status)
        val cierre = campo("Cierre", base?.closedAt, mio.closedAt, remoto.closedAt)
        fusion.copy(
            status = estado,
            closedAt = if (estado == EstimadoStatus.CERRADO) cierre else null,
        )
    }

    return ResultadoFusion(
        estimado = fusionado,
        huboCambiosDelOtro = fusionado != mio,
        camposEnConflicto = conflictos.distinct(),
    )
}

/**
 * Une las listas de ítems por su id.
 *
 * - Ítem que solo tiene el otro → **se agrega** (lo creó él mientras tanto).
 * - Ítem que solo tengo yo → se conserva.
 * - Ítem que existía en [base] y uno de los dos borró → se respeta el borrado.
 * - Ítem en ambos → se fusionan sus campos igual que el resto.
 */
private fun fusionarItems(
    base: List<DamageItem>?,
    mios: List<DamageItem>,
    remotos: List<DamageItem>,
    conflictos: MutableList<String>,
): List<DamageItem> {
    val porBase = base?.associateBy { it.id }.orEmpty()
    val porMio = mios.associateBy { it.id }
    val porRemoto = remotos.associateBy { it.id }

    val resultado = mutableListOf<DamageItem>()

    // Se recorre en MI orden para no reordenarle la lista al usuario.
    mios.forEach { mio ->
        val remoto = porRemoto[mio.id]
        val enBase = porBase[mio.id]
        when {
            // El otro lo borró y yo no lo toqué → respeto su borrado.
            remoto == null && enBase != null && mio == enBase -> Unit
            remoto == null -> resultado += mio
            else -> resultado += fusionarItem(enBase, mio, remoto, conflictos)
        }
    }

    // Ítems que él creó y yo todavía no tengo.
    remotos.filter { it.id !in porMio && it.id !in porBase }.forEach { resultado += it }

    return resultado
}

private fun fusionarItem(
    base: DamageItem?,
    mio: DamageItem,
    remoto: DamageItem,
    conflictos: MutableList<String>,
): DamageItem {
    fun <T> campo(nombre: String, deBase: T?, deMio: T, deRemoto: T): T {
        if (deMio == deRemoto) return deMio
        if (base == null) return deMio
        val yoCambie = deMio != deBase
        val elCambio = deRemoto != deBase
        return when {
            yoCambie && elCambio -> { conflictos += nombre; deMio }
            elCambio -> deRemoto
            else -> deMio
        }
    }

    val etiqueta = mio.nombreParaMostrar(0)

    // Las fotos se UNEN siempre: que dos técnicos suban fotos distintas del
    // mismo daño es lo normal, no un conflicto. Se respeta el tope por grupo.
    val fotosDano = (mio.damagePhotos + remoto.damagePhotos).distinct().take(MAX_FOTOS_POR_GRUPO)
    val fotosRep = (mio.repairPhotos + remoto.repairPhotos).distinct().take(MAX_FOTOS_POR_GRUPO)

    return mio.copy(
        nombre = campo("$etiqueta — nombre", base?.nombre, mio.nombre, remoto.nombre),
        damageDescription = campo("$etiqueta — daño", base?.damageDescription, mio.damageDescription, remoto.damageDescription),
        repairAction = campo("$etiqueta — reparación", base?.repairAction, mio.repairAction, remoto.repairAction),
        cantidad = campo("$etiqueta — cantidad", base?.cantidad, mio.cantidad, remoto.cantidad),
        precioUnitario = campo("$etiqueta — valor", base?.precioUnitario, mio.precioUnitario, remoto.precioUnitario),
        // Reparado gana sobre pendiente: si alguno lo marcó hecho, está hecho.
        status = if (mio.status == DamageItemStatus.REPARADO || remoto.status == DamageItemStatus.REPARADO) {
            DamageItemStatus.REPARADO
        } else {
            DamageItemStatus.PENDIENTE
        },
        damagePhotos = fotosDano,
        repairPhotos = fotosRep,
    )
}

/**
 * Lo que devuelve un guardado, para que la pantalla sepa qué contar al usuario.
 */
data class ResultadoGuardado(
    val estimado: Estimado,
    /** true si quedó guardado solo en el teléfono (la nube no confirmó). */
    val pendienteDeSubir: Boolean,
    /** true si al guardar se incorporaron cambios que hizo otra persona. */
    val huboCambiosDelOtro: Boolean,
    /** Campos que ambos editaron distinto; ganó el mío y hay que avisarlo. */
    val camposEnConflicto: List<String> = emptyList(),
)
