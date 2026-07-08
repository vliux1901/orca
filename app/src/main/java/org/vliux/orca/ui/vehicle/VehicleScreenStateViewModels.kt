package org.vliux.orca.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.VehicleEntity

internal data class VehicleDetailUiState(
    val vehicleLoaded: Boolean = false,
    val vehicle: VehicleEntity? = null,
    val documentsLoaded: Boolean = false,
    val documents: List<DocumentListItem> = emptyList(),
)

internal class VehicleDetailViewModel(
    vehicleId: String,
    observeVehicle: (String) -> Flow<VehicleEntity?>,
    observeDocuments: (String) -> Flow<List<DocumentListItem>>,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(VehicleDetailUiState())
    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            observeVehicle(vehicleId).collect { vehicle ->
                _uiState.update { current ->
                    current.copy(
                        vehicleLoaded = true,
                        vehicle = vehicle,
                    )
                }
            }
        }
        scope.launch {
            observeDocuments(vehicleId).collect { documents ->
                _uiState.update { current ->
                    current.copy(
                        documentsLoaded = true,
                        documents = documents,
                    )
                }
            }
        }
    }
}

internal data class DocumentDetailUiState(
    val loaded: Boolean = false,
    val detail: DocumentDetail? = null,
)

internal class DocumentDetailViewModel(
    documentId: String,
    observeDetail: (String) -> Flow<DocumentDetail?>,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(DocumentDetailUiState())
    val uiState: StateFlow<DocumentDetailUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            observeDetail(documentId).collect { detail ->
                _uiState.value = DocumentDetailUiState(
                    loaded = true,
                    detail = detail,
                )
            }
        }
    }
}

internal data class VehicleEditorUiState(
    val loaded: Boolean = false,
    val vehicle: VehicleEntity? = null,
)

internal class VehicleEditorViewModel(
    vehicleId: String?,
    observeVehicle: (String) -> Flow<VehicleEntity?>,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        VehicleEditorUiState(
            loaded = vehicleId == null,
            vehicle = null,
        ),
    )
    val uiState: StateFlow<VehicleEditorUiState> = _uiState.asStateFlow()

    init {
        if (vehicleId != null) {
            scope.launch {
                observeVehicle(vehicleId).collect { vehicle ->
                    _uiState.value = VehicleEditorUiState(
                        loaded = true,
                        vehicle = vehicle,
                    )
                }
            }
        }
    }
}
