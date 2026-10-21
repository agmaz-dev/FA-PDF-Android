package de.fapdf.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class OcrWord(val text: String, val viewBox: Box)
data class OcrLine(val text: String, val viewBox: Box, val words: List<OcrWord>)
data class OcrPage(val pageIndex: Int, val lines: List<OcrLine>)

/**
 * Texterkennung mit Google ML Kit (auf dem Gerät, kostenlos, keine Datenübertragung).
 * Das lateinische Modell erkennt u. a. Deutsch, Englisch und Türkisch inklusive ä, ö, ü, ß, ç, ğ, ı, ş.
 */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** @param scale Pixel pro Punkt des gerenderten Bildes. */
    suspend fun recognize(pageIndex: Int, bitmap: Bitmap, scale: Float): OcrPage {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = ArrayList<OcrLine>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val r = line.boundingBox ?: continue
                val words = line.elements.mapNotNull { el ->
                    el.boundingBox?.let { b -> OcrWord(el.text, Box(b.left / scale, b.top / scale, b.right / scale, b.bottom / scale)) }
                }
                lines.add(OcrLine(line.text, Box(r.left / scale, r.top / scale, r.right / scale, r.bottom / scale), words))
            }
        }
        lines.sortWith(compareBy<OcrLine> { it.viewBox.top.toInt() / 4 }.thenBy { it.viewBox.left })
        return OcrPage(pageIndex, lines)
    }

    fun close() = recognizer.close()

    companion object {
        /** Empfohlene Renderauflösung für OCR (ca. 200 dpi). */
        const val OCR_SCALE = 2.8f
    }
}

