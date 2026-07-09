package com.checkingcontainer.core.model

/**
 * Validación de identificaciones tributarias ecuatorianas (SRI) por dígito
 * verificador — mismo espíritu que Iso6346 para contenedores: lo que entra por
 * OCR, IA o tipeo se valida matemáticamente antes de aceptarse.
 *
 * - Cédula: 10 dígitos, provincia 01-24 (o 30 para inscritos en el exterior),
 *   tercer dígito 0-5, verificador módulo 10 con coeficientes 2-1-2-1... y
 *   resta de 9 a los productos mayores a 9.
 * - RUC (13 dígitos):
 *   · Persona natural (3er dígito 0-5): los 10 primeros son una cédula válida.
 *   · Sociedad privada (3er dígito 9): módulo 11, coef. 4-3-2-7-6-5-4-3-2,
 *     verificador en la posición 10.
 *   · Sector público (3er dígito 6): módulo 11, coef. 3-2-7-6-5-4-3-2,
 *     verificador en la posición 9.
 *   El sufijo de establecimiento no puede ser 000 (ni 0000 en el público).
 */
object IdentificacionEc {

    fun cedulaValida(cedula: String): Boolean {
        if (cedula.length != 10 || cedula.any { !it.isDigit() }) return false
        val provincia = cedula.take(2).toInt()
        if (provincia !in 1..24 && provincia != 30) return false
        if (cedula[2].digitToInt() > 5) return false
        var suma = 0
        for (i in 0..8) {
            var p = cedula[i].digitToInt() * if (i % 2 == 0) 2 else 1
            if (p > 9) p -= 9
            suma += p
        }
        val verificador = (10 - suma % 10) % 10
        return verificador == cedula[9].digitToInt()
    }

    fun rucValido(ruc: String): Boolean {
        if (ruc.length != 13 || ruc.any { !it.isDigit() }) return false
        val provincia = ruc.take(2).toInt()
        if (provincia !in 1..24 && provincia != 30) return false
        return when (ruc[2].digitToInt()) {
            in 0..5 -> cedulaValida(ruc.take(10)) && ruc.substring(10) != "000"
            9 -> modulo11(ruc, intArrayOf(4, 3, 2, 7, 6, 5, 4, 3, 2), posVerificador = 9) &&
                ruc.substring(10) != "000"
            6 -> modulo11(ruc, intArrayOf(3, 2, 7, 6, 5, 4, 3, 2), posVerificador = 8) &&
                ruc.substring(9) != "0000"
            else -> false
        }
    }

    /** Valida [numero] según [tipo]; el pasaporte solo exige no estar vacío. */
    fun valida(tipo: ClientIdType, numero: String): Boolean = when (tipo) {
        ClientIdType.RUC -> rucValido(numero)
        ClientIdType.CEDULA -> cedulaValida(numero)
        ClientIdType.PASAPORTE -> numero.isNotBlank()
    }

    private fun modulo11(numero: String, coef: IntArray, posVerificador: Int): Boolean {
        var suma = 0
        for (i in coef.indices) suma += numero[i].digitToInt() * coef[i]
        val resto = suma % 11
        val verificador = when (resto) {
            0 -> 0
            1 -> return false // combinación inválida en módulo 11
            else -> 11 - resto
        }
        return verificador == numero[posVerificador].digitToInt()
    }
}
