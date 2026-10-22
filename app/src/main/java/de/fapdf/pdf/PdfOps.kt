package de.fapdf.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import java.io.File
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

enum class NumberPosition(val title: String) {
    BOTTOM_CENTER("Unten Mitte"), BOTTOM_RIGHT("Unten rechts"), BOTTOM_LEFT("Unten links"),
    TOP_CENTER("Oben Mitte"), TOP_RIGHT("Oben rechts"), TOP_LEFT("Oben links"),
}

enum class NumberFormat(val title: String) {
    PLAIN("1"), PAGE("Seite 1"), OF("1 / 10"), PAGE_OF("Seite 1 von 10");

    fun format(n: Int, total: Int) = when (this) {
        PLAIN -> "$n"
        PAGE -> "Seite $n"
        OF -> "$n / $total"
        PAGE_OF -> "Seite $n von $total"
    }
}

enum class WatermarkPosition(val title: String) { CENTER("Mitte"), TOP("Oben"), BOTTOM("Unten"), TILED("Gekachelt") }

data class WatermarkSpec(
    val text: String? = null,
    val image: Bitmap? = null,
    val fontSize: Float = 48f,
    val colorArgb: Int = 0xFFE53935.toInt(),
    val opacity: Float = 0.25f,
    val rotationDeg: Float = 45f,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    /** Bildbreite in Prozent der Seitenbreite. */
    val imageScalePercent: Float = 40f,
)

/** Eine Seite aus einem (beliebigen) Quelldokument, z. B. beim Organisieren mit hinzugefügten Seiten. */
data class PageRef(val source: Long, val page: Int, val extraRotation: Int = 0)

object PdfOps {
    /** Baut eine neue PDF aus Seiten mehrerer Dokumente in beliebiger Reihenfolge. */
    fun compose(sources: Map<Long, File>, refs: List<PageRef>, output: File, passwords: Map<Long, String?> = emptyMap()) {
        if (refs.isEmpty()) throw PdfUserException("Es muss mindestens eine Seite erhalten bleiben.")
        val opened = HashMap<Long, PDDocument>()
        try {
            sources.forEach { (k, f) ->
                val d = PdfIo.load(f, passwords[k])
                if (d.isEncrypted) d.isAllSecurityToBeRemoved = true
                opened[k] = d
            }
            PDDocument().use { out ->
                refs.forEach { r ->
                    val src = opened[r.source] ?: throw PdfUserException("Quelldokument fehlt.")
                    val p = out.importPage(src.getPage(r.page))
                    p.rotation = (((p.rotation + r.extraRotation) % 360) + 360) % 360
                }
                PdfIo.save(out, output)
            }
        } finally {
            opened.values.forEach { runCatching { it.close() } }
        }
    }


    fun merge(inputs: List<File>, output: File) {
        val util = PDFMergerUtility()
        inputs.forEach { util.addSource(it) }
        util.destinationFileName = output.absolutePath
        try {
            util.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        } catch (e: java.io.IOException) {
            throw PdfUserException("Zusammenfügen fehlgeschlagen. Ist eine der Dateien verschlüsselt oder beschädigt? (${e.message})")
        }
    }

    /** Erzeugt pro Bereich eine neue PDF. */
    fun split(input: File, ranges: List<List<Int>>, outputs: List<File>, password: String? = null) {
        PdfIo.load(input, password).use { src ->
            if (src.isEncrypted) src.isAllSecurityToBeRemoved = true
            ranges.forEachIndexed { i, pages ->
                PDDocument().use { out ->
                    pages.forEach { out.importPage(src.getPage(it)) }
                    PdfIo.save(out, outputs[i])
                }
            }
        }
    }

    fun extract(input: File, pages: List<Int>, output: File, password: String? = null) =
        split(input, listOf(pages), listOf(output), password)

    fun rotate(doc: PDDocument, pages: Collection<Int>, degrees: Int) {
        pages.forEach { i ->
            val p = doc.getPage(i)
            p.rotation = (((p.rotation + degrees) % 360) + 360) % 360
        }
    }

    fun deletePages(doc: PDDocument, pages: Collection<Int>) {
        if (pages.size >= doc.numberOfPages) throw PdfUserException("Es muss mindestens eine Seite erhalten bleiben.")
        pages.sortedDescending().forEach { doc.removePage(it) }
    }

    /** Neue Reihenfolge, Drehungen und gelöschte Seiten in einem Schritt. */
    fun reorganize(doc: PDDocument, order: List<Int>, extraRotation: Map<Int, Int>) {
        if (order.isEmpty()) throw PdfUserException("Es muss mindestens eine Seite erhalten bleiben.")
        val pages = (0 until doc.numberOfPages).map { doc.getPage(it) }
        extraRotation.forEach { (i, deg) -> pages[i].rotation = (((pages[i].rotation + deg) % 360) + 360) % 360 }
        pages.forEach { doc.removePage(it) }
        order.forEach { doc.addPage(pages[it]) }
    }

