package com.checkingcontainer.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON DTO for the `reefer_units` Supabase table.
 *
 * Column names use snake_case to match PostgreSQL / Supabase conventions.
 * The [id] field is omitted (null) when inserting so that PostgreSQL can
 * auto-generate it; on reads it will always be present.
 */
@Serializable
data class ReeferUnitDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("container_no") val containerNo: String,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("unit_model") val unitModel: String,
    @SerialName("unit_model_no") val unitModelNo: String,
    @SerialName("unit_serial_no") val unitSerialNo: String,
    @SerialName("year_of_built") val yearOfBuilt: String,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("status") val status: String,
    @SerialName("pti_instruction") val ptiInstruction: String? = null,
    @SerialName("unit_type") val unitType: String,
    @SerialName("deployed_as") val deployedAs: String? = null,
    @SerialName("technician_id") val technicianId: Long,
    @SerialName("technician_name") val technicianName: String,
    @SerialName("observations") val observations: String,
    @SerialName("local_id") val localId: Long? = null,
)
