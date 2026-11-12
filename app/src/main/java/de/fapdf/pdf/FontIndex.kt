package de.fapdf.pdf

import com.tom_roush.fontbox.ttf.TTFParser
import java.io.File

/** Eine Schriftdatei (TrueType) mit ihrer Familie und ihrem Schnitt. */
data class FontFile(val file: File, val family: String, val bold: Boolean, val italic: Boolean, val variable: Boolean) {
    val key: String get() = "user:${file.name}"
    val title: String get() = family + (if (bold && italic) " Fett Kursiv" else if (bold) " Fett" else if (italic) " Kursiv" else "")
}

/**
 * Verzeichnis der Schriften auf dem Gerät: vom Benutzer hinzugefügte ([userDir]) und installierte Systemschriften.
 * Gelesen wird nur die Namenstabelle; Ergebnisse werden pro Ordner gemerkt.
 */
object FontIndex {
    private val cache = HashMap<String, List<FontFile>>()

    @Synchronized fun invalidate(dir: File) { cache.remove(dir.path) }

    @Synchronized fun scan(dir: File, maxBytes: Long = 6_000_000L): List<FontFile> =
        cache.getOrPut(dir.path) {
            (dir.listFiles() ?: emptyArray())
                .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") && it.length() in 1..maxBytes }
                .mapNotNull { parse(it) }
                .sortedWith(compareBy({ it.family.lowercase() }, { it.bold }, { it.italic }))
        }

    /** Liest Familie und Schnitt; null, wenn die Datei keine lesbare TrueType-Schrift ist. */
    fun parse(file: File): FontFile? = runCatching {
        val ttf = TTFParser(true).parse(file)
        try {
            val n = ttf.naming
            val fam = (n.fontFamily ?: ttf.name ?: file.nameWithoutExtension).trim()
            val sub = (n.fontSubFamily ?: "").lowercase()
            val wght = runCatching { ttf.getOS2Windows().weightClass.toInt() }.getOrDefault(400)
            val bold = "bold" in sub || "black" in sub || "heavy" in sub || wght >= 600
            val italic = "italic" in sub || "oblique" in sub
            FontFile(file, fam, bold, italic, ttf.tableMap.containsKey("fvar"))
        } finally { ttf.close() }
    }.getOrNull()

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /**
     * Beste passende Schrift für den Namen einer Originalschrift (z. B. „ABCDEF+Calibri-Bold“):
     * Familienname ist Anfang des Namens; längster Treffer gewinnt; Schnitt passend, sonst der nächste.
     */
    fun match(fonts: List<FontFile>, originalName: String, bold: Boolean, italic: Boolean): FontFile? {
        val n = norm(FontMeta.cleanName(originalName))
        val candidates = fonts.filter { norm(it.family).let { f -> f.length >= 3 && n.startsWith(f) } }
        if (candidates.isEmpty()) return null
        val family = candidates.maxByOrNull { norm(it.family).length }!!.family
        val same = candidates.filter { it.family == family }
        return same.firstOrNull { it.bold == bold && it.italic == italic && !it.variable }
            ?: same.firstOrNull { it.bold == bold && it.italic == italic }
            ?: same.firstOrNull { !it.bold && !it.italic }
            ?: same.first()
    }
}
