package de.fapdf.pdf

import kotlin.math.abs
import kotlin.math.max

/**
 * Ein einzelnes dargestelltes Zeichen (Glyphe) einer PDF-Seite.
 * Koordinaten in PDF-Benutzerkoordinaten (Ursprung unten links).
 */
data class GlyphInfo(
    /** Index des Text-Operators im Seiteninhalt (oberste Ebene); -1 = in einem Form-XObject. */
    val opIndex: Int,
    /** Laufende Nummer der Glyphe innerhalb dieses Operators. */
    val glyphIndex: Int,
    val code: Int,
    val unicode: String,
    /** Grundlinienursprung. */
    val x: Float,
    val y: Float,
    /** Vorschubbreite in Benutzerkoordinaten. */
    val width: Float,
    val size: Float,
    val bbox: Box,
    /** Kompensation in TJ-Einheiten, damit nachfolgende Glyphen beim Entfernen an ihrer Stelle bleiben. */
    val tjAdvance: Float,
    val fontId: Int,
    val colorArgb: Int,
    val horizontal: Boolean,
    val invisible: Boolean,
)

data class TextLine(
    val id: Int,
    /** Indizes in die Glyphenliste der Seite, nach x sortiert. */
    val glyphs: List<Int>,
    val text: String,
    /** Für jedes Zeichen in [text]: Glyphenindex oder -1 (rekonstruiertes Leerzeichen). */
    val charToGlyph: IntArray,
    val bbox: Box,
    val baseline: Float,
    val startX: Float,
    val endX: Float,
    val size: Float,
    val fontId: Int,
    val colorArgb: Int,
    /** false, wenn Teile des Textes in eingebetteten Objekten liegen. */
    val directlyEditable: Boolean,
)

data class TextBlock(
    val id: Int,
    val lineIds: List<Int>,
    val bbox: Box,
    val lineSpacing: Float,
    val size: Float,
    val fontId: Int,
)

enum class Alignment { LEFT, CENTER, RIGHT }

object TextLayout {
    /** Legt Zeilen aus Glyphen an (nur waagerechte Glyphen). */
    fun buildLines(glyphs: List<GlyphInfo>, includeInvisible: Boolean): List<TextLine> {
        val idx = glyphs.indices.filter { i ->
            val g = glyphs[i]
            g.horizontal && (includeInvisible || !g.invisible) && g.size > 0.5f
        }
        if (idx.isEmpty()) return emptyList()
        // 1) Nach Grundlinie gruppieren
        val sortedByY = idx.sortedWith(compareByDescending<Int> { glyphs[it].y }.thenBy { glyphs[it].x })
        val clusters = ArrayList<MutableList<Int>>()
        var current = mutableListOf<Int>()
        var clusterY = Float.NaN
        var clusterSize = 0f
        for (i in sortedByY) {
            val g = glyphs[i]
            if (current.isEmpty() || abs(g.y - clusterY) <= 0.3f * max(clusterSize, g.size)) {
                if (current.isEmpty()) { clusterY = g.y; clusterSize = g.size }
                current.add(i)
            } else {
                clusters.add(current)
                current = mutableListOf(i); clusterY = g.y; clusterSize = g.size
            }
        }
        if (current.isNotEmpty()) clusters.add(current)

        // 2) Innerhalb einer Grundlinie nach x sortieren und an großen Lücken (Spalten) trennen
        val lines = ArrayList<TextLine>()
        for (cluster in clusters) {
            val byX = cluster.sortedBy { glyphs[it].x }
            var seg = mutableListOf<Int>()
            for (i in byX) {
                if (seg.isNotEmpty()) {
                    val p = glyphs[seg.last()]
                    val g = glyphs[i]
                    val gap = g.x - (p.x + p.width)
                    val em = max(p.size, g.size)
                    // Spalten/Tabellenzellen trennen: große Lücke ohne Leerzeichen-Glyphe (Wortabstand
                    // ist ≤ ~0,6 em, auch im Blocksatz) oder sehr große Lücke trotz Leerzeichen.
                    val spaceBetween = p.unicode.isBlank() || g.unicode.isBlank()
                    if ((!spaceBetween && gap > 1.0f * em) || gap > 3.0f * em) {
                        lines.add(makeLine(lines.size, seg, glyphs)); seg = mutableListOf()
                    }
                }
                seg.add(i)
            }
            if (seg.isNotEmpty()) lines.add(makeLine(lines.size, seg, glyphs))
        }
        // Lesereihenfolge: oben nach unten, links nach rechts
        return lines.sortedWith(compareByDescending<TextLine> { it.baseline }.thenBy { it.startX })
            .mapIndexed { n, l -> l.copy(id = n) }
            .filter { it.text.isNotBlank() }
            .mapIndexed { n, l -> l.copy(id = n) }
    }

