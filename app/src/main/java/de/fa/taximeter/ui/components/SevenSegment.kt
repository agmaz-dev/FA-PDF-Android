package de.fa.taximeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.fa.taximeter.ui.theme.MeterColors

private fun FloatArray.toPath(): Path {
    val p = Path()
    p.moveTo(this[0], this[1])
    var i = 2
    while (i < size) {
        p.lineTo(this[i], this[i + 1])
        i += 2
    }
    p.close()
    return p
}

/**
 * Siebensegment-Anzeige mit [digits] Zellen. Die Größe passt sich dem verfügbaren Platz an
 * (Breite und Höhe), damit Ziffern nie abgeschnitten werden. Freie Stellen erscheinen wie bei
 * einem echten Taxameter als dunkelrote, nicht leuchtende "8".
 */
@Composable
fun SevenSegment(
    text: String,
    digits: Int,
    modifier: Modifier = Modifier,
    lit: Color = MeterColors.Lit,
    unlit: Color = MeterColors.Unlit,
    alignment: Alignment = Alignment.CenterEnd,
    description: String = text,
) {
    BoxWithConstraints(modifier.semantics { contentDescription = description }, contentAlignment = alignment) {
        val byWidth = maxWidth / digits
        val byHeight = maxHeight * SegmentGeometry.CELL_ASPECT
        val cellW = if (byWidth < byHeight) byWidth else byHeight
        val cellH = cellW / SegmentGeometry.CELL_ASPECT
        val cells = SegmentGeometry.cells(text, digits)
        Canvas(Modifier.size(cellW * digits, cellH)) {
            val w = size.width / digits
            val h = size.height
            val glow = Stroke(width = w * 0.045f)
            val glowColor = lit.copy(alpha = 0.28f)
            cells.forEachIndexed { i, cell ->
                val x0 = i * w
                val mask = SegmentGeometry.mask(cell.ch)
                SegmentGeometry.segments(x0, 0f, w, h).forEachIndexed { si, poly ->
                    val path = poly.toPath()
                    if (SegmentGeometry.isLit(mask, si)) {
                        drawPath(path, glowColor, style = glow)
                        drawPath(path, lit)
                    } else {
                        drawPath(path, unlit)
                    }
                }
                if (cell.comma && cell.ch != null) {
                    val path = SegmentGeometry.comma(x0, 0f, w, h).toPath()
                    drawPath(path, glowColor, style = glow)
                    drawPath(path, lit)
                }
            }
        }
    }
}
