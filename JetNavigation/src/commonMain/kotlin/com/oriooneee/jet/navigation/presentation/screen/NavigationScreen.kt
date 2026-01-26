package com.oriooneee.jet.navigation.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.presentation.FloorAndBuildingBadge
import com.oriooneee.jet.navigation.presentation.MapComponent
import com.oriooneee.jet.navigation.presentation.NavigationViewModel
import com.oriooneee.jet.navigation.presentation.navigation.LocalNavController
import com.oriooneee.jet.navigation.presentation.navigation.Route
import com.oriooneee.jet.navigation.presentation.screen.components.NavigationControls
import com.oriooneee.jet.navigation.presentation.screen.components.RouteSelectionBottomSheet
import com.oriooneee.jet.navigation.presentation.screen.map.ZoomableMapCanvas
import com.oriooneee.jet.navigation.presentation.screen.transitions.TransitionScreen
import com.oriooneee.jet.navigation.presentation.screen.transitions.TransitionToBuildingScreen
import com.oriooneee.jet.navigation.presentation.screen.transitions.TransitionToInDoorScreen
import com.oriooneee.jet.navigation.presentation.screen.transitions.TransitionToOutDoorScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.compose.viewmodel.koinViewModel

const val KEY_SELECTED_START_NODE = "selected_start_node"
const val KEY_SELECTED_END_NODE = "selected_end_node"

@Composable
fun NavigationScreen(
    isDarkTheme: Boolean,
    viewModel: NavigationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val planColor = MaterialTheme.colorScheme.onSurface
    val planLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val routeColor = MaterialTheme.colorScheme.primary
    val startNodeColor = MaterialTheme.colorScheme.primary
    val endNodeColor = MaterialTheme.colorScheme.primary
    val navController = LocalNavController.current
    var mapHeight by remember { mutableStateOf(0.dp) }

    var showRouteSelection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect {
            val savedStateHandle = it.savedStateHandle
            launch {
                savedStateHandle.getStateFlow<String?>(
                    KEY_SELECTED_START_NODE,
                    null
                ).collect { node ->
                    if (node != null) {
                        viewModel.onStartNodeSelected(Json.decodeFromString(node))
                        it.savedStateHandle.remove<String>(KEY_SELECTED_START_NODE)
                    }
                }
            }
            launch {
                savedStateHandle.getStateFlow<String?>(
                    KEY_SELECTED_END_NODE,
                    null
                ).collect { node ->
                    if (node != null) {
                        viewModel.onEndNodeSelected(Json.decodeFromString(node))
                        it.savedStateHandle.remove<String>(KEY_SELECTED_END_NODE)
                    }
                }
            }
        }
    }

    BoxWithConstraints {
        val isLargeScreen = maxWidth >= 650.dp
        var isPanelExpanded by remember { mutableStateOf(true) }
        val maxHeightForBottomSheet = (maxHeight - (mapHeight + 80.dp)).takeIf {
            mapHeight > 0.dp
        } ?: 180.dp

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Card(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 0.dp,
                            bottom = 16.dp
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentStep = uiState.currentStep
                        val activeMapData = currentStep as? NavigationStep.ByFlor

                        ZoomableMapCanvas(
                            renderData = activeMapData?.image,
                            initFocusPoint = activeMapData?.pointOfInterest ?: Offset.Zero,
                            routeBounds = activeMapData?.routeBounds,
                            planColor = planColor,
                            labelColor = planLabelColor,
                            routeColor = routeColor,
                            startNodeColor = startNodeColor,
                            endNodeColor = endNodeColor,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (activeMapData != null) 1f else 0f)
                        )

                        if (currentStep != null) {
                            AnimatedContent(
                                targetState = currentStep,
                                transitionSpec = {
                                    fadeIn(tween(400)) + scaleIn(initialScale = 0.95f) togetherWith
                                            fadeOut(tween(400))
                                },
                                label = "MapAnim"
                            ) { step ->
                                when (step) {
                                    is NavigationStep.ByFlor -> {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            FloorAndBuildingBadge(
                                                floorNumber = step.flor,
                                                buildingNumber = step.building,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(16.dp)
                                            )
                                        }
                                    }

                                    is NavigationStep.TransitionToFlor -> {
                                        TransitionScreen(
                                            targetFloor = step.to,
                                            currentFlor = step.from
                                        )
                                    }

                                    is NavigationStep.TransitionToBuilding -> {
                                        TransitionToBuildingScreen(
                                            fromBuilding = step.form,
                                            toBuilding = step.to
                                        )
                                    }

                                    is NavigationStep.OutDoorMaps -> {
                                        val density = LocalDensity.current
                                        MapComponent(
                                            step = step,
                                            isDarkTheme = isDarkTheme,
                                            modifier = Modifier.onGloballyPositioned {
                                                mapHeight = with(density) {
                                                    it.size.height.toDp()
                                                }
                                            }
                                        )
                                    }

                                    is NavigationStep.TransitionToInDoor -> {
                                        TransitionToInDoorScreen(toBuilding = step.toBuilding)
                                    }

                                    is NavigationStep.TransitionToOutDoor -> {
                                        TransitionToOutDoorScreen(fromBuilding = step.fromBuilding)
                                    }
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = "Ready to Navigate",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Select your start and destination points to view the route on the map",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPanelExpanded = !isPanelExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPanelExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = "Toggle Panel",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        NavigationControls(
                            currentStepIndex = uiState.currentStepIndex,
                            totalSteps = uiState.navigationSteps.size,
                            routeStats = uiState.routeStats,
                            isIndoorRecommended = false,
                            onPrevious = viewModel::previousStep,
                            onNext = viewModel::nextStep,
                            startNode = uiState.startNode,
                            endNode = uiState.endNode,
                            isLoading = uiState.isLoading,
                            onSelectStart = {
                                navController.navigate(
                                    Route.SelectDestination(
                                        isStartNode = true,
                                        false
                                    )
                                )
                            },
                            onSelectEnd = {
                                navController.navigate(
                                    Route.SelectDestination(
                                        isStartNode = false,
                                        uiState.startNode != null
                                    )
                                )
                            },
                            onSwapNodes = viewModel::swapNodes,
                            isExpanded = isPanelExpanded,
                            isVertical = !isLargeScreen,
                            availableRoutesCount = uiState.availableRoutes.size,
                            onOpenRouteSelection = { showRouteSelection = true },
                            isDarkMode = isDarkTheme
                        )
                    }
                }
            }
        }

        if (showRouteSelection && uiState.availableRoutes.size > 1) {
            RouteSelectionBottomSheet(
                routes = uiState.availableRoutes,
                selectedRoute = uiState.routeStats,
                isDarkMode = isDarkTheme,
                maxHeightForBottomSheet = maxHeightForBottomSheet,
                onRouteSelected = { route ->
                    viewModel.selectRoute(route)
                    showRouteSelection = false
                },
                onDismiss = { showRouteSelection = false }
            )
        }
    }
}
