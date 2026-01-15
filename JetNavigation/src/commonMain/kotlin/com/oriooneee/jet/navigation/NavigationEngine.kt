package com.oriooneee.jet.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.UniversityNavGraph
import com.oriooneee.jet.navigation.domain.entities.plan.Flor
import com.oriooneee.jet.navigation.domain.entities.plan.UniversityPlan
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class TextLabel(
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val color: String = "#000000",
    val bold: Boolean = false,
    val hasBackground: Boolean = false
)

data class FloorRenderData(
    val width: Float,
    val height: Float,
    val polygons: List<List<Offset>>,
    val polylines: List<List<Offset>>,
    val singleLines: List<Pair<Offset, Offset>>,
    val routePath: List<Offset>,
    val startNode: Offset?,
    val endNode: Offset?,
    val textLabels: List<TextLabel>
)

class NavigationEngine(
    private val navGraph: UniversityNavGraph,
    private val plan: UniversityPlan,
) {
    private val outputWidth = 2000.0
    private val paddingPct = 0.05
    private val zToFloor = mutableMapOf<Double, Int>()

    init {
        val floorZSamples = mutableMapOf<Int, MutableList<Double>>()

        navGraph.nodes.forEach { node ->
            if (node.id.contains("_FLOR_")) {
                val parts = node.id.split("_FLOR_")
                if (parts.size > 1) {
                    val floorStr = parts[1].split("_")[0]
                    val floorNum = floorStr.toIntOrNull()
                    if (floorNum != null) {
                        floorZSamples.getOrPut(floorNum) { mutableListOf() }.add(node.z)
                    }
                }
            }
        }

        val floorMeans = floorZSamples.mapValues { (_, zList) ->
            zList.average()
        }

        navGraph.nodes.forEach { node ->
            if (floorMeans.isNotEmpty()) {
                val closestFloor = floorMeans.minByOrNull { (_, meanZ) ->
                    abs(meanZ - node.z)
                }?.key ?: 1
                zToFloor[node.z] = closestFloor
            } else {
                zToFloor[node.z] = 1
            }
        }
    }

    fun getRoute(
        from: Node,
        to: Node
    ): NavigationDirection {
        val path = findPath(from, to) ?: return NavigationDirection(emptyList(), 0.0)
        val totalDistance = calculateTotalDistance(path)
        val steps = buildNavigationSteps(path)
        return NavigationDirection(steps, totalDistance)
    }

    private fun findPath(start: Node, end: Node): List<Node>? {
        val adjacency = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        navGraph.edges.forEach { edge ->
            adjacency.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
        }

        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()
        val nodesMap = navGraph.nodes.associateBy { it.id }

        navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
        distances[start.id] = 0.0

        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }
        pq.offer(start.id to 0.0)

        val startClean = start.id.filter { it.isLetterOrDigit() }
        val endClean = end.id.filter { it.isLetterOrDigit() }

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue
            if (u == end.id) break

            adjacency[u]?.forEach { (v, weight) ->
                val node = nodesMap[v]
                if (node != null) {
                    val shouldSkip = if (node.id.contains("AUD")) {
                        val nodeClean = node.id.filter { it.isLetterOrDigit() }
                        node.id != start.id &&
                                node.id != end.id &&
                                nodeClean != startClean &&
                                nodeClean != endClean
                    } else {
                        false
                    }

                    if (!shouldSkip) {
                        val alt = d + weight
                        if (alt < (distances[v] ?: Double.MAX_VALUE)) {
                            distances[v] = alt
                            previous[v] = u
                            pq.offer(v to alt)
                        }
                    }
                }
            }
        }

        if (distances[end.id] == Double.MAX_VALUE) return null

        val path = mutableListOf<Node>()
        var current: String? = end.id
        while (current != null) {
            nodesMap[current]?.let { path.add(it) }
            current = previous[current]
            if (current == start.id) {
                nodesMap[start.id]?.let { path.add(it) }
                break
            }
        }
        return path.reversed()
    }

    private fun calculateTotalDistance(path: List<Node>): Double {
        var distance = 0.0
        for (i in 0 until path.size - 1) {
            val u = path[i]
            val v = path[i + 1]
            val edge = navGraph.edges.find { it.from == u.id && it.to == v.id }
            distance += edge?.weight ?: 0.0
        }
        return distance
    }

    private fun buildNavigationSteps(fullPath: List<Node>): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()
        if (fullPath.isEmpty()) return steps

        val globalStartNode = fullPath.first()
        val globalEndNode = fullPath.last()

        val floorGroups = groupPathByFloor(fullPath)

        val visibleFloorGroups = floorGroups.filter { (_, nodes) ->
            nodes.any { !it.id.contains("STAIRS") }
        }

        visibleFloorGroups.forEachIndexed { index, (floorNum, stepNodes) ->
            if (index > 0) {
                val previousFloor = visibleFloorGroups[index - 1].first
                steps.add(
                    NavigationStep.TransitionToFlor(
                        to = floorNum,
                        from = previousFloor
                    )
                )
            }

            val florData = when (floorNum) {
                1 -> plan.flor1
                2 -> plan.flor2
                3 -> plan.flor3
                4 -> plan.flor4
                else -> plan.flor1
            }

            val renderData = generateFloorData(
                flor = florData,
                stepPath = stepNodes,
                globalStart = globalStartNode,
                globalEnd = globalEndNode
            )

            val focusPoint = renderData.startNode ?: renderData.routePath.firstOrNull() ?: Offset.Zero

            steps.add(NavigationStep.ByFlor(floorNum, renderData, focusPoint))
        }

        return steps
    }

    private fun groupPathByFloor(path: List<Node>): List<Pair<Int, List<Node>>> {
        val groups = mutableListOf<Pair<Int, List<Node>>>()
        if (path.isEmpty()) return groups

        var currentNodes = mutableListOf<Node>()
        var currentFloor = getFloorByZ(path.first().z)

        path.forEach { node ->
            val nodeFloor = getFloorByZ(node.z)

            if (nodeFloor != currentFloor) {
                if (currentNodes.isNotEmpty()) {
                    groups.add(currentFloor to ArrayList(currentNodes))
                }
                currentNodes = mutableListOf()
                currentFloor = nodeFloor
            }
            currentNodes.add(node)
        }

        if (currentNodes.isNotEmpty()) {
            groups.add(currentFloor to currentNodes)
        }
        return groups
    }

    private fun getFloorByZ(z: Double): Int {
        return zToFloor[z] ?: 1
    }

    private fun generateFloorData(
        flor: Flor,
        stepPath: List<Node>,
        globalStart: Node,
        globalEnd: Node
    ): FloorRenderData {
        val allX = mutableListOf<Double>()
        val allY = mutableListOf<Double>()

        flor.polylines.forEach { p -> p.points.forEach { pt -> allX.add(pt[0]); allY.add(pt[1]) } }
        flor.lines.forEach { l -> allX.add(l.x1); allX.add(l.x2); allY.add(l.y1); allY.add(l.y2) }
        flor.texts.forEach { t -> allX.add(t.x); allY.add(t.y) }

        if (allX.isEmpty()) return FloorRenderData(1f, 1f, emptyList(), emptyList(), emptyList(), emptyList(), null, null, emptyList())

        val minX = allX.minOrNull() ?: 0.0
        val maxX = allX.maxOrNull() ?: 1.0
        val minY = allY.minOrNull() ?: 0.0
        val maxY = allY.maxOrNull() ?: 1.0

        val dataW = maxX - minX
        val dataH = maxY - minY

        if (dataW == 0.0 || dataH == 0.0) return FloorRenderData(1f, 1f, emptyList(), emptyList(), emptyList(), emptyList(), null, null, emptyList())

        val drawWidth = outputWidth * (1 - paddingPct * 2)
        val scale = drawWidth / dataW
        val outputHeight = (dataH * scale + (outputWidth * paddingPct * 2)).toInt()
        val padding = outputWidth * paddingPct

        fun tx(x: Double): Float = ((x - minX) * scale + padding).toFloat()
        fun ty(y: Double): Float = (outputHeight - ((y - minY) * scale + padding)).toFloat()

        val polygons = mutableListOf<List<Offset>>()
        val polylines = mutableListOf<List<Offset>>()

        flor.polylines.forEach { poly ->
            val points = poly.points.map { Offset(tx(it[0]), ty(it[1])) }
            if (poly.closed) {
                polygons.add(points)
            } else {
                polylines.add(points)
            }
        }

        val singleLines = flor.lines.map { l ->
            Pair(Offset(tx(l.x1), ty(l.y1)), Offset(tx(l.x2), ty(l.y2)))
        }

        val routePoints = mutableListOf<Offset>()
        stepPath.forEach { node ->
            if (!node.id.contains("STAIRS")) {
                routePoints.add(Offset(tx(node.x), ty(node.y)))
            }
        }

        var startNodeOffset: Offset? = null
        var endNodeOffset: Offset? = null

        if (stepPath.isNotEmpty() && stepPath.first().id == globalStart.id) {
            val start = stepPath.first()
            if (!start.id.contains("STAIRS")) {
                startNodeOffset = Offset(tx(start.x), ty(start.y))
            }
        }
        if (stepPath.isNotEmpty() && stepPath.last().id == globalEnd.id) {
            val end = stepPath.last()
            if (!end.id.contains("STAIRS")) {
                endNodeOffset = Offset(tx(end.x), ty(end.y))
            }
        }

        val strokeBase = max(1.0, outputHeight * 0.001)
        val textLabels = mutableListOf<TextLabel>()

        val boundaryMargin = drawWidth * 0.05

        flor.texts.forEach { txt ->
            var clean = txt.text
            val sbClean = StringBuilder()
            for (char in clean) {
                if (!char.isISOControl()) sbClean.append(char)
            }
            clean = sbClean.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .trim()

            if (clean.isNotEmpty()) {
                val cx = tx(txt.x)
                val cy = ty(txt.y)

                val isNearEdge = cx < boundaryMargin || cx > (drawWidth - boundaryMargin) ||
                        cy < boundaryMargin || cy > (outputHeight - boundaryMargin)

                val baseSize = (strokeBase * 6).toFloat()
                var finalSize = baseSize

                if (clean.length > 7 || isNearEdge) {
                    finalSize = baseSize * 0.7f
                }
                if (isNearEdge && clean.length > 7) {
                    finalSize = baseSize * 0.5f
                }

                textLabels.add(
                    TextLabel(
                        text = clean,
                        x = cx,
                        y = cy,
                        fontSize = finalSize,
                        color = "#666666"
                    )
                )
            }
        }

        return FloorRenderData(
            width = outputWidth.toFloat(),
            height = outputHeight.toFloat(),
            polygons = polygons,
            polylines = polylines,
            singleLines = singleLines,
            routePath = routePoints,
            startNode = startNodeOffset,
            endNode = endNodeOffset,
            textLabels = textLabels
        )
    }

    private fun Double.round(decimals: Int): String {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        val rounded = (this * multiplier).roundToInt() / multiplier
        return rounded.toString()
    }
}

