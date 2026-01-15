package com.oriooneee.jet.navigation.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.presentation.NavigationScreen
import com.oriooneee.jet.navigation.presentation.selectdestination.SelectDestinationScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

private val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(MyNavKey::class) {
            subclass(MyNavKey.NavigationScreen::class, MyNavKey.NavigationScreen.serializer())
            subclass(MyNavKey.SelectDestination::class, MyNavKey.SelectDestination.serializer())
        }
    }
}
val LocalBackStack = compositionLocalOf<SnapshotStateList<MyNavKey>> {
    error("No backstack provided")
}

@Composable
fun NavigationApp() {
    val backStack = remember {
        mutableStateListOf<MyNavKey>(MyNavKey.NavigationScreen)
    }

    // Lifted State: We keep the selected nodes here to persist them across screen changes
    var startNode by remember { mutableStateOf<Node?>(null) }
    var endNode by remember { mutableStateOf<Node?>(null) }
    MaterialTheme(
        darkColorScheme()
    ) {
        CompositionLocalProvider(LocalBackStack provides backStack) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<MyNavKey.NavigationScreen> {
                        NavigationScreen(
                            startNode = startNode,
                            endNode = endNode,
                            onSelectStart = { backStack.add(MyNavKey.SelectDestination(isStartNode = true)) },
                            onSelectEnd = { backStack.add(MyNavKey.SelectDestination(isStartNode = false)) },
                            onSwapNodes = {
                                val temp = startNode
                                startNode = endNode
                                endNode = temp
                            }
                        )
                    }

                    entry<MyNavKey.SelectDestination>(
                        metadata = NavDisplay.transitionSpec {
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300)) togetherWith
                                    ExitTransition.KeepUntilTransitionsFinished
                        } + NavDisplay.popTransitionSpec {
                            EnterTransition.None togetherWith
                                    slideOutVertically(
                                        targetOffsetY = { it },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300))
                        }
                    ) { key ->
                        Surface(modifier = Modifier.fillMaxSize()) {
                            SelectDestinationScreen(
                                onSelect = { node ->
                                    if (key.isStartNode) {
                                        startNode = node
                                    } else {
                                        endNode = node
                                    }
                                    backStack.removeLastOrNull()
                                },
                                onBack = {
                                    backStack.removeLastOrNull()
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}