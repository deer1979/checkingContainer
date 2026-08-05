package com.checkingcontainer.core.model

import java.text.Normalizer

/**
 * Normaliza texto para buscar: sin tildes, sin diéresis y en minúsculas.
 *
 * Escribiendo rápido en el móvil nadie pone tildes, así que "compania" tiene que
 * encontrar "Compañía Álvarez". Comparar solo ignorando mayúsculas no bastaba.
 *
 * La eñe se conserva como `n` a propósito: quien escribe "compania" espera que
 * "compañía" aparezca igual.
 */
fun String.normalizarParaBuscar(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(MARCAS_DIACRITICAS, "")
        .lowercase()
        .trim()

/** true si [consulta] aparece en este texto, ignorando tildes y mayúsculas. */
fun String.coincideCon(consulta: String): Boolean =
    normalizarParaBuscar().contains(consulta.normalizarParaBuscar())

private val MARCAS_DIACRITICAS = "\\p{Mn}+".toRegex()
