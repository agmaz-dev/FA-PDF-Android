package de.fapdf.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter

enum class MarkupType(val subtype: String, val title: String) {
    HIGHLIGHT("Highlight", "Markieren"),
    UNDERLINE("Underline", "Unterstreichen"),
    STRIKEOUT("StrikeOut", "Durchstreichen"),
}

/**
 * Standardkonforme PDF-Kommentare (Anmerkungen) mit eigenem Erscheinungsbild,
 * damit sie in jedem PDF-Programm – und nach dem Abflachen – korrekt aussehen.
 */
object Annotator {
    private const val AUTHOR = "FA PDF"

    /** Markiert/unterstreicht/durchstreicht den Text im Ansichtsrechteck. @return Anzahl Zeilen. */
    fun addMarkup(doc: PDDocument, pageIndex: Int, viewRect: Box, type: MarkupType, colorArgb: Int): Int {
        val page = doc.getPage(pageIndex)
        val pt = PageAnalyzer.analyze(doc, pageIndex)
        val userRect = pt.geometry.viewBoxToUser(viewRect)
        val hit = pt.glyphsIn(userRect, includeInvisible = true).toSet()
        val quads = ArrayList<Box>()
        val allLines = pt.lines + pt.hiddenLines
        for (line in allLines) {
            val sel = line.glyphs.filter { it in hit }
            if (sel.isEmpty()) continue
            var b = pt.glyphs[sel.first()].bbox
            sel.forEach { b = b.union(pt.glyphs[it].bbox) }
            quads.add(b)
        }
        if (quads.isEmpty()) quads.add(userRect) // z. B. auf Scans ohne Textebene: Bereich direkt markieren
        var rect = quads.first()
        quads.forEach { rect = rect.union(it) }
        rect = rect.inset(1f)

        val dict = COSDictionary()
        dict.setName(COSName.TYPE, "Annot")
        dict.setName(COSName.SUBTYPE, type.subtype)
        val qp = FloatArray(quads.size * 8)
        quads.forEachIndexed { i, q ->
            // Reihenfolge wie Acrobat: oben links, oben rechts, unten links, unten rechts
            val v = floatArrayOf(q.left, q.bottom, q.right, q.bottom, q.left, q.top, q.right, q.top)
            System.arraycopy(v, 0, qp, i * 8, 8)
        }
        dict.setItem(COSName.getPDFName("QuadPoints"), floatArray(qp))
        common(dict, rect, colorArgb, if (type == MarkupType.HIGHLIGHT) 1f else 1f)

        val ap = newAppearance(doc, rect)
        val cb = ContentBuilder(ap.resources)
        if (type == MarkupType.HIGHLIGHT) {
            val gs = PDExtendedGraphicsState()
            gs.blendMode = BlendMode.MULTIPLY
            gs.nonStrokingAlphaConstant = 0.45f
            cb.graphicsState(gs)
            cb.fillRgb(colorArgb)
            quads.forEach { cb.rect(it.left, it.top, it.width, it.height) }
            cb.fill()
        } else {
            cb.strokeRgb(colorArgb)
            quads.forEach { q ->
                val h = q.height
                cb.lineWidth(maxOf(0.6f, h * 0.07f))
                val y = if (type == MarkupType.UNDERLINE) q.top + h * 0.12f else q.top + h * 0.42f
                cb.moveTo(q.left, y); cb.lineTo(q.right, y)
            }
            cb.stroke()
        }
        writeAppearance(ap, cb)
        addAnnotation(page, dict, ap)
        return quads.size
    }

    /** Haftnotiz an einer Stelle (Ansichtskoordinaten). */
    fun addNote(doc: PDDocument, pageIndex: Int, u: Float, v: Float, text: String, colorArgb: Int) {
        val page = doc.getPage(pageIndex)
        val g = PdfIo.geometry(page)
        val (x, y) = g.viewToUser(u, v)
        val rect = Box(x, y - 22f, x + 22f, y)
        val dict = COSDictionary()
        dict.setName(COSName.TYPE, "Annot")
        dict.setName(COSName.SUBTYPE, "Text")
        dict.setName(COSName.NAME, "Comment")
        dict.setString(COSName.CONTENTS, text)
        common(dict, rect, colorArgb, 1f)
        val ap = newAppearance(doc, rect)
        val cb = ContentBuilder(ap.resources)
        cb.fillRgb(colorArgb).rect(rect.left + 1, rect.top + 1, rect.width - 2, rect.height - 2).fill()
        cb.strokeRgb(0xFF5D4300.toInt()).lineWidth(1.2f)
        for (i in 1..3) {
            val ly = rect.bottom - 5f - i * 4f
            cb.moveTo(rect.left + 5f, ly).lineTo(rect.right - 5f, ly)
        }
        cb.stroke()
        writeAppearance(ap, cb)
        addAnnotation(page, dict, ap)
    }

