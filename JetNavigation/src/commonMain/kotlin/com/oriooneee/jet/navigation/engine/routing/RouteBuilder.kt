package com.oriooneee.jet.navigation.engine.routing

import com.oriooneee.jet.navigation.domain.entities.Coordinates
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.graph.NodeType
import com.oriooneee.jet.navigation.engine.models.ResolvedNode
import com.oriooneee.jet.navigation.engine.rendering.FloorRenderer

class RouteBuilder(
    private val masterNav: MasterNavigation,
    private val floorRenderer: FloorRenderer
) {
    fun buildStepsFromUnifiedPath(path: List<ResolvedNode>): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()
        if (path.isEmpty()) return steps

        val segments = mutableListOf<List<ResolvedNode>>()
        var currentSegment = mutableListOf<ResolvedNode>()

        path.forEachIndexed { index, node ->
            if (index == 0) {
                currentSegment.add(node)
            } else {
                val prev = path[index - 1]
                val isSplit = when {
                    prev is ResolvedNode.InDoor && node is ResolvedNode.OutDoor -> true
                    prev is ResolvedNode.OutDoor && node is ResolvedNode.InDoor -> true
                    prev is ResolvedNode.InDoor && node is ResolvedNode.InDoor -> {
                        prev.node.buildNum != node.node.buildNum || prev.node.floorNum != node.node.floorNum
                    }
                    else -> false
                }

                if (isSplit) {
                    segments.add(ArrayList(currentSegment))
                    currentSegment = mutableListOf()
                }
                currentSegment.add(node)
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
        }

        val globalStart = path.first()
        val globalEnd = path.last()

        var lastRenderedFloor: Int? = null

        segments.forEachIndexed { i, segment ->
            val firstNode = segment.first()
            val indoorNodes = segment.mapNotNull { (it as? ResolvedNode.InDoor)?.node }
            val isSingleIndoorPoint = firstNode is ResolvedNode.InDoor && indoorNodes.size <= 1
            val isStairsOnlyFloor = indoorNodes.isNotEmpty() && indoorNodes.all { it.type.contains(NodeType.STAIRS) }
            val isLastSegment = i == segments.lastIndex

            if (i > 0) {
                val prevSegment = segments[i - 1]
                val prevLast = prevSegment.last()

                if (prevLast is ResolvedNode.InDoor && firstNode is ResolvedNode.OutDoor) {
                    val prevIndoorCount = prevSegment.count { it is ResolvedNode.InDoor }
                    val isPrevStartSingle = (i - 1 == 0) && prevIndoorCount <= 1

                    if (!isPrevStartSingle) {
                        steps.add(NavigationStep.TransitionToOutDoor(fromBuilding = prevLast.node.buildNum))
                    }
                } else if (prevLast is ResolvedNode.OutDoor && firstNode is ResolvedNode.InDoor) {
                    val skipTransition = (isLastSegment && isSingleIndoorPoint)
                    if (!skipTransition) {
                        steps.add(NavigationStep.TransitionToInDoor(toBuilding = firstNode.node.buildNum))
                    }
                } else if (prevLast is ResolvedNode.InDoor && firstNode is ResolvedNode.InDoor) {
                    val prevIndoorCount = prevSegment.count { it is ResolvedNode.InDoor }
                    val isPrevStartSingle = (i - 1 == 0) && prevIndoorCount <= 1

                    if (!isPrevStartSingle) {
                        if (prevLast.node.buildNum != firstNode.node.buildNum) {
                            steps.add(NavigationStep.TransitionToBuilding(
                                prevLast.node.buildNum,
                                firstNode.node.buildNum
                            ))
                            lastRenderedFloor = null
                        } else if (prevLast.node.floorNum != firstNode.node.floorNum) {
                            val skipTransitionToLastSingle = isLastSegment && isSingleIndoorPoint
                            val skipTransitionToStairsOnly = isStairsOnlyFloor
                            if (!skipTransitionToLastSingle && !skipTransitionToStairsOnly) {
                                val fromFloor = lastRenderedFloor ?: prevLast.node.floorNum
                                steps.add(NavigationStep.TransitionToFlor(
                                    firstNode.node.floorNum,
                                    fromFloor
                                ))
                            }
                        }
                    }
                }
            }

            if (firstNode is ResolvedNode.InDoor) {
                val isStartSegment = i == 0
                val isEndSegment = i == segments.lastIndex

                val nextIsOutdoor = (i + 1 < segments.size) && (segments[i + 1].first() is ResolvedNode.OutDoor)
                val prevIsOutdoor = (i - 1 >= 0) && (segments[i - 1].last() is ResolvedNode.OutDoor)
                val nextIsIndoor = (i + 1 < segments.size) && (segments[i + 1].first() is ResolvedNode.InDoor)
                val prevIsIndoor = (i - 1 >= 0) && (segments[i - 1].last() is ResolvedNode.InDoor)

                val skipStartOutdoor = isStartSegment && isSingleIndoorPoint && nextIsOutdoor
                val skipStartIndoor = isStartSegment && isSingleIndoorPoint && nextIsIndoor
                val skipEnd = isEndSegment && isSingleIndoorPoint
                val skipIntermediate = !isStartSegment && !isEndSegment && isSingleIndoorPoint && prevIsIndoor && nextIsIndoor

                if (!skipStartOutdoor && !skipStartIndoor && !skipEnd && !skipIntermediate && !isStairsOnlyFloor) {
                    val buildingId = firstNode.node.buildNum
                    val floorNum = firstNode.node.floorNum

                    val building = masterNav.buildings.find { it.num == buildingId }
                    val flor = building?.flors?.find { it.num == floorNum }

                    if (flor != null) {
                        val startNodeForRender = if (segment.first().id == globalStart.id) {
                            (globalStart as? ResolvedNode.InDoor)?.node ?: indoorNodes.first()
                        } else indoorNodes.first()

                        val endNodeForRender = if (segment.last().id == globalEnd.id) {
                            (globalEnd as? ResolvedNode.InDoor)?.node ?: indoorNodes.last()
                        } else indoorNodes.last()

                        val renderData = floorRenderer.generateFloorData(
                            flor = flor,
                            buildingId = buildingId,
                            stepPath = indoorNodes,
                            localStart = startNodeForRender,
                            localEnd = endNodeForRender
                        )

                        val focusPoint = renderData.startNode ?: renderData.routePath.firstOrNull() ?: androidx.compose.ui.geometry.Offset.Zero

                        val routeBounds = if (renderData.routePath.isNotEmpty()) {
                            val points = buildList {
                                addAll(renderData.routePath)
                                renderData.startNode?.let { add(it) }
                                renderData.endNode?.let { add(it) }
                            }
                            val minX = points.minOf { it.x }
                            val minY = points.minOf { it.y }
                            val maxX = points.maxOf { it.x }
                            val maxY = points.maxOf { it.y }
                            androidx.compose.ui.geometry.Offset(minX, minY) to androidx.compose.ui.geometry.Offset(maxX, maxY)
                        } else null

                        steps.add(
                            NavigationStep.ByFlor(
                                flor = floorNum,
                                building = buildingId,
                                image = renderData,
                                pointOfInterest = focusPoint,
                                routeBounds = routeBounds,
                                textLabels = renderData.textLabels
                            )
                        )
                        lastRenderedFloor = floorNum
                    }
                }
            } else if (firstNode is ResolvedNode.OutDoor) {
                val outdoorNodes = segment.mapNotNull { (it as? ResolvedNode.OutDoor)?.node }
                val coords = outdoorNodes.map { Coordinates(it.lat, it.lon) }

                val prevSegment = segments.getOrNull(i - 1)
                val nextSegment = segments.getOrNull(i + 1)

                val fromBuilding = (prevSegment?.lastOrNull() as? ResolvedNode.InDoor)?.node?.buildNum
                val toBuilding = (nextSegment?.firstOrNull() as? ResolvedNode.InDoor)?.node?.buildNum

                val lastOutdoorNode = outdoorNodes.lastOrNull()
                val destinationLabel = if (isLastSegment) {
                    (globalEnd as? ResolvedNode.OutDoor)?.node?.label
                } else {
                    lastOutdoorNode?.label
                }

                val fromDescription = fromBuilding?.let { "Exit building $it" }
                val toDescription = when {
                    destinationLabel != null -> "Go to $destinationLabel"
                    toBuilding != null -> "Go to entrance of building $toBuilding"
                    else -> null
                }

                steps.add(NavigationStep.OutDoorMaps(
                    path = coords,
                    fromBuilding = fromBuilding,
                    toBuilding = toBuilding,
                    fromDescription = fromDescription,
                    toDescription = toDescription
                ))
                lastRenderedFloor = null
            }
        }

        return steps
    }
}