    fun addPageNumbers(
        doc: PDDocument, fonts: FontLibrary, position: NumberPosition, format: NumberFormat,
        startNumber: Int, fontSize: Float, marginPt: Float, skipFirst: Boolean,
    ) {
        val cache = DocFontCache(doc, fonts)
        val font = cache.get(FontFamily.SANS, false, false)
        val total = doc.numberOfPages
        for (i in 0 until total) {
            if (skipFirst && i == 0) continue
            val page = doc.getPage(i)
            val g = PdfIo.geometry(page)
            val text = format.format(startNumber + i - (if (skipFirst) 1 else 0), total - (if (skipFirst) 1 else 0) + startNumber - 1)
            text.codePoints().forEach { font.addToSubset(it) }
            val w = font.getStringWidth(text) / 1000f * fontSize
            val u = when (position) {
                NumberPosition.BOTTOM_LEFT, NumberPosition.TOP_LEFT -> marginPt
                NumberPosition.BOTTOM_RIGHT, NumberPosition.TOP_RIGHT -> g.viewWidth - marginPt - w
                else -> (g.viewWidth - w) / 2f
            }
            val v = when (position) {
                NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER, NumberPosition.TOP_RIGHT -> marginPt + fontSize
                else -> g.viewHeight - marginPt
            }
            val cb = ContentBuilder(PdfIo.resourcesOf(page))
            cb.text(font, fontSize, g.uprightMatrixAt(u, v), listOf(font.encode(text)))
            PdfIo.appendToPage(doc, page, cb.tokens)
        }
    }

    /** Ränder in Ansichtspunkten (oben, rechts, unten, links). Setzt die CropBox. */
    fun crop(doc: PDDocument, pages: Collection<Int>, top: Float, right: Float, bottom: Float, left: Float) {
        pages.forEach { i ->
            val page = doc.getPage(i)
            val g = PdfIo.geometry(page)
            if (left + right >= g.viewWidth - 10 || top + bottom >= g.viewHeight - 10) {
                throw PdfUserException("Die Ränder sind größer als die Seite ${i + 1}.")
            }
            val user = g.viewBoxToUser(Box(left, top, g.viewWidth - right, g.viewHeight - bottom))
            val media = page.mediaBox
            val llx = max(user.left, media.lowerLeftX)
            val lly = max(user.top, media.lowerLeftY)
            val urx = min(user.right, media.upperRightX)
            val ury = min(user.bottom, media.upperRightY)
            page.cropBox = PDRectangle(llx, lly, urx - llx, ury - lly)
        }
    }

    /**
     * Zuschneiden mit Anteilen der sichtbaren Seite (0…1: links, oben, rechts, unten).
     * So passt derselbe Rahmen auch bei unterschiedlich großen Seiten.
     */
    fun cropFractions(doc: PDDocument, pages: Collection<Int>, frame: Box) {
        pages.forEach { i ->
            val g = PdfIo.geometry(doc.getPage(i))
            crop(
                doc, listOf(i),
                top = frame.top * g.viewHeight, right = (1f - frame.right) * g.viewWidth,
                bottom = (1f - frame.bottom) * g.viewHeight, left = frame.left * g.viewWidth,
            )
        }
    }

    fun watermark(doc: PDDocument, fonts: FontLibrary, spec: WatermarkSpec) {
        val cache = DocFontCache(doc, fonts)
        val font = cache.get(FontFamily.SANS, true, false)
        val text = spec.text?.takeIf { it.isNotBlank() }
        text?.codePoints()?.forEach { font.addToSubset(it) }
        val img = spec.image?.let { TextEditEngine.downscale(it, 2000) }?.let {
            if (it.hasAlpha()) LosslessFactory.createFromImage(doc, it) else JPEGFactory.createFromImage(doc, it, 0.9f)
        }
        for (i in 0 until doc.numberOfPages) {
            val page = doc.getPage(i)
            val g = PdfIo.geometry(page)
            val cb = ContentBuilder(PdfIo.resourcesOf(page))
            cb.save()
            cb.opacity(spec.opacity)
            val centers: List<Pair<Float, Float>> = when (spec.position) {
                WatermarkPosition.CENTER -> listOf(Pair(g.viewWidth / 2, g.viewHeight / 2))
                WatermarkPosition.TOP -> listOf(Pair(g.viewWidth / 2, g.viewHeight * 0.12f))
                WatermarkPosition.BOTTOM -> listOf(Pair(g.viewWidth / 2, g.viewHeight * 0.88f))
                WatermarkPosition.TILED -> {
                    val list = ArrayList<Pair<Float, Float>>()
                    var y = g.viewHeight * 0.15f
                    while (y < g.viewHeight) {
                        var x = g.viewWidth * 0.25f
                        while (x < g.viewWidth) { list.add(Pair(x, y)); x += g.viewWidth * 0.5f }
                        y += g.viewHeight * 0.25f
                    }
                    list
                }
            }
            for ((cx, cy) in centers) {
                if (text != null) {
                    val w = font.getStringWidth(text) / 1000f * spec.fontSize
                    // Text um seinen Mittelpunkt drehen: Matrix für die Grundlinie berechnen
                    val rad = Math.toRadians(-spec.rotationDeg.toDouble())
                    val cos = kotlin.math.cos(rad).toFloat()
                    val sin = kotlin.math.sin(rad).toFloat()
                    // Startpunkt der Grundlinie in Ansichtskoordinaten (y nach unten)
                    val hx = -w / 2; val hy = spec.fontSize * 0.35f
                    val su = cx + hx * cos - hy * sin
                    val sv = cy + hx * sin + hy * cos
                    val o = g.viewToUser(su, sv)
                    val xe = g.viewToUser(su + cos, sv + sin)
                    val ye = g.viewToUser(su + sin, sv - cos)
                    cb.text(font, spec.fontSize, Affine.fromBasis(o, xe, ye), listOf(font.encode(text)), spec.colorArgb)
                }
                if (img != null) {
                    val w = g.viewWidth * spec.imageScalePercent / 100f
                    val h = w * img.height / img.width
                    cb.image(img, g.imageMatrix(cx, cy, w, h, -spec.rotationDeg))
                }
            }
            cb.restore()
            PdfIo.appendToPage(doc, page, cb.tokens)
        }
    }

