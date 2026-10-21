package de.fapdf.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import de.fapdf.pdf.Box as FBox
import de.fapdf.pdf.PdfPageSource
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

/** Welcher Teil des Schnittrahmens wird gezogen? */
private enum class Grip { TL, TR, BL, BR, L, T, R, B, MOVE, NONE }

/**
 * Sichtbarer Schnittbereich: Die Seite wird groß angezeigt, der helle Rahmen ist der Bereich,
 * der erhalten bleibt. Ecken und Kanten lassen sich mit dem Finger ziehen, das Innere verschieben.
 * [crop] und Rückgabe sind Anteile der Seite (0…1): links, oben, rechts, unten.
 */
@Composable
fun CropSelector(src: PdfPageSource, page: Int, crop: FBox, onChange: (FBox) -> Unit) {
    val (wPt, hPt) = src.pageSizes[page]
    val img by produceState<ImageBitmap?>(null, src, page) {
        value = withContext(Dispatchers.IO) { runCatching { src.render(page, 1100).asImageBitmap() }.getOrNull() }
    }
    val current by rememberUpdatedState(crop)
    val change by rememberUpdatedState(onChange)
    val grip = remember { arrayOf(Grip.NONE) }
    Box(
        Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().aspectRatio(wPt / hPt)
            .shadow(3.dp).background(Color.White)
    ) {
        val i = img
        if (i != null) Image(i, "Seite ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else Text("Seite wird geladen …", Modifier.align(Alignment.Center), color = FaColors.SecondaryLabel)
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                val touch = 30.dp.toPx()
                detectDragGestures(
                    onDragStart = { o ->
                        val c = current
                        val l = c.left * size.width; val t = c.top * size.height
                        val r = c.right * size.width; val b = c.bottom * size.height
                        fun near(x: Float, y: Float) = hypot(o.x - x, o.y - y) < touch
                        grip[0] = when {
                            near(l, t) -> Grip.TL
                            near(r, t) -> Grip.TR
                            near(l, b) -> Grip.BL
                            near(r, b) -> Grip.BR
                            abs(o.x - l) < touch && o.y in t..b -> Grip.L
                            abs(o.x - r) < touch && o.y in t..b -> Grip.R
                            abs(o.y - t) < touch && o.x in l..r -> Grip.T
                            abs(o.y - b) < touch && o.x in l..r -> Grip.B
                            o.x in l..r && o.y in t..b -> Grip.MOVE
                            else -> Grip.NONE
                        }
                    },
                    onDragEnd = { grip[0] = Grip.NONE },
                    onDragCancel = { grip[0] = Grip.NONE },
                    onDrag = { change2, d ->
                        if (grip[0] == Grip.NONE) return@detectDragGestures
                        change2.consume()
                        val dx = d.x / size.width
                        val dy = d.y / size.height
                        val c = current
                        val min = 0.08f
                        var l = c.left; var t = c.top; var r = c.right; var b = c.bottom
                        when (grip[0]) {
                            Grip.TL -> { l += dx; t += dy }
                            Grip.TR -> { r += dx; t += dy }
                            Grip.BL -> { l += dx; b += dy }
                            Grip.BR -> { r += dx; b += dy }
                            Grip.L -> l += dx
                            Grip.R -> r += dx
                            Grip.T -> t += dy
                            Grip.B -> b += dy
                            Grip.MOVE -> {
                                val mx = dx.coerceIn(-l, 1f - r)
                                val my = dy.coerceIn(-t, 1f - b)
                                l += mx; r += mx; t += my; b += my
                            }
                            Grip.NONE -> {}
                        }
                        l = l.coerceIn(0f, r - min); r = r.coerceIn(l + min, 1f)
                        t = t.coerceIn(0f, b - min); b = b.coerceIn(t + min, 1f)
                        change(FBox(l, t, r, b))
                    },
                )
            }
        ) {
            val l = crop.left * size.width; val t = crop.top * size.height
            val r = crop.right * size.width; val b = crop.bottom * size.height
            val shade = Color(0x99000000)
            // Abgedunkelt = wird weggeschnitten
            drawRect(shade, Offset.Zero, Size(size.width, t))
            drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
            drawRect(shade, Offset(0f, t), Size(l, b - t))
            drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
            drawRect(FaColors.Accent, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
            // Drittel-Hilfslinien
            for (k in 1..2) {
                val x = l + (r - l) * k / 3; val y = t + (b - t) * k / 3
                drawLine(Color.White.copy(alpha = 0.6f), Offset(x, t), Offset(x, b), 1f)
                drawLine(Color.White.copy(alpha = 0.6f), Offset(l, y), Offset(r, y), 1f)
            }
            val hs = 9.dp.toPx()
            listOf(l to t, r to t, l to b, r to b, (l + r) / 2 to t, (l + r) / 2 to b, l to (t + b) / 2, r to (t + b) / 2).forEach { (x, y) ->
                drawCircle(Color.White, hs, Offset(x, y))
                drawCircle(FaColors.Accent, hs, Offset(x, y), style = Stroke(2.5f.dp.toPx()))
            }
        }
    }
}
