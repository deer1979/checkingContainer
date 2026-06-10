package com.checkingcontainer.feature.units

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.checkingcontainer.core.model.EstimadoStatus

@Composable
fun EstimadoRoute(
    onBack: () -> Unit,
    viewModel: EstimadoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EstimadoScreen(
        state = state,
        onBack = onBack,
        onEvent = viewModel::onEvent,
        onSave = viewModel::save,
        onAddDamagePhoto = viewModel::addDamagePhoto,
        onAddRepairPhoto = viewModel::addRepairPhoto,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimadoScreen(
    state: EstimadoUiState,
    onBack: () -> Unit,
    onEvent: (EstimadoEvent) -> Unit,
    onSave: () -> Unit,
    onAddDamagePhoto: (Uri) -> Unit,
    onAddRepairPhoto: (Uri) -> Unit,
) {
    val pickDamagePhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAddDamagePhoto(it) }
    }
    val pickRepairPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAddRepairPhoto(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.containerNo.isNotEmpty())
                            "Estimado — ${state.containerNo}"
                        else
                            "Estimado",
                    )
                },
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
            if (!state.isLoading && state.status != EstimadoStatus.REPARADO) {
                ExtendedFloatingActionButton(
                    onClick = onSave,
                    icon = {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                        }
                    },
                    text = { Text(if (state.isSaving) "Guardando…" else "Guardar Estimado") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── DAÑO ──────────────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionTitle("Daño reportado")

                    OutlinedTextField(
                        value = state.clientName,
                        onValueChange = { onEvent(EstimadoEvent.ClientNameChange(it)) },
                        label = { Text("Nombre del cliente") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words,
                        ),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = state.damageDescription,
                        onValueChange = { onEvent(EstimadoEvent.DamageDescriptionChange(it)) },
                        label = { Text("Descripción del daño") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    )

                    FieldLabel("Fotos del daño (${state.damagePhotos.size}/10)")
                    PhotoGrid(
                        photos = state.damagePhotos,
                        onAdd = if (state.damagePhotos.size < 10) {
                            { pickDamagePhoto.launch("image/*") }
                        } else null,
                        onRemove = { url -> onEvent(EstimadoEvent.RemoveDamagePhoto(url)) },
                        isUploading = state.isUploadingPhoto,
                    )

                    if (!state.showRepairSection && state.status == EstimadoStatus.ABIERTO) {
                        OutlinedButton(
                            onClick = { onEvent(EstimadoEvent.ShowRepairSection) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Registrar reparación")
                        }
                    }
                }
            }

            // ── REPARACIÓN ────────────────────────────────────────────────────
            if (state.showRepairSection || state.status == EstimadoStatus.REPARADO) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionTitle(
                            text = "Reparación",
                            isComplete = if (state.status == EstimadoStatus.REPARADO) true else null,
                        )

                        OutlinedTextField(
                            value = state.repairDescription,
                            onValueChange = { onEvent(EstimadoEvent.RepairDescriptionChange(it)) },
                            label = { Text("Descripción de la reparación") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                            enabled = state.status != EstimadoStatus.REPARADO,
                        )

                        FieldLabel("Fotos de la reparación (${state.repairPhotos.size}/10)")
                        PhotoGrid(
                            photos = state.repairPhotos,
                            onAdd = if (state.repairPhotos.size < 10 && state.status != EstimadoStatus.REPARADO) {
                                { pickRepairPhoto.launch("image/*") }
                            } else null,
                            onRemove = if (state.status != EstimadoStatus.REPARADO) {
                                { url -> onEvent(EstimadoEvent.RemoveRepairPhoto(url)) }
                            } else null,
                            isUploading = state.isUploadingPhoto,
                        )

                        if (state.status == EstimadoStatus.ABIERTO) {
                            Button(
                                onClick = { onEvent(EstimadoEvent.MarkAsReparado) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isSaving && state.repairDescription.isNotBlank(),
                            ) {
                                Text("Marcar como Reparado")
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "✓ Reparación registrada",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            // ── Mensajes ──────────────────────────────────────────────────────
            state.savedMessage?.let { msg ->
                Text(
                    text = "✓ $msg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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

@Composable
private fun PhotoGrid(
    photos: List<String>,
    onAdd: (() -> Unit)?,
    onRemove: ((String) -> Unit)?,
    isUploading: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { url ->
            Box(modifier = Modifier.size(100.dp)) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                )
                if (onRemove != null) {
                    IconButton(
                        onClick = { onRemove(url) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(bottomStart = 8.dp),
                            ),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Eliminar foto",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (onAdd != null) {
            if (isUploading) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Agregar foto",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
