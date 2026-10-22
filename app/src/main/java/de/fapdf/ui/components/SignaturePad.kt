package de.fapdf.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.fapdf.ui.theme.FaColors
import kotlin.math.max
import kotlin.math.min

/** Zustand der Zeichenfläche: Striche in Pixeln. */
class SignaturePadState {
    val strokes = mutableStateListOf<List<Offset>>()
    var current by mutableStateOf<List<Offset>>(emptyList())
    var colorArgb by mutableStateOf(0xFF111111.toInt())
    var widthDp by mutableStateOf(3.5f)
    val isEmpty: Boolean get() = strokes.isEmpty()
    fun undo() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1) }
    fun clear() { strokes.clear(); current = emptyList() }

    /** Zugeschnittenes Bild mit transparentem Hintergrund (doppelte Auflösung). */
    fun render(density: Float): Bitmap? = SignatureRender.strokes(strokes.toList(), colorArgb, widthDp * density)
}

@Composable
fun SignaturePad(state: SignaturePadState, modifier: Modifier = Modifier, hint: String = "Hier unterschreiben") {
    Box(
        modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, FaColors.CardBorder, RoundedCornerShape(14.dp))
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { state.current = listOf(it) },
                    onDrag = { change, _ -> change.consume(); state.current = state.current + change.position },
                    onDragEnd = { if (state.current.size > 1) state.strokes.add(state.current); state.current = emptyList() },
                    onDragCancel = { state.current = emptyList() },
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(FaColors.Separator, Offset(24f, size.height * 0.75f), Offset(size.width - 24f, size.height * 0.75f), strokeWidth = 2f)
            (state.strokes + listOf(state.current)).forEach { pts ->
                if (pts.size > 1) {
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) {
                            val mx = (pts[i - 1].x + pts[i].x) / 2
                            val my = (pts[i - 1].y + pts[i].y) / 2
                            quadraticBezierTo(pts[i - 1].x, pts[i - 1].y, mx, my)
                        }
                        lineTo(pts.last().x, pts.last().y)
                    }
                    drawPath(path, Color(state.colorArgb), style = Stroke(width = state.widthDp * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        if (state.isEmpty && state.current.isEmpty()) Text(hint, Modifier.align(Alignment.Center), color = FaColors.SecondaryLabel)
    }
}

object SignatureRender {
    fun strokes(strokes: List<List<Offset>>, color: Int, widthPx: Float): Bitmap? {
        val all = strokes.flatten()
        if (all.size < 2) return null
        val pad = widthPx * 2
        val minX = all.minOf { it.x } - pad
        val minY = all.minOf { it.y } - pad
        val maxX = all.maxOf { it.x } + pad
        val maxY = all.maxOf { it.y } + pad
        val scale = 2f
        val w = max(1, ((maxX - minX) * scale).toInt())
        val h = max(1, ((maxY - minY) * scale).toInt())
        val bmp = Bitmap.createBitmap(min(w, 3000), min(h, 1500), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.scale(scale, scale)
        canvas.translate(-minX, -minY)
        val paint = Paint()
        paint.color = color; paint.strokeWidth = widthPx; paint.isAntiAlias = true
        paint.setStyle(Paint.Style.STROKE); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        strokes.filter { it.size > 1 }.forEach { pts ->
            val path = android.graphics.Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val mx = (pts[i - 1].x + pts[i].x) / 2
                val my = (pts[i - 1].y + pts[i].y) / 2
                path.quadTo(pts[i - 1].x, pts[i - 1].y, mx, my)
            }
            path.lineTo(pts.last().x, pts.last().y)
            canvas.drawPath(path, paint)
        }
        return bmp
    }

    /** Getippter Name in der Schreibschrift des Geräts („cursive“, meist Dancing Script). */
    fun typed(name: String, color: Int): Bitmap? {
        val text = name.trim()
        if (text.isEmpty()) return null
        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = color
        paint.textSize = 160f
        paint.typeface = Typeface.create("cursive", Typeface.NORMAL)
        val w = paint.measureText(text)
        val fm = paint.fontMetrics
        val bmp = Bitmap.createBitmap((w + 60).toInt().coerceAtMost(4000), (fm.bottom - fm.top + 40).toInt(), Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawText(text, 30f, -fm.top + 20f, paint)
        return bmp
    }

    /** Foto einer Unterschrift: heller Hintergrund wird transparent, Tinte bleibt erhalten. */
    fun fromPhoto(src: Bitmap): Bitmap {
        val maxSide = 1600
        val f = min(1f, maxSide.toFloat() / max(src.width, src.height))
        val b = if (f < 1f) Bitmap.createScaledBitmap(src, (src.width * f).toInt(), (src.height * f).toInt(), true) else src
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        // Papierhelligkeit schätzen (Median eines Gitters)
        val samples = ArrayList<Int>()
        for (y in 0 until h step max(1, h / 40)) for (x in 0 until w step max(1, w / 40)) samples.add(lum(px[y * w + x]))
        samples.sort()
        val paper = samples[samples.size / 2]
        val threshold = (paper * 0.75f).toInt()
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val l = lum(px[i])
            if (l >= threshold) px[i] = 0 else {
                // je dunkler, desto deckender – weiche Kanten
                val a = (255f * (threshold - l) / threshold.coerceAtLeast(1)).toInt().coerceIn(80, 255)
                px[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
                if (x < minX) minX = x; if (y < minY) minY = y; if (x > maxX) maxX = x; if (y > maxY) maxY = y
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (maxX < 0) return out
        val pad = 8
        val l = (minX - pad).coerceAtLeast(0); val t = (minY - pad).coerceAtLeast(0)
        val r = (maxX + pad).coerceAtMost(w - 1); val btm = (maxY + pad).coerceAtMost(h - 1)
        return Bitmap.createBitmap(out, l, t, r - l + 1, btm - t + 1)
    }

    private fun lum(c: Int) = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
}
