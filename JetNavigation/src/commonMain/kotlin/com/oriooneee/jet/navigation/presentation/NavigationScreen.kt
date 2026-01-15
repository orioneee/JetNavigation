package com.oriooneee.jet.navigation.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriooneee.jet.navigation.TextLabel
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.Node

@Composable
expect fun rememberVectorSvgPainter(bytes: ByteArray): Painter

@Composable
fun rememberZoomState(minScale: Float = 0.1f, maxScale: Float = 10f) =
    remember { ZoomState(minScale, maxScale) }

class ZoomState(private val minScale: Float, private val maxScale: Float) {
    var scale by mutableStateOf(1f); private set
    var offsetX by mutableStateOf(0f); private set
    var offsetY by mutableStateOf(0f); private set
    private var containerSize = Size.Zero

    fun updateContainerSize(size: Size) {
        containerSize = size
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
        offsetX = newOffset.x; offsetY = newOffset.y
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

    fun zoomIn() = zoomBy(1.5f)
    fun zoomOut() = zoomBy(0.66f)
    private fun zoomBy(factor: Float) {
        val center = Offset(containerSize.width / 2, containerSize.height / 2)
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val actualZoomFactor = newScale / scale
        val finalOffset =
            Offset(offsetX, offsetY) + (center - Offset(offsetX, offsetY)) * (1 - actualZoomFactor)
        scale = newScale; offsetX = finalOffset.x; offsetY = finalOffset.y
    }
}

fun parseColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    val color = cleanHex.toLongOrNull(16) ?: 0x000000
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    return Color(r, g, b)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class)
@Composable
fun NavigationScreen(
    startNode: Node?,
    endNode: Node?,
    onSelectStart: () -> Unit,
    onSelectEnd: () -> Unit,
    onSwapNodes: () -> Unit,
    viewModel: NavigationViewModel = remember { NavigationViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()

    val planStrokeColor = MaterialTheme.colorScheme.onSurface
    val routeColor = MaterialTheme.colorScheme.primary
    val startNodeColor = MaterialTheme.colorScheme.tertiary
    val endNodeColor = MaterialTheme.colorScheme.error

    LaunchedEffect(startNode, endNode) {
        viewModel.onStartNodeSelected(startNode)
        viewModel.onEndNodeSelected(endNode)
        if (startNode != null && endNode != null && startNode != endNode) {
            viewModel.calculateRoute(
                planStrokeColor,
                routeColor,
                startNodeColor,
                endNodeColor
            )
        }
    }

    Scaffold {
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
                    if (uiState.currentStep != null) {
                        AnimatedContent(
                            targetState = uiState.currentStep!!,
                            transitionSpec = {
                                fadeIn(androidx.compose.animation.core.tween(400)) + scaleIn(
                                    initialScale = 0.95f
                                ) togetherWith
                                        fadeOut(androidx.compose.animation.core.tween(400))
                            },
                            label = "MapAnim"
                        ) { step ->
                            when (step) {
                                is NavigationStep.ByFlor -> {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        ZoomableSvgCanvas(
                                            svgBytes = step.image,
                                            initFocusPoint = step.pointOfInterest,
                                            textLabels = step.textLabels
                                        )
                                        FloorBadge(
                                            floorNumber = step.flor,
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(24.dp)
                            .widthIn(max = 800.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        item {
                            DestinationInputPanel(
                                startNode = startNode,
                                endNode = endNode,
                                isLoading = uiState.isLoading,
                                onSelectStart = onSelectStart,
                                onSelectEnd = onSelectEnd,
                                onSwap = onSwapNodes
                            )
                        }

                        item {
                            this@Column.AnimatedVisibility(
                                visible = uiState.navigationSteps.isNotEmpty() && !uiState.isLoading,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    NavigationControls(
                                        currentStepIndex = uiState.currentStepIndex,
                                        totalSteps = uiState.navigationSteps.size,
                                        routeStats = uiState.routeStats,
                                        onPrevious = viewModel::previousStep,
                                        onNext = viewModel::nextStep
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationInputPanel(
    startNode: Node?,
    endNode: Node?,
    isLoading: Boolean,
    onSelectStart: () -> Unit,
    onSelectEnd: () -> Unit,
    onSwap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 18.dp, bottom = 18.dp, end = 12.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Canvas(modifier = Modifier.width(2.dp).height(24.dp).padding(vertical = 4.dp)) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(center.x, 0f),
                        end = Offset(center.x, size.height),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                        strokeWidth = 4f
                    )
                }
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LocationInputRow(
                    label = "Start",
                    value = startNode?.label ?: "Start",
                    isEmpty = startNode == null,
                    onClick = onSelectStart
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LocationInputRow(
                    label = "End",
                    value = endNode?.label ?: "Destination",
                    isEmpty = endNode == null,
                    onClick = onSelectEnd
                )
            }

            IconButton(
                onClick = onSwap,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Swap",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Calculating route...", style = MaterialTheme.typography.bodySmall)
            }
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
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        routeStats?.let { stats ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val mins = stats.estimatedTimeMinutes.toInt()
                    val secs = ((stats.estimatedTimeMinutes - mins) * 60).toInt()
                    Text(
                        text = "$mins min $secs sec",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${stats.totalDistanceMeters.toInt()} meters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }

            Text(
                text = "Step ${currentStepIndex + 1} of $totalSteps",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = onNext,
                enabled = currentStepIndex < totalSteps - 1,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)
            ) {
                Text("Next")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }
}

@Composable
fun ZoomableSvgCanvas(
    svgBytes: ByteArray,
    initFocusPoint: Offset,
    textLabels: List<TextLabel>
) {
    val painter = rememberVectorSvgPainter(svgBytes)
    val zoomState = rememberZoomState()
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerSize = Size(
            with(density) { maxWidth.toPx() },
            with(density) { maxHeight.toPx() }
        )

        LaunchedEffect(painter.intrinsicSize, containerSize, initFocusPoint) {
            zoomState.updateContainerSize(containerSize)
            if (initFocusPoint != Offset.Zero) {
                zoomState.zoomToPoint(initFocusPoint, painter.intrinsicSize, 3f)
            } else {
                zoomState.resetToFit(painter.intrinsicSize)
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
        ) {
            withTransform({
                translate(left = zoomState.offsetX, top = zoomState.offsetY)
                scale(scaleX = zoomState.scale, scaleY = zoomState.scale, pivot = Offset.Zero)
            }) {
                with(painter) { draw(size = intrinsicSize) }

                textLabels.forEach { label ->
                    val textStyle = TextStyle(
                        color = parseColor(label.color),
                        fontSize = label.fontSize.sp,
                        fontWeight = if (label.bold) FontWeight.Bold else FontWeight.Normal
                    )

                    val measuredText = textMeasurer.measure(
                        text = label.text,
                        style = textStyle
                    )

                    val textWidth = measuredText.size.width.toFloat()
                    val textHeight = measuredText.size.height.toFloat()
                    val centeredX = label.x - (textWidth / 2f)
                    val centeredY = label.y - (textHeight / 2f)

                    if (label.hasBackground) {
                        val padding = label.fontSize * 0.3f
                        val rectLeft = centeredX - padding
                        val rectTop = centeredY - padding
                        val rectRight = centeredX + textWidth + padding
                        val rectBottom = centeredY + textHeight + padding

                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f),
                            topLeft = Offset(rectLeft, rectTop),
                            size = Size(rectRight - rectLeft, rectBottom - rectTop),
                            cornerRadius = CornerRadius(padding / 2f)
                        )
                    }

                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(centeredX, centeredY)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { zoomState.zoomIn() },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, "Zoom In")
            }
            SmallFloatingActionButton(
                onClick = { zoomState.zoomOut() },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Remove, "Zoom Out")
            }
        }
    }
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