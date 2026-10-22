package de.fapdf.pdf

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.StringWriter
import kotlin.math.abs
import kotlin.math.sqrt

/** Bild- oder Objektbereich einer Seite (für Schwärzen und Scan-Erkennung). */
data class ImageRegion(val opIndex: Int, val bbox: Box, val isForm: Boolean, val name: String? = null)

/** Ergebnis der Analyse einer Seite. Gültig, solange das zugehörige PDDocument geöffnet ist. */
class PageText(
    val pageIndex: Int,
    val geometry: PageGeometry,
    val glyphs: List<GlyphInfo>,
    val lines: List<TextLine>,
    val blocks: List<TextBlock>,
    val hiddenLines: List<TextLine>,
    val fonts: FontRegistry,
    val opFonts: Map<Int, PDFont>,
    val images: List<ImageRegion>,
) {
    val visibleGlyphCount: Int get() = glyphs.count { !it.invisible && it.unicode.isNotBlank() }

    /** Seite besteht im Wesentlichen aus einem Bild ohne sichtbaren Text → wie ein Scan behandeln. */
    val looksScanned: Boolean
        get() {
            val pageArea = geometry.cropWidth * geometry.cropHeight
            val imageArea = images.filter { !it.isForm }.sumOf { it.bbox.width.toDouble() * it.bbox.height.toDouble() }
            return visibleGlyphCount < 15 && imageArea > 0.4 * pageArea
        }

    fun fontMeta(id: Int): FontMeta = fonts.metas[id]
    fun blockOfLine(lineId: Int): TextBlock? = blocks.firstOrNull { lineId in it.lineIds }

    fun lineAtView(u: Float, v: Float, tolerance: Float = 3f): TextLine? {
        val (x, y) = geometry.viewToUser(u, v)
        return lines.firstOrNull { it.bbox.inset(tolerance).contains(x, y) }
    }

    fun viewBoxOf(line: TextLine): Box = geometry.userBoxToView(line.bbox)

    /** Glyphen, die ein Benutzerrechteck schneiden (mindestens 30 % Überdeckung). */
    fun glyphsIn(userBox: Box, includeInvisible: Boolean = true): List<Int> = glyphs.indices.filter { i ->
        val g = glyphs[i]
        (includeInvisible || !g.invisible) && g.bbox.intersectionArea(userBox) >= 0.3f * maxOf(0.01f, g.bbox.width * g.bbox.height)
    }

    /** Gesamter Text der Seite (sichtbare und unsichtbare OCR-Ebene) in Lesereihenfolge. */
    fun plainText(): String {
        val src = if (lines.isNotEmpty()) lines else hiddenLines
        return src.joinToString("\n") { it.text }
    }
}

/**
 * Erfasst jede Glyphe zusammen mit dem Operator, der sie zeichnet. Basis ist PDFTextStripper,
 * weil dieser bereits alle Text- und Zustandsoperatoren korrekt verarbeitet.
 */
private class GlyphCollector : PDFTextStripper() {
    val glyphs = ArrayList<GlyphInfo>()
    val images = ArrayList<ImageRegion>()
    val registry = FontRegistry()
    val opFonts = HashMap<Int, PDFont>()

    private var opCounter = 0
    private var nesting = 0
    private var currentOp = -1
    private var currentTopName = ""
    private var glyphInOp = 0
    private var lastGlyphOp = Int.MIN_VALUE

    init {
        // Farboperatoren registrieren, damit die Textfarbe erkannt wird
        addOperator(SetNonStrokingColorSpace())
        addOperator(SetNonStrokingColor())
        addOperator(SetNonStrokingColorN())
        addOperator(SetNonStrokingDeviceGrayColor())
        addOperator(SetNonStrokingDeviceRGBColor())
        addOperator(SetNonStrokingDeviceCMYKColor())
        sortByPosition = false
    }

