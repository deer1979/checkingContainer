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
import com.checkingcontainer.core.model.Brand

@Composable
internal fun ManufacturerBadge(unitType: Brand) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ManufacturerLogo(
            brand = unitType,
            modifier = Modifier.width(56.dp).height(28.dp),
        )
        Text(
            text = unitType.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Solo el logo del fabricante sobre fondo blanco, sin la etiqueta de texto. */
@Composable
internal fun ManufacturerLogo(
    brand: Brand,
    modifier: Modifier = Modifier,
) {
    val logoRes = when (brand) {
        Brand.CARRIER -> R.drawable.logo_carrier
        Brand.STAR_COOL -> R.drawable.logo_starcool
        Brand.THERMO_KING -> R.drawable.logo_thermoking
        Brand.DAIKIN -> R.drawable.logo_daikin
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = brand.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(4.dp),
        )
    }
}
