package de.fapdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import de.fapdf.pdf.FontLibrary
import de.fapdf.pdf.LayoutTranslator
import de.fapdf.pdf.PageAnalyzer
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.TextEditEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Durchläuft die echte PDF-Engine (PDFBox-Android auf der JVM) mit Beispiel-PDFs aus
 * Chrome, LibreOffice und ReportLab. Die erzeugten Dateien landen in build/test-pdf-out
 * und werden zusätzlich mit pdfium gerendert geprüft (CI).
 */
class PdfEngineIntegrationTest {
    private val assets = File("src/main/assets")
    private val fonts = FontLibrary { path -> File(assets, path).inputStream() }
    private val out = File("build/test-pdf-out").apply { mkdirs() }

    private fun fixture(name: String): File =
        File(javaClass.classLoader!!.getResource("fixtures/$name")!!.toURI())

    private fun text(file: File): String = PDDocument.load(file).use { PDFTextStripper().getText(it) }

    /** Jede auf den Seiten benutzte Schrift muss eingebettet sein – sonst zeigt Android Zeichensalat. */
    private fun assertAllFontsEmbedded(file: File) {
        PDDocument.load(file).use { doc ->
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.fontNames) {
                    val f = res.getFont(name) ?: continue
                    if (f is com.tom_roush.pdfbox.pdmodel.font.PDType1Font && f.isStandard14) continue
                    assertTrue("Schrift ${f.name} ist nicht eingebettet (${file.name})", f.isEmbedded)
                    if (f is com.tom_roush.pdfbox.pdmodel.font.PDType0Font) {
                        val fd = f.descendantFont.fontDescriptor
                        assertTrue("Schriftdatei fehlt: ${f.name}", fd?.fontFile2 != null || fd?.fontFile3 != null || fd?.fontFile != null)
                    }
                }
            }
        }
    }

    private fun editFirstMatching(name: String, contains: String, replacement: String, tag: String) {
        val src = fixture(name)
        val target = File(out, "${name.removeSuffix(".pdf")}_$tag.pdf")
        PdfIo.transform(src, target) { doc ->
            val pt = PageAnalyzer.analyze(doc, 0)
            val line = pt.lines.first { it.text.contains(contains) }
            val engine = TextEditEngine(fonts)
            val style = engine.detectStyle(pt, listOf(line.id)).toTextStyle()
            engine.replaceText(doc, 0, listOf(line.id), line.text.replace(contains, replacement), style, de.fapdf.pdf.Alignment.LEFT, asBlock = false)
        }
        val t = text(target)
        assertTrue("Ergebnis enthält Ersatztext nicht: $t", t.contains(replacement))
        assertAllFontsEmbedded(target)
    }

    @Test fun editChromeTicket() {
        editFirstMatching("chrome_boarding.pdf", "SunValue", "SunPremium", "edit_same_glyphs")
        editFirstMatching("chrome_boarding.pdf", "Boarding pass", "Bordkarte Ünal ğış", "edit_new_glyphs")
    }

    @Test fun editLibreOfficeLetter() {
        editFirstMatching("lo_letter.pdf", "Damen und Herren", "Frau Öztürk", "edit_umlaut")
        editFirstMatching("lo_letter.pdf", "Mietvertrag", "Mietänderung", "edit_title")
    }

    @Test fun editReportLabForm() {
        editFirstMatching("rl_form.pdf", "Kontoinhaber", "Kontoinhaberin", "edit_reuse")
        editFirstMatching("rl_form.pdf", "Bitte", "Lütfen ğüşıöç", "edit_turkish")
    }

    private fun translate(name: String, fake: (String) -> String) {
        val src = fixture(name)
        val target = File(out, "${name.removeSuffix(".pdf")}_translated.pdf")
        val units = PDDocument.load(src).use { LayoutTranslator.collect(it).first }
        assertTrue(units.isNotEmpty())
        PdfIo.transform(src, target) { doc ->
            LayoutTranslator.apply(doc, units.map { it to fake(it.text) }, fonts, src, out)
        }
        assertAllFontsEmbedded(target)
        val t = text(target)
        units.take(5).forEach { u -> assertTrue("Übersetzung fehlt: ${fake(u.text)} in $t", t.replace("\n", " ").contains(fake(u.text).split(' ').first())) }
    }

    @Test fun translateChromeTicket() = translate("chrome_boarding.pdf") { "Çeviri: $it ğüşİı" }
    @Test fun translateLibreOfficeLetter() = translate("lo_letter.pdf") { "Übersetzt: $it" }
    @Test fun translateReportLabForm() = translate("rl_form.pdf") { "Tercüme: $it" }
}
