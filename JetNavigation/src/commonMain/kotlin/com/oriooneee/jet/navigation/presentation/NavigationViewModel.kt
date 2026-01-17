package com.oriooneee.jet.navigation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriooneee.jet.navigation.NavigationEngine
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult
import jetnavigation.jetnavigation.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

data class NavigationUiState(
    val startNode: Node? = null,
    val endNode: Node? = null,
    val navigationSteps: List<NavigationStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val routeStats: NavigationDirection? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentStep: NavigationStep?
        get() = if (navigationSteps.isNotEmpty() && currentStepIndex in navigationSteps.indices) {
            navigationSteps[currentStepIndex]
        } else null
}

class NavigationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState = _uiState.asStateFlow()

    private val navigationEngine: MutableStateFlow<NavigationEngine?> = MutableStateFlow(null)

    init {
        loadData()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun loadData() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true) }

            try {

                val engine = NavigationEngine(MasterNavigation.loadFromAssets())
                navigationEngine.value = engine
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onStartNodeSelected(result: SelectNodeResult) {
        viewModelScope.launch(Dispatchers.Default) {
            val engine = navigationEngine.value ?: return@launch
            val referenceNode = uiState.value.endNode
            val resolvedNode = engine.resolveSelection(result, referenceNode)

            withContext(Dispatchers.Main) {
                if (resolvedNode != null) {
                    _uiState.update {
                        it.copy(
                            startNode = resolvedNode,
                            navigationSteps = emptyList(),
                            routeStats = null,
                            currentStepIndex = 0,
                            error = null
                        )
                    }
                    if (uiState.value.endNode != null) {
                        calculateRoute()
                    }
                } else if (result !is SelectNodeResult.SelectedNode) {
                    _uiState.update { it.copy(error = "Select a destination first to find the nearest start point") }
                }
            }
        }
    }

    fun onEndNodeSelected(result: SelectNodeResult) {
        viewModelScope.launch(Dispatchers.Default) {
            val engine = navigationEngine.value ?: return@launch
            val referenceNode = uiState.value.startNode
            val resolvedNode = engine.resolveSelection(result, referenceNode)

            withContext(Dispatchers.Main) {
                if (resolvedNode != null) {
                    _uiState.update {
                        it.copy(
                            endNode = resolvedNode,
                            navigationSteps = emptyList(),
                            routeStats = null,
                            currentStepIndex = 0,
                            error = null
                        )
                    }
                    if (uiState.value.startNode != null) {
                        calculateRoute()
                    }
                } else if (result !is SelectNodeResult.SelectedNode) {
                    _uiState.update { it.copy(error = "Select a start point first to find the nearest destination") }
                }
            }
        }
    }

    fun swapNodes() {
        val currentStart = uiState.value.startNode
        val currentEnd = uiState.value.endNode

        _uiState.update {
            it.copy(
                startNode = currentEnd,
                endNode = currentStart,
                navigationSteps = emptyList(),
                routeStats = null,
                currentStepIndex = 0
            )
        }
        if (currentStart != null && currentEnd != null) {
            calculateRoute()
        }
    }

    private fun calculateRoute() {
        val start = uiState.value.startNode ?: return
        val end = uiState.value.endNode ?: return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true, navigationSteps = emptyList()) }

            try {
                val engine = navigationEngine.filterNotNull().first()
                val result = engine.getRoute(
                    from = start,
                    to = end,
                )

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            navigationSteps = result.steps,
                            routeStats = result,
                            currentStepIndex = 0,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun nextStep() {
        _uiState.update {
            if (it.currentStepIndex < it.navigationSteps.lastIndex) {
                it.copy(currentStepIndex = it.currentStepIndex + 1)
            } else it
        }
    }

    fun previousStep() {
        _uiState.update {
            if (it.currentStepIndex > 0) {
                it.copy(currentStepIndex = it.currentStepIndex - 1)
            } else it
        }
    }
}