package de.fapdf.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlin.math.max

/** Gewünschte Formatierung für neuen oder geänderten Text. */
data class TextStyle(
    /** true = möglichst die im Dokument eingebettete Originalschrift verwenden. */
    val useOriginalFont: Boolean = true,
    val family: FontFamily = FontFamily.SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** Schriftgröße in PDF-Punkten. */
    val size: Float = 11f,
    val colorArgb: Int = 0xFF000000.toInt(),
)

/** Automatisch erkannte Formatierung einer Zeile. */
data class DetectedStyle(
    val fontName: String,
    val family: FontFamily,
    val bold: Boolean,
    val italic: Boolean,
    val size: Float,
    val colorArgb: Int,
    val embedded: Boolean,
    val alignment: Alignment,
    val lineSpacing: Float,
) {
    fun toTextStyle() = TextStyle(true, family, bold, italic, size, colorArgb)
}

data class EditOutcome(val note: String? = null)

/**
 * Echte Textbearbeitung digitaler PDFs:
 * 1. Originalglyphen werden aus dem Inhaltsstrom entfernt ([ContentRewriter]).
 * 2. Der neue Text wird an derselben Stelle neu gesetzt – bevorzugt mit der eingebetteten
 *    Originalschrift, sonst mit einer optisch und metrisch passenden Ersatzschrift.
 */
class TextEditEngine(private val fontLibrary: FontLibrary) {

    private class Encoded(val font: PDFont, val elements: List<Any>, val widthUnits: Float, val usedOriginal: Boolean)

    fun detectStyle(pt: PageText, lineIds: List<Int>): DetectedStyle {
        val lines = lineIds.map { pt.lines[it] }
        val first = lines.first()
        val meta = pt.fontMeta(first.fontId)
        val spacing = if (lines.size > 1) (lines.first().baseline - lines.last().baseline) / (lines.size - 1) else first.size * 1.2f
        val block = pt.blockOfLine(first.id)
        val alignLines = if (lines.size > 1) lines else block?.lineIds?.map { pt.lines[it] } ?: lines
        val g = pt.geometry
        return DetectedStyle(
            fontName = meta.displayName, family = meta.family, bold = meta.bold, italic = meta.italic,
            size = (first.size * 10).toInt() / 10f, colorArgb = first.colorArgb, embedded = meta.embedded,
            alignment = TextLayout.guessAlignment(alignLines, g.cropLeft, g.cropLeft + g.cropWidth),
            lineSpacing = spacing,
        )
    }

    private fun encode(
        doc: PDDocument, cache: DocFontCache, pt: PageText, text: String, style: TextStyle,
        originalFontId: Int?, glyphMaps: HashMap<Int, Map<String, Int>>,
    ): Encoded {
        if (style.useOriginalFont && originalFontId != null) {
            val f = pt.fonts.fonts[originalFontId]
            FontSupport.encodeIfComplete(f, text)?.let { bytes ->
                return Encoded(f, listOf(bytes), FontSupport.widthOfBytes(f, bytes), true)
            }
            // Zeichen aus bereits vorhandenen Glyphen derselben Schrift zusammensetzen
            val pageMap = HashMap<String, Int>()
            pt.glyphs.filter { it.fontId == originalFontId && it.unicode.length == 1 }.forEach { pageMap.putIfAbsent(it.unicode, it.code) }
            reuseGlyphs(f, text, pageMap)?.let { return it }
            val docMap = glyphMaps.getOrPut(originalFontId) { PageAnalyzer.glyphMapFor(doc, f.cosObject) }
            reuseGlyphs(f, text, docMap + pageMap)?.let { return it }
        }
        val sub = cache.get(style.family, style.bold, style.italic)
        val clean = sanitizeFor(sub, text)
        clean.codePoints().forEach { sub.addToSubset(it) }
        val bytes = sub.encode(clean)
        return Encoded(sub, listOf(bytes), sub.getStringWidth(clean), false)
    }

    private fun reuseGlyphs(f: PDFont, text: String, map: Map<String, Int>): Encoded? {
        val elements = ArrayList<Any>()
        val buf = java.io.ByteArrayOutputStream()
        var width = 0f
        for (ch in text) {
            val s = ch.toString()
            val code = map[s]
            if (code == null) {
                if (ch == ' ') {
                    if (buf.size() > 0) { elements.add(buf.toByteArray()); buf.reset() }
                    val sw = FontSupport.spaceWidth(f)
                    elements.add(-sw); width += sw
                    continue
                }
                return null
            }
            val bytes = FontSupport.codeToBytes(f, code) ?: return null
            buf.write(bytes)
            width += runCatching { f.getWidth(code) }.getOrDefault(500f)
        }
        if (buf.size() > 0) elements.add(buf.toByteArray())
        return Encoded(f, elements, width, true)
    }