private class MinHeap<T>(private val comparator: Comparator<T>) {
    private val heap = ArrayList<T>()

    fun isNotEmpty(): Boolean = heap.isNotEmpty()

    fun offer(element: T) {
        heap.add(element)
        siftUp(heap.size - 1)
    }

    fun poll(): T? {
        if (heap.isEmpty()) return null
        val result = heap[0]
        val last = heap.removeAt(heap.size - 1)
        if (heap.isNotEmpty()) {
            heap[0] = last
            siftDown(0)
        }
        return result
    }

    private fun siftUp(index: Int) {
        var k = index
        while (k > 0) {
            val parent = (k - 1) / 2
            if (comparator.compare(heap[k], heap[parent]) >= 0) break
            swap(k, parent)
            k = parent
        }
    }

    private fun siftDown(index: Int) {
        var k = index
        val half = heap.size / 2
        while (k < half) {
            var child = 2 * k + 1
            val right = child + 1
            if (right < heap.size && comparator.compare(heap[right], heap[child]) < 0) {
                child = right
            }
            if (comparator.compare(heap[k], heap[child]) <= 0) break
            swap(k, child)
            k = child
        }
    }

    private fun swap(i: Int, j: Int) {
        val temp = heap[i]
        heap[i] = heap[j]
        heap[j] = temp
    }
}