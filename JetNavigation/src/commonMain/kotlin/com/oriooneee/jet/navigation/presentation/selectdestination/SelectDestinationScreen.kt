package com.oriooneee.jet.navigation.presentation.selectdestination

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Man
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.Woman
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.NodeType
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult
import com.oriooneee.jet.navigation.presentation.KEY_SELECTED_END_NODE
import com.oriooneee.jet.navigation.presentation.KEY_SELECTED_START_NODE
import com.oriooneee.jet.navigation.presentation.navigation.LocalNavController
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SelectDestinationScreen(
    isStartNode: Boolean,
    isSelectedStartNode: Boolean,
    onBack: () -> Unit
) {
    val navController = LocalNavController.current
    var masterNavigation by remember { mutableStateOf<MasterNavigation?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedBuilding by remember { mutableStateOf<Int?>(null) }
    var selectedFloor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        masterNavigation = MasterNavigation.loadFromAssets()
    }

    fun handleSelection(result: SelectNodeResult) {
        val key = if (isStartNode) KEY_SELECTED_START_NODE else KEY_SELECTED_END_NODE
        val jsonResult = Json.encodeToString(result)
        navController.previousBackStackEntry?.savedStateHandle?.set(key, jsonResult)
        navController.popBackStack()
    }

    val nodesInfo = remember(masterNavigation) {
        val nav = masterNavigation ?: return@remember emptyList<NodeInfo>()
        val audRegex = Regex("AUD_(\\d)(\\d)")

        nav.navGraph.nodes
            .filter { node ->
                !node.id.startsWith("TURN") &&
                        !node.id.startsWith("NODE") &&
                        !node.id.startsWith("TRANSFER")
            }
            .map { node ->
                var building = 2
                var floor = 1

                val match = audRegex.find(node.id)
                if (match != null) {
                    building = match.groupValues[1].toInt()
                    floor = match.groupValues[2].toInt()
                } else {
                    if (node.id.contains("_5_") || node.id.contains("BUILDING_5") || node.z > 1.0) {
                        building = 5
                    }

                    if (building == 5) {
                        floor = when {
                            node.z > 12.0 -> 4
                            node.z > 9.0 -> 3
                            node.z > 5.0 -> 2
                            else -> 1
                        }
                    } else {
                        if (node.label?.contains("поверх 1", true) == true) floor = 1
                        else if (node.label?.contains("поверх 2", true) == true) floor = 2
                        else if (node.label?.contains("поверх 3", true) == true) floor = 3
                    }
                }

                NodeInfo(node, building, floor)
            }
    }

    val availableBuildings = remember(nodesInfo) {
        nodesInfo.map { it.building }.distinct().sorted()
    }

    val availableFloors = remember(nodesInfo, selectedBuilding) {
        if (selectedBuilding != null) {
            nodesInfo.filter { it.building == selectedBuilding }
                .map { it.floor }
                .distinct()
                .sorted()
        } else {
            emptyList()
        }
    }

    val filteredNodes by remember(nodesInfo, selectedBuilding, selectedFloor, searchQuery) {
        derivedStateOf {
            nodesInfo.filter { info ->
                val matchBuilding = selectedBuilding == null || info.building == selectedBuilding
                val matchFloor = selectedFloor == null || info.floor == selectedFloor
                val matchSearch = searchQuery.isEmpty() ||
                        ((info.node.label?.contains(searchQuery, ignoreCase = true) == true) ||
                                info.node.id.contains(searchQuery, ignoreCase = true))

                matchBuilding && matchFloor && matchSearch
            }.sortedWith(
                compareBy<NodeInfo> { info ->
                    when {
                        info.node.type.contains(NodeType.MAIN_ENTRANCE) -> 0
                        info.node.type.contains(NodeType.WC_MAN) ||
                                info.node.type.contains(NodeType.WC_WOMAN) ||
                                info.node.id.contains("WC", ignoreCase = true) -> 1
                        info.node.type.contains(NodeType.AUDITORIUM) -> 2
                        info.node.type.contains(NodeType.POINT_OF_INTEREST) -> 3
                        else -> 4
                    }
                }.thenBy { info ->
                    when {
                        info.node.type.contains(NodeType.WC_MAN) ||
                                info.node.type.contains(NodeType.WC_WOMAN) ||
                                info.node.id.contains("WC", ignoreCase = true) -> {
                            info.floor
                        }
                        info.node.type.contains(NodeType.AUDITORIUM) -> {
                            info.node.id.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                        }
                        else -> 0
                    }
                }.thenBy { it.node.label }
            )
        }
    }

    val poiNodes by remember(filteredNodes) {
        derivedStateOf { filteredNodes.filter { it.node.type.contains(NodeType.POINT_OF_INTEREST) } }
    }

    val listNodes by remember(filteredNodes) {
        derivedStateOf { filteredNodes.filter { !it.node.type.contains(NodeType.POINT_OF_INTEREST) } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isStartNode) "Start Point" else "Destination",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search room, hall, etc.") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                } else null,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            if (masterNavigation == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (availableBuildings.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        items(availableBuildings) { building ->
                            InputChip(
                                selected = selectedBuilding == building,
                                onClick = {
                                    if (selectedBuilding == building) {
                                        selectedBuilding = null
                                        selectedFloor = null
                                    } else {
                                        selectedBuilding = building
                                        selectedFloor = null
                                    }
                                },
                                label = { Text("Building $building") },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }

                if (selectedBuilding != null && availableFloors.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(availableFloors) { floor ->
                            InputChip(
                                selected = selectedFloor == floor,
                                onClick = {
                                    selectedFloor = if (selectedFloor == floor) null else floor
                                },
                                label = { Text("Floor $floor") },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {

                    if (!isStartNode && isSelectedStartNode && searchQuery.isEmpty() && selectedBuilding == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            QuickActionRow(
                                onManWcClick = { handleSelection(SelectNodeResult.NearestManWC) },
                                onWomanWcClick = { handleSelection(SelectNodeResult.NearestWomanWC) },
                                onExitClick = { handleSelection(SelectNodeResult.NearestMainEntrance) }
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (poiNodes.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = "Points of Interest",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    poiNodes.forEach { info ->
                                        SuggestionChip(
                                            onClick = { handleSelection(SelectNodeResult.SelectedNode(info.node)) },
                                            label = {
                                                Text(
                                                    info.node.label ?: info.node.id
                                                )
                                            },
                                            icon = {
                                                Icon(
                                                    Icons.Outlined.Star,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                labelColor = MaterialTheme.colorScheme.onSurface,
                                                iconContentColor = MaterialTheme.colorScheme.primary
                                            ),
                                            border = SuggestionChipDefaults.suggestionChipBorder(
                                                enabled = true,
                                                borderColor = Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    items(
                        listNodes,
                        key = { it.node.id }
                    ) { info ->
                        NodeListItem(node = info.node) {
                            handleSelection(SelectNodeResult.SelectedNode(info.node))
                        }
                    }
                }
            }
        }
    }
}

private data class NodeInfo(
    val node: Node,
    val building: Int,
    val floor: Int
)

@Composable
fun QuickActionRow(
    onManWcClick: () -> Unit,
    onWomanWcClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Outlined.Man,
            label = "WC",
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onManWcClick
        )
        QuickActionButton(
            icon = Icons.Outlined.Woman,
            label = "WC",
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onWomanWcClick
        )
        QuickActionButton(
            icon = Icons.Outlined.DoorFront,
            label = "Exit",
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
            onClick = onExitClick
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
fun NodeListItem(node: Node, onClick: () -> Unit) {
    val (icon, color) = when {
        node.type.contains(NodeType.POINT_OF_INTEREST) -> Icons.Outlined.Star to MaterialTheme.colorScheme.primary
        node.type.contains(NodeType.MAIN_ENTRANCE) -> Icons.Outlined.DoorFront to MaterialTheme.colorScheme.primary
        node.id.contains("WC", ignoreCase = true) -> Icons.Outlined.Wc to MaterialTheme.colorScheme.tertiary
        node.type.contains(NodeType.AUDITORIUM) -> Icons.Outlined.Class to MaterialTheme.colorScheme.secondary
        node.type.contains(NodeType.TRANSFER_TO_ANOTHER_BUILDING) -> Icons.Outlined.DirectionsWalk to MaterialTheme.colorScheme.primary
        else -> Icons.Outlined.Place to MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        headlineContent = {
            Text(
                text = node.label ?: node.id,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}