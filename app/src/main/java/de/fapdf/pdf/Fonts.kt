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
 * Ersatzschriften. Zuerst wird eine zur Originalschrift passende Systemschrift des Geräts gesucht
 * (z. B. Roboto, Noto Serif – auf jedem Android-Gerät vorhanden), sonst die mitgelieferten
 * Liberation-Schriften (SIL Open Font License). Diese sind metrisch kompatibel zu Arial/Helvetica,
 * Times New Roman und Courier New und enthalten alle deutschen und türkischen Sonderzeichen.
 *
 * Wichtig: Eingebettet wird nur, was vor dem Speichern als Subset erzeugt wird – siehe [PendingSubsets].
 */
class FontLibrary(
    private val open: (String) -> java.io.InputStream,
    private val systemFontDirs: List<java.io.File> = emptyList(),
) {
    constructor(context: Context) : this({ path -> context.assets.open(path) }, listOf(java.io.File("/system/fonts")))

    private fun assetName(family: FontFamily, bold: Boolean, italic: Boolean): String {
        val base = when (family) {
            FontFamily.SANS -> "LiberationSans"
            FontFamily.SERIF -> "LiberationSerif"
            FontFamily.MONO -> "LiberationMono"
        }
        return "fonts/$base-${styleSuffix(bold, italic)}.ttf"
    }

    fun bytes(family: FontFamily, bold: Boolean, italic: Boolean): ByteArray =
        open(assetName(family, bold, italic)).use { it.readBytes() }

    /** Lädt die Ersatzschrift in ein Dokument (Subset = nur benutzte Zeichen werden eingebettet). */
    fun load(doc: PDDocument, family: FontFamily, bold: Boolean, italic: Boolean, subset: Boolean = true): PDType0Font =
        loadBytes(doc, bytes(family, bold, italic), subset)

    private fun loadBytes(doc: PDDocument, data: ByteArray, subset: Boolean): PDType0Font {
        val font = PDType0Font.load(doc, ByteArrayInputStream(data), subset)
        if (subset) PendingSubsets.register(doc, font)
        return font
    }

    /**
     * Systemschrift, die zur Originalschrift [originalName] passt (gleiche Schriftfamilie), oder null.
     * Variable Schriften werden nur für normale Strichstärke verwendet (Standardinstanz = Regular).
     */
    fun systemMatch(originalName: String, bold: Boolean, italic: Boolean): java.io.File? {
        val n = FontMeta.cleanName(originalName).lowercase().replace(Regex("[^a-z]"), "")
        val bases = when {
            "roboto" in n && "mono" in n -> listOf("RobotoMono")
            "roboto" in n && ("condensed" in n) -> listOf("RobotoCondensed", "Roboto", "RobotoStatic")
            "roboto" in n -> listOf("Roboto", "RobotoStatic")
            "notoserif" in n -> listOf("NotoSerif")
            "notosans" in n && "mono" in n -> listOf("NotoSansMono")
            "notosans" in n || "opensans" in n -> listOf("NotoSans", "Roboto", "RobotoStatic")
            "droidsansmono" in n -> listOf("DroidSansMono")
            "droidserif" in n -> listOf("NotoSerif")
            "droidsans" in n -> listOf("Roboto", "RobotoStatic")
            "sourcesanspro" in n || "sourcesans" in n -> listOf("SourceSansPro")
            "cutivemono" in n -> listOf("CutiveMono")
            "dancingscript" in n -> listOf("DancingScript")
            else -> return null
        }
        val style = styleSuffix(bold, italic)
        for (dir in systemFontDirs) for (base in bases) {
            val exact = java.io.File(dir, "$base-$style.ttf")
            if (exact.isFile) return exact
            if (!bold && !italic) {
                val plain = java.io.File(dir, "$base.ttf")
                if (plain.isFile) return plain
            }
        }
        return null
    }

    /** Lädt eine Bibliotheksschrift; null, wenn sie deutsche/türkische Sonderzeichen nicht darstellen kann. */
    fun loadCatalog(doc: PDDocument, font: CatalogFont, bold: Boolean, italic: Boolean): PDType0Font? = runCatching {
        val data = open("fonts/${font.assetBase}-${styleSuffix(bold, italic)}.ttf").use { it.readBytes() }
        val f = PDType0Font.load(doc, ByteArrayInputStream(data), true)
        f.encode("äöüßÄÖÜğşıİç")
        PendingSubsets.register(doc, f)
        f
    }.getOrNull()

    /** Ersatzschrift für eine Originalschrift: passende Systemschrift, dann Bibliothek, sonst Liberation. */
    fun loadMatching(doc: PDDocument, originalName: String?, family: FontFamily, bold: Boolean, italic: Boolean): PDType0Font {
        val sys = originalName?.let { systemMatch(it, bold, italic) }
        if (sys != null) {
            val ok = runCatching {
                val data = sys.readBytes()
                val f = PDType0Font.load(doc, ByteArrayInputStream(data), true)
                // Variable Schrift (fvar) nur für Normalgewicht; Glyphen für Umlaute müssen vorhanden sein
                val variable = runCatching { (f.descendantFont as? com.tom_roush.pdfbox.pdmodel.font.PDCIDFontType2)?.trueTypeFont?.tableMap?.containsKey("fvar") == true }.getOrDefault(false)
                if ((variable && (bold || italic)) || runCatching { f.encode("äöüßğşıİç") }.isFailure) null else f
            }.getOrNull()
            if (ok != null) { PendingSubsets.register(doc, ok); return ok }
        }
        originalName?.let { FontCatalog.matchByName(it) }?.let { c -> loadCatalog(doc, c, bold, italic)?.let { return it } }
        return load(doc, family, bold, italic)
    }

    companion object {
        fun styleSuffix(bold: Boolean, italic: Boolean) = when {
            bold && italic -> "BoldItalic"
            bold -> "Bold"
            italic -> "Italic"
            else -> "Regular"
        }
    }
}


