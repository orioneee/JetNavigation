package com.oriooneee.jet.navigation.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NaturePeople
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriooneee.jet.navigation.FloorRenderData
import com.oriooneee.jet.navigation.ResolvedNode
import com.oriooneee.jet.navigation.TextLabel
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.presentation.navigation.LocalNavController
import com.oriooneee.jet.navigation.presentation.navigation.Route
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun rememberZoomState(minScale: Float = 0.1f, maxScale: Float = 10f) =
    remember { ZoomState(minScale, maxScale) }

class ZoomState(private val minScale: Float, private val maxScale: Float) {
    var scale by mutableStateOf(1f); private set
    var offsetX by mutableStateOf(0f); private set
    var offsetY by mutableStateOf(0f); private set
    private var containerSize = Size.Zero

    fun updateContainerSize(size: Size) {
        if (containerSize == size) return
        val oldCenter = Offset(containerSize.width / 2, containerSize.height / 2)
        val targetPoint = (oldCenter - Offset(offsetX, offsetY)) / scale
        containerSize = size
        if (scale != 1f) {
            val newCenter = Offset(containerSize.width / 2, containerSize.height / 2)
            val newOffset = newCenter - (targetPoint * scale)
            offsetX = newOffset.x
            offsetY = newOffset.y
        }
    }

    fun resetToFit(contentSize: Size) {
        if (containerSize == Size.Zero || contentSize == Size.Zero) return
        val fitScale = minOf(
            containerSize.width / contentSize.width,
            containerSize.height / contentSize.height
        )
        scale = fitScale
        centerContent(contentSize, fitScale)
    }

    fun zoomToPoint(focusPoint: Offset, contentSize: Size, zoomMultiplier: Float) {
        if (containerSize == Size.Zero || contentSize == Size.Zero) return
        val fitScale = minOf(
            containerSize.width / contentSize.width,
            containerSize.height / contentSize.height
        )
        scale = (fitScale * zoomMultiplier).coerceIn(minScale, maxScale)
        val screenCenter = Offset(containerSize.width / 2, containerSize.height / 2)
        val newOffset = screenCenter - (focusPoint * scale)
        offsetX = newOffset.x
        offsetY = newOffset.y
    }

    private fun centerContent(contentSize: Size, currentScale: Float) {
        offsetX = (containerSize.width - contentSize.width * currentScale) / 2
        offsetY = (containerSize.height - contentSize.height * currentScale) / 2
    }

    fun onGesture(centroid: Offset, pan: Offset, zoomChange: Float) {
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(minScale, maxScale)
        val actualZoomFactor = newScale / oldScale
        val targetOffset = Offset(offsetX, offsetY) + pan
        val finalOffset = targetOffset + (centroid - targetOffset) * (1 - actualZoomFactor)
        scale = newScale; offsetX = finalOffset.x; offsetY = finalOffset.y
    }

    suspend fun smoothZoomIn() = animateZoomBy(1.5f)
    suspend fun smoothZoomOut() = animateZoomBy(0.66f)

    private suspend fun animateZoomBy(factor: Float) {
        val startScale = scale
        val targetScale = (startScale * factor).coerceIn(minScale, maxScale)
        val actualFactor = targetScale / startScale

        val center = Offset(containerSize.width / 2, containerSize.height / 2)
        val startOffset = Offset(offsetX, offsetY)
        val targetOffset = startOffset + (center - startOffset) * (1 - actualFactor)

        androidx.compose.animation.core.animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) { progress, _ ->
            scale = androidx.compose.ui.util.lerp(startScale, targetScale, progress)
            val currentOffset =
                androidx.compose.ui.geometry.lerp(startOffset, targetOffset, progress)
            offsetX = currentOffset.x
            offsetY = currentOffset.y
        }
    }
}

const val KEY_SELECTED_START_NODE = "selected_start_node"
const val KEY_SELECTED_END_NODE = "selected_end_node"

