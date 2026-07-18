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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        // Tipo elegido en el asistente del "+" (solo para creación; en edición
        // manda el tipo guardado del equipo).
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
            val inspection = inspectionRepo.findById(id) ?: return@launch
            val equipment = equipmentRepo.findByContainerNo(inspection.containerNo)
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
        }
    }

    fun onEvent(event: UnitEntryEvent) {
        if (event == UnitEntryEvent.TriggerManualLookup) {
            val modelNo = _state.value.unitModelNo
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
                is UnitEntryEvent.ManufacturerChange ->
                    s.copy(manufacturer = event.value, errorMessage = null)
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
                is UnitEntryEvent.UpdateFichaCampo ->
                    s.copy(
                        fichaTecnica = s.fichaTecnica.mapIndexed { i, c ->
                            if (i == event.index) event.campo else c
                        },
                    )
                is UnitEntryEvent.AddFichaCampo ->
                    s.copy(fichaTecnica = s.fichaTecnica + event.campo)
                UnitEntryEvent.ShowDeleteConfirm -> s.copy(showDeleteConfirm = true)
                UnitEntryEvent.DismissDeleteConfirm -> s.copy(showDeleteConfirm = false)
                UnitEntryEvent.TriggerManualLookup -> s
                UnitEntryEvent.DismissDuplicateWarning -> s.copy(duplicateWarning = null)
            }
        }
        if (event is UnitEntryEvent.OcrResult) {
            val modelNo = _state.value.unitModelNo
            if (event.fields.containsKey("Unit Model") && modelNo.isNotBlank()) {
                triggerCatalogLookup(modelNo)
            }
        }
        if (event is UnitEntryEvent.ContainerNoChange && editId == null) {
            val containerNo = event.value.uppercase()
            if (_state.value.isContainerValid) checkDuplicate(containerNo)
        }
        if (event is UnitEntryEvent.UnitModelNoChange) {
            val model = event.value
            if (Iso6346.isCompleteCarrierModel(model) && model != lastAutoTriggeredModel) {
                lastAutoTriggeredModel = model
                triggerCatalogLookup(model)
            }
        }
    }

    fun deleteUnit() {
        val id = _state.value.inspectionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, showDeleteConfirm = false) }
            inspectionRepo.delete(id)
            _state.update { it.copy(isDeleting = false, deletedSuccessfully = true) }
        }
    }

    /**
     * Foto de la placa: se guarda al instante (copia local persistente) y el
     * análisis corre en segundo plano en el ViewModel — el usuario sigue
     * llenando el formulario y la ficha aparece sola. La subida a Storage es
     * best-effort (sin conexión, queda la copia local).
     */
    fun onFotoPlaca(uri: Uri) {
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "placas").apply { mkdirs() }
                    val f = File(dir, "${UUID.randomUUID()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        f.outputStream().use { out -> inp.copyTo(out) }
                    } ?: return@runCatching null
                    f
                }.getOrNull()
            }
            val localUri = local?.let(Uri::fromFile) ?: uri
            _state.update { it.copy(fotoPlacaUrl = localUri.toString()) }
            analizarPlaca(localUri)
            // Subida en paralelo; si logra, la URL remota reemplaza la local.
            local?.let { f ->
                launch(Dispatchers.IO) {
                    runCatching { equipmentRepo.uploadFotoPlaca(f.readBytes()) }
                        .onSuccess { url -> _state.update { it.copy(fotoPlacaUrl = url) } }
                }
            }
        }
    }

    /** Vuelve a leer la placa desde la foto guardada (local o remota). */
    fun reanalizarPlaca() {
        val actual = _state.value.fotoPlacaUrl ?: return
        viewModelScope.launch {
            val uri = if (actual.startsWith("http")) {
                // Remota: bajarla a un archivo temporal vía la caché de Coil.
                withContext(Dispatchers.IO) {
                    runCatching {
                        val loader = coil3.SingletonImageLoader.get(context)
                        val req = coil3.request.ImageRequest.Builder(context).data(actual).build()
                        val bmp = (loader.execute(req) as? coil3.request.SuccessResult)
                            ?.image?.let { (it as? coil3.BitmapImage)?.bitmap } ?: return@runCatching null
                        val f = File(context.cacheDir, "placa_reanalisis.jpg")
                        f.outputStream().use { out ->
                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        Uri.fromFile(f)
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
            val r = runCatching {
                PlacaEquipoExtractor.desdeImagen(context, uri, _state.value.tipoEquipo)
            }.getOrNull()
            _state.update { s ->
                if (r == null || (r.ficha.isEmpty() && r.fields.isEmpty())) {
                    s.copy(analizandoPlaca = false, metodoLectura = "No se pudo leer la placa")
                } else {
                    val fields = if (s.containerNo.isNotBlank()) r.fields - "Container No." else r.fields
                    s.applyOcrFields(fields).copy(
                        fichaTecnica = if (r.ficha.isNotEmpty()) r.ficha else s.fichaTecnica,
                        analizandoPlaca = false,
                        metodoLectura = "${r.ficha.size} datos leídos con ${r.metodo}",
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
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val authUser = authRepository.state
                .filterIsInstance<AuthState.Authenticated>()
                .first()
                .user

            val equipment = current.toEquipment()
            val inspection = current.toInspection(authUser.id, authUser.fullName, authUser.location)

            var savedInspectionId = current.inspectionId ?: 0L

            val result: Result<Unit> = if (current.inspectionId != null) {
                equipmentRepo.upsert(equipment)
                inspectionRepo.update(inspection.copy(id = current.inspectionId))
            } else {
                equipmentRepo.upsert(equipment)
                inspectionRepo.create(inspection).also { r ->
                    savedInspectionId = r.getOrElse { 0L }
                }.map {}
            }

            result
                .onSuccess {
                    // Correctivo y Reparación se cobran → generan estimado.
                    // Preventivo es visita de contrato (solo historial).
                    val esEstimable = current.status == InspStatus.EST ||
                        current.status == InspStatus.REPARACION ||
                        current.status == InspStatus.MANT_CORRECTIVO
                    val navTarget = if (esEstimable && savedInspectionId != 0L) {
                        savedInspectionId
                    } else null
                    _state.update { it.copy(isSaving = false, savedSuccessfully = true, navigateToEstimado = navTarget) }
                }
                .onFailure { error ->
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
        viewModelScope.launch {
            val existing = inspectionRepo.findTodayByContainerNo(containerNo) ?: return@launch
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(existing.createdAt))
            _state.update { it.copy(duplicateWarning = DuplicateWarning(timeStr, existing.technicianName)) }
        }
    }

    private fun triggerCatalogLookup(unitModelNo: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLookingUpCatalog = true, catalogError = null) }
            val lookup = runCatching { catalogLookupUseCase(unitModelNo) }
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
                    onFailure = { ex ->
                        current.copy(
                            isLookingUpCatalog = false,
                            catalogError = ex.message,
                        )
                    },
                )
            }
        }
    }
}
