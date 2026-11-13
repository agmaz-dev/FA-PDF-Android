package de.fapdf.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Macht Scans lesbarer: Schatten und grauen Hintergrund entfernen (Hintergrund wird geschätzt und
 * herausgerechnet), Kontrast anheben, Schrift dunkler. Farbe bleibt erhalten (Stempel, Unterschriften).
 */
object ScanEnhancer {
    private const val MAX_SIDE = 2400

    fun decode(input: InputStream): Bitmap? {
        val bytes = input.readBytes()
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > MAX_SIDE * 1.5) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
    }

    fun enhance(src: Bitmap): Bitmap {
        val scale = min(1f, MAX_SIDE.toFloat() / max(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true) else src
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)

        // Hintergrund je Farbkanal schätzen (90%-Quantil je Block ≈ Papier) – gleicht Schatten
        // und Farbstich der Lampe aus, statt nur die Helligkeit aufzuziehen.
        val sw = max(8, w / 24); val sh = max(8, h / 24)
        val bgs = Array(3) { ch ->
            val shift = 16 - ch * 8
            val bg = FloatArray(sw * sh)
            for (y in 0 until sh) for (x in 0 until sw) {
                val x0 = x * w / sw; val x1 = (x + 1) * w / sw; val y0 = y * h / sh; val y1 = (y + 1) * h / sh
                val hist = IntArray(256); var n = 0
                var yy = y0
                while (yy < y1) { var xx = x0; while (xx < x1) { hist[(px[yy * w + xx] shr shift) and 255]++; n++; xx += 2 }; yy += 2 }
                var acc = 0; var best = 255; val target = (n * 0.9f).toInt()
                for (i in 0 until 256) { acc += hist[i]; if (acc >= target) { best = i; break } }
                bg[y * sw + x] = max(best, 60).toFloat()
            }
            val sm = bg.copyOf()
            repeat(2) {
                for (y in 0 until sh) for (x in 0 until sw) {
                    var s = 0f; var c = 0
                    for (dy in -1..1) for (dx in -1..1) { val nx = x + dx; val ny = y + dy; if (nx in 0 until sw && ny in 0 until sh) { s += bg[ny * sw + nx]; c++ } }
                    sm[y * sw + x] = s / c
                }
                System.arraycopy(sm, 0, bg, 0, bg.size)
            }
            bg
        }
        val lut = IntArray(1025) { i -> curve(i / 1024f) }
        for (y in 0 until h) {
            val fy = (y.toFloat() / h * sh - 0.5f).coerceIn(0f, sh - 1f)
            val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = (x.toFloat() / w * sw - 0.5f).coerceIn(0f, sw - 1f)
                val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = fx - x0
                val c = px[y * w + x]
                var o = 0xFF shl 24
                for (ch in 0..2) {
                    val bg = bgs[ch]
                    val b = (bg[y0 * sw + x0] * (1 - tx) + bg[y0 * sw + x1] * tx) * (1 - ty) + (bg[y1 * sw + x0] * (1 - tx) + bg[y1 * sw + x1] * tx) * ty
                    val shift = 16 - ch * 8
                    val n = (((c shr shift) and 255) / b).coerceIn(0f, 1f)
                    o = o or (lut[(n * 1024).toInt()] shl shift)
                }
                px[y * w + x] = o
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (bmp !== src) bmp.recycle()
        return out
    }

    /** Papier rein weiß, Schrift und feine Linien kräftiger (n = Wert relativ zum Papier, 0..1). */
    private fun curve(n: Float): Int {
        val t = (n / 0.93f).coerceIn(0f, 1f)
        return (Math.pow(t.toDouble(), 1.8) * 255).roundToInt().coerceIn(0, 255)
    }

    /** Baut aus den verbesserten Seiten eine PDF (A4-Breite, Seitenverhältnis wie Scan). */
    fun buildPdf(pages: List<Bitmap>, out: File) {
        PDDocument().use { doc ->
            pages.forEach { b ->
                val pw = PDRectangle.A4.width
                val ph = pw * b.height / b.width
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val img = JPEGFactory.createFromImage(doc, b, 0.85f)
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, 0f, 0f, pw, ph) }
            }
            doc.save(out)
        }
    }
}