    override fun processPage(page: PDPage) {
        opCounter = 0; nesting = 0; currentOp = -1; currentTopName = ""; glyphInOp = 0; lastGlyphOp = Int.MIN_VALUE
        super.processPage(page)
    }

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        val top = nesting == 0
        if (top) {
            currentOp = opCounter++
            currentTopName = operator.name
            runCatching {
                when (operator.name) {
                    "Do" -> captureXObject(operands)
                    "BI" -> captureUnitSquare(false)
                }
            }
        }
        nesting++
        try {
            super.processOperator(operator, operands)
        } finally {
            nesting--
        }
    }

    private fun ctm(): Affine = graphicsState.currentTransformationMatrix.toAffine()

    private fun captureUnitSquare(isForm: Boolean, name: String? = null) {
        val m = ctm()
        val pts = listOf(m.apply(0f, 0f), m.apply(1f, 0f), m.apply(0f, 1f), m.apply(1f, 1f))
        images.add(ImageRegion(currentOp, Box.bounding(pts), isForm, name))
    }

    private fun captureXObject(operands: List<COSBase>) {
        val name = operands.firstOrNull() as? COSName ?: return
        val xobj = resources?.getXObject(name) ?: return
        when (xobj) {
            is PDImageXObject -> captureUnitSquare(false, name.name)
            is PDFormXObject -> {
                val bb = xobj.bBox ?: return
                val m = xobj.matrix.toAffine().then(ctm())
                val pts = listOf(
                    m.apply(bb.lowerLeftX, bb.lowerLeftY), m.apply(bb.upperRightX, bb.lowerLeftY),
                    m.apply(bb.lowerLeftX, bb.upperRightY), m.apply(bb.upperRightX, bb.upperRightY),
                )
                images.add(ImageRegion(currentOp, Box.bounding(pts), true, name.name))
            }
        }
    }

    override fun showGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, unicode: String?, displacement: Vector) {
        val opIndex = if (currentTopName == "Do") -1 else currentOp
        if (opIndex != lastGlyphOp) { glyphInOp = 0; lastGlyphOp = opIndex }
        val gi = glyphInOp++
        val trm = textRenderingMatrix.toAffine()
        val w0 = displacement.x
        val fd = font.fontDescriptor
        var asc = (fd?.ascent ?: 0f) / 1000f
        var desc = (fd?.descent ?: 0f) / 1000f
        if (asc <= 0.3f || asc > 1.2f) asc = 0.8f
        if (desc >= 0f || desc < -0.5f) desc = -0.2f
        val corners = listOf(trm.apply(0f, desc), trm.apply(w0, desc), trm.apply(0f, asc), trm.apply(w0, asc))
        val horizontal = abs(trm.b) < 0.01f * abs(trm.a) && abs(trm.c) < 0.01f * abs(trm.d) && trm.a > 0 && trm.d > 0
        val size = if (horizontal) abs(trm.d) else sqrt(trm.c * trm.c + trm.d * trm.d)
        val ts = graphicsState.textState
        val color = runCatching { graphicsState.nonStrokingColor.toRGB() }.getOrDefault(0)
        val invisible = runCatching { ts.renderingMode == RenderingMode.NEITHER }.getOrDefault(false)
        val tj = TextLayout.tjCompensation(
            w0, ts.fontSize, ts.characterSpacing, ts.wordSpacing,
            isSingleByteSpace = code == 32 && font !is PDType0Font,
        )
        if (opIndex >= 0) opFonts.putIfAbsent(opIndex, font)
        glyphs.add(
            GlyphInfo(
                opIndex = opIndex, glyphIndex = gi, code = code, unicode = unicode ?: "�",
                x = trm.e, y = trm.f, width = w0 * trm.a, size = size, bbox = Box.bounding(corners),
                tjAdvance = tj, fontId = registry.idOf(font), colorArgb = (0xFF shl 24) or (color and 0xFFFFFF),
                horizontal = horizontal, invisible = invisible,
            )
        )
    }
}

fun Matrix.toAffine(): Affine = Affine(scaleX, shearY, shearX, scaleY, translateX, translateY)

object PageAnalyzer {
    fun analyze(doc: PDDocument, pageIndex: Int): PageText {
        val page = doc.getPage(pageIndex)
        val collector = GlyphCollector()
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.writeText(doc, StringWriter())
        val visibleLines = TextLayout.buildLines(collector.glyphs, includeInvisible = false)
        val hidden = TextLayout.buildLines(collector.glyphs.filter { it.invisible }, includeInvisible = true)
        return PageText(
            pageIndex = pageIndex,
            geometry = PdfIo.geometry(page),
            glyphs = collector.glyphs,
            lines = visibleLines,
            blocks = TextLayout.buildBlocks(visibleLines),
            hiddenLines = hidden,
            fonts = collector.registry,
            opFonts = collector.opFonts,
            images = collector.images,
        )
    }

    /**
     * Sammelt für eine Schrift alle bereits im Dokument vorkommenden Zeichen → Zeichencode.
     * Damit können neue Texte oft mit der exakt eingebetteten Originalschrift gesetzt werden,
     * selbst wenn diese nur als Teilmenge (Subset) eingebettet ist.
     */
    fun glyphMapFor(doc: PDDocument, fontCos: Any, maxPages: Int = 30): Map<String, Int> {
        val map = HashMap<String, Int>()
        val n = minOf(doc.numberOfPages, maxPages)
        for (p in 0 until n) {
            val pt = analyze(doc, p)
            pt.glyphs.forEach { g ->
                val f = pt.fonts.fonts[g.fontId]
                if (f.cosObject === fontCos && g.unicode.length == 1 && g.unicode != "�") map.putIfAbsent(g.unicode, g.code)
            }
        }
        return map
    }
}