data class RoutePresentation(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

private fun generatePathSummary(steps: List<NavigationStep>): String {
    val parts = mutableListOf<String>()

    steps.forEach { step ->
        val part = when (step) {
            is NavigationStep.OutDoorMaps -> "Street"
            is NavigationStep.ByFlor -> "${step.building}"
            else -> null
        }

        if (part != null && parts.lastOrNull() != part) {
            parts.add(part)
        }
    }

    if (parts.isEmpty()) return "Direct Route"
    if (parts.size == 1 && parts.first() != "Street") return "Building ${parts.first()} Only"

    return parts.joinToString(" → ")
}

@Composable
fun getRoutePresentation(route: NavigationDirection, isFastest: Boolean): RoutePresentation {
    val summary = generatePathSummary(route.steps)
    val isPureOutdoor =
        route.steps.all { it is NavigationStep.OutDoorMaps || it is NavigationStep.TransitionToOutDoor }
    val hasOutdoor = route.steps.any { it is NavigationStep.OutDoorMaps }

    return when {
        isPureOutdoor -> RoutePresentation(
            title = "Via Streets",
            subtitle = "Outdoor route • ${route.totalDistanceMeters.toInt()}m",
            icon = Icons.Outlined.WbSunny,
            color = Color(0xFFE65100)
        )

        else -> RoutePresentation(
            title = summary,
            subtitle = if (isFastest) "Fastest • ${route.totalDistanceMeters.toInt()}m" else "${route.totalDistanceMeters.toInt()}m",
            icon = if (hasOutdoor) Icons.Outlined.NaturePeople else Icons.Outlined.Apartment,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(
    ExperimentalLayoutApi::class, ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun NavigationScreen(
    isDarkTheme: Boolean,
    viewModel: NavigationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isIndoorRecommended by viewModel.isIndoorRecommended.collectAsState()

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
        LaunchedEffect(maxHeightForBottomSheet){
            println("Max height for bottom sheet: $maxHeightForBottomSheet")
        }

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            {
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
                            isIndoorRecommended = isIndoorRecommended,
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
                            onOpenRouteSelection = { showRouteSelection = true }
                        )
                    }
                }
            }
        }

        if (showRouteSelection && uiState.availableRoutes.size > 1) {
            RouteSelectionBottomSheet(
                routes = uiState.availableRoutes,
                selectedRoute = uiState.routeStats,
                onRouteSelected = { route ->
                    viewModel.selectRoute(route)
                    showRouteSelection = false
                },
                onDismiss = { showRouteSelection = false },
                isIndoorRecommended = isIndoorRecommended,
                maxHeightForBottomSheet = maxHeightForBottomSheet
            )
        }
    }
}

