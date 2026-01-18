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
import androidx.compose.material.icons.outlined.ExitToApp
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
import androidx.compose.material3.contentColorFor
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
import com.oriooneee.jet.navigation.utils.containsAny
import kotlinx.serialization.json.Json
val enterColor = Color(0xFF4CAF50).copy(alpha = 0.35f)

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
        masterNavigation?.navGraph?.nodes?.filter { node ->
            !node.type.containsAny(
                NodeType.STAIRS,
                NodeType.TRANSFER_TO_ANOTHER_BUILDING,
                NodeType.TURN,
            ) && node.label != null
        } ?: listOf()
    }

    val availableBuildings = remember(nodesInfo) {
        nodesInfo.map { it.buildNum }.distinct().sorted()
    }

    val availableFloors = remember(nodesInfo, selectedBuilding) {
        if (selectedBuilding != null) {
            nodesInfo.filter { it.buildNum == selectedBuilding }
                .map { it.floorNum }
                .distinct()
                .sorted()
        } else {
            emptyList()
        }
    }

    val filteredNodes by remember(nodesInfo, selectedBuilding, selectedFloor, searchQuery) {
        derivedStateOf {
            nodesInfo.filter { info ->
                val matchBuilding = selectedBuilding == null || info.buildNum == selectedBuilding
                val matchFloor = selectedFloor == null || info.floorNum == selectedFloor
                val matchSearch = searchQuery.isEmpty() ||
                        ((info.label?.contains(searchQuery, ignoreCase = true) == true) ||
                                info.id.contains(searchQuery, ignoreCase = true))

                matchBuilding && matchFloor && matchSearch
            }.sortedBy { info ->
                when {
                    info.type.contains(NodeType.MAIN_ENTRANCE) -> {
                        ((info.buildNum * 10 + info.floorNum) * 100) - 1
                    }

                    info.type.containsAny(NodeType.WC_MAN, NodeType.WC_WOMAN) -> {
                        (info.buildNum * 10 + info.floorNum) * 100
                    }

                    info.type.contains(NodeType.AUDITORIUM) -> {
                        info.label?.filter { it.isDigit() }?.toIntOrNull() ?: Int.MAX_VALUE
                    }

                    else -> {
                        (info.buildNum * 10 + info.floorNum) * 100
                    }
                }
            }
        }
    }

    val mainEntranceNodes by remember(filteredNodes) {
        derivedStateOf { filteredNodes.filter { it.type.contains(NodeType.MAIN_ENTRANCE) } }
    }

    val poiNodes by remember(filteredNodes) {
        derivedStateOf {
            filteredNodes.filter {
                it.type.contains(NodeType.POINT_OF_INTEREST) &&
                        !it.type.contains(NodeType.MAIN_ENTRANCE)
            }
        }
    }

    val listNodes by remember(filteredNodes) {
        derivedStateOf {
            filteredNodes.filter {
                !it.type.contains(NodeType.POINT_OF_INTEREST) &&
                        !it.type.contains(NodeType.MAIN_ENTRANCE)
            }
        }
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
                        item {
                            Text(
                                text = "Nearest Quick Actions",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                            )
                        }
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

                    if (mainEntranceNodes.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = "Main Entrances",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    mainEntranceNodes.forEach { info ->
                                        SuggestionChip(
                                            onClick = {
                                                handleSelection(
                                                    SelectNodeResult.SelectedNode(
                                                        info
                                                    )
                                                )
                                            },
                                            label = {
                                                Text(
                                                    "${info.buildNum} Корпус"
                                                )
                                            },
                                            icon = {
                                                Icon(
                                                    Icons.Outlined.ExitToApp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = enterColor,
                                                labelColor = contentColorFor(enterColor),
                                                iconContentColor = contentColorFor(enterColor)
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
                                            onClick = {
                                                handleSelection(
                                                    SelectNodeResult.SelectedNode(
                                                        info
                                                    )
                                                )
                                            },
                                            label = {
                                                Text(
                                                    "${info.label ?: info.id} (${info.buildNum} Корпус, ${info.floorNum} Поверх)"
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
                        key = { it.id }
                    ) { info ->
                        NodeListItem(node = info) {
                            handleSelection(SelectNodeResult.SelectedNode(info))
                        }
                    }
                }
            }
        }
    }
}

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
            icon = Icons.Outlined.ExitToApp,
            label = "Exit",
            color = enterColor,
            contentColor = contentColorFor(enterColor),
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
        node.type.contains(NodeType.MAIN_ENTRANCE) -> Icons.Outlined.ExitToApp to Color(0xFF4CAF50)
        node.type.containsAll(
            listOf(NodeType.WC_WOMAN, NodeType.WC_MAN)
        ) -> Icons.Outlined.Wc to Color(0xFF9B27AF)

        node.type.contains(NodeType.WC_MAN) -> Icons.Outlined.Man to Color(0xFF4A90E2)
        node.type.contains(NodeType.WC_WOMAN) -> Icons.Outlined.Woman to Color(0xFFE91E63)
        node.type.contains(NodeType.AUDITORIUM) -> Icons.Outlined.Class to MaterialTheme.colorScheme.secondary
        node.type.contains(NodeType.TRANSFER_TO_ANOTHER_BUILDING) -> Icons.Outlined.DirectionsWalk to MaterialTheme.colorScheme.primary
        else -> Icons.Outlined.Place to MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        headlineContent = {
            Text(
                text = (node.label ?: node.id),
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