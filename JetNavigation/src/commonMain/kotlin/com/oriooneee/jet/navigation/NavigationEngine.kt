package com.oriooneee.jet.navigation

import androidx.compose.ui.geometry.Offset
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult
import com.oriooneee.jet.navigation.domain.entities.graph.Flor
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import kotlinx.serialization.Serializable

@Serializable
data class TextLabel(
    val text: String,
    val x: Float,
    val y: Float,
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

class NavigationEngine(private val masterNav: MasterNavigation) {
    private val outputWidth = 2000.0
    private val paddingPct = 0.05
    private val buildingRegex = Regex("_b_(\\d+)", RegexOption.IGNORE_CASE)
    private val audRegex = Regex("AUD_(\\d)(\\d)")

    fun resolveSelection(
        result: SelectNodeResult,
        referenceNode: Node?
    ): Node? {
        return when (result) {
            is SelectNodeResult.SelectedNode -> result.node
            is SelectNodeResult.NearestManWC -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.id.contains("WC", ignoreCase = true) &&
                            (it.id.contains("MAN", ignoreCase = true) || it.id.contains(
                                "_M_",
                                ignoreCase = true
                            ))
                }
            }

            is SelectNodeResult.NearestWomanWC -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.id.contains("WC", ignoreCase = true) &&
                            (it.id.contains("WOMAN", ignoreCase = true) || it.id.contains(
                                "_W_",
                                ignoreCase = true
                            ))
                }
            }

            is SelectNodeResult.NearestMainEntrance -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    (it.id.contains("ENTER", ignoreCase = true) || it.id.contains(
                        "EXIT",
                        ignoreCase = true
                    )) &&
                            !it.id.contains("TO_BUILDING", ignoreCase = true)
                }
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

    private fun findNearestNode(
        referenceNode: Node,
        criteria: (Node) -> Boolean
    ): Node? {
        val adjacency = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        masterNav.navGraph.edges.forEach { edge ->
            adjacency.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
        }

        val distances = mutableMapOf<String, Double>()
        val nodesMap = masterNav.navGraph.nodes.associateBy { it.id }

        masterNav.navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
        distances[referenceNode.id] = 0.0

        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }
        pq.offer(referenceNode.id to 0.0)

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue

            val currentNode = nodesMap[u]
            if (currentNode != null && u != referenceNode.id && criteria(currentNode)) {
                return currentNode
            }

            adjacency[u]?.forEach { (v, weight) ->
                val alt = d + weight
                if (alt < (distances[v] ?: Double.MAX_VALUE)) {
                    distances[v] = alt
                    pq.offer(v to alt)
                }
            }
        }
        return null
    }

    private fun findPath(start: Node, end: Node): List<Node>? {
        val adjacency = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        masterNav.navGraph.edges.forEach { edge ->
            adjacency.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
        }

        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()
        val nodesMap = masterNav.navGraph.nodes.associateBy { it.id }

        masterNav.navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
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
            val edge = masterNav.navGraph.edges.find { it.from == u.id && it.to == v.id }
            distance += edge?.weight ?: 0.0
        }
        return distance
    }

    private data class PathSegment(
        val buildingNum: Int,
        val floorNum: Int,
        val nodes: List<Node>
    )

    private fun buildNavigationSteps(fullPath: List<Node>): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()
        if (fullPath.isEmpty()) return steps

        println(
            fullPath.joinToString(" ->\n") {
                val (b, f) = getNodeLocation(it)
                "(${it.id}|B$b|F$f)"
            }
        )

        val segments = groupPathByLocation(fullPath)

        println("Segments before filtering:")
        segments.forEachIndexed { index, seg ->
            println("  Segment $index: B${seg.buildingNum} F${seg.floorNum}, nodes=${seg.nodes.size}, hasNonStairs=${seg.nodes.any { !it.id.contains("STAIRS") }}")
        }

        val visibleSegments = segments.filter { segment ->
            segment.nodes.any { !it.id.contains("STAIRS") }
        }

        println("Segments after filtering: ${visibleSegments.size}")

        val globalStartNode = fullPath.first()
        val globalEndNode = fullPath.last()

        println("Available buildings: ${masterNav.buildings.map { "B${it.num}(floors: ${it.flors.map { f -> f.num }})" }}")

        visibleSegments.forEachIndexed { index, segment ->
            println("Processing segment $index: B${segment.buildingNum} F${segment.floorNum}")

            if (index > 0) {
                val prevSegment = visibleSegments[index - 1]

                if (prevSegment.buildingNum != segment.buildingNum) {
                    println("  Adding TransitionToBuilding from B${prevSegment.buildingNum} to B${segment.buildingNum}")
                    steps.add(
                        NavigationStep.TransitionToBuilding(
                            form = prevSegment.buildingNum,
                            to = segment.buildingNum
                        )
                    )
                } else if (prevSegment.floorNum != segment.floorNum) {
                    println("  Adding TransitionToFlor from F${prevSegment.floorNum} to F${segment.floorNum}")
                    steps.add(
                        NavigationStep.TransitionToFlor(
                            to = segment.floorNum,
                            from = prevSegment.floorNum
                        )
                    )
                }
            }

            val building = masterNav.buildings.find { it.num == segment.buildingNum }
            val flor = building?.flors?.find { it.num == segment.floorNum }

            println("  Looking for B${segment.buildingNum} F${segment.floorNum}: building=$building, flor=$flor")

            if (flor != null) {
                println("  Adding ByFlor for F${segment.floorNum}")
                val renderData = generateFloorData(
                    flor = flor,
                    stepPath = segment.nodes,
                    globalStart = globalStartNode,
                    globalEnd = globalEndNode
                )

                val focusPoint =
                    renderData.startNode ?: renderData.routePath.firstOrNull() ?: Offset.Zero

                steps.add(
                    NavigationStep.ByFlor(
                        flor = segment.floorNum,
                        image = renderData,
                        pointOfInterest = focusPoint,
                        textLabels = renderData.textLabels
                    )
                )
            } else {
                println("  WARNING: Floor not found! Skipping ByFlor step.")
            }
        }

        return steps.also {
            println("Steps: ${it.joinToString(", ") { step ->
                when(step) {
                    is NavigationStep.ByFlor -> "ByFlor(F${step.flor})"
                    is NavigationStep.TransitionToBuilding -> "ToBuilding(B${step.to})"
                    is NavigationStep.TransitionToFlor -> "ToFlor(F${step.to})"
                }
            }}")
        }
    }

    private fun groupPathByLocation(path: List<Node>): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        if (path.isEmpty()) return segments

        var currentNodes = mutableListOf<Node>()
        var (currentBuilding, currentFloor) = getNodeLocation(path.first())

        path.forEach { node ->
            val (nodeBuilding, nodeFloor) = getNodeLocation(node)

            if (nodeBuilding != currentBuilding || nodeFloor != currentFloor) {
                if (currentNodes.isNotEmpty()) {
                    segments.add(
                        PathSegment(
                            currentBuilding,
                            currentFloor,
                            ArrayList(currentNodes)
                        )
                    )
                }
                currentNodes = mutableListOf()
                currentBuilding = nodeBuilding
                currentFloor = nodeFloor
            }
            currentNodes.add(node)
        }

        if (currentNodes.isNotEmpty()) {
            segments.add(PathSegment(currentBuilding, currentFloor, currentNodes))
        }
        return segments
    }

    /**
     * Определяет местоположение ноды: корпус и этаж
     * Приоритеты определения корпуса:
     * 1. Суффикс _b_X в id (например, NODE_b_2 -> корпус 2)
     * 2. Regex AUD_XY (X - корпус, Y - этаж)
     * 3. Fallback эвристики по содержимому id и координатам
     */
    private fun getNodeLocation(node: Node): Pair<Int, Int> {
        var building = 2  // По умолчанию корпус 2
        var floor = 1     // По умолчанию этаж 1

        // Приоритет 1: Проверяем суффикс _b_X
        val buildingMatch = buildingRegex.find(node.id)
        if (buildingMatch != null) {
            building = buildingMatch.groupValues[1].toIntOrNull() ?: 2
            println("Node ${node.id}: найден суффикс _b_$building")
        } else {
            // Приоритет 2: Проверяем формат AUD_XY
            val audMatch = audRegex.find(node.id)
            if (audMatch != null) {
                building = audMatch.groupValues[1].toInt()
                floor = audMatch.groupValues[2].toInt()
                println("Node ${node.id}: найден AUD формат -> B$building F$floor")
                return building to floor
            } else {
                // Приоритет 3: Fallback эвристики
                if (node.id.contains("_5_", ignoreCase = true) ||
                    node.id.contains("BUILDING_5", ignoreCase = true) ||
                    node.z > 1.0) {
                    building = 5
                    println("Node ${node.id}: определён как корпус 5 по содержимому/координатам")
                }
            }
        }

        // Определение этажа
        if (building == 5) {
            floor = when {
                node.z > 12.0 -> 4
                node.z > 9.0 -> 3
                node.z > 5.0 -> 2
                else -> 1
            }
        } else {
            // Для других корпусов пытаемся определить этаж по label
            floor = when {
                node.label?.contains("поверх 3", ignoreCase = true) == true -> 3
                node.label?.contains("поверх 2", ignoreCase = true) == true -> 2
                node.label?.contains("поверх 1", ignoreCase = true) == true -> 1
                else -> 1
            }
        }

        return building to floor
    }

    private fun generateFloorData(
        flor: Flor,
        stepPath: List<Node>,
        globalStart: Node,
        globalEnd: Node
    ): FloorRenderData {
        val allX = mutableListOf<Double>()
        val allY = mutableListOf<Double>()

        flor.plan.polylines.forEach { p ->
            p.points.forEach { pt ->
                allX.add(pt[0])
                allY.add(pt[1])
            }
        }
        flor.plan.lines.forEach { l ->
            allX.add(l.x1)
            allX.add(l.x2)
            allY.add(l.y1)
            allY.add(l.y2)
        }
        flor.plan.texts.forEach { t ->
            allX.add(t.x)
            allY.add(t.y)
        }

        if (allX.isEmpty()) {
            return FloorRenderData(
                1f, 1f,
                emptyList(), emptyList(), emptyList(), emptyList(),
                null, null, emptyList()
            )
        }

        val minX = allX.minOrNull() ?: 0.0
        val maxX = allX.maxOrNull() ?: 1.0
        val minY = allY.minOrNull() ?: 0.0
        val maxY = allY.maxOrNull() ?: 1.0

        val dataW = maxX - minX
        val dataH = maxY - minY

        if (dataW == 0.0 || dataH == 0.0) {
            return FloorRenderData(
                1f, 1f,
                emptyList(), emptyList(), emptyList(), emptyList(),
                null, null, emptyList()
            )
        }

        val drawWidth = outputWidth * (1 - paddingPct * 2)
        val scale = drawWidth / dataW
        val outputHeight = (dataH * scale + (outputWidth * paddingPct * 2)).toInt()
        val padding = outputWidth * paddingPct

        fun tx(x: Double): Float = ((x - minX) * scale + padding).toFloat()
        fun ty(y: Double): Float = (outputHeight - ((y - minY) * scale + padding)).toFloat()

        val polygons = mutableListOf<List<Offset>>()
        val polylines = mutableListOf<List<Offset>>()

        flor.plan.polylines.forEach { poly ->
            val points = poly.points.map { Offset(tx(it[0]), ty(it[1])) }
            if (poly.closed) {
                polygons.add(points)
            } else {
                polylines.add(points)
            }
        }

        val singleLines = flor.plan.lines.map { l ->
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

        val textLabels = mutableListOf<TextLabel>()

        flor.plan.texts.forEach { txt ->
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

                textLabels.add(
                    TextLabel(
                        text = clean,
                        x = cx,
                        y = cy,
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
}