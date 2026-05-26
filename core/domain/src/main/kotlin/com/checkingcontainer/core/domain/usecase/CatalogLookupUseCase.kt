package com.checkingcontainer.core.domain.usecase

import com.checkingcontainer.core.domain.CatalogRepository
import com.checkingcontainer.core.model.UnitType
import javax.inject.Inject

class CatalogLookupUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
) {
    data class Result(
        val unitType: UnitType,
        val manufacturer: String,
        val deployedAs: String?,
    )

    suspend operator fun invoke(unitModel: String, unitSerialNo: String): Result {
        return when {
            unitModel.startsWith("69NT40", ignoreCase = true) ||
                unitModel.startsWith("X2", ignoreCase = true) ->
                lookupCarrier(unitModel, unitSerialNo)

            unitModel.startsWith("SCI-", ignoreCase = true) ->
                lookupStarCool(unitModel)

            else -> Result(UnitType.CARRIER, "Carrier", null)
        }
    }

    private suspend fun lookupCarrier(unitModel: String, unitSerialNo: String): Result {
        val serialDigits = unitSerialNo.filter { it.isDigit() }.toLongOrNull()
        val manufacturers = catalogRepository.getManufacturers()
        val carrier = manufacturers.firstOrNull { m ->
            m.modelPrefixes.any { prefix -> unitModel.startsWith(prefix, ignoreCase = true) }
        }

        if (carrier != null && serialDigits != null) {
            val entries = catalogRepository.getEntriesForManufacturer(carrier.id)
            // Narrow by model family prefix match, then find most specific range
            val modelFamily = unitModel.substringBeforeLast("-").ifBlank { unitModel }
            val match = entries
                .filter { it.modelFamily.equals(modelFamily, ignoreCase = true) }
                .filter { e ->
                    (e.serialRangeStart == null || serialDigits >= e.serialRangeStart) &&
                        (e.serialRangeEnd == null || serialDigits <= e.serialRangeEnd)
                }
                .minByOrNull { e ->
                    val span = if (e.serialRangeStart != null && e.serialRangeEnd != null)
                        e.serialRangeEnd - e.serialRangeStart else Long.MAX_VALUE
                    span
                }
            if (match != null) {
                return Result(UnitType.CARRIER, carrier.name, null)
            }
        }

        return Result(UnitType.CARRIER, carrier?.name ?: "Carrier", null)
    }

    private fun lookupStarCool(unitModel: String): Result {
        val deployedAs = if (unitModel.endsWith("-CA", ignoreCase = true))
            "Atmósfera Controlada"
        else
            "Estándar"
        return Result(UnitType.STAR_COOL, "Star Cool", deployedAs)
    }
}
