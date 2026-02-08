package com.oriooneee.jet.navigation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriooneee.jet.navigation.data.NavigationRemoteRepository
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.InDoorNode
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult
import com.oriooneee.jet.navigation.engine.NavigationEngine
import com.oriooneee.jet.navigation.engine.models.ResolvedNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

data class NavigationUiState(
    val startNode: ResolvedNode? = null,
    val endNode: ResolvedNode? = null,
    val navigationSteps: List<NavigationStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val routeStats: NavigationDirection? = null,
    val availableRoutes: List<NavigationDirection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentStep: NavigationStep?
        get() = if (navigationSteps.isNotEmpty() && currentStepIndex in navigationSteps.indices) {
            navigationSteps[currentStepIndex]
        } else null

    val startInDoorNode: InDoorNode?
        get() = (startNode as? ResolvedNode.InDoor)?.node

    val endInDoorNode: InDoorNode?
        get() = (endNode as? ResolvedNode.InDoor)?.node
}

class NavigationViewModel(
    private val remoteRepository: NavigationRemoteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState = _uiState.asStateFlow()

    private val _masterNavigation = MutableStateFlow<MasterNavigation?>(null)


    private val navigationEngine = _masterNavigation.filterNotNull().map { navigation ->
        NavigationEngine(navigation, false)
    }

    init {
        loadData()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun loadData() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val navData = async {
                    remoteRepository.getMainNavigation().getOrNull()
                }
                _masterNavigation.value = navData.await()
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
            val engine = navigationEngine.first()
            val referenceNode = uiState.value.endNode
            val resolvedNode = engine.resolveSelection(result, referenceNode)

            withContext(Dispatchers.Main) {
                if (resolvedNode != null) {
                    _uiState.update {
                        it.copy(
                            startNode = resolvedNode,
                            navigationSteps = emptyList(),
                            routeStats = null,
                            availableRoutes = emptyList(),
                            currentStepIndex = 0,
                            error = null
                        )
                    }
                    if (uiState.value.endNode != null) {
                        calculateRoute()
                    }
                } else if (result !is SelectNodeResult.SelectedNode && result !is SelectNodeResult.SelectedOutDoorNode) {
                    _uiState.update { it.copy(error = "Select a destination first to find the nearest start point") }
                }
            }
        }
    }

    fun onEndNodeSelected(result: SelectNodeResult) {
        viewModelScope.launch(Dispatchers.Default) {
            val engine = navigationEngine.first()
            val referenceNode = uiState.value.startNode
            val resolvedNode = engine.resolveSelection(result, referenceNode)

            withContext(Dispatchers.Main) {
                if (resolvedNode != null) {
                    _uiState.update {
                        it.copy(
                            endNode = resolvedNode,
                            navigationSteps = emptyList(),
                            routeStats = null,
                            availableRoutes = emptyList(),
                            currentStepIndex = 0,
                            error = null
                        )
                    }
                    if (uiState.value.startNode != null) {
                        calculateRoute()
                    }
                } else if (result !is SelectNodeResult.SelectedNode && result !is SelectNodeResult.SelectedOutDoorNode) {
                    _uiState.update { it.copy(error = "Select a start point first to find the nearest destination") }
                }
            }
        }
    }

    fun swapNodes() {
        val currentStart = uiState.value.startNode
        val currentEnd = uiState.value.endNode

        if (currentStart != null && currentEnd != null) {
            viewModelScope.launch(Dispatchers.Default) {
                _uiState.update { it.copy(isLoading = true) }

                try {
                    val engine = navigationEngine.first()
                    val routes = engine.getRoute(from = currentEnd, to = currentStart)

                    withContext(Dispatchers.Main) {
                        if (routes.isNotEmpty()) {
                            val bestRoute = routes.first()
                            _uiState.update {
                                it.copy(
                                    startNode = currentEnd,
                                    endNode = currentStart,
                                    navigationSteps = bestRoute.steps,
                                    routeStats = bestRoute,
                                    availableRoutes = routes,
                                    currentStepIndex = 0,
                                    isLoading = false,
                                    error = null
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    startNode = currentEnd,
                                    endNode = currentStart,
                                    navigationSteps = emptyList(),
                                    routeStats = null,
                                    availableRoutes = emptyList(),
                                    currentStepIndex = 0,
                                    isLoading = false,
                                    error = "No route found"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }

    fun selectRoute(route: NavigationDirection) {
        _uiState.update {
            it.copy(
                navigationSteps = route.steps,
                routeStats = route,
                currentStepIndex = 0
            )
        }
    }

    private fun calculateRoute() {
        val start = uiState.value.startNode ?: return
        val end = uiState.value.endNode ?: return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    navigationSteps = emptyList(),
                    availableRoutes = emptyList()
                )
            }

            try {
                val engine = navigationEngine.filterNotNull().first()
                val routes = engine.getRoute(from = start, to = end)

                withContext(Dispatchers.Main) {
                    if (routes.isNotEmpty()) {
                        val bestRoute = routes.first()
                        _uiState.update {
                            it.copy(
                                availableRoutes = routes,
                                navigationSteps = bestRoute.steps,
                                routeStats = bestRoute,
                                currentStepIndex = 0,
                                isLoading = false
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "No route found") }
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
    fun setStepIndex(index: Int) {
        _uiState.update {
            if (index in it.navigationSteps.indices) {
                it.copy(currentStepIndex = index)
            } else it
        }
    }
}