/** Eine mitgelieferte freie Schrift (Regular/Bold/Italic/BoldItalic als TTF in assets/fonts). */
data class CatalogFont(
    val id: String, val title: String, val family: FontFamily, val assetBase: String,
    /** Namensteile (klein, nur Buchstaben) von Originalschriften, die diese Schrift ersetzt. */
    val replaces: List<String>,
)

/**
 * Bibliothek freier Schriften. Wird automatisch gewählt, wenn eine Originalschrift (z. B. Calibri, Cambria, Georgia)
 * nicht vollständig eingebettet ist, und steht in der Schriftauswahl zur Verfügung.
 * Arial/Helvetica, Times und Courier ersetzt weiterhin Liberation (metrisch kompatibel).
 */
object FontCatalog {
    val fonts = listOf(
        CatalogFont("carlito", "Carlito (wie Calibri)", FontFamily.SANS, "Carlito", listOf("calibri", "carlito")),
        CatalogFont("lato", "Lato", FontFamily.SANS, "Lato", listOf("lato", "opensans", "segoe", "myriad", "frutiger", "helveticaneue")),
        CatalogFont("ptsans", "PT Sans", FontFamily.SANS, "PTSans", listOf("ptsans", "tahoma", "verdana", "trebuchet", "centurygothic")),
        CatalogFont("ubuntu", "Ubuntu", FontFamily.SANS, "Ubuntu", listOf("ubuntu")),
        CatalogFont("caladea", "Caladea (wie Cambria)", FontFamily.SERIF, "Caladea", listOf("cambria", "caladea")),
        CatalogFont("crimson", "Crimson Text", FontFamily.SERIF, "CrimsonText", listOf("crimson", "georgia", "garamond", "minion", "palatino", "bookantiqua", "baskerville", "bembo")),
        CatalogFont("ptserif", "PT Serif", FontFamily.SERIF, "PTSerif", listOf("ptserif", "charter", "constantia")),
        CatalogFont("courierprime", "Courier Prime", FontFamily.MONO, "CourierPrime", listOf("courierprime", "lucidaconsole")),
        CatalogFont("comicneue", "Comic Neue", FontFamily.SANS, "ComicNeue", listOf("comic", "chalkboard")),
    )

    fun byId(id: String?): CatalogFont? = fonts.firstOrNull { it.id == id }

    /** Passende Bibliotheksschrift für den Namen einer Originalschrift oder null. */
    fun matchByName(originalName: String): CatalogFont? {
        val n = FontMeta.cleanName(originalName).lowercase().replace(Regex("[^a-z]"), "")
        return fonts.firstOrNull { f -> f.replaces.any { it in n } }
    }
}

/**
 * PDFBox bettet eine Schrift mit Subset erst beim Speichern ein – aber nur, wenn sie zuvor über
 * PDPageContentStream.setFont registriert wurde. Da FA PDF Inhaltsströme selbst schreibt, werden die
 * Schriften hier gesammelt und in [PdfIo.save] vor dem Speichern als Subset eingebettet.
 * Ohne diesen Schritt würde die Schrift nicht eingebettet und Android zeigte Ersatzglyphen (Zeichensalat).
 */
object PendingSubsets {
    private val pending = java.util.WeakHashMap<PDDocument, MutableSet<PDType0Font>>()

    @Synchronized fun register(doc: PDDocument, font: PDType0Font) {
        pending.getOrPut(doc) { java.util.LinkedHashSet() }.add(font)
    }

    @Synchronized fun flush(doc: PDDocument) {
        pending.remove(doc)?.forEach { font -> if (font.willBeSubset()) font.subset() }
    }
}

/** Cache für Ersatzschriften pro geöffnetem Dokument. */
class DocFontCache(private val doc: PDDocument, private val library: FontLibrary) {
    private val cache = HashMap<String, PDType0Font>()
    fun get(family: FontFamily, bold: Boolean, italic: Boolean): PDType0Font =
        cache.getOrPut("$family/$bold/$italic") { library.load(doc, family, bold, italic) }

    /** Ersatzschrift passend zur Originalschrift (System-, Bibliotheks- oder Liberation-Schrift). */
    fun matching(originalName: String?, family: FontFamily, bold: Boolean, italic: Boolean): PDType0Font =
        if (originalName == null) get(family, bold, italic)
        else cache.getOrPut("m/${FontMeta.cleanName(originalName)}/$family/$bold/$italic") {
            library.loadMatching(doc, originalName, family, bold, italic)
        }

    /** Vom Benutzer gewählte Bibliotheksschrift ([fontId]) oder die Familie; fehlen Zeichen, wird Liberation genommen. */
    fun forStyle(fontId: String?, family: FontFamily, bold: Boolean, italic: Boolean): PDType0Font {
        val c = FontCatalog.byId(fontId) ?: return get(family, bold, italic)
        return cache.getOrPut("c/${c.id}/$bold/$italic") { library.loadCatalog(doc, c, bold, italic) ?: library.load(doc, c.family, bold, italic) }
    }
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
