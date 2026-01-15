package com.oriooneee.jet.navigation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM

@Composable
actual fun rememberVectorSvgPainter(bytes: ByteArray): Painter {
    return remember(bytes) {
        try {
            val svgString = bytes.decodeToString()
            val styleBlock = "<style>*{stroke-linecap:round;stroke-linejoin:round;}</style>"
            val modifiedSvgString = svgString.replaceFirst(Regex("<svg[^>]*>"), "$0$styleBlock")
            val modifiedBytes = modifiedSvgString.encodeToByteArray()

            val data = Data.makeFromBytes(modifiedBytes)
            val dom = SVGDOM(data)
            SkiaSvgPainter(dom)
        } catch (e: Exception) {
            EmptyPainter
        }
    }
}

class SkiaSvgPainter(private val dom: SVGDOM) : Painter() {
    override val intrinsicSize: Size
        get() {
            val root = dom.root
            return if (root != null) {
                Size(root.width.value, root.height.value)
            } else {
                Size.Unspecified
            }
        }

    override fun DrawScope.onDraw() {
        dom.setContainerSize(size.width, size.height)
        dom.render(drawContext.canvas.nativeCanvas)
    }
}

object EmptyPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified
    override fun DrawScope.onDraw() {}
}