    /** Freihandzeichnung. Striche in Ansichtskoordinaten. */
    fun addInk(doc: PDDocument, pageIndex: Int, strokes: List<List<Pair<Float, Float>>>, colorArgb: Int, widthPt: Float) {
        val usable = strokes.filter { it.size >= 2 }
        if (usable.isEmpty()) return
        val page = doc.getPage(pageIndex)
        val g = PdfIo.geometry(page)
        val userStrokes = usable.map { s -> s.map { (u, v) -> g.viewToUser(u, v) } }
        val rect = Box.bounding(userStrokes.flatten()).inset(widthPt + 1f)
        val dict = COSDictionary()
        dict.setName(COSName.TYPE, "Annot")
        dict.setName(COSName.SUBTYPE, "Ink")
        val inkList = COSArray()
        userStrokes.forEach { s ->
            inkList.add(floatArray(s.flatMap { listOf(it.first, it.second) }.toFloatArray()))
        }
        dict.setItem(COSName.getPDFName("InkList"), inkList)
        val bs = COSDictionary()
        bs.setFloat(COSName.W, widthPt)
        dict.setItem(COSName.BS, bs)
        common(dict, rect, colorArgb, 1f)
        val ap = newAppearance(doc, rect)
        val cb = ContentBuilder(ap.resources)
        cb.strokeRgb(colorArgb).lineWidth(widthPt).lineCapRound()
        userStrokes.forEach { s ->
            cb.moveTo(s.first().first, s.first().second)
            for (i in 1 until s.size) cb.lineTo(s[i].first, s[i].second)
        }
        cb.stroke()
        writeAppearance(ap, cb)
        addAnnotation(page, dict, ap)
    }

    // ---------------- Kommentarliste ----------------

    private val listed = setOf("Text", "Highlight", "Underline", "StrikeOut", "Ink", "FreeText", "Squiggly", "Square", "Circle", "Line", "Stamp", "Polygon", "PolyLine", "Caret", "FileAttachment")

    /** Alle Kommentare des Dokuments (ohne Formularfelder, Links und Antwort-Notizen), mit ihren Antworten. */
    fun listComments(doc: PDDocument): List<CommentInfo> {
        val out = ArrayList<CommentInfo>()
        for (p in 0 until doc.numberOfPages) {
            val page = doc.getPage(p)
            val annots = runCatching { page.annotations }.getOrNull() ?: continue
            val g = PdfIo.geometry(page)
            val replies = HashMap<COSDictionary, MutableList<CommentReply>>()
            annots.forEach { a ->
                val d = a.cosObject
                val parent = d.getDictionaryObject(COSName.getPDFName("IRT")) as? COSDictionary ?: return@forEach
                replies.getOrPut(parent) { ArrayList() }.add(
                    CommentReply(d.getString(COSName.T) ?: "", d.getString(COSName.CONTENTS) ?: "", parseDate(d.getString(COSName.M)))
                )
            }
            annots.forEachIndexed { i, a ->
                val d = a.cosObject
                val sub = d.getNameAsString(COSName.SUBTYPE) ?: return@forEachIndexed
                if (sub !in listed || d.containsKey(COSName.getPDFName("IRT"))) return@forEachIndexed
                val r = a.rectangle ?: return@forEachIndexed
                val view = g.userBoxToView(Box(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY))
                out.add(
                    CommentInfo(
                        page = p, index = i, subtype = sub,
                        author = d.getString(COSName.T) ?: "",
                        text = d.getString(COSName.CONTENTS) ?: "",
                        modified = parseDate(d.getString(COSName.M)),
                        viewBox = view,
                        replies = replies[d].orEmpty().sortedBy { it.modified ?: 0L },
                    )
                )
            }
        }
        return out
    }

    /** Entfernt einen Kommentar samt Antworten und zugehörigem Popup. */
    fun deleteComment(doc: PDDocument, pageIndex: Int, index: Int) {
        val page = doc.getPage(pageIndex)
        val list = page.annotations.toMutableList()
        val target = list.getOrNull(index)?.cosObject ?: throw PdfUserException("Kommentar nicht gefunden.")
        val popup = target.getDictionaryObject(COSName.getPDFName("Popup")) as? COSDictionary
        list.removeAll { a ->
            val d = a.cosObject
            d === target || d === popup || d.getDictionaryObject(COSName.getPDFName("IRT")) === target ||
                d.getDictionaryObject(COSName.PARENT) === target
        }
        page.annotations = list
    }