object OcrLayer {
    /**
     * Legt eine unsichtbare Textebene über die Seite (Rendermodus 3), damit gescannte
     * Dokumente durchsuchbar und kopierbar werden. Das Erscheinungsbild ändert sich nicht.
     */
    fun addInvisibleText(doc: PDDocument, pageIndex: Int, lines: List<OcrLine>, cache: DocFontCache) {
        val page = doc.getPage(pageIndex)
        val g = PdfIo.geometry(page)
        val font = cache.get(FontFamily.SANS, bold = false, italic = false)
        val cb = ContentBuilder(PdfIo.resourcesOf(page))
        for (line in lines) {
            val text = line.text.filter { it.code >= 32 }
            if (text.isBlank()) continue
            val clean = text.map { c -> if (runCatching { font.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")
            clean.codePoints().forEach { font.addToSubset(it) }
            val size = max(2f, line.viewBox.height * 0.8f)
            val natural = font.getStringWidth(clean) / 1000f * size
            val hScale = if (natural > 0) (line.viewBox.width / natural * 100f).coerceIn(10f, 1000f) else 100f
            val baselineV = line.viewBox.bottom - line.viewBox.height * 0.2f
            cb.text(font, size, g.uprightMatrixAt(line.viewBox.left, baselineV), listOf(font.encode(clean)), renderMode = 3, horizontalScale = hScale)
        }
        PdfIo.appendToPage(doc, page, cb.tokens)
    }
}

/**
 * Bearbeitung gescannter Seiten. Da ein Scan nur aus Pixeln besteht, wird die Seite neu
 * aufgebaut: Die Bildpixel der alten Zeile werden durch die erkannte Hintergrundfarbe ersetzt,
 * der neue Text wird als echter Vektortext gesetzt, und die übrigen erkannten Zeilen werden
 * als unsichtbare Textebene hinterlegt (Durchsuchbarkeit bleibt erhalten).
 */
class ScanEditEngine(private val fontLibrary: FontLibrary) {

    data class ScanStyle(val family: FontFamily, val bold: Boolean, val italic: Boolean, val size: Float, val colorArgb: Int)

    /** Schätzt Schriftgröße und Farbe einer OCR-Zeile anhand der Pixel. */
    fun detect(bitmap: Bitmap, scale: Float, line: OcrLine): ScanStyle {
        val h = line.viewBox.height
        val size = (h / 1.18f).coerceIn(4f, 72f)
        val px = toPx(line.viewBox, scale, bitmap)
        val color = darkestMedian(bitmap, px)
        // Fettdruck grob über den Anteil dunkler Pixel schätzen
        val ink = inkRatio(bitmap, px)
        return ScanStyle(FontFamily.SANS, ink > 0.28f, false, (size * 10).toInt() / 10f, color)
    }

    /**
     * Ersetzt eine Zeile auf einer gescannten Seite.
     * @param bitmap Seite gerendert mit [scale] Pixel pro Punkt (ohne Anmerkungen).
     * @return aktualisierte OCR-Daten der Seite.
     */
    fun replaceLine(
        doc: PDDocument, pageIndex: Int, bitmap: Bitmap, scale: Float, ocr: OcrPage,
        lineIndex: Int, newText: String, style: ScanStyle,
    ): OcrPage {
        val page = doc.getPage(pageIndex)
        val g = PdfIo.geometry(page)
        val line = ocr.lines[lineIndex]
        val work = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val px = toPx(line.viewBox, scale, work)
        val bg = borderMedian(work, px)
        val fillPaint = Paint()
        fillPaint.color = bg
        fillPaint.setStyle(Paint.Style.FILL)
        fillPaint.isAntiAlias = false
        Canvas(work).drawRect(px, fillPaint)

        // Seite neu aufbauen: bearbeitetes Bild + Vektortext + unsichtbare Textebene
        page.resources = PDResources()
        val cache = DocFontCache(doc, fontLibrary)
        val img = JPEGFactory.createFromImage(doc, work, 0.92f)
        val cb = ContentBuilder(page.resources)
        cb.image(img, g.imageMatrix(g.viewWidth / 2f, g.viewHeight / 2f, g.viewWidth, g.viewHeight, 0f))
        val font = cache.get(style.family, style.bold, style.italic)
        val clean = newText.replace('\n', ' ').map { c -> if (runCatching { font.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")
        var newBox = line.viewBox
        if (clean.isNotBlank()) {
            clean.codePoints().forEach { font.addToSubset(it) }
            val baselineV = line.viewBox.bottom - line.viewBox.height * 0.2f
            cb.text(font, style.size, g.uprightMatrixAt(line.viewBox.left, baselineV), listOf(font.encode(clean)), style.colorArgb)
            val w = font.getStringWidth(clean) / 1000f * style.size
            newBox = Box(line.viewBox.left, line.viewBox.top, line.viewBox.left + w, line.viewBox.bottom)
        }
        PdfIo.replacePageContent(doc, page, cb.tokens)
        val others = ocr.lines.filterIndexed { i, _ -> i != lineIndex }
        OcrLayer.addInvisibleText(doc, pageIndex, others, cache)
        val updated = ocr.lines.toMutableList()
        if (clean.isBlank()) updated.removeAt(lineIndex) else updated[lineIndex] = OcrLine(clean, newBox, emptyList())
        return OcrPage(pageIndex, updated)
    }

    /** Ersetzt die komplette Seite durch ihr Bild (z. B. für maximal sicheres Schwärzen). */
    fun rasterizePage(doc: PDDocument, pageIndex: Int, bitmap: Bitmap, blackBoxesView: List<Box> = emptyList(), scale: Float = 1f) {
        val page = doc.getPage(pageIndex)
        val g = PdfIo.geometry(page)
        val work = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (blackBoxesView.isNotEmpty()) {
            val c = Canvas(work)
            val p = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
            blackBoxesView.forEach { c.drawRect(it.left * scale, it.top * scale, it.right * scale, it.bottom * scale, p) }
        }
        page.resources = PDResources()
        val img = JPEGFactory.createFromImage(doc, work, 0.92f)
        val cb = ContentBuilder(page.resources)
        cb.image(img, g.imageMatrix(g.viewWidth / 2f, g.viewHeight / 2f, g.viewWidth, g.viewHeight, 0f))
        PdfIo.replacePageContent(doc, page, cb.tokens)
    }

    private fun toPx(b: Box, scale: Float, bmp: Bitmap) = RectF(
        max(0f, b.left * scale - 2f), max(0f, b.top * scale - 2f),
        min(bmp.width.toFloat(), b.right * scale + 2f), min(bmp.height.toFloat(), b.bottom * scale + 2f),
    )

    companion object {
        private fun lum(c: Int) = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF))

        /** Median der Farben auf einem Rahmen knapp außerhalb des Rechtecks = Papierfarbe. */
        fun borderMedian(bmp: Bitmap, r: RectF): Int {
            val samples = ArrayList<Int>()
            val l = (r.left - 3).toInt().coerceIn(0, bmp.width - 1)
            val t = (r.top - 3).toInt().coerceIn(0, bmp.height - 1)
            val rr = (r.right + 3).toInt().coerceIn(0, bmp.width - 1)
            val b = (r.bottom + 3).toInt().coerceIn(0, bmp.height - 1)
            val stepX = max(1, (rr - l) / 60)
            val stepY = max(1, (b - t) / 20)
            for (x in l..rr step stepX) { samples.add(bmp.getPixel(x, t)); samples.add(bmp.getPixel(x, b)) }
            for (y in t..b step stepY) { samples.add(bmp.getPixel(l, y)); samples.add(bmp.getPixel(rr, y)) }
            if (samples.isEmpty()) return android.graphics.Color.WHITE
            samples.sortBy { lum(it) }
            return samples[samples.size / 2] or 0xFF000000.toInt()
        }

        /** Median der dunkelsten 10 % = Textfarbe. */
        fun darkestMedian(bmp: Bitmap, r: RectF): Int {
            val samples = ArrayList<Int>()
            val stepX = max(1, (r.width() / 80).toInt())
            val stepY = max(1, (r.height() / 20).toInt())
            var y = r.top.toInt()
            while (y < r.bottom.toInt().coerceAtMost(bmp.height)) {
                var x = r.left.toInt()
                while (x < r.right.toInt().coerceAtMost(bmp.width)) { samples.add(bmp.getPixel(x, y)); x += stepX }
                y += stepY
            }
            if (samples.isEmpty()) return 0xFF000000.toInt()
            samples.sortBy { lum(it) }
            val dark = samples.take(max(1, samples.size / 10))
            return dark[dark.size / 2] or 0xFF000000.toInt()
        }

        fun inkRatio(bmp: Bitmap, r: RectF): Float {
            var dark = 0; var total = 0
            val stepX = max(1, (r.width() / 120).toInt())
            val stepY = max(1, (r.height() / 30).toInt())
            var y = r.top.toInt()
            while (y < r.bottom.toInt().coerceAtMost(bmp.height)) {
                var x = r.left.toInt()
                while (x < r.right.toInt().coerceAtMost(bmp.width)) {
                    if (lum(bmp.getPixel(x, y)) < 110f) dark++
                    total++; x += stepX
                }
                y += stepY
            }
            return if (total == 0) 0f else dark.toFloat() / total
        }
    }
}

/** Rendert eine Seite ohne Anmerkungen (für Scan-Bearbeitung und Rasterung). */
object PageRaster {
    fun render(src: File, pageIndex: Int, scale: Float, tmpDir: File): Bitmap {
        val tmp = File(tmpDir, "raster_${System.nanoTime()}.pdf")
        try {
            PdfIo.load(src).use { doc ->
                if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
                doc.pages.forEach { p -> p.annotations = ArrayList() }
                runCatching { doc.documentCatalog.acroForm = null }
                PdfIo.save(doc, tmp)
            }
            PdfPageSource.open(tmp).use { s ->
                val (w, _) = s.pageSizes[pageIndex]
                return s.renderBlocking(pageIndex, (w * scale).toInt())
            }
        } finally {
            tmp.delete()
        }
    }
}
