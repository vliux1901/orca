package org.vliux.orca.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.SearchRepository
import org.vliux.orca.data.VehicleMaintenanceSummaryItem
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.VehicleEntity
import org.vliux.orca.search.SearchAnswer
import org.vliux.orca.search.SearchResultItem

internal data class VehicleDetailUiState(
    val vehicleLoaded: Boolean = false,
    val vehicle: VehicleEntity? = null,
    val maintenanceSummaryLoaded: Boolean = false,
    val maintenanceSummary: List<VehicleMaintenanceSummaryItem> = emptyList(),
    val documentsLoaded: Boolean = false,
    val documents: List<DocumentListItem> = emptyList(),
)

internal class VehicleDetailViewModel(
    vehicleId: String,
    observeVehicle: (String) -> Flow<VehicleEntity?>,
    observeMaintenanceSummary: (String) -> Flow<List<VehicleMaintenanceSummaryItem>>,
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
            observeMaintenanceSummary(vehicleId).collect { maintenanceSummary ->
                _uiState.update { current ->
                    current.copy(
                        maintenanceSummaryLoaded = true,
                        maintenanceSummary = maintenanceSummary,
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

internal data class SearchScreenUiState(
    val query: String = "",
    val resultsLoaded: Boolean = true,
    val results: List<SearchResultItem> = emptyList(),
    val answerLoading: Boolean = false,
    val answer: SearchAnswer? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class SearchScreenViewModel(
    initialQuery: String,
    private val searchRepository: SearchRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val queryFlow = MutableStateFlow(initialQuery)
    private val _uiState = MutableStateFlow(
        SearchScreenUiState(
            query = initialQuery,
            resultsLoaded = initialQuery.isBlank(),
            answerLoading = initialQuery.isNotBlank(),
        ),
    )
    val uiState: StateFlow<SearchScreenUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            queryFlow
                .debounce(150)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        _uiState.update { current -> current.copy(resultsLoaded = false) }
                        searchRepository.observeSearchResults(query)
                    }
                }.collect { results ->
                    _uiState.update { current ->
                        current.copy(
                            resultsLoaded = true,
                            results = results,
                        )
                    }
                }
        }
        scope.launch {
            queryFlow
                .debounce(150)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { current ->
                            current.copy(
                                answerLoading = false,
                                answer = null,
                            )
                        }
                    } else {
                        _uiState.update { current -> current.copy(answerLoading = true) }
                        val answer = searchRepository.answerQuestion(query)
                        _uiState.update { current ->
                            current.copy(
                                answerLoading = false,
                                answer = answer,
                            )
                        }
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        queryFlow.value = query
        _uiState.update { current -> current.copy(query = query) }
    }
}

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
