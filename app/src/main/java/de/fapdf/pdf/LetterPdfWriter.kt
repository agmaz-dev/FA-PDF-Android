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
        val attachments: List<String> = emptyList(),
        /** Bezugszeichen für den Infoblock: Datum des erhaltenen Schreibens und Zeichen/Nummer. */
        val theirDate: String = "",
        val reference: String = "",
    )

    /** Ein Infoblock-Eintrag: Beschriftung (klein) und Wert. */
    data class Info(val label: String, val value: String)

    /** Absender in Name / Anschrift / Kontakt aufteilen. */
    fun splitSender(sender: String): Triple<String, List<String>, List<Info>> {
        val lines = sender.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val name = lines.firstOrNull().orEmpty()
        val contact = ArrayList<Info>(); val postal = ArrayList<String>()
        lines.drop(1).forEach { l ->
            when {
                l.contains("@") -> contact += Info("E-Mail", l.substringAfter(":").trim())
                Regex("(?i)^(tel|telefon|mobil|handy|fon)\\b").containsMatchIn(l) || Regex("^\\+?[\\d /()-]{7,}$").matches(l) ->
                    contact += Info("Telefon", l.replace(Regex("(?i)^(tel(efon)?|mobil|handy|fon)\\.?:?\\s*"), ""))
                else -> postal += l
            }
        }
        return Triple(name, postal, contact)
    }

    /** Adresszeilen: „Straße 86-90- 22041 Hamburg“ in Straße und PLZ/Ort trennen. */
    fun splitAddress(text: String): List<String> = text.lines().flatMap { l ->
        val m = Regex("^(.*\\S)\\s*[-,·|]?\\s+(\\d{5}\\s+\\S.*)$").find(l.trim())
        if (m != null && m.groupValues[1].any { it.isLetter() }) listOf(m.groupValues[1].trimEnd('-', ',', ' '), m.groupValues[2]) else listOf(l)
    }.map { it.trim() }.filter { it.isNotEmpty() }

    /** Nur das Datum aus „Ort, 13.11.2026“. */
    fun dateOnly(placeDate: String) = Regex("\\d{1,2}\\.\\s?\\d{1,2}\\.\\s?\\d{2,4}").find(placeDate)?.value ?: placeDate.substringAfterLast(",").trim()

    private const val MM = 2.8346457f

    /** Mehrfache Leerzeichen und Leerzeichen vor Satzzeichen entfernen. */
    private fun tidy(s: String) = s.lines().joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").replace(Regex(" ([,.;:])"), "$1").trim() }

    fun write(letterIn: Letter, signature: Bitmap?, fonts: FontLibrary, out: File) {
        val letter = letterIn.copy(
            sender = tidy(letterIn.sender), recipient = tidy(letterIn.recipient), placeDate = tidy(letterIn.placeDate),
            subject = tidy(letterIn.subject), signerName = tidy(letterIn.signerName),
        )
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

            // DIN 5008 Form B: links Anschriftfeld, rechts daneben auf gleicher Höhe der Infoblock
            val (name, postal, contact) = splitSender(letter.sender)
            val returnLine = (listOf(name) + postal).filter { it.isNotBlank() }.joinToString(" · ")
            val top = a4.height - 45 * MM - 3 * MM
            if (returnLine.isNotBlank()) {
                put(regular, 7f, left, top, returnLine, 0xFF555555.toInt())
                val w = width(regular, returnLine, 7f).coerceAtMost(85 * MM)
                cb.strokeRgb(0xFF888888.toInt()).lineWidth(0.4f).moveTo(left, top - 8.5f).lineTo(left + w, top - 8.5f).stroke()
            }
            var ay = a4.height - 45 * MM - 12 * MM
            splitAddress(letter.recipient).take(7).forEach { l ->
                put(regular, size, left, ay, l)
                ay -= size * 1.3f
            }

            // Infoblock rechts (linksbündig ab 125 mm): Name, Anschrift, Kontakt, Bezugszeichen, Datum
            val ix = 125 * MM
            var iy = top + 4f
            if (name.isNotBlank()) { put(bold, 11f, ix, iy, name); iy -= 11f * 1.35f }
            postal.forEach { put(regular, 9f, ix, iy, it); iy -= 9f * 1.35f }
            val labelW = 22 * MM
            fun info(label: String, value: String) {
                if (value.isBlank()) return
                put(regular, 8f, ix, iy, label, 0xFF666666.toInt())
                val avail = right - ix - labelW
                val fs = generateSequence(9f) { it - 0.5f }.takeWhile { it >= 6.5f }.firstOrNull { width(regular, value, it) <= avail } ?: 6.5f
                put(regular, fs, ix + labelW, iy, value)
                iy -= 9f * 1.45f
            }
            if (contact.isNotEmpty()) iy -= 6f
            contact.forEach { info(it.label, it.value) }
            iy -= 8f
            info("Ihr Schreiben", letter.theirDate)
            info("Ihr Zeichen", letter.reference.take(40))
            info("Datum", dateOnly(letter.placeDate))

            // Betreff unter beiden Blöcken
            var y = minOf(ay, iy, a4.height - 98 * MM) - leading * 1.2f
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
            if (letter.attachments.isNotEmpty()) {
                y -= leading * 2.2f
                if (y - leading * (letter.attachments.size + 1) < 20 * MM) y = newPage()
                put(bold, size, left, y, if (letter.attachments.size == 1) "Anlage" else "Anlagen")
                y -= leading
                letter.attachments.forEach { a -> put(regular, size, left, y, a); y -= leading }
            }
            PdfIo.appendToPage(doc, page, cb.tokens)
            PdfIo.save(doc, out)
        }
    }
}
