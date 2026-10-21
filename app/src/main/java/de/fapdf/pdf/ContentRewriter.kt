package de.fapdf.pdf

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Entfernt Glyphen tatsächlich aus dem Seiteninhalt (kein Überdecken!).
 *
 * Vorgehen: Der Inhaltsstrom wird in Token zerlegt. Jeder Textoperator (Tj, TJ, ', "), der
 * zu entfernende Glyphen enthält, wird in einen TJ-Operator umgeschrieben. Entfernte Glyphen
 * werden durch eine exakt gleich große Positionskorrektur ersetzt – dadurch bleibt jedes
 * verbleibende Zeichen an seiner ursprünglichen Stelle.
 */
object ContentRewriter {

    /**
     * @param removal Operatorindex → Glyphenindizes, die entfernt werden sollen.
     * @param advances (Operatorindex, Glyphenindex) → TJ-Kompensation der Glyphe.
     */
    fun removeGlyphs(doc: PDDocument, page: PDPage, pageText: PageText, removal: Map<Int, Set<Int>>) {
        if (removal.isEmpty()) return
        val advances = HashMap<Long, Float>()
        pageText.glyphs.forEach { g ->
            if (g.opIndex >= 0) advances[key(g.opIndex, g.glyphIndex)] = g.tjAdvance
        }
        val tokens = parse(page)
        val out = ArrayList<Any>(tokens.size + 16)
        val operands = ArrayList<Any>()
        var opIndex = 0
        for (t in tokens) {
            if (t !is Operator) { operands.add(t); continue }
            val idx = opIndex++
            val remove = removal[idx]
            val font = pageText.opFonts[idx]
            if (remove == null || remove.isEmpty() || font == null) {
                out.addAll(operands); out.add(t); operands.clear(); continue
            }
            when (t.name) {
                "Tj" -> {
                    val s = operands.lastOrNull() as? COSString
                    if (s == null) { out.addAll(operands); out.add(t) } else {
                        out.add(rebuild(listOf(s), idx, remove, font, advances))
                        out.add(Operator.getOperator("TJ"))
                    }
                }
                "TJ" -> {
                    val arr = operands.lastOrNull() as? COSArray
                    if (arr == null) { out.addAll(operands); out.add(t) } else {
                        val items = (0 until arr.size()).map { arr.getObject(it) }
                        out.add(rebuild(items, idx, remove, font, advances))
                        out.add(t)
                    }
                }
                "'" -> {
                    val s = operands.lastOrNull() as? COSString
                    if (s == null) { out.addAll(operands); out.add(t) } else {
                        out.add(Operator.getOperator("T*"))
                        out.add(rebuild(listOf(s), idx, remove, font, advances))
                        out.add(Operator.getOperator("TJ"))
                    }
                }
                "\"" -> {
                    if (operands.size < 3 || operands[2] !is COSString) { out.addAll(operands); out.add(t) } else {
                        out.add(operands[0]); out.add(Operator.getOperator("Tw"))
                        out.add(operands[1]); out.add(Operator.getOperator("Tc"))
                        out.add(Operator.getOperator("T*"))
                        out.add(rebuild(listOf(operands[2] as COSString), idx, remove, font, advances))
                        out.add(Operator.getOperator("TJ"))
                    }
                }
                else -> { out.addAll(operands); out.add(t) }
            }
            operands.clear()
        }
        out.addAll(operands)
        PdfIo.replacePageContent(doc, page, out)
    }

    private fun key(op: Int, glyph: Int): Long = (op.toLong() shl 32) or (glyph.toLong() and 0xFFFFFFFFL)

    private fun rebuild(
        items: List<COSBase?>, opIndex: Int, remove: Set<Int>,
        font: com.tom_roush.pdfbox.pdmodel.font.PDFont, advances: Map<Long, Float>,
    ): COSArray {
        val result = COSArray()
        var pendingAdjust = 0f
        var glyphCounter = 0
        val kept = ByteArrayOutputStream()

        fun flushKept() {
            if (kept.size() > 0) {
                if (pendingAdjust != 0f) { result.add(COSFloat(pendingAdjust)); pendingAdjust = 0f }
                result.add(COSString(kept.toByteArray()))
                kept.reset()
            }
        }

        for (item in items) {
            when (item) {
                is COSString -> {
                    val bytes = item.bytes
                    val input = ByteArrayInputStream(bytes)
                    while (input.available() > 0) {
                        val before = bytes.size - input.available()
                        font.readCode(input)
                        val after = bytes.size - input.available()
                        if (glyphCounter in remove) {
                            flushKept()
                            // Negativer TJ-Wert verschiebt nach rechts – genau um die Breite der entfernten Glyphe
                            pendingAdjust -= advances[key(opIndex, glyphCounter)] ?: 0f
                        } else {
                            if (pendingAdjust != 0f && kept.size() == 0) {
                                result.add(COSFloat(pendingAdjust)); pendingAdjust = 0f
                            }
                            kept.write(bytes, before, after - before)
                        }
                        glyphCounter++
                    }
                }
                is COSNumber -> {
                    flushKept()
                    pendingAdjust += item.floatValue()
                }
                else -> {}
            }
        }
        flushKept()
        if (pendingAdjust != 0f) result.add(COSFloat(pendingAdjust))
        return result
    }

    fun parse(page: PDPage): List<Any> {
        val parser = PDFStreamParser(page)
        val tokens = ArrayList<Any>()
        while (true) {
            val t = parser.parseNextToken() ?: break
            tokens.add(t)
        }
        return tokens
    }
}
