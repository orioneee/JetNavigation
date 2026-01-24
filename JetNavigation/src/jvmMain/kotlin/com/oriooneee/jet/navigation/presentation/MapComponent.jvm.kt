package com.oriooneee.jet.navigation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.oriooneee.jet.navigation.domain.entities.Coordinates
import com.oriooneee.jet.navigation.domain.entities.NavigationStep

@Composable
actual fun MapComponent(
    modifier: Modifier,
    step: NavigationStep.OutDoorMaps
) {
    MapPlaceholderContent(
        step
    )
}