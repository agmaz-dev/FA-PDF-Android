package de.fapdf

import de.fapdf.pdf.Alignment
import de.fapdf.pdf.Box
import de.fapdf.pdf.GlyphInfo
import de.fapdf.pdf.TextLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class TextLayoutTest {
    private fun glyph(op: Int, gi: Int, ch: String, x: Float, y: Float, w: Float = 6f, size: Float = 12f, font: Int = 0) =
        GlyphInfo(op, gi, ch[0].code, ch, x, y, w, size, Box(x, y - 2f, x + w, y + 9f), w / size * 1000f, font, 0xFF000000.toInt(), true, false)

    private fun word(op: Int, text: String, x0: Float, y: Float): List<GlyphInfo> =
        text.mapIndexed { i, c -> glyph(op, i, c.toString(), x0 + i * 6f, y) }

    @Test fun reconstructsSpacesFromGaps() {
        val glyphs = word(0, "Sehr", 50f, 700f) + word(1, "geehrte", 50f + 4 * 6f + 3.5f, 700f)
        val lines = TextLayout.buildLines(glyphs, includeInvisible = false)
        assertEquals(1, lines.size)
        assertEquals("Sehr geehrte", lines[0].text)
        // Das rekonstruierte Leerzeichen gehört zu keiner Glyphe
        assertEquals(-1, lines[0].charToGlyph[4])
    }

    @Test fun separatesLinesAndOrdersTopDown() {
        val glyphs = word(0, "unten", 50f, 600f) + word(1, "oben", 50f, 700f)
        val lines = TextLayout.buildLines(glyphs, includeInvisible = false)
        assertEquals(listOf("oben", "unten"), lines.map { it.text })
    }

    @Test fun splitsColumnsOnLargeGaps() {
        val glyphs = word(0, "Links", 50f, 700f) + word(1, "Rechts", 400f, 700f)
        val lines = TextLayout.buildLines(glyphs, includeInvisible = false)
        assertEquals(2, lines.size)
    }

    @Test fun ignoresFakeBoldDuplicatesInText() {
        val a = word(0, "AB", 50f, 700f)
        val dup = word(1, "AB", 50.3f, 700f)
        val lines = TextLayout.buildLines((a + dup).sortedBy { it.x }, includeInvisible = false)
        assertEquals("AB", lines[0].text)
        assertEquals(4, lines[0].glyphs.size) // beide Durchgänge werden beim Bearbeiten entfernt
    }

    @Test fun groupsParagraphs() {
        val glyphs = word(0, "Zeile1", 50f, 700f) + word(1, "Zeile2", 50f, 686f) + word(2, "Zeile3", 50f, 672f) + word(3, "Abseits", 50f, 500f)
        val lines = TextLayout.buildLines(glyphs, includeInvisible = false)
        val blocks = TextLayout.buildBlocks(lines)
        assertEquals(2, blocks.size)
        assertEquals(3, blocks[0].lineIds.size)
        assertEquals(14f, blocks[0].lineSpacing, 0.01f)
    }

    @Test fun tjCompensationMatchesPdfFormula() {
        // w0 = 0.5 em, Tfs = 12, Tc = 0, Tw = 0 → 500
        assertEquals(500f, TextLayout.tjCompensation(0.5f, 12f, 0f, 0f, false), 0.001f)
        // Zeichenabstand 1.2 pt bei 12 pt → +100
        assertEquals(600f, TextLayout.tjCompensation(0.5f, 12f, 1.2f, 0f, false), 0.001f)
        // Wortabstand gilt nur für das einfache Leerzeichen
        assertEquals(250f + 200f, TextLayout.tjCompensation(0.25f, 10f, 0f, 2f, true), 0.001f)
        assertEquals(250f, TextLayout.tjCompensation(0.25f, 10f, 0f, 2f, false), 0.001f)
    }

    @Test fun wrapsWithinWidth() {
        val lines = TextLayout.wrap("eins zwei drei vier", 60f) { it.length * 6f }
        assertEquals(listOf("eins zwei", "drei vier"), lines)
        assertEquals(listOf("a", "", "b"), TextLayout.wrap("a\n\nb", 100f) { it.length * 6f })
    }

    @Test fun alignment() {
        assertEquals(10f, TextLayout.alignX(Alignment.LEFT, 10f, 110f, 40f), 0.001f)
        assertEquals(70f, TextLayout.alignX(Alignment.RIGHT, 10f, 110f, 40f), 0.001f)
        assertEquals(40f, TextLayout.alignX(Alignment.CENTER, 10f, 110f, 40f), 0.001f)
    }

    @Test fun guessesRightAlignedDateLine() {
        val glyphs = word(0, "Berlin,", 420f, 750f)
        val lines = TextLayout.buildLines(glyphs, includeInvisible = false)
        // rechter Rand ~ 462, Seite 0..520 → rechtsbündig
        assertEquals(Alignment.RIGHT, TextLayout.guessAlignment(lines, 0f, 520f))
    }
}
