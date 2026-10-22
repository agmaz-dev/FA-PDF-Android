package de.fapdf.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Liste, deren Einträge sich durch Gedrückthalten und Ziehen neu anordnen lassen.
 * [itemContent] erhält einen Modifier, der die Ziehgeste auslöst (ganze Zeile oder Griff).
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Int = 8,
    itemContent: @Composable (item: T, index: Int, dragModifier: Modifier, dragging: Boolean) -> Unit,
) {
    val current by rememberUpdatedState(items)
    val move by rememberUpdatedState(onMove)
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val gapPx = with(LocalDensity.current) { spacing.dp.toPx() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        items.forEachIndexed { index, item ->
            val k = keyOf(item)
            key(k) {
                val isDragging = draggingKey == k
                val dragMod = Modifier.pointerInput(k) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingKey = k; offset = 0f },
                        onDragEnd = { draggingKey = null; offset = 0f },
                        onDragCancel = { draggingKey = null; offset = 0f },
                        onDrag = { change, d ->
                            change.consume()
                            offset += d.y
                            val list = current
                            val i = list.indexOfFirst { keyOf(it) == k }
                            if (i < 0) return@detectDragGesturesAfterLongPress
                            if (offset > 0 && i < list.size - 1) {
                                val h = (heights[keyOf(list[i + 1])] ?: 0) + gapPx
                                if (offset > h / 2) { move(i, i + 1); offset -= h }
                            } else if (offset < 0 && i > 0) {
                                val h = (heights[keyOf(list[i - 1])] ?: 0) + gapPx
                                if (-offset > h / 2) { move(i, i - 1); offset += h }
                            }
                        },
                    )
                }
                Box(
                    Modifier.zIndex(if (isDragging) 1f else 0f)
                        .onSizeChanged { heights[k] = it.height }
                        .graphicsLayer { translationY = if (isDragging) offset else 0f; scaleX = if (isDragging) 1.02f else 1f; scaleY = scaleX }
                        .let { if (isDragging) it.shadow(8.dp) else it }
                ) {
                    itemContent(item, index, dragMod, isDragging)
                }
            }
        }
    }
}