    /** Antwort auf einen Kommentar (PDF-Standard: Text-Anmerkung mit /IRT und /RT /R). */
    fun addReply(doc: PDDocument, pageIndex: Int, index: Int, text: String) {
        val page = doc.getPage(pageIndex)
        val parent = page.annotations.getOrNull(index)?.cosObject ?: throw PdfUserException("Kommentar nicht gefunden.")
        val dict = COSDictionary()
        dict.setName(COSName.TYPE, "Annot")
        dict.setName(COSName.SUBTYPE, "Text")
        dict.setName(COSName.NAME, "Comment")
        dict.setString(COSName.CONTENTS, text)
        dict.setItem(COSName.getPDFName("IRT"), parent)
        dict.setName(COSName.getPDFName("RT"), "R")
        dict.setItem(COSName.RECT, parent.getDictionaryObject(COSName.RECT))
        dict.setString(COSName.T, AUTHOR)
        dict.setString(COSName.M, pdfDate(System.currentTimeMillis()))
        dict.setInt(COSName.F, 4 or 2) // Drucken, versteckt (Antworten haben kein eigenes Symbol)
        val list = page.annotations.toMutableList()
        list.add(PDAnnotation.createAnnotation(dict))
        page.annotations = list
    }

    fun pdfDate(millis: Long): String {
        val f = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
        val tz = java.util.TimeZone.getDefault()
        val off = tz.getOffset(millis) / 60000
        val sign = if (off >= 0) "+" else "-"
        val a = kotlin.math.abs(off)
        return "D:" + f.format(java.util.Date(millis)) + sign + "%02d'%02d'".format(a / 60, a % 60)
    }

    /** Liest ein PDF-Datum (D:YYYYMMDDHHmmSS±HH'mm'); null bei unbekanntem Format. */
    fun parseDate(s: String?): Long? {
        if (s == null) return null
        val t = s.removePrefix("D:")
        if (t.length < 8 || !t.take(8).all { it.isDigit() }) return null
        val digits = t.takeWhile { it.isDigit() }.padEnd(14, '0').take(14)
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt().coerceIn(1, 12) - 1, digits.substring(6, 8).toInt().coerceIn(1, 31),
            digits.substring(8, 10).toInt(), digits.substring(10, 12).toInt(), digits.substring(12, 14).toInt())
        val rest = t.drop(t.takeWhile { it.isDigit() }.length)
        var offsetMin = 0
        if (rest.isNotEmpty() && (rest[0] == '+' || rest[0] == '-')) {
            val nums = rest.drop(1).split('\'').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
            offsetMin = (nums.getOrElse(0) { 0 } * 60 + nums.getOrElse(1) { 0 }) * if (rest[0] == '-') -1 else 1
            return cal.timeInMillis - offsetMin * 60000L
        }
        if (rest.startsWith("Z")) return cal.timeInMillis
        // ohne Zeitzone: als Ortszeit deuten
        return cal.timeInMillis - java.util.TimeZone.getDefault().getOffset(cal.timeInMillis)
    }

    private fun floatArray(values: FloatArray): COSArray {
        val a = COSArray()
        values.forEach { a.add(COSFloat(it)) }
        return a
    }

    private fun common(dict: COSDictionary, rect: Box, colorArgb: Int, opacity: Float) {
        dict.setItem(COSName.RECT, PDRectangle(rect.left, rect.top, rect.width, rect.height).cosArray)
        dict.setItem(COSName.C, floatArray(floatArrayOf(
            ((colorArgb shr 16) and 0xFF) / 255f, ((colorArgb shr 8) and 0xFF) / 255f, (colorArgb and 0xFF) / 255f,
        )))
        dict.setString(COSName.T, AUTHOR)
        dict.setString(COSName.M, pdfDate(System.currentTimeMillis()))
        dict.setInt(COSName.F, 4) // Drucken
        if (opacity < 1f) dict.setFloat(COSName.CA, opacity)
    }

    private fun newAppearance(doc: PDDocument, rect: Box): PDAppearanceStream {
        val ap = PDAppearanceStream(doc)
        ap.bBox = PDRectangle(rect.left, rect.top, rect.width, rect.height)
        ap.resources = PDResources()
        return ap
    }

    private fun writeAppearance(ap: PDAppearanceStream, cb: ContentBuilder) {
        ap.cosObject.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(cb.tokens) }
    }

    private fun addAnnotation(page: PDPage, dict: COSDictionary, ap: PDAppearanceStream) {
        val apDict = PDAppearanceDictionary()
        apDict.setNormalAppearance(ap)
        dict.setItem(COSName.AP, apDict.cosObject)
        val annotation = PDAnnotation.createAnnotation(dict)
        val list = page.annotations.toMutableList()
        list.add(annotation)
        page.annotations = list
    }
}

