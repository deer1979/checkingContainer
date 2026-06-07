package com.checkingcontainer.feature.units

/**
 * ISO 6346 container number validator.
 * Expects exactly 11 chars: 4 uppercase letters + 6 digits + 1 check digit.
 */
object Iso6346 {

    // Identificador de categoría (4ª letra, posición 3). Por norma ISO 6346 solo puede
    // ser U (contenedores), J (equipo desmontable) o Z (chasis/remolques). Sin esta
    // restricción, el dígito verificador valida por error lecturas como A o K en vez de
    // U (A=10, K=21, U=32 → todas ≡ 10 mod 11, mismo check digit en la posición 3).
    private val CATEGORY_IDENTIFIERS = setOf('U', 'J', 'Z')

    private val CHAR_VALUES: Map<Char, Int> = buildMap {
        for (i in 0..9) put('0' + i, i)
        var v = 10
        for (c in 'A'..'Z') {
            if (v % 11 == 0) v++       // skip multiples of 11 (11, 22, 33)
            put(c, v++)
        }
    }

    /** Computes the ISO 6346 check digit for the first 10 characters (4 letters + 6 digits). */
    fun computeCheckDigit(first10: String): Int {
        val s = first10.trim().uppercase()
        if (s.length != 10) return -1
        var sum = 0
        for (i in 0..9) {
            val v = CHAR_VALUES[s[i]] ?: return -1
            sum += v * (1 shl i)
        }
        return (sum % 11) % 10
    }

    internal fun isCompleteCarrierModel(model: String): Boolean {
        val parts = model.split("-")
        if (parts.size != 3) return false
        val prefix = parts[0].uppercase()
        return (prefix == "69NT40" || prefix == "69NT20") &&
            parts[1].length == 3 && parts[1].all(Char::isDigit) &&
            parts[2].length == 3 && parts[2].all(Char::isDigit)
    }

    fun isValid(input: String): Boolean {
        val s = input.trim().uppercase()
        if (s.length != 11) return false
        if (s.take(4).any { !it.isLetter() }) return false
        if (s[3] !in CATEGORY_IDENTIFIERS) return false   // identificador de categoría
        if (s.drop(4).take(6).any { !it.isDigit() }) return false
        if (!s[10].isDigit()) return false

        var sum = 0
        for (i in 0..9) {
            val v = CHAR_VALUES[s[i]] ?: return false
            sum += v * (1 shl i)        // weight = 2^i
        }
        val checkDigit = (sum % 11) % 10   // remainder 10 → maps to 0
        return checkDigit == s[10].digitToInt()
    }
}