package com.oriooneee.jet.navigation.presentation.screen.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriooneee.jet.navigation.engine.models.FloorRenderData
import com.oriooneee.jet.navigation.engine.models.TextLabel
import com.oriooneee.jet.navigation.presentation.screen.rememberZoomState
import kotlinx.coroutines.launch

data class RenderedLabel(
    val label: TextLabel,
    val fontSize: TextUnit,
    val visible: Boolean,
    val bounds: Rect? = null
)

private data class CachedPaths(
    val polygonPaths: List<Pair<Path, Rect>>,
    val polylinePaths: List<Pair<Path, Rect>>,
    val routePath: Path?,
    val routeBounds: Rect?
)

private fun List<Offset>.toBoundingRect(): Rect {
    if (isEmpty()) return Rect.Zero
    val minX = minOf { it.x }
    val minY = minOf { it.y }
    val maxX = maxOf { it.x }
    val maxY = maxOf { it.y }
    return Rect(minX, minY, maxX, maxY)
}

private fun Rect.intersects(other: Rect): Boolean {
    return left < other.right && right > other.left &&
            top < other.bottom && bottom > other.top
}

@Composable
fun ZoomableMapCanvas(
    renderData: FloorRenderData?,
    initFocusPoint: Offset,
    routeBounds: Pair<Offset, Offset>? = null,
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
    val layerPaint = remember { Paint() }

    val iconPainters = renderData.icons.map { it.icon }.distinct()
        .associateWith { rememberVectorPainter(it) }

    // Кэшируем Path объекты - создаются один раз при изменении renderData
    val cachedPaths = remember(renderData) {
        val polygonPaths = renderData.polygons.mapNotNull { points ->
            if (points.isEmpty()) return@mapNotNull null
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                close()
            }
            path to points.toBoundingRect()
        }

        val polylinePaths = renderData.polylines.mapNotNull { points ->
            if (points.isEmpty()) return@mapNotNull null
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            path to points.toBoundingRect()
        }

        val routePath = if (renderData.routePath.isNotEmpty()) {
            Path().apply {
                val start = renderData.routePath.first()
                moveTo(start.x, start.y)
                for (i in 1 until renderData.routePath.size) {
                    lineTo(renderData.routePath[i].x, renderData.routePath[i].y)
                }
            }
        } else null

        val routeBounds = routePath?.getBounds()

        CachedPaths(polygonPaths, polylinePaths, routePath, routeBounds)
    }

    // Кэшируем bounds для singleLines
    val singleLineBounds = remember(renderData) {
        renderData.singleLines.map { (start, end) ->
            Rect(
                minOf(start.x, end.x),
                minOf(start.y, end.y),
                maxOf(start.x, end.x),
                maxOf(start.y, end.y)
            )
        }
    }

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

        LaunchedEffect(renderData, initFocusPoint, routeBounds) {
            zoomState.updateContainerSize(containerSize)
            when {
                routeBounds != null -> {
                    zoomState.fitToBounds(
                        topLeft = routeBounds.first,
                        bottomRight = routeBounds.second,
                        contentSize = contentSize,
                        paddingFraction = 0.1f
                    )
                }
                initFocusPoint != Offset.Zero -> {
                    zoomState.zoomToPoint(initFocusPoint, contentSize, 3f)
                }
                else -> {
                    zoomState.resetToFit(contentSize)
                }
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
            // Вычисляем видимую область в координатах контента
            val viewportInContent = Rect(
                left = -zoomState.offsetX / zoomState.scale,
                top = -zoomState.offsetY / zoomState.scale,
                right = (size.width - zoomState.offsetX) / zoomState.scale,
                bottom = (size.height - zoomState.offsetY) / zoomState.scale
            )

            withTransform({
                translate(left = zoomState.offsetX, top = zoomState.offsetY)
                scale(scaleX = zoomState.scale, scaleY = zoomState.scale, pivot = Offset.Zero)
            }) {
                val strokeWidth = maxOf(1f, contentSize.height * 0.001f) / 2

                // Рисуем только полигоны в видимой области
                cachedPaths.polygonPaths.forEach { (path, bounds) ->
                    if (bounds.intersects(viewportInContent)) {
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

                // Рисуем только polylines в видимой области
                cachedPaths.polylinePaths.forEach { (path, bounds) ->
                    if (bounds.intersects(viewportInContent)) {
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

                // Рисуем только линии в видимой области
                renderData.singleLines.forEachIndexed { index, (start, end) ->
                    if (singleLineBounds[index].intersects(viewportInContent)) {
                        drawLine(
                            color = planColor,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                val routePathObj = cachedPaths.routePath
                val pathBounds = cachedPaths.routeBounds

                if (routePathObj != null && pathBounds != null && pathBounds.intersects(viewportInContent)) {
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
                            val startPt = renderData.routePath[i]
                            val endPt = renderData.routePath[i + 1]

                            val dx = endPt.x - startPt.x
                            val dy = endPt.y - startPt.y
                            val segmentLength = kotlin.math.sqrt(dx * dx + dy * dy)
                            val currentAngle =
                                kotlin.math.atan2(dy, dx) * (180 / kotlin.math.PI.toFloat())

                            var startBuffer = safeCornerDistance
                            if (i > 0) {
                                val prevStart = renderData.routePath[i - 1]
                                val prevDx = startPt.x - prevStart.x
                                val prevDy = startPt.y - prevStart.y
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
                                val nextDx = nextEnd.x - endPt.x
                                val nextDy = nextEnd.y - endPt.y
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
                                    val posX = startPt.x + dx * fraction
                                    val posY = startPt.y + dy * fraction

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
                                            blendMode = BlendMode.Clear,
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

                // Рисуем start/end ноды только если в видимой области
                renderData.startNode?.let {
                    if (viewportInContent.contains(it)) {
                        drawCircle(color = startNodeColor, radius = strokeWidth * 3f, center = it)
                    }
                }
                renderData.endNode?.let {
                    if (viewportInContent.contains(it)) {
                        drawCircle(color = endNodeColor, radius = strokeWidth * 3f, center = it)
                    }
                }

                val referenceFontSizeSp = if (renderedLabels.isNotEmpty()) {
                    renderedLabels.map { it.fontSize.value }.average().toFloat()
                } else {
                    12f
                }
                val baseSizePx = with(density) { referenceFontSizeSp.sp.toPx() }
                val baseIconSize = baseSizePx
                val circleRadius = (baseIconSize / 2f) * 1.5f
                val labelBuffer = 100f // буфер для размера текста

                renderedLabels.forEach { rendered ->
                    if (rendered.visible) {
                        val label = rendered.label
                        val labelBounds = Rect(
                            label.x - labelBuffer,
                            label.y - labelBuffer,
                            label.x + labelBuffer,
                            label.y + labelBuffer
                        )
                        if (!labelBounds.intersects(viewportInContent)) return@forEach

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
                    // Проверяем видимость иконки
                    val iconBounds = Rect(
                        iconLabel.x - circleRadius,
                        iconLabel.y - circleRadius,
                        iconLabel.x + circleRadius,
                        iconLabel.y + circleRadius
                    )
                    if (!iconBounds.intersects(viewportInContent)) return@forEach

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
                                    colorFilter = ColorFilter.tint(iconLabel.tint)
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
