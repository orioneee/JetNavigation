package com.oriooneee.jet.navigation

import androidx.compose.ui.geometry.Offset
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult
import com.oriooneee.jet.navigation.domain.entities.graph.Flor
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.graph.NodeType
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

    // Кэшируем граф смежности при создании
    private val adjacency: Map<String, List<Pair<String, Double>>> = buildAdjacencyMap()
    private val nodesMap: Map<String, Node> = masterNav.navGraph.nodes.associateBy { it.id }

    private fun buildAdjacencyMap(): Map<String, MutableList<Pair<String, Double>>> {
        val adj = mutableMapOf<String, MutableList<Pair<String, Double>>>()

        println("=== Построение графа смежности ===")
        println("Всего рёбер: ${masterNav.navGraph.edges.size}")

        masterNav.navGraph.edges.forEach { edge ->
            // Прямое направление
            adj.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
            // Обратное направление (двустороннее ребро)
            adj.getOrPut(edge.to) { mutableListOf() }.add(edge.from to edge.weight)
        }

        println("Узлов с рёбрами: ${adj.size}")
        adj.forEach { (nodeId, edges) ->
            println("  $nodeId -> ${edges.size} связей")
        }
        println("=================================\n")

        return adj
    }

    fun resolveSelection(
        result: SelectNodeResult,
        referenceNode: Node?
    ): Node? {
        return when (result) {
            is SelectNodeResult.SelectedNode -> result.node
            is SelectNodeResult.NearestManWC -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.type.contains(NodeType.WC_MAN)
                }
            }

            is SelectNodeResult.NearestWomanWC -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.type.contains(NodeType.WC_WOMAN)
                }
            }

            is SelectNodeResult.NearestMainEntrance -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.type.contains(NodeType.MAIN_ENTRANCE)
                }
            }
        }
    }

    fun getRoute(
        from: Node,
        to: Node
    ): NavigationDirection {
        println("\n╔═══════════════════════════════════════════════════════════════")
        println("║ ПОИСК МАРШРУТА")
        println("╠═══════════════════════════════════════════════════════════════")
        println("║ От: ${from.id} (${from.label ?: "без метки"})")
        println("║     Координаты: (${from.x}, ${from.y})")
        println("║     Здание: ${from.buildNum}, Этаж: ${from.floorNum}")
        println("║     Типы: ${from.type}")
        println("║")
        println("║ До: ${to.id} (${to.label ?: "без метки"})")
        println("║     Координаты: (${to.x}, ${to.y})")
        println("║     Здание: ${to.buildNum}, Этаж: ${to.floorNum}")
        println("║     Типы: ${to.type}")
        println("╚═══════════════════════════════════════════════════════════════\n")

        val path = findPath(from, to)

        if (path == null) {
            println("❌ МАРШРУТ НЕ НАЙДЕН!\n")
            return NavigationDirection(emptyList(), 0.0)
        }

        val totalDistance = calculateTotalDistance(path)
        println("\n✅ МАРШРУТ НАЙДЕН!")
        println("   Длина пути: ${path.size} узлов")
        println("   Общее расстояние: ${totalDistance} м")
        println("   Узлы маршрута:")
        path.forEachIndexed { index, node ->
            println("     $index. ${node.id} (${node.label ?: "?"}) - Здание ${node.buildNum}, Этаж ${node.floorNum}")
        }
        println()

        val steps = buildNavigationSteps(path)
        return NavigationDirection(steps, totalDistance)
    }

    private fun findNearestNode(
        referenceNode: Node,
        criteria: (Node) -> Boolean
    ): Node? {
        println("\n--- Поиск ближайшего узла ---")
        println("От узла: ${referenceNode.id}")

        val distances = mutableMapOf<String, Double>()
        masterNav.navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
        distances[referenceNode.id] = 0.0

        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }
        pq.offer(referenceNode.id to 0.0)

        var nodesChecked = 0
        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break
            nodesChecked++

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue

            val currentNode = nodesMap[u]
            if (currentNode != null && u != referenceNode.id && criteria(currentNode)) {
                println("Найден узел: ${currentNode.id} на расстоянии ${d} м")
                println("Проверено узлов: $nodesChecked\n")
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

        println("Узел не найден! Проверено узлов: $nodesChecked\n")
        return null
    }

    private fun findPath(start: Node, end: Node): List<Node>? {
        println("┌─ Начало алгоритма Дейкстры ─┐")

        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()

        masterNav.navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
        distances[start.id] = 0.0

        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }
        pq.offer(start.id to 0.0)

        var iterations = 0
        var nodesVisited = 0
        val visited = mutableSetOf<String>()

        while (pq.isNotEmpty()) {
            iterations++
            val (u, d) = pq.poll() ?: break

            if (u in visited) continue
            visited.add(u)
            nodesVisited++

            if (iterations <= 10 || iterations % 50 == 0) {
                println("  Итерация $iterations: узел $u, dist=${d}")
            }

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue

            if (u == end.id) {
                println("└─ Целевой узел достигнут! ─┘")
                println("   Итераций: $iterations")
                println("   Посещено узлов: $nodesVisited")
                break
            }

            val neighbors = adjacency[u]
            if (neighbors == null || neighbors.isEmpty()) {
                if (iterations <= 10) {
                    println("    ⚠️  У узла $u нет соседей!")
                }
                continue
            }

            neighbors.forEach { (v, weight) ->
                val node = nodesMap[v]
                if (node != null) {
                    val shouldSkip = if(node.type.contains(NodeType.AUDITORIUM)){
                        val audNum = node.label?.filter { it.isDigit() }?.toIntOrNull()
                        val startAudNum = start.label?.filter { it.isDigit() }?.toIntOrNull()
                        val endAudNum = end.label?.filter { it.isDigit() }?.toIntOrNull()
                        if(audNum == null || startAudNum == null || endAudNum == null){
                            false
                        } else{
                            audNum != startAudNum && audNum != endAudNum
                        }
                    } else{
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

        println("└─ Завершено ─┘")
        println("   Всего итераций: $iterations")
        println("   Посещено узлов: $nodesVisited")

        if (distances[end.id] == Double.MAX_VALUE) {
            println("\n❌ Путь не найден!")
            println("   Конечный узел недостижим из начального")
            println("   Расстояние до конечного узла: ${distances[end.id]}")

            // Диагностика
            println("\n🔍 Диагностика:")
            println("   Начальный узел ${start.id} имеет ${adjacency[start.id]?.size ?: 0} соседей")
            println("   Конечный узел ${end.id} имеет ${adjacency[end.id]?.size ?: 0} соседей")

            return null
        }

        // Восстановление пути
        val path = mutableListOf<Node>()
        var current: String? = end.id
        var pathLength = 0

        while (current != null) {
            nodesMap[current]?.let { path.add(it) }
            pathLength++
            current = previous[current]
            if (current == start.id) {
                nodesMap[start.id]?.let { path.add(it) }
                pathLength++
                break
            }
            if (pathLength > 10000) {
                println("⚠️  Обнаружен цикл при восстановлении пути!")
                return null
            }
        }

        return path.reversed()
    }

    private fun calculateTotalDistance(path: List<Node>): Double {
        var distance = 0.0
        for (i in 0 until path.size - 1) {
            val u = path[i]
            val v = path[i + 1]
            val edge = masterNav.navGraph.edges.find {
                (it.from == u.id && it.to == v.id) || (it.to == u.id && it.from == v.id)
            }
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

        val segments = groupPathByLocation(fullPath)

        val visibleSegments = segments.filter { segment ->
            segment.nodes.any { !it.type.contains(NodeType.STAIRS) }
        }

        val globalStartNode = fullPath.first()
        val globalEndNode = fullPath.last()


        visibleSegments.forEachIndexed { index, segment ->

            if (index > 0) {
                val prevSegment = visibleSegments[index - 1]

                if (prevSegment.buildingNum != segment.buildingNum) {
                    steps.add(
                        NavigationStep.TransitionToBuilding(
                            form = prevSegment.buildingNum,
                            to = segment.buildingNum
                        )
                    )
                } else if (prevSegment.floorNum != segment.floorNum) {
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


            if (flor != null) {
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

        return steps
    }

    private fun groupPathByLocation(path: List<Node>): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        if (path.isEmpty()) return segments

        var currentNodes = mutableListOf<Node>()
        var (currentBuilding, currentFloor) = path.first().buildNum to path.first().floorNum

        path.forEach { node ->
            val (nodeBuilding, nodeFloor) = node.buildNum to node.floorNum

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
            if (!node.type.contains(NodeType.STAIRS)) {
                routePoints.add(Offset(tx(node.x), ty(node.y)))
            }
        }

        var startNodeOffset: Offset? = null
        var endNodeOffset: Offset? = null

        if (stepPath.isNotEmpty() && stepPath.first().id == globalStart.id) {
            val start = stepPath.first()
            if (!start.type.contains(NodeType.STAIRS)) {
                startNodeOffset = Offset(tx(start.x), ty(start.y))
            }
        }
        if (stepPath.isNotEmpty() && stepPath.last().id == globalEnd.id) {
            val end = stepPath.last()
            if (!end.type.contains(NodeType.STAIRS)) {
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