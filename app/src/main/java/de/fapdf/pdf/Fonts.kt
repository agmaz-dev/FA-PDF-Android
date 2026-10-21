package de.fapdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont
import java.io.ByteArrayInputStream
import java.util.IdentityHashMap

/**
 * Die mitgelieferten Ersatzschriften (Liberation, SIL Open Font License). Sie sind
 * metrisch kompatibel zu Arial/Helvetica, Times New Roman und Courier New – also zu den
 * häufigsten Schriften in Briefen und Formularen – und enthalten alle deutschen und
 * türkischen Sonderzeichen.
 */
class FontLibrary(private val context: Context) {
    private fun assetName(family: FontFamily, bold: Boolean, italic: Boolean): String {
        val base = when (family) {
            FontFamily.SANS -> "LiberationSans"
            FontFamily.SERIF -> "LiberationSerif"
            FontFamily.MONO -> "LiberationMono"
        }
        val style = when {
            bold && italic -> "BoldItalic"
            bold -> "Bold"
            italic -> "Italic"
            else -> "Regular"
        }
        return "fonts/$base-$style.ttf"
    }

    fun bytes(family: FontFamily, bold: Boolean, italic: Boolean): ByteArray =
        context.assets.open(assetName(family, bold, italic)).use { it.readBytes() }

    /** Lädt die Ersatzschrift in ein Dokument (Subset = nur benutzte Zeichen werden eingebettet). */
    fun load(doc: PDDocument, family: FontFamily, bold: Boolean, italic: Boolean, subset: Boolean = true): PDType0Font =
        PDType0Font.load(doc, ByteArrayInputStream(bytes(family, bold, italic)), subset)
}

/** Cache für Ersatzschriften pro geöffnetem Dokument. */
class DocFontCache(private val doc: PDDocument, private val library: FontLibrary) {
    private val cache = HashMap<String, PDType0Font>()
    fun get(family: FontFamily, bold: Boolean, italic: Boolean): PDType0Font =
        cache.getOrPut("$family/$bold/$italic") { library.load(doc, family, bold, italic) }
}

object FontSupport {
    fun meta(font: PDFont): FontMeta {
        val name = runCatching { font.name }.getOrNull() ?: "Unbekannt"
        val d = font.fontDescriptor
        val (family, bold, italic) = FontClassifier.classify(
            rawName = name,
            fixedPitch = d?.isFixedPitch ?: false,
            serifFlag = d?.isSerif ?: false,
            forceBold = d?.isForceBold ?: false,
            weight = d?.fontWeight ?: 0f,
            italicFlag = d?.isItalic ?: false,
            italicAngle = d?.italicAngle ?: 0f,
        )
        return FontMeta(name, family, bold, italic, runCatching { font.isEmbedded }.getOrDefault(false))
    }

    /** Liefert Bytes, falls [font] alle Zeichen von [text] selbst darstellen kann (inkl. vorhandener Glyphen). */
    fun encodeIfComplete(font: PDFont, text: String): ByteArray? {
        if (font is PDType3Font) return null
        return try {
            val bytes = font.encode(text)
            if (font is PDVectorFont) {
                val input = ByteArrayInputStream(bytes)
                while (input.available() > 0) {
                    val code = font.readCode(input)
                    if (!font.hasGlyph(code)) return null
                }
            }
            bytes
        } catch (e: Exception) {
            null
        }
    }

    /** Breite einer kodierten Zeichenfolge in Textraum-Einheiten (1/1000 em). */
    fun widthOfBytes(font: PDFont, bytes: ByteArray): Float {
        val input = ByteArrayInputStream(bytes)
        var w = 0f
        while (input.available() > 0) {
            val code = font.readCode(input)
            w += runCatching { font.getWidth(code) }.getOrDefault(500f)
        }
        return w
    }

    fun spaceWidth(font: PDFont): Float {
        val w = runCatching { font.spaceWidth }.getOrDefault(0f)
        return if (w > 50f) w else 250f
    }

    /** Kodiert einen Zeichencode als Bytes (für bekannte Glyphen aus der Seite). */
    fun codeToBytes(font: PDFont, code: Int): ByteArray? {
        return if (font is PDType0Font) {
            val cmapName = runCatching { font.cMap.name }.getOrNull() ?: ""
            if (cmapName.startsWith("Identity") || cmapName.contains("UCS2")) {
                byteArrayOf(((code shr 8) and 0xFF).toByte(), (code and 0xFF).toByte())
            } else null
        } else {
            if (code in 0..255) byteArrayOf(code.toByte()) else null
        }
    }
}

/** Identitätsbasierte Zuordnung PDFont → laufende Nummer. */
class FontRegistry {
    private val ids = IdentityHashMap<PDFont, Int>()
    val fonts = ArrayList<PDFont>()
    val metas = ArrayList<FontMeta>()
    fun idOf(font: PDFont): Int = ids.getOrPut(font) {
        fonts.add(font); metas.add(FontSupport.meta(font)); fonts.size - 1
    }
}
