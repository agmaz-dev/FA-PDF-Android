package de.fapdf.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Zwei-Finger-Zoom (1×–4×) für Vorschauen: zoomt um den Punkt zwischen den Fingern, zwei Finger verschieben.
 * Ein-Finger-Gesten (Scrollen, Ziehen, Tippen) bleiben unberührt. Am Ende der Modifier-Kette verwenden.
 */
fun Modifier.pinchZoomable(): Modifier = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    this
        .onSizeChanged { size = it }
        .clipToBounds()
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        val z = event.calculateZoom()
                        val pan = event.calculatePan()
                        val c = event.calculateCentroid(useCurrent = true)
                        val ns = ZoomMath.clampZoom(scale * z)
                        val r = ns / scale
                        tx = (c.x - (c.x - tx) * r + pan.x).coerceIn(size.width * (1f - ns), 0f)
                        ty = (c.y - (c.y - ty) * r + pan.y).coerceIn(size.height * (1f - ns), 0f)
                        scale = ns
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = tx; translationY = ty
            transformOrigin = TransformOrigin(0f, 0f)
        }
}