    fun encrypt(doc: PDDocument, userPassword: String, ownerPassword: String?, allowPrint: Boolean, allowCopy: Boolean, allowModify: Boolean) {
        val ap = AccessPermission()
        ap.setCanPrint(allowPrint)
        ap.setCanPrintFaithful(allowPrint)
        ap.setCanExtractContent(allowCopy)
        ap.setCanModify(allowModify)
        ap.setCanModifyAnnotations(allowModify)
        ap.setCanFillInForm(allowModify)
        ap.setCanAssembleDocument(allowModify)
        val owner = ownerPassword?.takeIf { it.isNotBlank() } ?: randomPassword()
        val policy = StandardProtectionPolicy(owner, userPassword, ap)
        policy.encryptionKeyLength = 256
        doc.protect(policy)
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Übernimmt Formularfelder und Anmerkungen dauerhaft in den Seiteninhalt. */
    fun flattenAll(doc: PDDocument) {
        val form = doc.documentCatalog.acroForm
        if (form != null) {
            runCatching { if (form.needAppearances) form.refreshAppearances() }
            runCatching { form.flatten() }
            // Nach dem Abflachen leere Felder vollständig entfernen
            runCatching { if (form.fields.isEmpty()) doc.documentCatalog.acroForm = null }
        }
        for (page in doc.pages) flattenAnnotations(doc, page)
    }

    fun flattenAnnotations(doc: PDDocument, page: PDPage) {
        val keep = ArrayList<PDAnnotation>()
        val cb = ContentBuilder(PdfIo.resourcesOf(page))
        for (a in page.annotations) {
            val sub = a.subtype
            if (sub == "Link" || sub == "Popup") { keep.add(a); continue }
            if (a is PDAnnotationWidget) { keep.add(a); continue }
            if (a.isHidden || a.isNoView) continue
            val ap = a.normalAppearanceStream
            val rect = a.rectangle
            if (ap == null || rect == null) continue
            val bbox = ap.bBox ?: continue
            val m = ap.matrix.toAffine()
            val pts = listOf(
                m.apply(bbox.lowerLeftX, bbox.lowerLeftY), m.apply(bbox.upperRightX, bbox.lowerLeftY),
                m.apply(bbox.lowerLeftX, bbox.upperRightY), m.apply(bbox.upperRightX, bbox.upperRightY),
            )
            val tb = Box.bounding(pts)
            if (tb.width <= 0f || tb.height <= 0f) continue
            val sx = rect.width / tb.width
            val sy = rect.height / tb.height
            val fit = Affine.translate(-tb.left, -tb.top).then(Affine.scale(sx, sy)).then(Affine.translate(rect.lowerLeftX, rect.lowerLeftY))
            val name = PdfIo.resourcesOf(page).add(ap)
            // Laut PDF-Spezifikation (12.5.5): Do wendet die Formularmatrix selbst an; cm = Abbildung bbox → rect
            cb.save(); cb.transform(fit); drawForm(cb, name); cb.restore()
        }
        if (cb.tokens.isNotEmpty()) PdfIo.appendToPage(doc, page, cb.tokens)
        page.annotations = keep
    }

    private fun drawForm(cb: ContentBuilder, name: COSName) {
        cb.tokens.add(name)
        cb.tokens.add(com.tom_roush.pdfbox.contentstream.operator.Operator.getOperator("Do"))
    }
}
