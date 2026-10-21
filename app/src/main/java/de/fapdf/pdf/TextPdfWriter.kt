package de.fapdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File

/** Erstellt aus reinem Text eine neue, saubere A4-PDF (z. B. für Übersetzungen). */
object TextPdfWriter {
    fun write(title: String, text: String, fonts: FontLibrary, out: File) {
        PDDocument().use { doc ->
            val cache = DocFontCache(doc, fonts)
            val regular = cache.get(FontFamily.SANS, false, false)
            val bold = cache.get(FontFamily.SANS, true, false)
            val size = 11f
            val leading = size * 1.35f
            val margin = 56f
            val a4 = PDRectangle.A4
            val maxW = a4.width - 2 * margin

            fun clean(s: String) = s.map { c -> if (c == '\t') ' ' else if (runCatching { regular.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")
            fun measure(s: String) = regular.getStringWidth(s) / 1000f * size

            var page: PDPage? = null
            var cb: ContentBuilder? = null
            var y = 0f
            fun flush() { val p = page; val c = cb; if (p != null && c != null) PdfIo.appendToPage(doc, p, c.tokens) }
            fun newPage() {
                flush()
                page = PDPage(a4).also { doc.addPage(it) }
                cb = ContentBuilder(PdfIo.resourcesOf(page!!))
                y = a4.height - margin
            }
            newPage()
            val t = clean(title)
            t.codePoints().forEach { bold.addToSubset(it) }
            cb!!.text(bold, 15f, Affine.translate(margin, y - 15f), listOf(bold.encode(t)))
            y -= 15f + leading * 1.5f

            for (raw in text.split('\n')) {
                val para = clean(raw)
                if (Regex("\\[Seite \\d+]").matches(para.trim())) {
                    if (y < a4.height - margin - 40f) y -= leading * 0.5f
                    val label = para.trim()
                    label.codePoints().forEach { bold.addToSubset(it) }
                    if (y - leading < margin) newPage()
                    cb!!.text(bold, size, Affine.translate(margin, y - size), listOf(bold.encode(label)), 0xFF555555.toInt())
                    y -= leading
                    continue
                }
                val lines = TextLayout.wrap(para, maxW) { measure(it) }
                for (l in lines) {
                    if (y - leading < margin) newPage()
                    if (l.isNotEmpty()) {
                        l.codePoints().forEach { regular.addToSubset(it) }
                        cb!!.text(regular, size, Affine.translate(margin, y - size), listOf(regular.encode(l)))
                    }
                    y -= leading
                }
            }
            flush()
            PdfIo.save(doc, out)
        }
    }
}
