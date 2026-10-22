package de.fapdf.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import kotlin.math.max

/** Ein zu übersetzender Absatz mit Position und Formatierung (Ansichtskoordinaten). */
data class TransUnit(
    val page: Int,
    val text: String,
    val box: Box,
    val size: Float,
    val colorArgb: Int,
    val family: FontFamily,
    val bold: Boolean,
    val italic: Boolean,
    val alignment: Alignment,
    /** Digitale PDF: Zeilen-IDs der Seite. Scan: leer. */
    val lineIds: List<Int> = emptyList(),
    /** Scan: Rechtecke der erkannten Zeilen (werden im Bild durch Papierfarbe ersetzt). */
    val ocrBoxes: List<Box> = emptyList(),
)

/**
 * Übersetzung mit erhaltenem Layout: Jeder Absatz wird einzeln übersetzt und an derselben Stelle
 * in derselben Schriftfamilie, Farbe und Ausrichtung neu gesetzt. Passt die Übersetzung nicht in
 * den Platz des Originals, wird die Schrift schrittweise verkleinert.
 */
object LayoutTranslator {

    /** Sammelt alle Absätze digitaler Seiten. Seiten ohne Text werden für die OCR zurückgegeben. */
    fun collect(doc: PDDocument): Pair<List<TransUnit>, List<Int>> {
        val units = ArrayList<TransUnit>()
        val scanned = ArrayList<Int>()
        for (p in 0 until doc.numberOfPages) {
            val pt = PageAnalyzer.analyze(doc, p)
            if (pt.lines.isEmpty() || pt.looksScanned) { scanned.add(p); continue }
            for (block in pt.blocks) {
                val lines = block.lineIds.map { pt.lines[it] }
                if (lines.any { !it.directlyEditable }) continue
                val text = joinLines(lines.map { it.text })
                if (text.isBlank() || !text.any { it.isLetter() }) continue
                val meta = pt.fontMeta(block.fontId)
                val g = pt.geometry
                units.add(
                    TransUnit(
                        page = p, text = text, box = g.userBoxToView(block.bbox), size = block.size,
                        colorArgb = lines.first().colorArgb, family = meta.family, bold = meta.bold, italic = meta.italic,
                        alignment = TextLayout.guessAlignment(lines, g.cropLeft, g.cropLeft + g.cropWidth),
                        lineIds = block.lineIds,
                    )
                )
            }
        }
        return units to scanned
    }

