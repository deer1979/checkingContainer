package com.checkingcontainer.feature.units

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContainerSearchRoute(
    onEstimadoClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ContainerSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContainerSearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::onSearch,
        onEstimadoClick = onEstimadoClick,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerSearchScreen(
    state: ContainerSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onEstimadoClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar contenedor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Número de contenedor") },
                    placeholder = { Text("Ej. TCKU3456789") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
                Button(
                    onClick = onSearch,
                    enabled = state.query.isNotBlank() && !state.isSearching,
                ) {
                    Text("Buscar")
                }
            }

            if (state.isSearching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            state.error?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.results.isNotEmpty()) {
                Text(
                    state.results.first().containerNo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                ElevatedCard(Modifier.fillMaxWidth()) {
                    state.results.forEachIndexed { index, estimado ->
                        SearchResultRow(
                            estimado = estimado,
                            onClick = { onEstimadoClick(estimado.inspectionId) },
                        )
                        if (index < state.results.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            } else if (state.searched && state.error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin resultados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    estimado: Estimado,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                formatSearchDate(estimado.createdAt),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                estimado.technicianName.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = if (estimado.status == EstimadoStatus.ABIERTO)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                if (estimado.status == EstimadoStatus.ABIERTO) "Abierto" else "Cerrado",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (estimado.status == EstimadoStatus.ABIERTO)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private val SEARCH_DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
private fun formatSearchDate(millis: Long): String = SEARCH_DATE_FMT.format(Date(millis))
