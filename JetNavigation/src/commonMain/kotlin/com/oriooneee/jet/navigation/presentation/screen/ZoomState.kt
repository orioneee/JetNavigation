package com.oriooneee.jet.navigation.presentation.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

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

    fun fitToBounds(
        topLeft: Offset, bottomRight: Offset,
        paddingFraction: Float = 0.15f,
        maxZoom: Float = 4f
    ) {
        if (containerSize == Size.Zero) return

        val boundsWidth = bottomRight.x - topLeft.x
        val boundsHeight = bottomRight.y - topLeft.y
        if (boundsWidth <= 0 || boundsHeight <= 0) return

        val paddingX = containerSize.width * paddingFraction
        val paddingY = containerSize.height * paddingFraction
        val availableWidth = containerSize.width - paddingX * 2
        val availableHeight = containerSize.height - paddingY * 2

        val scaleX = availableWidth / boundsWidth
        val scaleY = availableHeight / boundsHeight
        scale = minOf(scaleX, scaleY).coerceIn(minScale, minOf(maxScale, maxZoom))

        val boundsCenter = Offset(
            (topLeft.x + bottomRight.x) / 2,
            (topLeft.y + bottomRight.y) / 2
        )
        val screenCenter = Offset(containerSize.width / 2, containerSize.height / 2)
        val newOffset = screenCenter - (boundsCenter * scale)
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
