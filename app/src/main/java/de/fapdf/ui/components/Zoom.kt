package de.fapdf.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size = IntSize.Zero
    fun reset() { scale = 1f; offset = Offset.Zero }
    fun clamp(o: Offset): Offset {
        val mx = (scale - 1f) * size.width / 2f; val my = (scale - 1f) * size.height / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
}

@Composable
fun rememberZoomState() = remember { ZoomState() }

/**
 * Mit zwei Fingern zoomen, vergrößert mit einem Finger verschieben, Doppeltipp = 2,5× bzw. zurück.
 * Einfacher Tipp ruft [onTap] auf. Ohne Zoom bleibt normales Scrollen (z. B. einer Liste) möglich.
 */
fun Modifier.pinchZoom(state: ZoomState, onTap: (() -> Unit)? = null): Modifier = this
    .onSizeChanged { state.size = it }
    .pointerInput(state) {
        detectTapGestures(
            onDoubleTap = { if (state.scale > 1.05f) state.reset() else { state.scale = 2.5f; state.offset = Offset.Zero } },
            onTap = { onTap?.invoke() },
        )
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = ev.changes.count { it.pressed }
                if (pressed > 1 || state.scale > 1.01f) {
                    val zoom = ev.calculateZoom(); val pan = ev.calculatePan()
                    state.scale = (state.scale * zoom).coerceIn(1f, 5f)
                    state.offset = if (state.scale <= 1.01f) Offset.Zero else state.clamp(state.offset + pan)
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (ev.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        scaleX = state.scale; scaleY = state.scale
        translationX = state.offset.x; translationY = state.offset.y
    }
