package de.fapdf

import de.fapdf.pdf.FontCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class FontCatalogTest {
    @Test fun commonOfficeFontsMapToMetricSimilarFonts() {
        assertEquals("carlito", FontCatalog.matchByName("ABCDEF+Calibri-Bold")?.id)
        assertEquals("caladea", FontCatalog.matchByName("Cambria")?.id)
        assertEquals("crimson", FontCatalog.matchByName("Georgia-Italic")?.id)
        assertEquals("ptsans", FontCatalog.matchByName("Verdana")?.id)
        assertEquals("lato", FontCatalog.matchByName("SegoeUI")?.id)
    }

    @Test fun liberationHandlesArialTimesCourier() {
        assertNull(FontCatalog.matchByName("ArialMT"))
        assertNull(FontCatalog.matchByName("TimesNewRomanPSMT"))
        assertNull(FontCatalog.matchByName("CourierNewPSMT"))
    }

    @Test fun everyCatalogFontShipsAllFourStyles() {
        val dir = File("src/main/assets/fonts")
        for (f in FontCatalog.fonts) for (st in listOf("Regular", "Bold", "Italic", "BoldItalic"))
            assertEquals("${f.assetBase}-$st.ttf fehlt", true, File(dir, "${f.assetBase}-$st.ttf").isFile)
    }
}
