package com.checkingcontainer.core.domain.usecase

import com.checkingcontainer.core.domain.CatalogRepository
import com.checkingcontainer.core.model.Brand
import javax.inject.Inject

class CatalogNotFoundException(message: String) : Exception(message)

class CatalogLookupUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
) {
    data class Result(
        val brand: Brand,
        val manufacturer: String,
        val unitModel: String,
        val unitType: String,
        val deployedAs: String?,
    )

    suspend operator fun invoke(unitModelNo: String): Result {
        return when {
            unitModelNo.startsWith("SCI-", ignoreCase = true) ->
                lookupStarCool(unitModelNo)
            unitModelNo.startsWith("69NT40", ignoreCase = true) ||
                unitModelNo.startsWith("69NT20", ignoreCase = true) ||
                unitModelNo.startsWith("X2", ignoreCase = true) ->
                lookupCarrier(unitModelNo)
            else -> lookupByPrefix(unitModelNo)
        }
    }

    private suspend fun lookupCarrier(unitModelNo: String): Result {
        val (serie, variante) = parseCarrierModel(unitModelNo)

        if (serie.isBlank()) {
            throw CatalogNotFoundException(
                "Formato de modelo Carrier no reconocido: '$unitModelNo'"
            )
        }

        val manufacturers = catalogRepository.getManufacturers()
        val carrier = manufacturers.firstOrNull { m ->
            m.modelPrefixes.any { prefix -> unitModelNo.startsWith(prefix, ignoreCase = true) }
        }

        if (carrier != null) {
            val entries = catalogRepository.getEntriesByManufacturerAndSerie(carrier.id, serie)
            val match = entries.firstOrNull { e -> variante in e.rangeStart..e.rangeEnd }
            if (match != null) {
                return Result(
                    brand = Brand.CARRIER,
                    manufacturer = "Carrier Transicold",
                    unitModel = match.unitModel,
                    unitType = match.unitType,
                    deployedAs = null,
                )
            }

            // Serie no encontrada — buscar sugerencia por similitud
            val allEntries = catalogRepository.getEntriesForManufacturer(carrier.id)
            val knownSeries = allEntries.map { it.serie }.distinct()
            val suggestion = findSimilarSeries(serie, knownSeries)

            val prefix = when {
                unitModelNo.startsWith("69NT20", ignoreCase = true) -> "69NT20"
                unitModelNo.startsWith("X2", ignoreCase = true) -> "X2"
                else -> "69NT40"
            }
            val msg = if (suggestion != null) {
                "Serie '$serie' no existe en catálogo. ¿El escáner invirtió dígitos? Serie más cercana: '$prefix-$suggestion-…'"
            } else {
                "Serie '$serie' no existe en el catálogo de Carrier Transicold."
            }
            throw CatalogNotFoundException(msg)
        }

        return Result(Brand.CARRIER, "Carrier Transicold", "", "", null)
    }

    private fun lookupStarCool(unitModelNo: String): Result {
        val isCA = unitModelNo.endsWith("-CA", ignoreCase = true)
        return Result(
            brand = Brand.STAR_COOL,
            manufacturer = "Star Cool",
            unitModel = if (isCA) "Star Cool CA" else "Star Cool",
            unitType = "",
            deployedAs = if (isCA) "Atmósfera Controlada" else "Estándar",
        )
    }

    private suspend fun lookupByPrefix(unitModelNo: String): Result {
        val manufacturers = catalogRepository.getManufacturers()
        val match = manufacturers.firstOrNull { m ->
            m.modelPrefixes.any { prefix -> unitModelNo.startsWith(prefix, ignoreCase = true) }
        }
        return when (match?.name) {
            "Thermo King" -> Result(Brand.THERMO_KING, "Thermo King", "", "", null)
            "Daikin" -> Result(Brand.DAIKIN, "Daikin", "", "", null)
            else -> Result(Brand.CARRIER, "Carrier Transicold", "", "", null)
        }
    }

    private fun parseCarrierModel(modelNo: String): Pair<String, Int> {
        // Handles both "69NT40-541-022" / "69NT20-541-022" and compact "69NT40541022"
        val normalized = modelNo.replace("-", "").uppercase()
        val carrierBases = listOf("69NT40", "69NT20")
        val matchedBase = carrierBases.firstOrNull { normalized.startsWith(it) }

        return if (matchedBase != null && normalized.length > matchedBase.length + 2) {
            val afterBase = normalized.substring(matchedBase.length)
            val serie = afterBase.substring(0, minOf(3, afterBase.length))
            val variante = afterBase.drop(3).filter { it.isDigit() }.toIntOrNull() ?: 0
            Pair(serie, variante)
        } else {
            val segments = modelNo.split("-")
            val serie = if (segments.size >= 2) segments[1] else ""
            val variante = if (segments.size >= 3) segments[2].filter { it.isDigit() }.toIntOrNull() ?: 0 else 0
            Pair(serie, variante)
        }
    }

    /**
     * Busca la serie conocida más parecida a [entered].
     * Primero chequea transposición de dígitos (anagrama) — error de OCR más común.
     * Si no hay anagrama, busca la de menor distancia de edición (≤ 1).
     */
    private fun findSimilarSeries(entered: String, known: List<String>): String? {
        if (entered.length == 3 && entered.all { it.isDigit() }) {
            val sortedEntered = entered.toCharArray().sorted()
            val anagram = known.firstOrNull { s ->
                s != entered && s.length == 3 && s.all { it.isDigit() } &&
                    s.toCharArray().sorted() == sortedEntered
            }
            if (anagram != null) return anagram
        }
        return known.firstOrNull { s -> s != entered && editDistance(entered, s) == 1 }
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }
}
