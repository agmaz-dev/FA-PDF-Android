package de.fapdf.pdf

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.IOException

/** Wird geworfen, wenn eine PDF ein Öffnungspasswort benötigt. */
class PasswordRequiredException : IOException("Diese PDF ist mit einem Passwort geschützt.")

/** Fehler mit verständlicher, deutscher Meldung für die Oberfläche. */
class PdfUserException(message: String) : IOException(message)

data class PdfInspection(val pageCount: Int, val encrypted: Boolean, val needsPassword: Boolean)

object PdfIo {
    /**
     * Alle PDFBox-Operationen laufen sequenziell auf diesem Dispatcher: PDFBox-Dokumente sind
     * nicht threadsicher, und große PDFs sollen den Speicher nicht mehrfach belegen.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pdfDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    fun load(file: File, password: String? = null): PDDocument {
        if (!file.exists() || file.length() == 0L) throw PdfUserException("Die Datei ist leer oder fehlt.")
        try {
            return if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
        } catch (e: InvalidPasswordException) {
            throw PasswordRequiredException()
        } catch (e: IOException) {
            throw PdfUserException("Die PDF-Datei ist beschädigt oder wird nicht unterstützt (${e.message ?: "Lesefehler"}).")
        }
    }

    fun inspect(file: File): PdfInspection {
        return try {
            load(file).use { PdfInspection(it.numberOfPages, it.isEncrypted, false) }
        } catch (e: PasswordRequiredException) {
            PdfInspection(0, true, true)
        }
    }

    /** Lädt und entfernt jede Verschlüsselung (für die interne Arbeitskopie). */
    fun loadDecrypted(file: File, password: String?): PDDocument {
        val doc = load(file, password)
        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
        return doc
    }

    fun save(doc: PDDocument, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        doc.save(tmp)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
    }

    /** Öffnet [input], wendet [block] an und speichert nach [output]. */
    inline fun <T> transform(input: File, output: File, password: String? = null, block: (PDDocument) -> T): T {
        return load(input, password).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            val r = block(doc)
            save(doc, output)
            r
        }
    }

    fun geometry(page: PDPage): PageGeometry {
        val cb = page.cropBox
        return PageGeometry(cb.lowerLeftX, cb.lowerLeftY, cb.width, cb.height, page.rotation)
    }

    fun resourcesOf(page: PDPage): PDResources =
        page.resources ?: PDResources().also { page.resources = it }

    /** Schreibt Inhaltsstrom-Token in einen neuen, komprimierten Stream. */
    fun writeStream(doc: PDDocument, tokens: List<Any>): COSStream {
        val stream = doc.document.createCOSStream()
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
        return stream
    }

    /**
     * Hängt Inhalt an eine Seite an. Der bestehende Inhalt wird in q/Q eingeschlossen,
     * damit dessen Grafikzustand (z. B. Transformationen) den neuen Inhalt nicht beeinflusst.
     */
    fun appendToPage(doc: PDDocument, page: PDPage, tokens: List<Any>) {
        if (tokens.isEmpty()) return
        val dict = page.cosObject
        val existing = dict.getDictionaryObject(COSName.CONTENTS)
        val arr = COSArray()
        when (existing) {
            is COSStream -> {
                arr.add(writeStream(doc, listOf(Operator.getOperator("q"))))
                arr.add(dict.getItem(COSName.CONTENTS))
            }
            is COSArray -> {
                arr.add(writeStream(doc, listOf(Operator.getOperator("q"))))
                for (i in 0 until existing.size()) arr.add(existing.get(i))
            }
        }
        val tail = ArrayList<Any>()
        if (existing is COSStream || existing is COSArray) tail.add(Operator.getOperator("Q"))
        tail.addAll(tokens)
        arr.add(writeStream(doc, tail))
        dict.setItem(COSName.CONTENTS, arr)
    }

    /** Ersetzt den gesamten Seiteninhalt. */
    fun replacePageContent(doc: PDDocument, page: PDPage, tokens: List<Any>) {
        page.cosObject.setItem(COSName.CONTENTS, writeStream(doc, tokens))
    }
}

/** Kleiner Baukasten für Inhaltsstrom-Operatoren. */
class ContentBuilder(private val resources: PDResources) {
    val tokens = ArrayList<Any>()

    private fun op(name: String, vararg operands: COSBase): ContentBuilder {
        tokens.addAll(operands)
        tokens.add(Operator.getOperator(name))
        return this
    }

    private fun n(v: Float): COSBase = COSFloat(v)

    fun save() = op("q")
    fun restore() = op("Q")
    fun transform(m: Affine) = op("cm", n(m.a), n(m.b), n(m.c), n(m.d), n(m.e), n(m.f))

    fun fillRgb(argb: Int): ContentBuilder {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return op("rg", n(r), n(g), n(b))
    }

    fun strokeRgb(argb: Int): ContentBuilder {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return op("RG", n(r), n(g), n(b))
    }

    fun lineWidth(w: Float) = op("w", n(w))
    fun lineCapRound() = op("J", COSInteger.get(1)).op("j", COSInteger.get(1))
    fun moveTo(x: Float, y: Float) = op("m", n(x), n(y))
    fun lineTo(x: Float, y: Float) = op("l", n(x), n(y))
    fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        op("c", n(x1), n(y1), n(x2), n(y2), n(x3), n(y3))
    fun stroke() = op("S")
    fun rect(x: Float, y: Float, w: Float, h: Float) = op("re", n(x), n(y), n(w), n(h))
    fun fill() = op("f")
    fun closeFill() = op("h").op("f")

    fun graphicsState(gs: PDExtendedGraphicsState) = op("gs", resources.add(gs))

    fun opacity(alpha: Float): ContentBuilder {
        val gs = PDExtendedGraphicsState()
        gs.nonStrokingAlphaConstant = alpha
        gs.strokingAlphaConstant = alpha
        return graphicsState(gs)
    }

    /** Zeichnet ein Bild: Einheitsquadrat wird über [m] auf die Seite abgebildet. */
    fun image(img: PDImageXObject, m: Affine): ContentBuilder {
        val name = resources.add(img)
        save(); transform(m); op("Do", name); restore()
        return this
    }

    /** Zeichnet ein Form-XObject: dessen Einheitsquadrat wird über [m] auf die Seite abgebildet. */
    fun form(f: com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject, m: Affine): ContentBuilder {
        val name = resources.add(f)
        save(); transform(m); op("Do", name); restore()
        return this
    }

    /**
     * Gibt Text aus. [elements] enthält kodierte Zeichenfolgen (ByteArray) und
     * Positionskorrekturen (Float, in Tausendstel Textraum wie beim TJ-Operator).
     */
    fun text(
        font: PDFont, size: Float, matrix: Affine, elements: List<Any>,
        colorArgb: Int = 0xFF000000.toInt(), renderMode: Int = 0, horizontalScale: Float = 100f,
    ): ContentBuilder {
        val fontName = resources.add(font)
        op("BT")
        op("Tf", fontName, n(size))
        fillRgb(colorArgb)
        if (renderMode != 0) op("Tr", COSInteger.get(renderMode.toLong()))
        if (horizontalScale != 100f) op("Tz", n(horizontalScale))
        op("Tm", n(matrix.a), n(matrix.b), n(matrix.c), n(matrix.d), n(matrix.e), n(matrix.f))
        val arr = COSArray()
        for (el in elements) {
            when (el) {
                is ByteArray -> arr.add(COSString(el))
                is Float -> arr.add(COSFloat(el))
            }
        }
        op("TJ", arr)
        op("ET")
        return this
    }
}
