package com.checkingcontainer.feature.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.ReeferEquipment
import com.checkingcontainer.core.model.TipoEquipo

@Composable
internal fun UnitDetailCard(equipment: ReeferEquipment, deployedAs: String? = null) {
    // Equipos no-reefer: ficha propia (sin logo Carrier ni etiquetas de contenedor).
    if (equipment.tipoEquipo != TipoEquipo.REEFER) {
        EquipoGenericoDetailCard(equipment)
        return
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Ficha Técnica")
            ManufacturerBadge(equipment.brand)
            HorizontalDivider()
            DetailField("Container No.", equipment.containerNo)
            DetailField("Machinery Manufacturer", equipment.manufacturer.ifBlank { equipment.brand.label })
            DetailField("Unit Model", equipment.unitModel.ifBlank { "—" })
            DetailField("Unit model No.", equipment.unitModelNo)
            DetailField("Unit Type", equipment.unitType.ifBlank { "—" })
            DetailField("Unit Serial No.", equipment.unitSerialNo)
            DetailField("Year of Built", equipment.yearOfBuilt)
            if (equipment.brand == Brand.STAR_COOL) {
                HorizontalDivider()
                DetailField(
                    label = "Deployment",
                    value = deployedAs ?: "—",
                    highlight = true,
                )
            }
        }
    }
}

/** Detalle para equipos NO-reefer: en español, con ficha de placa y su foto. */
@Composable
private fun EquipoGenericoDetailCard(equipment: ReeferEquipment) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Datos del Equipo")
            DetailField("Código del equipo", equipment.containerNo)
            if (equipment.manufacturer.isNotBlank()) DetailField("Marca", equipment.manufacturer)
            if (equipment.unitModelNo.isNotBlank()) DetailField("Modelo", equipment.unitModelNo)
            if (equipment.unitSerialNo.isNotBlank()) DetailField("No. Serie", equipment.unitSerialNo)
            if (equipment.yearOfBuilt.isNotBlank()) DetailField("Año", equipment.yearOfBuilt)

            // Ficha técnica completa leída de la placa.
            if (equipment.fichaTecnica.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "Ficha técnica (placa)",
                    style = MaterialTheme.typography.titleSmall,
                )
                equipment.fichaTecnica.forEach { campo ->
                    DetailField(campo.etiqueta, campo.valor)
                }
            }

            // Foto de la placa guardada con el equipo.
            equipment.fotoPlacaUrl?.let { url ->
                HorizontalDivider()
                Text("Foto de la placa", style = MaterialTheme.typography.titleSmall)
                AsyncImage(
                    model = url,
                    contentDescription = "Foto de la placa",
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}
