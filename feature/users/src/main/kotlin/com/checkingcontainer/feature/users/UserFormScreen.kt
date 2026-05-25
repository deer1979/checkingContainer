package com.checkingcontainer.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.model.JobTitle
import com.checkingcontainer.core.model.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFormRoute(
    onBack: () -> Unit,
    viewModel: UserFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == null) "Nuevo usuario" else "Editar usuario") },
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
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        UserFormContent(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            state = state,
            onFirstNameChange = viewModel::onFirstNameChange,
            onLastNameChange = viewModel::onLastNameChange,
            onPinChange = viewModel::onPinChange,
            onTogglePin = viewModel::onTogglePinVisibility,
            onCompanyChange = viewModel::onCompanyChange,
            onLocationChange = viewModel::onLocationChange,
            onJobTitleChange = viewModel::onJobTitleChange,
            onRoleChange = viewModel::onRoleChange,
            onActiveChange = viewModel::onToggleActive,
            onSave = viewModel::onSave,
        )
    }
}

@Composable
private fun UserFormContent(
    modifier: Modifier,
    state: UserFormUiState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onCompanyChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onJobTitleChange: (JobTitle) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersonalSection(
            state = state,
            onFirstNameChange = onFirstNameChange,
            onLastNameChange = onLastNameChange,
            onPinChange = onPinChange,
            onTogglePin = onTogglePin,
        )
        OperationalSection(
            state = state,
            onCompanyChange = onCompanyChange,
            onLocationChange = onLocationChange,
        )
        AccessSection(
            state = state,
            onJobTitleChange = onJobTitleChange,
            onRoleChange = onRoleChange,
            onActiveChange = onActiveChange,
        )
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = onSave,
            enabled = state.canSave,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Guardando…" else "Guardar")
        }
    }
}

@Composable
private fun PersonalSection(
    state: UserFormUiState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onTogglePin: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Datos personales y acceso")
            OutlinedTextField(
                value = state.firstName,
                onValueChange = onFirstNameChange,
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.lastName,
                onValueChange = onLastNameChange,
                label = { Text("Apellido") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            EmailReadonlyField(state.previewEmail)
            PinFormField(state.pin, state.pinVisible, onPinChange, onTogglePin)
        }
    }
}

@Composable
private fun EmailReadonlyField(email: String) {
    Column {
        Text(
            text = "Email generado",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = email.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PinFormField(
    pin: String,
    visible: Boolean,
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    OutlinedTextField(
        value = pin,
        onValueChange = onChange,
        label = { Text("PIN de 6 dígitos") },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Ocultar PIN" else "Mostrar PIN",
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun OperationalSection(
    state: UserFormUiState,
    onCompanyChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Datos operativos")
            OutlinedTextField(
                value = state.company,
                onValueChange = onCompanyChange,
                label = { Text("Empresa o contratista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.location,
                onValueChange = onLocationChange,
                label = { Text("Localidad / Depósito") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessSection(
    state: UserFormUiState,
    onJobTitleChange: (JobTitle) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Niveles de acceso")
            EnumDropdown(
                label = "Puesto",
                selected = state.jobTitle,
                options = JobTitle.entries.toList(),
                display = JobTitle::display,
                onSelected = onJobTitleChange,
            )
            EnumDropdown(
                label = "Rol",
                selected = state.role,
                options = UserRole.entries.toList(),
                display = { it.name },
                onSelected = onRoleChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Cuenta activa",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = state.isActive, onCheckedChange = onActiveChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<T>,
    display: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
