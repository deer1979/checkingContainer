package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import com.checkingcontainer.core.domain.usecase.CatalogLookupUseCase
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.TipoEquipo
import com.checkingcontainer.feature.units.navigation.UNIT_ENTRY_ID_ARG
import com.checkingcontainer.feature.units.navigation.UNIT_ENTRY_TIPO_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class UnitEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val equipmentRepo: ReeferEquipmentRepository,
    private val inspectionRepo: InspectionRepository,
    private val authRepository: AuthRepository,
    private val catalogLookupUseCase: CatalogLookupUseCase,
) : ViewModel() {

    private val editId: Long? = savedStateHandle.get<Long>(UNIT_ENTRY_ID_ARG)?.takeIf { it != -1L }

    private val _state = MutableStateFlow(UnitEntryUiState())
    val state: StateFlow<UnitEntryUiState> = _state.asStateFlow()

    private var lastAutoTriggeredModel = ""
    private var catalogLookupJob: Job? = null
    private var duplicateCheckJob: Job? = null

    init {
        savedStateHandle.get<String>(UNIT_ENTRY_TIPO_ARG)?.let { arg ->
            val tipo = runCatching { TipoEquipo.valueOf(arg) }.getOrDefault(TipoEquipo.REEFER)
            _state.update {
                it.copy(
                    tipoEquipo = tipo,
                    status = if (tipo == TipoEquipo.REEFER) it.status else InspStatus.MANT_PREVENTIVO,
                )
            }
        }
        editId?.let { loadInspection(it) }
    }

    private fun loadInspection(id: Long) {
        viewModelScope.launch {
            runCatching {
                val inspection = inspectionRepo.findById(id) ?: return@runCatching null
                inspection to equipmentRepo.findByContainerNo(inspection.containerNo)
            }.onSuccess { loaded ->
                val (inspection, equipment) = loaded ?: return@onSuccess
                _state.update {
                    it.copy(
                        inspectionId = id,
                        containerNo = inspection.containerNo,
                        unitModelNo = equipment?.unitModelNo ?: "",
                        unitModel = equipment?.unitModel ?: "",
                        unitType = equipment?.unitType ?: "",
                        manufacturer = equipment?.manufacturer ?: "",
                        unitSerialNo = equipment?.unitSerialNo ?: "",
                        yearOfBuilt = equipment?.yearOfBuilt ?: "",
                        brand = equipment?.brand ?: Brand.CARRIER,
                        tipoEquipo = equipment?.tipoEquipo ?: TipoEquipo.REEFER,
                        fichaTecnica = equipment?.fichaTecnica ?: emptyList(),
                        fotoPlacaUrl = equipment?.fotoPlacaUrl,
                        status = inspection.status,
                        ptiInstruction = inspection.ptiInstruction,
                        deployedAs = inspection.deployedAs,
                        observations = inspection.observations,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(errorMessage = error.message ?: "No se pudo cargar la inspección") }
            }
        }
    }

    fun onEvent(event: UnitEntryEvent) {
        if (event == UnitEntryEvent.TriggerManualLookup) {
            val modelNo = _state.value.unitModelNo.trim()
            if (modelNo.isNotBlank()) triggerCatalogLookup(modelNo)
            return
        }

        _state.update { s ->
            when (event) {
                is UnitEntryEvent.ContainerNoChange ->
                    s.copy(containerNo = event.value.uppercase(), errorMessage = null, duplicateWarning = null)
                is UnitEntryEvent.UnitModelNoChange ->
                    s.copy(unitModelNo = event.value, errorMessage = null, catalogError = null)
                is UnitEntryEvent.UnitSerialNoChange ->
                    s.copy(unitSerialNo = event.value.uppercase(), errorMessage = null)
                is UnitEntryEvent.YearOfBuiltChange ->
                    s.copy(yearOfBuilt = event.value, errorMessage = null)
                is UnitEntryEvent.StatusChange -> s.copy(status = event.value)
                is UnitEntryEvent.PtiInstructionChange -> s.copy(ptiInstruction = event.value)
                is UnitEntryEvent.DeployedAsChange -> s.copy(deployedAs = event.value)
                is UnitEntryEvent.ManufacturerChange -> s.copy(manufacturer = event.value, errorMessage = null)
                is UnitEntryEvent.ObservationsChange -> s.copy(observations = event.value)
                UnitEntryEvent.OpenOrientationPicker -> s.copy(showOrientationPicker = true)
                UnitEntryEvent.DismissOrientationPicker -> s.copy(showOrientationPicker = false)
                is UnitEntryEvent.OpenScanner -> s.copy(
                    showOrientationPicker = false,
                    showScanner = true,
                    scannerMode = event.mode,
                    scannerInitialVertical = event.isVertical,
                )
                UnitEntryEvent.CloseScanner -> s.copy(showScanner = false)
                is UnitEntryEvent.OcrResult -> s.applyOcrFields(event.fields)
                is UnitEntryEvent.FichaExtraida -> s.copy(fichaTecnica = event.ficha)
                is UnitEntryEvent.RemoveFichaCampo ->
                    s.copy(fichaTecnica = s.fichaTecnica.filterIndexed { i, _ -> i != event.index })
                is UnitEntryEvent.UpdateFichaCampo -> s.copy(
                    fichaTecnica = s.fichaTecnica.mapIndexed { i, c ->
                        if (i == event.index) event.campo else c
                    },
                )
                is UnitEntryEvent.AddFichaCampo -> s.copy(fichaTecnica = s.fichaTecnica + event.campo)
                UnitEntryEvent.ShowDeleteConfirm -> s.copy(showDeleteConfirm = true)
                UnitEntryEvent.DismissDeleteConfirm -> s.copy(showDeleteConfirm = false)
                UnitEntryEvent.TriggerManualLookup -> s
                UnitEntryEvent.DismissDuplicateWarning -> s.copy(duplicateWarning = null)
            }
        }

        if (event is UnitEntryEvent.OcrResult) {
            val modelNo = _state.value.unitModelNo.trim()
            if (event.fields.containsKey("Unit Model") && modelNo.isNotBlank()) triggerCatalogLookup(modelNo)
        }

        if (event is UnitEntryEvent.ContainerNoChange && editId == null) {
            val containerNo = event.value.uppercase().trim()
            if (_state.value.isContainerValid) checkDuplicate(containerNo) else duplicateCheckJob?.cancel()
        }

        if (event is UnitEntryEvent.UnitModelNoChange) {
            val model = event.value.trim()
            if (Iso6346.isCompleteCarrierModel(model) && model != lastAutoTriggeredModel) {
                lastAutoTriggeredModel = model
                triggerCatalogLookup(model)
            } else if (!Iso6346.isCompleteCarrierModel(model)) {
                catalogLookupJob?.cancel()
                _state.update { it.copy(isLookingUpCatalog = false) }
            }
        }
    }

    fun deleteUnit() {
        val id = _state.value.inspectionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, showDeleteConfirm = false, errorMessage = null) }
            runCatching { inspectionRepo.delete(id) }
                .onSuccess { _state.update { it.copy(isDeleting = false, deletedSuccessfully = true) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(isDeleting = false, errorMessage = error.message ?: "No se pudo eliminar la inspección")
                    }
                }
        }
    }

    fun onFotoPlaca(uri: Uri) {
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "placas").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    file
                }.getOrNull()
            }
            val localUri = local?.let(Uri::fromFile) ?: uri
            _state.update { it.copy(fotoPlacaUrl = localUri.toString()) }
            analizarPlaca(localUri)
            local?.let { file ->
                launch(Dispatchers.IO) {
                    runCatching { equipmentRepo.uploadFotoPlaca(file.readBytes()) }
                        .onSuccess { url -> _state.update { it.copy(fotoPlacaUrl = url) } }
                }
            }
        }
    }

    fun reanalizarPlaca() {
        val actual = _state.value.fotoPlacaUrl ?: return
        viewModelScope.launch {
            val uri = if (actual.startsWith("http")) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val loader = coil3.SingletonImageLoader.get(context)
                        val request = coil3.request.ImageRequest.Builder(context).data(actual).build()
                        val bitmap = (loader.execute(request) as? coil3.request.SuccessResult)
                            ?.image?.let { (it as? coil3.BitmapImage)?.bitmap } ?: return@runCatching null
                        val file = File(context.cacheDir, "placa_reanalisis.jpg")
                        file.outputStream().use { output ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, output)
                        }
                        Uri.fromFile(file)
                    }.getOrNull()
                }
            } else {
                Uri.parse(actual)
            }
            if (uri != null) analizarPlaca(uri) else {
                _state.update { it.copy(metodoLectura = "No se pudo cargar la foto guardada") }
            }
        }
    }

    private fun analizarPlaca(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(analizandoPlaca = true, metodoLectura = null) }
            val result = runCatching {
                PlacaEquipoExtractor.desdeImagen(context, uri, _state.value.tipoEquipo)
            }.getOrNull()
            _state.update { current ->
                if (result == null || (result.ficha.isEmpty() && result.fields.isEmpty())) {
                    current.copy(analizandoPlaca = false, metodoLectura = "No se pudo leer la placa")
                } else {
                    val fields = if (current.containerNo.isNotBlank()) {
                        result.fields - "Container No."
                    } else {
                        result.fields
                    }
                    current.applyOcrFields(fields).copy(
                        fichaTecnica = if (result.ficha.isNotEmpty()) result.ficha else current.fichaTecnica,
                        analizandoPlaca = false,
                        metodoLectura = "${result.ficha.size} datos leídos con ${result.metodo}",
                        textoOcrDiag = result.textoOcr,
                    )
                }
            }
        }
    }

    fun saveUnit() {
        val current = _state.value
        if (!current.canSave) {
            _state.update { it.copy(showValidation = true) }
            return
        }
        if (current.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val authUser = authRepository.state
                    .filterIsInstance<AuthState.Authenticated>()
                    .first()
                    .user

                val equipment = current.toEquipment()
                val inspection = current.toInspection(authUser.id, authUser.fullName, authUser.location)
                equipmentRepo.upsert(equipment).getOrThrow()

                val savedInspectionId = if (current.inspectionId != null) {
                    inspectionRepo.update(inspection.copy(id = current.inspectionId)).getOrThrow()
                    current.inspectionId
                } else {
                    inspectionRepo.create(inspection).getOrThrow()
                }

                val esEstimable = current.status == InspStatus.EST ||
                    current.status == InspStatus.REPARACION ||
                    current.status == InspStatus.MANT_CORRECTIVO
                val navTarget = savedInspectionId.takeIf { esEstimable && it != 0L }

                _state.update {
                    it.copy(
                        isSaving = false,
                        savedSuccessfully = true,
                        navigateToEstimado = navTarget,
                    )
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "No se pudo guardar la inspección",
                    )
                }
            }
        }
    }

    private fun checkDuplicate(containerNo: String) {
        duplicateCheckJob?.cancel()
        duplicateCheckJob = viewModelScope.launch {
            val existing = runCatching { inspectionRepo.findTodayByContainerNo(containerNo) }.getOrNull()
            if (_state.value.containerNo.trim() != containerNo) return@launch
            if (existing == null) {
                _state.update { it.copy(duplicateWarning = null) }
                return@launch
            }
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(existing.createdAt))
            _state.update { it.copy(duplicateWarning = DuplicateWarning(time, existing.technicianName)) }
        }
    }

    private fun triggerCatalogLookup(unitModelNo: String) {
        val requestedModel = unitModelNo.trim()
        catalogLookupJob?.cancel()
        catalogLookupJob = viewModelScope.launch {
            _state.update { it.copy(isLookingUpCatalog = true, catalogError = null) }
            val lookup = runCatching { catalogLookupUseCase(requestedModel) }
            if (_state.value.unitModelNo.trim() != requestedModel) return@launch
            _state.update { current ->
                lookup.fold(
                    onSuccess = { result ->
                        current.copy(
                            isLookingUpCatalog = false,
                            brand = result.brand,
                            manufacturer = result.manufacturer,
                            unitModel = result.unitModel,
                            unitType = result.unitType,
                            deployedAs = if (result.brand == Brand.STAR_COOL) result.deployedAs else null,
                            catalogError = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLookingUpCatalog = false,
                            catalogError = error.message,
                        )
                    },
                )
            }
        }
    }
}