data class CommentReply(val author: String, val text: String, val modified: Long?)

data class CommentInfo(
    val page: Int, val index: Int, val subtype: String, val author: String, val text: String,
    val modified: Long?, val viewBox: Box, val replies: List<CommentReply>,
) {
    val typeTitle: String get() = when (subtype) {
        "Text" -> "Notiz"; "Highlight" -> "Hervorhebung"; "Underline" -> "Unterstreichung"; "StrikeOut" -> "Durchstreichung"
        "Ink" -> "Zeichnung"; "FreeText" -> "Textfeld"; "Stamp" -> "Stempel"; else -> "Kommentar"
    }
}

data class RedactionReport(val pages: Int, val removedGlyphs: Int, val rasterizedPages: List<Int>)

/**
 * Echtes Schwärzen: Text wird aus dem Inhaltsstrom entfernt, Anmerkungen im Bereich gelöscht,
 * und danach wird geprüft, ob im Bereich noch Text vorhanden ist. Liegen Bilder oder
 * eingebettete Objekte im Bereich, wird die Seite als Bild neu aufgebaut (Pixel werden schwarz).
 */
class Redactor(private val tmpDir: java.io.File) {
    fun apply(
        doc: PDDocument, rectsByPage: Map<Int, List<Box>>, forceRaster: Boolean, removeMetadata: Boolean,
        scanEngine: ScanEditEngine,
    ): RedactionReport {
        var removed = 0
        val rasterized = ArrayList<Int>()
        // Formularfelder im Bereich zuerst einbrennen, damit ihre Werte sicher mit entfernt werden
        val widgetHit = rectsByPage.any { (p, rects) ->
            val g = PdfIo.geometry(doc.getPage(p))
            doc.getPage(p).annotations.any { a ->
                a.subtype == "Widget" && a.rectangle?.let { r -> rects.any { g.viewBoxToUser(it).intersects(Box(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY)) } } == true
            }
        }
        if (widgetHit) PdfOps.flattenAll(doc)

        for ((pageIndex, viewRects) in rectsByPage) {
            if (viewRects.isEmpty()) continue
            val page = doc.getPage(pageIndex)
            var pt = PageAnalyzer.analyze(doc, pageIndex)
            val userRects = viewRects.map { pt.geometry.viewBoxToUser(it) }
            val hit = userRects.flatMap { pt.glyphsIn(it, includeInvisible = true) }.toSet()
            val imageHit = pt.images.any { im -> userRects.any { it.intersects(im.bbox) } }
            var needRaster = forceRaster || imageHit || hit.any { pt.glyphs[it].opIndex < 0 }

            // Anmerkungen im Bereich entfernen
            page.annotations = page.annotations.filterNot { a ->
                val r = a.rectangle ?: return@filterNot false
                val box = Box(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY)
                userRects.any { it.intersects(box) }
            }

            if (!needRaster) {
                val m = HashMap<Int, MutableSet<Int>>()
                hit.forEach { i -> val g = pt.glyphs[i]; m.getOrPut(g.opIndex) { HashSet() }.add(g.glyphIndex) }
                ContentRewriter.removeGlyphs(doc, page, pt, m)
                removed += hit.size
                val cb = ContentBuilder(PdfIo.resourcesOf(page))
                cb.fillRgb(0xFF000000.toInt())
                userRects.forEach { cb.rect(it.left, it.top, it.width, it.height) }
                cb.fill()
                PdfIo.appendToPage(doc, page, cb.tokens)
                // Kontrolle: Ist im Bereich wirklich kein Text mehr vorhanden?
                pt = PageAnalyzer.analyze(doc, pageIndex)
                if (userRects.any { r -> pt.glyphsIn(r, includeInvisible = true).isNotEmpty() }) needRaster = true
            }
            if (needRaster) {
                val tmp = java.io.File(tmpDir, "redact_${System.nanoTime()}.pdf")
                try {
                    PdfIo.save(doc, tmp)
                    val scale = 2.5f
                    val bmp = PageRaster.render(tmp, pageIndex, scale, tmpDir)
                    scanEngine.rasterizePage(doc, pageIndex, bmp, viewRects, scale)
                } finally {
                    tmp.delete()
                }
                rasterized.add(pageIndex)
            }
        }
        if (removeMetadata) {
            doc.documentInformation = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()
            doc.documentCatalog.metadata = null
        }
        return RedactionReport(rectsByPage.count { it.value.isNotEmpty() }, removed, rasterized)
    }
}
