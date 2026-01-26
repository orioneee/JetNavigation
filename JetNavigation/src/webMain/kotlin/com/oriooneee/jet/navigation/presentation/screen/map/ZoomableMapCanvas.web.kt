package com.oriooneee.jet.navigation.presentation.screen.map

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.onMouseScroll(onScroll: (scrollDelta: Offset) -> Unit): Modifier {
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.firstOrNull() ?: continue
                    onScroll(change.scrollDelta)
                }
            }
        }
    }
}
