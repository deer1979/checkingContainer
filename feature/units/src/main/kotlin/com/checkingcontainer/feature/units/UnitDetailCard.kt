package com.checkingcontainer.feature.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.ReeferEquipment

@Composable
internal fun UnitDetailCard(equipment: ReeferEquipment, deployedAs: String? = null) {
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