    /** Entfernt Zeichen, die selbst die Ersatzschrift nicht darstellen kann (z. B. Emojis). */
    private fun sanitizeFor(font: PDType0Font, text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val s = String(Character.toChars(cp))
            val ok = runCatching { font.encode(s); true }.getOrDefault(false)
            sb.append(if (ok) s else "?")
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun originalFontNote(pt: PageText, fontId: Int, style: TextStyle, results: List<Encoded>): String? {
        if (!style.useOriginalFont || results.all { it.usedOriginal }) return null
        val meta = pt.fontMeta(fontId)
        return "Die Originalschrift „${meta.displayName}“ enthält nicht alle benötigten Zeichen. " +
            "Es wurde die passende Ersatzschrift (${style.family.title}${if (style.bold) ", fett" else ""}${if (style.italic) ", kursiv" else ""}) verwendet."
    }

    private fun removalMap(pt: PageText, glyphIdx: Collection<Int>): Map<Int, Set<Int>> {
        val m = HashMap<Int, MutableSet<Int>>()
        glyphIdx.forEach { i ->
            val g = pt.glyphs[i]
            if (g.opIndex >= 0) m.getOrPut(g.opIndex) { HashSet() }.add(g.glyphIndex)
        }
        return m
    }

    /**
     * Ersetzt eine oder mehrere Zeilen. Bei [asBlock] wird der neue Text innerhalb der
     * Breite des Originalabsatzes neu umbrochen.
     */
    fun replaceText(
        doc: PDDocument, pageIndex: Int, lineIds: List<Int>, newText: String,
        style: TextStyle, alignment: Alignment, asBlock: Boolean,
    ): EditOutcome {
        val page = doc.getPage(pageIndex)
        val pt = PageAnalyzer.analyze(doc, pageIndex)
        val lines = lineIds.mapNotNull { pt.lines.getOrNull(it) }
        if (lines.isEmpty()) throw PdfUserException("Die ausgewählte Textzeile wurde nicht gefunden.")
        if (lines.any { !it.directlyEditable }) {
            throw PdfUserException(
                "Dieser Text liegt in einem eingebetteten Objekt und kann nicht direkt geändert werden. " +
                    "Verwenden Sie „OCR“ → „Seite als Bild bearbeiten“."
            )
        }
        val fontId = lines.first().fontId
        val cache = DocFontCache(doc, fontLibrary)
        val maps = HashMap<Int, Map<String, Int>>()
        val left = lines.minOf { it.startX }
        val right = lines.maxOf { it.endX }
        val texts = if (asBlock) {
            TextLayout.wrap(newText, right - left) { s ->
                encode(doc, cache, pt, s, style, fontId, maps).widthUnits / 1000f * style.size
            }
        } else listOf(newText.replace('\n', ' '))
        val encoded = texts.map { encode(doc, cache, pt, it, style, fontId, maps) }

        ContentRewriter.removeGlyphs(doc, page, pt, removalMap(pt, lines.flatMap { it.glyphs }))

        val cb = ContentBuilder(PdfIo.resourcesOf(page))
        val spacing = if (lines.size > 1) (lines.first().baseline - lines.last().baseline) / (lines.size - 1) else style.size * 1.2f
        var baseline = lines.first().baseline
        encoded.forEachIndexed { i, e ->
            if (texts[i].isNotEmpty()) {
                val w = e.widthUnits / 1000f * style.size
                val x = TextLayout.alignX(alignment, left, right, w)
                cb.text(e.font, style.size, Affine.translate(x, baseline), e.elements, style.colorArgb)
            }
            baseline -= spacing
        }
        PdfIo.appendToPage(doc, page, cb.tokens)
        val overflow = asBlock && texts.size > lines.size
        val note = listOfNotNull(
            originalFontNote(pt, fontId, style, encoded),
            if (overflow) "Der neue Text ist länger als der ursprüngliche Absatz und kann nachfolgenden Inhalt überlappen." else null,
        ).joinToString("\n").ifBlank { null }
        return EditOutcome(note)
    }

    fun deleteLines(doc: PDDocument, pageIndex: Int, lineIds: List<Int>) {
        val page = doc.getPage(pageIndex)
        val pt = PageAnalyzer.analyze(doc, pageIndex)
        val lines = lineIds.mapNotNull { pt.lines.getOrNull(it) }
        if (lines.any { !it.directlyEditable }) throw PdfUserException("Dieser Text liegt in einem eingebetteten Objekt und kann nicht direkt gelöscht werden.")
        ContentRewriter.removeGlyphs(doc, page, pt, removalMap(pt, lines.flatMap { it.glyphs }))
    }

    /** Verschiebt eine Zeile um (du, dv) Ansichtspunkte – Glyphe für Glyphe in Originalschrift. */
    fun moveLine(doc: PDDocument, pageIndex: Int, lineId: Int, du: Float, dv: Float) {
        val page = doc.getPage(pageIndex)
        val pt = PageAnalyzer.analyze(doc, pageIndex)
        val line = pt.lines.getOrNull(lineId) ?: throw PdfUserException("Zeile nicht gefunden.")
        if (!line.directlyEditable) throw PdfUserException("Dieser Text liegt in einem eingebetteten Objekt und kann nicht verschoben werden.")
        val g = pt.geometry
        val (x0, y0) = g.viewToUser(0f, 0f)
        val (x1, y1) = g.viewToUser(du, dv)
        val dx = x1 - x0
        val dy = y1 - y0
        val cache = DocFontCache(doc, fontLibrary)
        val cb = ContentBuilder(PdfIo.resourcesOf(page))
        for (i in line.glyphs) {
            val gl = pt.glyphs[i]
            if (gl.unicode.isBlank() && gl.width <= 0f) continue
            val font = pt.fonts.fonts[gl.fontId]
            val bytes = FontSupport.codeToBytes(font, gl.code)
            if (bytes != null) {
                cb.text(font, gl.size, Affine.translate(gl.x + dx, gl.y + dy), listOf(bytes), gl.colorArgb)
            } else {
                val meta = pt.fontMeta(gl.fontId)
                val sub = cache.get(meta.family, meta.bold, meta.italic)
                val s = sanitizeFor(sub, gl.unicode)
                s.codePoints().forEach { sub.addToSubset(it) }
                cb.text(sub, gl.size, Affine.translate(gl.x + dx, gl.y + dy), listOf(sub.encode(s)), gl.colorArgb)
            }
        }
        ContentRewriter.removeGlyphs(doc, page, pt, removalMap(pt, line.glyphs))
        PdfIo.appendToPage(doc, page, cb.tokens)
    }

    /** Fügt neuen Text ein. (u, v) = Grundlinienbeginn in Ansichtspunkten. */
    fun addText(doc: PDDocument, pageIndex: Int, u: Float, v: Float, text: String, style: TextStyle, originalFontId: Int? = null): EditOutcome {
        val page = doc.getPage(pageIndex)
        val pt = PageAnalyzer.analyze(doc, pageIndex)
        val cache = DocFontCache(doc, fontLibrary)
        val maps = HashMap<Int, Map<String, Int>>()
        val cb = ContentBuilder(PdfIo.resourcesOf(page))
        var vv = v
        val results = ArrayList<Encoded>()
        for (line in text.split('\n')) {
            if (line.isNotEmpty()) {
                val e = encode(doc, cache, pt, line, style, originalFontId, maps)
                results.add(e)
                val m = pt.geometry.uprightMatrixAt(u, vv)
                cb.text(e.font, style.size, m, e.elements, style.colorArgb)
            }
            vv += style.size * 1.2f
        }
        PdfIo.appendToPage(doc, page, cb.tokens)
        return EditOutcome(originalFontId?.let { originalFontNote(pt, it, style, results) })
    }

    /** Fügt ein Bild (Foto oder Unterschrift mit Transparenz) ein. Maße in Ansichtspunkten. */
    fun insertImage(
        doc: PDDocument, pageIndex: Int, bitmap: Bitmap, cx: Float, cy: Float, w: Float, h: Float,
        rotationDeg: Float, opacity: Float = 1f,
    ) {
        val page = doc.getPage(pageIndex)
        val g = PdfIo.geometry(page)
        val src = downscale(bitmap, 2400)
        val img = if (src.hasAlpha()) LosslessFactory.createFromImage(doc, src) else JPEGFactory.createFromImage(doc, src, 0.9f)
        val cb = ContentBuilder(PdfIo.resourcesOf(page))
        cb.save()
        if (opacity < 1f) cb.opacity(opacity)
        cb.image(img, g.imageMatrix(cx, cy, w, h, rotationDeg))
        cb.restore()
        PdfIo.appendToPage(doc, page, cb.tokens)
    }

    companion object {
        fun downscale(b: Bitmap, maxSide: Int): Bitmap {
            val m = max(b.width, b.height)
            if (m <= maxSide) return b
            val f = maxSide.toFloat() / m
            return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
        }
    }
}
