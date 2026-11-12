package de.fapdf

import de.fapdf.pdf.FontFile
import de.fapdf.pdf.FontIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class FontIndexTest {
    private fun ff(name: String, fam: String, b: Boolean = false, i: Boolean = false, v: Boolean = false) = FontFile(File(name), fam, b, i, v)

    private val fonts = listOf(
        ff("Calibri.ttf", "Calibri"), ff("Calibri-Bold.ttf", "Calibri", b = true),
        ff("Cal.ttf", "Cal"), ff("Brush.ttf", "Brush Script"),
    )

    @Test fun matchesFamilyAndStyleFromOriginalName() {
        assertEquals("Calibri-Bold.ttf", FontIndex.match(fonts, "ABCDEF+Calibri-Bold", true, false)?.file?.name)
        assertEquals("Calibri.ttf", FontIndex.match(fonts, "Calibri", false, false)?.file?.name)
    }

    @Test fun longestFamilyWins() {
        assertEquals("Calibri", FontIndex.match(fonts, "CalibriLight", false, false)?.family)
    }

    @Test fun fallsBackToRegularWhenStyleMissing() {
        assertEquals("Calibri.ttf", FontIndex.match(fonts, "Calibri-Italic", false, true)?.file?.name)
    }

    @Test fun noMatchForUnknownFont() {
        assertNull(FontIndex.match(fonts, "Papyrus", false, false))
    }
}
