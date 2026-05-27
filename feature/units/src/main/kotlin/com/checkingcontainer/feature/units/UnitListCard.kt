package com.checkingcontainer.feature.units

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.designsystem.R
import com.checkingcontainer.core.designsystem.theme.chipColors
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.ReeferUnit
import com.checkingcontainer.core.model.Brand

@Composable
internal fun InspectionListItem(unit: ReeferUnit, onClick: () -> Unit) {
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
                ManufacturerLogo(unit.brand)
                Text(
                    text = unit.containerNo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(unit.status)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDateTime(unit.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = unit.technicianName.ifBlank { "—" },
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
                    text = unit.unitModel.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    unit.ptiInstruction?.let { pti ->
                        OutlineBadge(
                            text = if (pti == PtiInstruction.FULL_PTI) "Full PTI" else "Visual PTI",
                        )
                    }
                    if (unit.brand == Brand.STAR_COOL && unit.deployedAs != null) {
                        OutlineBadge(
                            text = if (unit.deployedAs == "Atmósfera Controlada") "CA" else "STD",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManufacturerLogo(unitType: Brand) {
    val logoRes = when (unitType) {
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
            contentDescription = unitType.label,
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
private fun OutlineBadge(text: String) {
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