    private fun makeLine(id: Int, seg: List<Int>, glyphs: List<GlyphInfo>): TextLine {
        val sb = StringBuilder()
        val map = ArrayList<Int>()
        var prev: GlyphInfo? = null
        for (i in seg) {
            val g = glyphs[i]
            val p = prev
            if (p != null) {
                // Doppelt gezeichnete Zeichen (künstlicher Fettdruck) nicht doppelt in den Text übernehmen
                if (abs(g.x - p.x) < 0.1f * g.size && g.unicode == p.unicode) continue
                val gap = g.x - (p.x + p.width)
                if (gap > 0.22f * max(p.size, g.size) && p.unicode != " " && g.unicode != " ") {
                    sb.append(' '); map.add(-1)
                }
            }
            for (ch in g.unicode) { sb.append(ch); map.add(i) }
            prev = g
        }
        val gl = seg.map { glyphs[it] }
        var box = gl.first().bbox
        gl.forEach { box = box.union(it.bbox) }
        val sizes = gl.map { it.size }.sorted()
        val fontId = gl.groupingBy { it.fontId }.eachCount().maxByOrNull { it.value }!!.key
        val color = gl.filter { it.unicode.isNotBlank() }.ifEmpty { gl }
            .groupingBy { it.colorArgb }.eachCount().maxByOrNull { it.value }!!.key
        val baseline = gl.groupingBy { (it.y * 10).toInt() }.eachCount().maxByOrNull { it.value }!!.key / 10f
        return TextLine(
            id = id, glyphs = seg, text = sb.toString(), charToGlyph = map.toIntArray(), bbox = box,
            baseline = baseline, startX = gl.minOf { it.x }, endX = gl.maxOf { it.x + it.width },
            size = sizes[sizes.size / 2], fontId = fontId, colorArgb = color,
            directlyEditable = gl.all { it.opIndex >= 0 },
        )
    }

    /** Fasst aufeinanderfolgende, gleichartige Zeilen zu Absätzen zusammen. */
    fun buildBlocks(lines: List<TextLine>): List<TextBlock> {
        val blocks = ArrayList<TextBlock>()
        val used = BooleanArray(lines.size)
        for (start in lines.indices) {
            if (used[start]) continue
            val ids = mutableListOf(start); used[start] = true
            var last = lines[start]
            var next = start + 1
            while (next < lines.size) {
                val cand = lines.indices.drop(next).firstOrNull { j ->
                    !used[j] && lines[j].baseline < last.baseline && overlapsHorizontally(lines[j], last)
                } ?: break
                val l = lines[cand]
                val gap = last.baseline - l.baseline
                val sameSize = abs(l.size - last.size) <= 0.15f * last.size
                val alignedLeft = abs(l.startX - last.startX) <= 1.5f * last.size
                val alignedCenter = abs((l.startX + l.endX) / 2 - (last.startX + last.endX) / 2) <= 1.5f * last.size
                if (sameSize && gap in (0.6f * last.size)..(1.9f * last.size) && (alignedLeft || alignedCenter)) {
                    ids.add(cand); used[cand] = true; last = l; next = cand + 1
                } else break
            }
            val ls = ids.map { lines[it] }
            var box = ls.first().bbox
            ls.forEach { box = box.union(it.bbox) }
            val spacing = if (ls.size > 1) (ls.first().baseline - ls.last().baseline) / (ls.size - 1) else ls.first().size * 1.2f
            blocks.add(TextBlock(blocks.size, ids, box, spacing, ls.first().size, ls.first().fontId))
        }
        return blocks
    }

    private fun overlapsHorizontally(a: TextLine, b: TextLine): Boolean =
        a.startX < b.endX && b.startX < a.endX

    /**
     * Bricht Text in Zeilen um. [measure] liefert die Breite eines Textes in Benutzerkoordinaten.
     * Explizite Zeilenumbrüche (\n) bleiben erhalten.
     */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            val words = para.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) { out.add(""); continue }
            var line = StringBuilder()
            for (w in words) {
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (line.isNotEmpty() && measure(candidate) > maxWidth) {
                    out.add(line.toString()); line = StringBuilder(w)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            out.add(line.toString())
        }
        return out
    }

    /** x-Position einer neu gesetzten Zeile entsprechend der Ausrichtung im Originalbereich. */
    fun alignX(alignment: Alignment, left: Float, right: Float, width: Float): Float = when (alignment) {
        Alignment.LEFT -> left
        Alignment.RIGHT -> right - width
        Alignment.CENTER -> (left + right) / 2f - width / 2f
    }

    /** Ermittelt die wahrscheinliche Ausrichtung eines Absatzes. */
    fun guessAlignment(lines: List<TextLine>, pageLeft: Float, pageRight: Float): Alignment {
        if (lines.size >= 2) {
            val lefts = lines.map { it.startX }
            val rights = lines.map { it.endX }
            val leftSpread = lefts.max() - lefts.min()
            val rightSpread = rights.max() - rights.min()
            val size = lines.first().size
            if (leftSpread < 0.5f * size) return Alignment.LEFT
            if (rightSpread < 0.5f * size) return Alignment.RIGHT
            val centers = lines.map { (it.startX + it.endX) / 2 }
            if (centers.max() - centers.min() < 0.5f * size) return Alignment.CENTER
            return Alignment.LEFT
        }
        val l = lines.firstOrNull() ?: return Alignment.LEFT
        val center = (pageLeft + pageRight) / 2
        val lineCenter = (l.startX + l.endX) / 2
        val width = pageRight - pageLeft
        return when {
            abs(lineCenter - center) < 0.03f * width && l.startX - pageLeft > 0.2f * width -> Alignment.CENTER
            pageRight - l.endX < 0.12f * width && l.startX - pageLeft > 0.45f * width -> Alignment.RIGHT
            else -> Alignment.LEFT
        }
    }

    /**
     * TJ-Kompensation: Wie viele Tausendstel Textraum ersetzen eine entfernte Glyphe, damit
     * die folgenden Glyphen an exakt derselben Stelle bleiben?
     * Herleitung aus PDF 32000-1, 9.4.4: tx = ((w0 − Tj/1000)·Tfs + Tc + Tw)·Th
     */
    fun tjCompensation(w0: Float, fontSize: Float, charSpacing: Float, wordSpacing: Float, isSingleByteSpace: Boolean): Float {
        if (fontSize == 0f) return w0 * 1000f
        val tw = if (isSingleByteSpace) wordSpacing else 0f
        return w0 * 1000f + (charSpacing + tw) * 1000f / fontSize
    }
}
