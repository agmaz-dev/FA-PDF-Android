package de.fapdf.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File

/** Geschäftsbrief nach DIN 5008 (Form B) als A4-PDF. */
object LetterPdfWriter {
    data class Letter(
        val sender: String,      // mehrzeilig: Name, Straße, PLZ Ort, ggf. Telefon/E-Mail
        val recipient: String,   // mehrzeilig
        val placeDate: String,   // z. B. „Schönefeld, 13.11.2026“
        val subject: String,
        val body: String,        // Anrede … Grußformel
        val signerName: String,
    )

    private const val MM = 2.8346457f

    fun write(letter: Letter, signature: Bitmap?, fonts: FontLibrary, out: File) {
        PDDocument().use { doc ->
            val cache = DocFontCache(doc, fonts)
            val regular = cache.get(FontFamily.SANS, false, false)
            val bold = cache.get(FontFamily.SANS, true, false)
            val a4 = PDRectangle.A4
            val left = 25 * MM
            val right = a4.width - 20 * MM
            val size = 11f
            val leading = size * 1.4f

            fun clean(f: PDType0Font, s: String) = s.map { c -> if (c == '\t') ' ' else if (runCatching { f.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")
            fun width(f: PDType0Font, s: String, sz: Float) = f.getStringWidth(clean(f, s)) / 1000f * sz

            var page = PDPage(a4).also { doc.addPage(it) }
            var cb = ContentBuilder(PdfIo.resourcesOf(page))
            fun put(f: PDType0Font, sz: Float, x: Float, yTop: Float, s: String, color: Int = 0xFF000000.toInt()) {
                val t = clean(f, s)
                if (t.isEmpty()) return
                t.codePoints().forEach { f.addToSubset(it) }
                cb.text(f, sz, Affine.translate(x, yTop - sz), listOf(f.encode(t)), color)
            }
            fun newPage(): Float {
                PdfIo.appendToPage(doc, page, cb.tokens)
                page = PDPage(a4).also { doc.addPage(it) }
                cb = ContentBuilder(PdfIo.resourcesOf(page))
                return a4.height - 20 * MM
            }

            // Absender oben rechts
            val senderLines = letter.sender.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var y = a4.height - 15 * MM
            val senderX = 125 * MM
            senderLines.forEachIndexed { i, l ->
                put(if (i == 0) bold else regular, 10f, senderX, y, l)
                y -= 10f * 1.35f
            }

            // Rücksendeangabe (klein, über dem Anschriftfeld) und Anschrift ab 45 mm
            val returnLine = senderLines.take(3).joinToString(" · ")
            var ay = a4.height - 45 * MM - 3 * MM
            if (returnLine.isNotBlank()) {
                put(regular, 7f, left, ay, returnLine, 0xFF555555.toInt())
                val w = width(regular, returnLine, 7f).coerceAtMost(85 * MM)
                cb.strokeRgb(0xFF888888.toInt()).lineWidth(0.4f).moveTo(left, ay - 8.5f).lineTo(left + w, ay - 8.5f).stroke()
            }
            ay = a4.height - 45 * MM - 12 * MM
            letter.recipient.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(7).forEach { l ->
                put(regular, size, left, ay, l)
                ay -= size * 1.3f
            }

            // Datum rechtsbündig, Betreff fett
            y = a4.height - 98 * MM
            val date = letter.placeDate.trim()
            if (date.isNotEmpty()) put(regular, size, right - width(regular, date, size), y, date)
            y -= leading * 2
            val maxW = right - left
            TextLayout.wrap(letter.subject.trim(), maxW) { width(bold, it, size) }.forEach { l ->
                put(bold, size, left, y, l); y -= leading
            }
            y -= leading

            // Brieftext
            for (raw in letter.body.trim().split('\n')) {
                if (raw.isBlank()) { y -= leading * 0.7f; continue }
                for (l in TextLayout.wrap(raw.trim(), maxW) { width(regular, it, size) }) {
                    if (y - leading < 25 * MM) y = newPage()
                    put(regular, size, left, y, l)
                    y -= leading
                }
            }

            // Unterschrift und Name
            if (signature != null) {
                val h = 16 * MM
                val w = (h * signature.width / signature.height.coerceAtLeast(1)).coerceAtMost(70 * MM)
                val hh = w * signature.height / signature.width.coerceAtLeast(1)
                if (y - hh - leading < 20 * MM) y = newPage()
                val img = LosslessFactory.createFromImage(doc, signature)
                cb.image(img, Affine(w, 0f, 0f, hh, left, y - hh - 2f))
                y -= hh + 4f
            } else y -= leading * 2
            if (letter.signerName.isNotBlank()) {
                if (y - leading < 20 * MM) y = newPage()
                put(regular, size, left, y, letter.signerName.trim())
            }
            PdfIo.appendToPage(doc, page, cb.tokens)
            PdfIo.save(doc, out)
        }
    }
}
