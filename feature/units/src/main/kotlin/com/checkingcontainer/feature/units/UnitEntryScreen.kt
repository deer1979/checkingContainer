package com.checkingcontainer.feature.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.designsystem.theme.AppTheme

@Composable
fun UnitEntryRoute(
    onBack: () -> Unit,
    viewModel: UnitEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }

    UnitEntryScreen(
        state = state,
        onBack = onBack,
        onEvent = viewModel::onEvent,
        onSave = viewModel::saveUnit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitEntryScreen(
    state: UnitEntryUiState,
    onBack: () -> Unit,
    onEvent: (UnitEntryEvent) -> Unit,
    onSave: () -> Unit,
) {
    if (state.showScanner) {
        OcrScannerBottomSheet(
            mode = state.scannerMode,
            onSuccess = { fields -> onEvent(UnitEntryEvent.OcrResult(fields)) },
            onDismiss = { onEvent(UnitEntryEvent.CloseScanner) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ingreso de Unidad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSave,
                icon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                text = { Text(if (state.isSaving) "Guardando…" else "Guardar Unidad") },
                containerColor = if (state.canSave)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdentificationCard(state = state, onEvent = onEvent)
            EquipmentDataCard(state = state, onEvent = onEvent)
            InspectionCard(state = state, onEvent = onEvent)
            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UnitEntryScreenPreview() {
    AppTheme {
        UnitEntryScreen(
            state = UnitEntryUiState(
                containerNo = "BMOU9012909",
                unitModel = "69NT40-531-J04",
                unitSerialNo = "KSB60036558",
                yearOfBuilt = "2008",
            ),
            onBack = {},
            onEvent = {},
            onSave = {},
        )
    }
}
