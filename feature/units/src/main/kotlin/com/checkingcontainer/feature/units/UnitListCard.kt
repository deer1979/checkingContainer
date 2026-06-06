package com.checkingcontainer.feature.units

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.designsystem.R
import com.checkingcontainer.core.designsystem.theme.chipColors
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.InspectionWithEquipment
import com.checkingcontainer.core.model.PtiInstruction

@Composable
internal fun InspectionListItem(
    item: InspectionWithEquipment,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val inspection = item.inspection
    val equipment = item.equipment

    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ManufacturerLogo(equipment.brand)
                Text(
                    text = inspection.containerNo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(inspection.status)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Opciones",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDateTime(inspection.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = inspection.technicianName.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = equipment.unitModel.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    inspection.ptiInstruction?.let { pti ->
                        OutlineBadge(
                            text = if (pti == PtiInstruction.FULL_PTI) "Full PTI" else "Visual PTI",
                        )
                    }
                    if (equipment.brand == Brand.STAR_COOL && inspection.deployedAs != null) {
                        OutlineBadge(
                            text = if (inspection.deployedAs == "Atmósfera Controlada") "CA" else "STD",
                        )
                    }
                }
            }
            if (inspection.idDigitador != null || inspection.statusDigitacion != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    inspection.idDigitador?.let {
                        Text(
                            text = "Digitador: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inspection.statusDigitacion?.let {
                        OutlineBadge(text = it)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManufacturerLogo(brand: Brand) {
    val logoRes = when (brand) {
        Brand.CARRIER -> R.drawable.logo_carrier
        Brand.STAR_COOL -> R.drawable.logo_starcool
        Brand.THERMO_KING -> R.drawable.logo_thermoking
        Brand.DAIKIN -> R.drawable.logo_daikin
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.width(56.dp).height(28.dp),
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = brand.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(3.dp),
        )
    }
}

@Composable
private fun StatusBadge(status: InspStatus) {
    val isDark = isSystemInDarkTheme()
    val colors = status.chipColors(isDark)
    Surface(
        color = colors.container,
        contentColor = colors.onContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun OutlineBadge(text: String) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
