package com.checkingcontainer.feature.units

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.designsystem.R
import com.checkingcontainer.core.model.UnitType

@Composable
internal fun ManufacturerBadge(unitType: UnitType) {
    val logoRes = when (unitType) {
        UnitType.CARRIER -> R.drawable.logo_carrier
        UnitType.STAR_COOL -> R.drawable.logo_starcool
        UnitType.THERMO_KING -> R.drawable.logo_thermoking
        UnitType.DAIKIN -> R.drawable.logo_daikin
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        Text(
            text = unitType.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
