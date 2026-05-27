package com.checkingcontainer.core.domain.usecase

import com.checkingcontainer.core.domain.CatalogRepository
import com.checkingcontainer.core.model.Brand
import javax.inject.Inject

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
                unitModelNo.startsWith("X2", ignoreCase = true) ->
                lookupCarrier(unitModelNo)
            else -> lookupByPrefix(unitModelNo)
        }
    }

    private suspend fun lookupCarrier(unitModelNo: String): Result {
        val (serie, variante) = parseCarrierModel(unitModelNo)

        val manufacturers = catalogRepository.getManufacturers()
        val carrier = manufacturers.firstOrNull { m ->
            m.modelPrefixes.any { prefix -> unitModelNo.startsWith(prefix, ignoreCase = true) }
        }

        if (carrier != null && serie.isNotBlank()) {
            val entries = catalogRepository.getEntriesByManufacturerAndSerie(carrier.id, serie)
            // Among matching range entries, the narrowest range wins (DAO already orders by range width ASC)
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
        // Handles both "69NT40-541-022" and "69NT40541022"
        val normalized = modelNo.replace("-", "").uppercase()
        val base = "69NT40"
        return if (normalized.startsWith(base) && normalized.length > base.length + 2) {
            val afterBase = normalized.substring(base.length)
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
}
