package de.fapdf.pdf

import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont
import java.io.ByteArrayInputStream
import java.text.Normalizer

/** Ein Textabschnitt in genau einer Schrift: kodierte Bytes und TJ-Abstände, Breite in 1/1000 em. */
class TextRun(val font: PDFont, val elements: List<Any>, val widthUnits: Float, val original: Boolean)

/**
 * Setzt Text möglichst in der Originalschrift des Dokuments. Wörter, deren Zeichen die (meist nur als
 * Teilmenge eingebettete) Originalschrift nicht enthält, werden in der passenden Ersatzschrift gesetzt –
 * wortweise, damit innerhalb eines Wortes kein Schriftwechsel sichtbar wird.
 *
 * Jede Kodierung in der Originalschrift wird über die Unicode-Zuordnung der Schrift gegengeprüft. So
 * entstehen keine falschen Glyphen, z. B. bei Schriften mit eigener CID→GID-Zuordnung.
 */
class TextRunEncoder(
    private val original: PDFont?,
    /** Unicode → Zeichencode, aus der Seite bekannt (bereits benutzte Glyphen der Originalschrift). */
    private val knownCodes: Map<String, Int>,
    private val substitute: () -> PDType0Font,
) {
    private val checked = HashMap<String, ByteArray?>()

    /** Kodiert ein einzelnes Zeichen (Graphem) in der Originalschrift oder liefert null. */
    private fun originalBytes(ch: String): ByteArray? = checked.getOrPut(ch) {
        val f = original ?: return@getOrPut null
        encodeChecked(f, ch) ?: knownCodes[ch]?.let { FontSupport.codeToBytes(f, it) }
    }

    fun originalCovers(text: String): Boolean = graphemes(text).all { it.isBlank() || originalBytes(it) != null }

    /** Zerlegt [text] in Abschnitte gleicher Schrift. */
    fun encode(text: String): List<TextRun> {
        val runs = ArrayList<TextRun>()
        if (text.isEmpty()) return runs
        // Wörter mit den folgenden Leerzeichen
        val tokens = Regex("\\S+|\\s+").findAll(text).map { it.value }.toList()
        var curFont: PDFont? = null
        var curOriginal = false
        val elements = ArrayList<Any>()
        val buf = java.io.ByteArrayOutputStream()
        var width = 0f

        fun flushBuf() { if (buf.size() > 0) { elements.add(buf.toByteArray()); buf.reset() } }
        fun flushRun() {
            flushBuf()
            val f = curFont
            if (f != null && elements.isNotEmpty()) runs.add(TextRun(f, ArrayList(elements), width, curOriginal))
            elements.clear(); width = 0f
        }
        fun switchTo(f: PDFont, isOriginal: Boolean) {
            if (curFont !== f) { flushRun(); curFont = f; curOriginal = isOriginal }
        }

        for (tok in tokens) {
            if (tok.isBlank()) {
                // Leerzeichen in der aktuellen Schrift – fehlt die Glyphe, als Abstand (TJ)
                val f = curFont ?: original ?: substitute()
                if (curFont == null) { curFont = f; curOriginal = f === original }
                for (sp in tok) {
                    val bytes = if (f === original) originalBytes(" ")
                    else safeEncode(f as PDType0Font, " ")?.also { f.addToSubset(32) }
                    if (bytes != null) { buf.write(bytes); width += FontSupport.widthOfBytes(f, bytes) }
                    else { flushBuf(); val sw = FontSupport.spaceWidth(f); elements.add(-sw); width += sw }
                }
                continue
            }
            val gs = graphemes(tok)
            val origParts = gs.map { originalBytes(it) }
            if (original != null && origParts.all { it != null }) {
                switchTo(original, true)
                origParts.forEach { b -> buf.write(b!!); width += FontSupport.widthOfBytes(original, b) }
            } else {
                val sub = substitute()
                switchTo(sub, false)
                for (g in gs) {
                    val enc = safeEncode(sub, g)
                    val shown = if (enc != null) g else "?" // z. B. Emojis, die keine Schrift enthält
                    val b = enc ?: safeEncode(sub, "?") ?: continue
                    shown.codePoints().forEach { cp -> sub.addToSubset(cp) }
                    buf.write(b)
                    width += FontSupport.widthOfBytes(sub, b)
                }
            }
        }
        flushRun()
        return runs
    }

    fun width(text: String): Float = encode(text).sumOf { it.widthUnits.toDouble() }.toFloat()

    companion object {
        private fun safeEncode(f: PDType0Font, s: String): ByteArray? = try {
            val b = f.encode(s)
            if (b.isEmpty()) null else b
        } catch (e: Exception) { null }

        fun graphemes(s: String): List<String> {
            val out = ArrayList<String>()
            val it = java.text.BreakIterator.getCharacterInstance()
            it.setText(s)
            var start = it.first()
            var end = it.next()
            while (end != java.text.BreakIterator.DONE) { out.add(s.substring(start, end)); start = end; end = it.next() }
            return out
        }

        private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

        /**
         * Kodiert [s] in [font] und prüft das Ergebnis: Jede Glyphe muss vorhanden sein und über die
         * Unicode-Zuordnung der Schrift wieder genau [s] ergeben. Sonst null.
         */
        fun encodeChecked(font: PDFont, s: String): ByteArray? {
            if (font is PDType3Font) return null
            val bytes = try { font.encode(s) } catch (e: Exception) { return null }
            if (bytes.isEmpty()) return null
            val input = ByteArrayInputStream(bytes)
            val back = StringBuilder()
            while (input.available() > 0) {
                val code = try { font.readCode(input) } catch (e: Exception) { return null }
                if (font is PDVectorFont && !runCatching { font.hasGlyph(code) }.getOrDefault(false)) return null
                val u = runCatching { font.toUnicode(code) }.getOrNull() ?: return null
                back.append(u)
            }
            return if (nfc(back.toString()) == nfc(s)) bytes else null
        }
    }
}