    /** Zeilen zu einem Absatz verbinden; Silbentrennung am Zeilenende wird aufgehoben. */
    fun joinLines(lines: List<String>): String {
        val sb = StringBuilder()
        for (raw in lines) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (sb.isEmpty()) { sb.append(l); continue }
            if (sb.endsWith("-") && l.first().isLowerCase()) sb.setLength(sb.length - 1) else sb.append(' ')
            sb.append(l)
        }
        return sb.toString()
    }

    /** Absätze einer gescannten Seite aus den OCR-Zeilen bilden. */
    fun ocrUnits(page: Int, ocr: OcrPage, bitmap: Bitmap, scale: Float): List<TransUnit> {
        val groups = ArrayList<MutableList<OcrLine>>()
        for (line in ocr.lines.sortedWith(compareBy<OcrLine> { it.viewBox.top }.thenBy { it.viewBox.left })) {
            val last = groups.lastOrNull()?.last()
            val h = line.viewBox.height
            if (last != null && line.viewBox.top - last.viewBox.bottom < 0.8f * h &&
                kotlin.math.abs(line.viewBox.left - last.viewBox.left) < 2.5f * h &&
                kotlin.math.abs(line.viewBox.height - last.viewBox.height) < 0.4f * h
            ) groups.last().add(line) else groups.add(mutableListOf(line))
        }
        val detector = ScanEditEngineDetector
        return groups.mapNotNull { g ->
            val text = joinLines(g.map { it.text })
            if (!text.any { it.isLetter() }) return@mapNotNull null
            var box = g.first().viewBox
            g.forEach { box = box.union(it.viewBox) }
            val st = detector.detect(bitmap, scale, g.first())
            TransUnit(page, text, box, st.size, st.colorArgb, FontFamily.SANS, st.bold, false, Alignment.LEFT, ocrBoxes = g.map { it.viewBox })
        }
    }

    /**
     * Setzt die Übersetzungen ein. [source] ist die Eingabedatei (für das Rendern gescannter Seiten).
     */
    fun apply(doc: PDDocument, items: List<Pair<TransUnit, String>>, fonts: FontLibrary, source: File, tmpDir: File) {
        val cache = DocFontCache(doc, fonts)
        for ((page, list) in items.groupBy { it.first.page }) {
            val pdPage = doc.getPage(page)
            val g = PdfIo.geometry(pdPage)
            val digital = list.filter { it.first.lineIds.isNotEmpty() }
            val scanned = list.filter { it.first.ocrBoxes.isNotEmpty() }
            val encoders = HashMap<TransUnit, TextRunEncoder>()
            if (digital.isNotEmpty()) {
                val pt = PageAnalyzer.analyze(doc, page)
                // Originalschrift je Absatz: so bleibt das Schriftbild erhalten, wo die Glyphen vorhanden sind
                for ((u, _) in digital) {
                    val fontId = pt.lines.getOrNull(u.lineIds.first())?.fontId ?: continue
                    val f = pt.fonts.fonts[fontId]
                    val meta = pt.fontMeta(fontId)
                    val known = HashMap<String, Int>()
                    pt.glyphs.filter { it.fontId == fontId && it.unicode.isNotEmpty() }.forEach { known.putIfAbsent(it.unicode, it.code) }
                    encoders[u] = TextRunEncoder(f, known) { cache.matching(meta.rawName, u.family, u.bold, u.italic) }
                }
                val removal = HashMap<Int, MutableSet<Int>>()
                digital.flatMap { it.first.lineIds }.forEach { id ->
                    pt.lines.getOrNull(id)?.glyphs?.forEach { gi ->
                        val gl = pt.glyphs[gi]
                        if (gl.opIndex >= 0) removal.getOrPut(gl.opIndex) { HashSet() }.add(gl.glyphIndex)
                    }
                }
                ContentRewriter.removeGlyphs(doc, pdPage, pt, removal)
            }
            if (scanned.isNotEmpty()) {
                val scale = OcrEngine.OCR_SCALE
                val bmp = PageRaster.render(source, page, scale, tmpDir).copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(bmp)
                val paint = Paint()
                paint.setStyle(Paint.Style.FILL)
                scanned.flatMap { it.first.ocrBoxes }.forEach { b ->
                    val r = RectF(max(0f, b.left * scale - 2), max(0f, b.top * scale - 2), b.right * scale + 2, b.bottom * scale + 2)
                    paint.color = ScanEditEngine.borderMedian(bmp, r)
                    canvas.drawRect(r, paint)
                }
                pdPage.resources = PDResources()
                val img = JPEGFactory.createFromImage(doc, bmp, 0.9f)
                val cb = ContentBuilder(pdPage.resources)
                cb.image(img, g.imageMatrix(g.viewWidth / 2f, g.viewHeight / 2f, g.viewWidth, g.viewHeight, 0f))
                PdfIo.replacePageContent(doc, pdPage, cb.tokens)
                bmp.recycle()
            }
            val cb = ContentBuilder(PdfIo.resourcesOf(pdPage))
            for ((u, translated) in list) {
                val enc = encoders[u] ?: TextRunEncoder(null, emptyMap()) { cache.get(u.family, u.bold, u.italic) }
                drawFitted(cb, enc, g, u, translated)
            }
            PdfIo.appendToPage(doc, pdPage, cb.tokens)
        }
    }

    private fun drawFitted(cb: ContentBuilder, enc: TextRunEncoder, g: PageGeometry, u: TransUnit, raw: String) {
        val text = raw.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return
        val width = max(u.box.width, 20f)
        val avail = u.box.height + u.size * 0.35f
        var size = u.size
        var lines: List<String> = emptyList()
        while (true) {
            val s = size
            lines = TextLayout.wrap(text, width) { enc.width(it) / 1000f * s }
            if (lines.size * s * 1.18f <= avail || size <= 5f) break
            size *= 0.93f
        }
        val lead = size * 1.18f
        var v = u.box.top + size * 0.88f
        for (l in lines) {
            if (l.isNotEmpty()) {
                val runs = enc.encode(l)
                val w = runs.sumOf { it.widthUnits.toDouble() }.toFloat() / 1000f * size
                var x = TextLayout.alignX(u.alignment, u.box.left, u.box.left + width, w)
                for (r in runs) {
                    cb.text(r.font, size, g.uprightMatrixAt(x, v), r.elements, u.colorArgb)
                    x += r.widthUnits / 1000f * size
                }
            }
            v += lead
        }
    }
}

/** Schriftgröße/Farbe einer Scan-Zeile (ohne eigene Schriftbibliothek nutzbar). */
object ScanEditEngineDetector {
    fun detect(bitmap: Bitmap, scale: Float, line: OcrLine): ScanEditEngine.ScanStyle {
        val px = RectF(
            max(0f, line.viewBox.left * scale), max(0f, line.viewBox.top * scale),
            kotlin.math.min(bitmap.width.toFloat(), line.viewBox.right * scale), kotlin.math.min(bitmap.height.toFloat(), line.viewBox.bottom * scale),
        )
        val size = (line.viewBox.height / 1.18f).coerceIn(4f, 72f)
        return ScanEditEngine.ScanStyle(
            FontFamily.SANS, ScanEditEngine.inkRatio(bitmap, px) > 0.28f, false, (size * 10).toInt() / 10f,
            ScanEditEngine.darkestMedian(bitmap, px),
        )
    }
}
