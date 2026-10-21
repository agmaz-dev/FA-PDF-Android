package de.fapdf

import de.fapdf.pdf.FontClassifier
import de.fapdf.pdf.FontFamily
import de.fapdf.pdf.FontMeta
import de.fapdf.pdf.PageRangeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeAndFontTest {
    @Test fun parsesRanges() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(4)), PageRangeParser.parse("1-3, 5", 10))
        assertEquals(listOf(listOf(7, 8, 9)), PageRangeParser.parse("8-", 10))
        assertEquals(listOf(listOf(0, 1)), PageRangeParser.parse("-2", 10))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsOutOfRange() { PageRangeParser.parse("1-11", 10) }
    @Test(expected = IllegalArgumentException::class) fun rejectsBackwards() { PageRangeParser.parse("5-2", 10) }
    @Test(expected = IllegalArgumentException::class) fun rejectsGarbage() { PageRangeParser.parse("abc", 10) }

    @Test fun classifiesCommonFonts() {
        assertEquals(Triple(FontFamily.SERIF, false, false), FontClassifier.classify("ABCDEF+TimesNewRomanPSMT"))
        assertEquals(Triple(FontFamily.SANS, true, false), FontClassifier.classify("Arial-BoldMT"))
        assertEquals(Triple(FontFamily.SANS, false, true), FontClassifier.classify("Helvetica-Oblique"))
        assertEquals(FontFamily.MONO, FontClassifier.classify("CourierNewPSMT").first)
        assertEquals(FontFamily.SERIF, FontClassifier.classify("Georgia").first)
        assertEquals(FontFamily.SANS, FontClassifier.classify("Calibri").first)
        assertTrue(FontClassifier.classify("Foo", weight = 700f).second)
        assertTrue(FontClassifier.classify("Foo", italicAngle = -12f).third)
        assertFalse(FontClassifier.classify("Foo").second)
    }

    @Test fun cleansSubsetPrefix() {
        assertEquals("Arial-BoldMT", FontMeta.cleanName("QWERTY+Arial-BoldMT"))
        assertEquals("Arial", FontMeta.cleanName("Arial"))
    }
}
