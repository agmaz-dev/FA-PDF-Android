package de.fa.kuran.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** İki parmakla büyütme; diğer dokunuşları engellemez. */
fun Modifier.pinch(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val ev = awaitPointerEvent()
            if (ev.changes.none { it.pressed }) break
            if (ev.changes.count { it.pressed } >= 2) {
                val z = ev.calculateZoom()
                if (z != 1f) onZoom(z)
                ev.changes.forEach { it.consume() }
            }
        }
    }
}

@Composable
fun Glass(
    pal: Palette,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(pal.glass)
            .border(1.dp, pal.glassLine, shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun RoundIcon(pal: Palette, icon: ImageVector, desc: String, tint: Color = pal.ink, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = desc, tint = tint) }
}

@Composable
fun TextButtonPill(pal: Palette, text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) pal.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else pal.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun <T> Segmented(pal: Palette, items: List<Pair<T, String>>, value: T, modifier: Modifier = Modifier, onChange: (T) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(pal.chip).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (k, label) ->
            val sel = k == value
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .shadow(if (sel) 2.dp else 0.dp, RoundedCornerShape(19.dp))
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (sel) pal.card else Color.Transparent)
                    .clickable { onChange(k) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) pal.accent else pal.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

enum class Side { LEFT, RIGHT, SINGLE }

/** Kâğıt sayfa: gölge, köşe, dokunma (menü) ve iki parmakla büyütme. */
@Composable
fun PaperPage(
    pal: Palette,
    modifier: Modifier,
    side: Side,
    onTap: () -> Unit,
    onPinch: (Float) -> Unit,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = when (side) {
        Side.LEFT -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 3.dp, bottomEnd = 3.dp)
        Side.RIGHT -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 16.dp, bottomEnd = 16.dp)
        Side.SINGLE -> RoundedCornerShape(14.dp)
    }
    val tap by rememberUpdatedState(onTap)
    val zoom by rememberUpdatedState(onPinch)
    Column(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(pal.paper)
            .pinch { zoom(it) }
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
            .padding(horizontal = padding, vertical = if (padding < 10.dp) padding else 12.dp),
        content = content,
    )
}

@Composable
fun Ornament(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.34f
        val st = Stroke(width = size.minDimension * 0.05f)
        for (deg in listOf(0f, 45f)) {
            rotate(deg, c) {
                drawRect(color, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = st)
            }
        }
        drawCircle(color, radius = r * 0.35f, center = c, style = st)
    }
}

@Composable
fun Spine(pal: Palette) {
    Box(Modifier.fillMaxHeight().padding(vertical = 2.dp).width(3.dp).background(pal.gold.copy(alpha = 0.25f)))
}
