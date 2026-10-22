package de.fapdf.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Eine von FA PDF eingefügte Unterschrift bzw. ein Bild, das sich später wieder bearbeiten lässt. */
data class PlacedObject(
    val pageIndex: Int,
    val opIndex: Int,
    val xObjectName: String,
    val viewBox: Box,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val rotation: Float,
    val signature: Boolean,
)

/**
 * Unterschriften und Bilder werden als eigenes Form-XObject eingefügt. Darin stehen Position,
 * Größe, Drehung und eine verlustfreie Kopie des Bildes. So erkennt FA PDF das Objekt später
 * wieder und kann es entfernen und neu platzieren – andere Programme zeigen es ganz normal an.
 */
object PlacedObjects {
    private val KEY: COSName = COSName.getPDFName("FaPdfPlacement")
    private val SRC: COSName = COSName.getPDFName("FaPdfSource")

    fun insert(
        doc: PDDocument, pageIndex: Int, bitmap: Bitmap, cx: Float, cy: Float, w: Float, h: Float,
        rotationDeg: Float, signature: Boolean, opacity: Float = 1f,
    ) {
        val page = doc.getPage(pageIndex)
        val g = PdfIo.geometry(page)
        val src = TextEditEngine.downscale(bitmap, 2400)
        val img: PDImageXObject = if (src.hasAlpha()) LosslessFactory.createFromImage(doc, src) else JPEGFactory.createFromImage(doc, src, 0.9f)

        val form = PDFormXObject(doc)
        form.bBox = PDRectangle(0f, 0f, 1f, 1f)
        val res = PDResources()
        form.resources = res
        val imgName = res.add(img)
        form.cosObject.createOutputStream(COSName.FLATE_DECODE).use {
            ContentStreamWriter(it).writeTokens(listOf(imgName, Operator.getOperator("Do")))
        }
        form.cosObject.setString(
            KEY, String.format(Locale.US, "%.3f;%.3f;%.3f;%.3f;%.2f;%d", cx, cy, w, h, rotationDeg, if (signature) 1 else 0),
        )
        // Verlustfreie Kopie für späteres Bearbeiten
        val png = ByteArrayOutputStream().also { TextEditEngine.downscale(bitmap, 1600).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val srcStream = doc.document.createCOSStream()
        srcStream.createOutputStream().use { it.write(png) }
        form.cosObject.setItem(SRC, srcStream)

        val cb = ContentBuilder(PdfIo.resourcesOf(page))
        cb.save()
        if (opacity < 1f) cb.opacity(opacity)
        cb.form(form, g.imageMatrix(cx, cy, w, h, rotationDeg))
        cb.restore()
        PdfIo.appendToPage(doc, page, cb.tokens)
    }

    /** Alle bearbeitbaren Objekte einer Seite (in Ansichtskoordinaten). */
    fun find(doc: PDDocument, pageIndex: Int, pt: PageText = PageAnalyzer.analyze(doc, pageIndex)): List<PlacedObject> {
        val res = doc.getPage(pageIndex).resources ?: return emptyList()
        return pt.images.mapNotNull { r ->
            val name = r.name ?: return@mapNotNull null
            if (!r.isForm || r.opIndex < 0) return@mapNotNull null
            val form = runCatching { res.getXObject(COSName.getPDFName(name)) as? PDFormXObject }.getOrNull() ?: return@mapNotNull null
            val meta = form.cosObject.getString(KEY) ?: return@mapNotNull null
            val v = meta.split(';')
            if (v.size < 6) return@mapNotNull null
            PlacedObject(
                pageIndex, r.opIndex, name, pt.geometry.userBoxToView(r.bbox),
                v[0].toFloat(), v[1].toFloat(), v[2].toFloat(), v[3].toFloat(), v[4].toFloat(), v[5] == "1",
            )
        }
    }

    /** Liefert das Originalbild des Objekts zurück. */
    fun bitmapOf(doc: PDDocument, obj: PlacedObject): Bitmap? {
        val res = doc.getPage(obj.pageIndex).resources ?: return null
        val form = runCatching { res.getXObject(COSName.getPDFName(obj.xObjectName)) as? PDFormXObject }.getOrNull() ?: return null
        val stream = form.cosObject.getDictionaryObject(SRC) as? COSStream
        if (stream != null) {
            val bytes = stream.createInputStream().use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
        }
        // Rückfall: Bild direkt aus dem Objekt lesen
        val inner = form.resources ?: return null
        for (n in inner.xObjectNames) {
            val x = runCatching { inner.getXObject(n) }.getOrNull()
            if (x is PDImageXObject) return runCatching { x.image }.getOrNull()
        }
        return null
    }

    /** Entfernt das Objekt aus der Seite (der Zeichenbefehl wird gelöscht). */
    fun remove(doc: PDDocument, obj: PlacedObject) {
        val page = doc.getPage(obj.pageIndex)
        ContentRewriter.removeOperators(doc, page, setOf(obj.opIndex))
    }
}