expect val applyMaxHeightForBottomSheet: Boolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSelectionBottomSheet(
    routes: List<NavigationDirection>,
    selectedRoute: NavigationDirection?,
    isIndoorRecommended: Boolean,
    maxHeightForBottomSheet: Dp,
    onRouteSelected: (NavigationDirection) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (applyMaxHeightForBottomSheet)
                        Modifier.heightIn(max = maxHeightForBottomSheet)
                    else
                        Modifier
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Choose a Route",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )

            val sortedForCheck = remember(routes) { routes.sortedBy { it.totalDistanceMeters } }

            routes.forEachIndexed { _, route ->
                val isSelected = route == selectedRoute
                val isFastest = route == sortedForCheck.first()
                val presentation = getRoutePresentation(route, isFastest)

                val badgeText = route.badge

                RouteOptionItem(
                    presentation = presentation,
                    timeMinutes = route.estimatedTimeMinutes,
                    stepCount = route.steps.size,
                    isSelected = isSelected,
                    badge = badgeText,
                    onClick = { onRouteSelected(route) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun RouteOptionItem(
    presentation: RoutePresentation,
    timeMinutes: Double,
    stepCount: Int,
    isSelected: Boolean,
    badge: String? = null,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh

    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else Modifier

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(presentation.color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    tint = presentation.color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (badge != null) {
                    val badgeColor = if (badge == "Recommended")
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer

                    val badgeTextColor = if (badge == "Recommended")
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer

                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = presentation.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                val mins = timeMinutes.toInt()
                val secs = ((timeMinutes - mins) * 60).toInt()

                Text(
                    text = "$mins min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (secs > 0) {
                    Text(
                        text = "$secs s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$stepCount steps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun DestinationInputPanel(
    startNode: ResolvedNode?,
    endNode: ResolvedNode?,
    isLoading: Boolean,
    onSelectStart: () -> Unit,
    onSelectEnd: () -> Unit,
    onSwap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(96.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(64.dp)
                            .align(Alignment.Center)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(1.dp)
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                            .border(
                                3.dp,
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                MaterialTheme.colorScheme.error,
                                CircleShape
                            )
                            .border(
                                3.dp,
                                MaterialTheme.colorScheme.errorContainer,
                                CircleShape
                            )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onSelectStart,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = startNode?.label ?: "Start",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (startNode == null)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onSelectEnd,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = endNode?.label ?: "Destination",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (endNode == null)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier
                        .size(48.dp),
                    onClick = onSwap,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = "Swap",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun LocationInputRow(
    label: String,
    value: String,
    isEmpty: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        headlineContent = {
            if (!isEmpty) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isEmpty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isEmpty) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        supportingContent = {
            if (!isEmpty) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
fun NavigationControls(
    currentStepIndex: Int,
    totalSteps: Int,
    routeStats: NavigationDirection?,
    isIndoorRecommended: Boolean,
    isExpanded: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    startNode: ResolvedNode?,
    endNode: ResolvedNode?,
    isLoading: Boolean,
    onSelectStart: () -> Unit,
    onSelectEnd: () -> Unit,
    onSwapNodes: () -> Unit,
    isVertical: Boolean,
    availableRoutesCount: Int,
    onOpenRouteSelection: () -> Unit
) {
    @Composable
    fun RouteStats() {
        routeStats?.let { stats ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                onClick = { if (availableRoutesCount > 1) onOpenRouteSelection() }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (availableRoutesCount > 1) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Change route",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(0.5f),
                                            CircleShape
                                        )
                                        .padding(4.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                            }

                            Column {
                                val mins = stats.estimatedTimeMinutes.toInt()
                                val secs = ((stats.estimatedTimeMinutes - mins) * 60).toInt()

                                val typeTitle = getRoutePresentation(stats, false).title

                                Text(
                                    text = "$mins min $secs sec",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (availableRoutesCount > 1) {
                                        Text(
                                            text = "$typeTitle • ",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "~${stats.totalDistanceMeters.toInt()} meters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.Default.Timer,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isIndoorRecommended) {
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Indoor route recommended",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StepNavigationControls() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onPrevious,
                enabled = currentStepIndex > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    modifier = Modifier.rotate(180f)
                )
                Spacer(Modifier.width(8.dp))
                Text("Previous")
            }

            Text(
                text = "${currentStepIndex + 1} / $totalSteps",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = onNext,
                enabled = currentStepIndex < totalSteps - 1,
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Text("Next")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }

    if (isVertical) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isExpanded) {
                DestinationInputPanel(
                    startNode = startNode,
                    endNode = endNode,
                    isLoading = isLoading,
                    onSelectStart = onSelectStart,
                    onSelectEnd = onSelectEnd,
                    onSwap = onSwapNodes
                )
                RouteStats()
            }
            if (totalSteps > 0) {
                StepNavigationControls()
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (isExpanded) {
                    DestinationInputPanel(
                        startNode = startNode,
                        endNode = endNode,
                        isLoading = isLoading,
                        onSelectStart = onSelectStart,
                        onSelectEnd = onSelectEnd,
                        onSwap = onSwapNodes
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isExpanded) {
                    RouteStats()
                }
                if (totalSteps > 0) {
                    StepNavigationControls()
                }
            }
        }
    }
}

data class RenderedLabel(
    val label: TextLabel,
    val fontSize: TextUnit,
    val visible: Boolean
)

@Composable
fun ZoomableMapCanvas(
    renderData: FloorRenderData?,
    initFocusPoint: Offset,
    planColor: Color,
    labelColor: Color,
    routeColor: Color,
    startNodeColor: Color,
    endNodeColor: Color,
    modifier: Modifier = Modifier
) {
    if (renderData == null) {
        Box(modifier = modifier)
        return
    }

    val zoomState = rememberZoomState()
    val textMeasurer = rememberTextMeasurer()
    val contentSize = remember(renderData) { Size(renderData.width, renderData.height) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val layerPaint = remember { androidx.compose.ui.graphics.Paint() }

    val iconPainters = renderData.icons.map { it.icon }.distinct()
        .associateWith { androidx.compose.ui.graphics.vector.rememberVectorPainter(it) }

    val renderedLabels = remember(renderData, density) {
        val linesForCollision = mutableListOf<Pair<Offset, Offset>>()
        renderData.polylines.forEach { poly ->
            for (i in 0 until poly.size - 1) linesForCollision.add(poly[i] to poly[i + 1])
        }
        renderData.polygons.forEach { poly ->
            for (i in 0 until poly.size - 1) linesForCollision.add(poly[i] to poly[i + 1])
            if (poly.isNotEmpty()) linesForCollision.add(poly.last() to poly.first())
        }
        linesForCollision.addAll(renderData.singleLines)

        val labelMaxSizes = renderData.textLabels.map { label ->
            val maxFontSize = 40.sp
            val minCheckSize = 2.sp
            var bestFitSize = 0.sp
            val steps = 20
            for (i in 0..steps) {
                val scaleFactor = 1f - (i * (1f / steps))
                val testSize = maxFontSize * scaleFactor
                if (testSize.value < minCheckSize.value) break
                val textStyle = TextStyle(
                    fontSize = testSize,
                    fontWeight = if (label.bold) FontWeight.Bold else FontWeight.Normal
                )
                val layoutResult = textMeasurer.measure(label.text, textStyle)
                val width = layoutResult.size.width
                val height = layoutResult.size.height
                val left = label.x - width / 2f
                val top = label.y - height / 2f
                val rect = Rect(left, top, left + width, top + height)
                var hasCollision = false
                for (line in linesForCollision) {
                    if (rectIntersectsLine(rect, line.first, line.second)) {
                        hasCollision = true; break
                    }
                }
                if (!hasCollision) {
                    bestFitSize = testSize; break
                }
            }
            label to bestFitSize
        }
        val minReadableSizeVal = 3f
        val goodSizes = labelMaxSizes.map { it.second.value }.filter { it > 0 }
        val standardSizeVal =
            if (goodSizes.isNotEmpty()) goodSizes.min() else minReadableSizeVal
        labelMaxSizes.map { (label, maxFitSize) ->
            val constrainedSize =
                if (maxFitSize.value < minReadableSizeVal) minReadableSizeVal else minOf(
                    maxFitSize.value,
                    standardSizeVal
                )
            RenderedLabel(label, constrainedSize.sp, true)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val containerSize = Size(
            with(density) { maxWidth.toPx() },
            with(density) { maxHeight.toPx() }
        )

        LaunchedEffect(containerSize) {
            zoomState.updateContainerSize(containerSize)
        }

        LaunchedEffect(renderData, initFocusPoint) {
            zoomState.updateContainerSize(containerSize)
            if (initFocusPoint != Offset.Zero) {
                zoomState.zoomToPoint(initFocusPoint, contentSize, 3f)
            } else {
                zoomState.resetToFit(contentSize)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        zoomState.onGesture(centroid, pan, zoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { scope.launch { zoomState.smoothZoomIn() } }
                    )
                }
        ) {
            withTransform({
                translate(left = zoomState.offsetX, top = zoomState.offsetY)
                scale(scaleX = zoomState.scale, scaleY = zoomState.scale, pivot = Offset.Zero)
            }) {
                val strokeWidth = maxOf(1f, contentSize.height * 0.001f) / 2

                renderData.polygons.forEach { points ->
                    if (points.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                            close()
                        }
                        drawPath(path = path, color = planColor.copy(alpha = 0.05f))
                        drawPath(
                            path = path,
                            color = planColor,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
                renderData.polylines.forEach { points ->
                    if (points.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                        }
                        drawPath(
                            path = path,
                            color = planColor,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
                renderData.singleLines.forEach { (start, end) ->
                    drawLine(
                        color = planColor,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                if (renderData.routePath.isNotEmpty()) {
                    val routePathObj = Path().apply {
                        val start = renderData.routePath.first()
                        moveTo(start.x, start.y)
                        for (i in 1 until renderData.routePath.size) {
                            lineTo(renderData.routePath[i].x, renderData.routePath[i].y)
                        }
                    }

                    val pathBounds = routePathObj.getBounds()
                    val buffer = strokeWidth * 10
                    val layerRect = Rect(
                        pathBounds.left - buffer,
                        pathBounds.top - buffer,
                        pathBounds.right + buffer,
                        pathBounds.bottom + buffer
                    )

                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(layerRect, layerPaint)

                        drawPath(
                            path = routePathObj,
                            color = routeColor.copy(alpha = 0.8f),
                            style = Stroke(
                                width = strokeWidth * 4f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        val arrowSizeScale = strokeWidth * 4f
                        val arrowSpacing = strokeWidth * 100f
                        val safeCornerDistance = strokeWidth * 4f
                        val straightAngleThreshold = 30f

                        var targetAccumulatedDistance = arrowSpacing
                        var currentAccumulatedDistance = 0f

                        for (i in 0 until renderData.routePath.size - 1) {
                            val start = renderData.routePath[i]
                            val end = renderData.routePath[i + 1]

                            val dx = end.x - start.x
                            val dy = end.y - start.y
                            val segmentLength = kotlin.math.sqrt(dx * dx + dy * dy)
                            val currentAngle =
                                kotlin.math.atan2(dy, dx) * (180 / kotlin.math.PI.toFloat())

                            var startBuffer = safeCornerDistance
                            if (i > 0) {
                                val prevStart = renderData.routePath[i - 1]
                                val prevDx = start.x - prevStart.x
                                val prevDy = start.y - prevStart.y
                                val prevAngle = kotlin.math.atan2(
                                    prevDy,
                                    prevDx
                                ) * (180 / kotlin.math.PI.toFloat())
                                val diff = kotlin.math.abs(currentAngle - prevAngle)
                                val angleDelta = if (diff > 180) 360 - diff else diff
                                if (angleDelta < straightAngleThreshold) startBuffer = 0f
                            } else {
                                startBuffer = 0f
                            }

                            var endBuffer = safeCornerDistance
                            if (i < renderData.routePath.size - 2) {
                                val nextEnd = renderData.routePath[i + 2]
                                val nextDx = nextEnd.x - end.x
                                val nextDy = nextEnd.y - end.y
                                val nextAngle = kotlin.math.atan2(
                                    nextDy,
                                    nextDx
                                ) * (180 / kotlin.math.PI.toFloat())
                                val diff = kotlin.math.abs(nextAngle - currentAngle)
                                val angleDelta = if (diff > 180) 360 - diff else diff
                                if (angleDelta < straightAngleThreshold) endBuffer = 0f
                            } else {
                                endBuffer = 0f
                            }

                            val segmentEndDist = currentAccumulatedDistance + segmentLength

                            while (targetAccumulatedDistance < segmentEndDist) {
                                val localPos =
                                    targetAccumulatedDistance - currentAccumulatedDistance

                                if (localPos > startBuffer && localPos < (segmentLength - endBuffer)) {
                                    val fraction = localPos / segmentLength
                                    val posX = start.x + dx * fraction
                                    val posY = start.y + dy * fraction

                                    withTransform({
                                        rotate(currentAngle, Offset(posX, posY))
                                        translate(posX, posY)
                                    }) {
                                        val arrowPath = Path().apply {
                                            moveTo(-arrowSizeScale, -arrowSizeScale * 0.7f)
                                            lineTo(0f, 0f)
                                            lineTo(-arrowSizeScale, arrowSizeScale * 0.7f)
                                        }

                                        drawPath(
                                            path = arrowPath,
                                            color = Color.Transparent,
                                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                                            style = Stroke(
                                                width = strokeWidth * 1.5f,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                                targetAccumulatedDistance += arrowSpacing
                            }
                            currentAccumulatedDistance += segmentLength
                        }
                        canvas.restore()
                    }
                }

                renderData.startNode?.let {
                    drawCircle(color = startNodeColor, radius = strokeWidth * 3f, center = it)
                }
                renderData.endNode?.let {
                    drawCircle(color = endNodeColor, radius = strokeWidth * 3f, center = it)
                }

                val referenceFontSizeSp = if (renderedLabels.isNotEmpty()) {
                    renderedLabels.map { it.fontSize.value }.average().toFloat()
                } else {
                    12f
                }
                val baseSizePx = with(density) { referenceFontSizeSp.sp.toPx() }
                val baseIconSize = baseSizePx
                val circleRadius = (baseIconSize / 2f) * 1.5f

                renderedLabels.forEach { rendered ->
                    if (rendered.visible) {
                        val label = rendered.label
                        val textColor =
                            if (label.color == "#000000") Color.Black else labelColor
                        val textStyle = TextStyle(
                            color = textColor,
                            fontSize = rendered.fontSize,
                            fontWeight = if (label.bold) FontWeight.Bold else FontWeight.Normal
                        )
                        val measuredText = textMeasurer.measure(label.text, style = textStyle)
                        val textWidth = measuredText.size.width.toFloat()
                        val textHeight = measuredText.size.height.toFloat()
                        val centeredX = label.x - (textWidth / 2f)
                        val centeredY = label.y - (textHeight / 2f)

                        if (label.hasBackground) {
                            val padding = rendered.fontSize.toPx() * 0.3f
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = Offset(centeredX - padding, centeredY - padding),
                                size = Size(textWidth + padding * 2, textHeight + padding * 2),
                                cornerRadius = CornerRadius(padding / 2f)
                            )
                        }
                        drawText(measuredText, topLeft = Offset(centeredX, centeredY))
                    }
                }

                renderData.icons.forEach { iconLabel ->
                    val painter = iconPainters[iconLabel.icon] ?: return@forEach

                    drawCircle(
                        color = iconLabel.tint.copy(alpha = 0.1f),
                        radius = circleRadius,
                        center = Offset(iconLabel.x, iconLabel.y)
                    )

                    withTransform({
                        translate(iconLabel.x, iconLabel.y)
                        scale(1f / zoomState.scale, 1f / zoomState.scale, Offset.Zero)
                    }) {
                        val targetScreenSize = baseIconSize * zoomState.scale
                        translate(-targetScreenSize / 2f, -targetScreenSize / 2f) {
                            with(painter) {
                                draw(
                                    size = Size(targetScreenSize, targetScreenSize),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                        iconLabel.tint
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { zoomState.smoothZoomIn() } },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) { Icon(Icons.Default.Add, "Zoom In") }
            SmallFloatingActionButton(
                onClick = { scope.launch { zoomState.smoothZoomOut() } },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) { Icon(Icons.Default.Remove, "Zoom Out") }
        }
    }
}

private fun rectIntersectsLine(rect: Rect, p1: Offset, p2: Offset): Boolean {
    if (rect.contains(p1) || rect.contains(p2)) return true
    if ((p1.x < rect.left && p2.x < rect.left) ||
        (p1.x > rect.right && p2.x > rect.right) ||
        (p1.y < rect.top && p2.y < rect.top) ||
        (p1.y > rect.bottom && p2.y > rect.bottom)
    ) return false

    val left = Offset(rect.left, rect.top)
    val bottom = Offset(rect.left, rect.bottom)
    val right = Offset(rect.right, rect.bottom)
    val top = Offset(rect.right, rect.top)

    return lineIntersectsLine(p1, p2, left, bottom) ||
            lineIntersectsLine(p1, p2, bottom, right) ||
            lineIntersectsLine(p1, p2, right, top) ||
            lineIntersectsLine(p1, p2, top, left)
}

private fun lineIntersectsLine(a1: Offset, a2: Offset, b1: Offset, b2: Offset): Boolean {
    val d = (b2.y - b1.y) * (a2.x - a1.x) - (b2.x - b1.x) * (a2.y - a1.y)
    if (d == 0f) return false
    val uA = ((b2.x - b1.x) * (a1.y - b1.y) - (b2.y - b1.y) * (a1.x - b1.x)) / d
    val uB = ((a2.x - a1.x) * (a1.y - b1.y) - (a2.y - a1.y) * (a1.x - b1.x)) / d
    return uA in 0f..1f && uB in 0f..1f
}

@Composable
fun TransitionScreen(currentFlor: Int, targetFloor: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isUp = targetFloor > currentFlor
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .rotate(if (isUp) 0f else 180f),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (isUp) "Go Up To" else "Go Down To",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Floor $targetFloor",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun TransitionToBuildingScreen(
    fromBuilding: Int,
    toBuilding: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val lineColor = MaterialTheme.colorScheme.primary
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationCity,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$fromBuilding",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Canvas(modifier = Modifier.width(40.dp).height(2.dp)) {
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        pathEffect = pathEffect,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).padding(top = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationCity,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$toBuilding",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = "Now you exiting building $fromBuilding",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "and entering building $toBuilding",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun TransitionToOutDoorScreen(fromBuilding: Int) {
    val outdoorColor = Color(0xFF4CAF50)
    val lineColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        outdoorColor.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationCity,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Building $fromBuilding",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = lineColor
                )
                Canvas(modifier = Modifier.width(40.dp).height(2.dp)) {
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        pathEffect = pathEffect,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).padding(top = 4.dp),
                    tint = lineColor
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Park,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = outdoorColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Outside",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = outdoorColor
                )
            }
        }

        Spacer(Modifier.height(48.dp))
        Text(
            text = "Exit the Building",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = outdoorColor
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Continue your route outdoors",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TransitionToInDoorScreen(toBuilding: Int) {
    val buildingColor = MaterialTheme.colorScheme.primary
    val outdoorColor = Color(0xFF4CAF50)
    val lineColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        outdoorColor.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            )
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Park,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = outdoorColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Outside",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = outdoorColor
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = lineColor
                )
                Canvas(modifier = Modifier.width(40.dp).height(2.dp)) {
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        pathEffect = pathEffect,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).padding(top = 4.dp),
                    tint = lineColor
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationCity,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = buildingColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Building $toBuilding",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = buildingColor
                )
            }
        }

        Spacer(Modifier.height(48.dp))
        Text(
            text = "Enter Building $toBuilding",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = buildingColor
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Continue your route inside",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}