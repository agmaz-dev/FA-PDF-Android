package de.fapdf.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Suchindex und Ordnung der Dokumente: erkannter Text (für die Volltextsuche),
 * Kategorie (z. B. „Krankenkasse“) und ob die KI den Namen schon vergeben hat.
 * Liegt neben der Datenbank in eigenen Dateien – keine Datenbank-Änderung nötig.
 */
class DocIndex(context: Context) {
    private val dir = File(context.filesDir, "index").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("doc_index", Context.MODE_PRIVATE)
    private val texts = ConcurrentHashMap<Long, String>()

    /** Steigt bei jeder Änderung – Oberfläche zeichnet dann neu. */
    var version by mutableIntStateOf(0); private set

    fun hasText(id: Long) = texts.containsKey(id) || File(dir, "$id.txt").exists()
    fun text(id: Long): String? = texts[id] ?: File(dir, "$id.txt").takeIf { it.exists() }?.readText()?.also { texts[id] = it }
    fun setText(id: Long, text: String) {
        File(dir, "$id.txt").writeText(text); texts[id] = text; version++
    }
    fun forget(id: Long) {
        File(dir, "$id.txt").delete(); texts.remove(id)
        prefs.edit().remove("cat_$id").remove("named_$id").apply(); version++
    }

    fun category(id: Long): String? = prefs.getString("cat_$id", null)
    fun setCategory(id: Long, c: String?) { prefs.edit().putString("cat_$id", c).apply(); version++ }
    fun isNamed(id: Long) = prefs.getBoolean("named_$id", false)
    fun markNamed(id: Long) { prefs.edit().putBoolean("named_$id", true).apply() }

    /** Treffer im Namen oder im Text (Leerzeichen und Groß/klein egal). */
    fun matches(d: DocumentEntity, query: String): Boolean {
        val q = norm(query)
        if (q.isEmpty()) return true
        if (norm(d.displayName).contains(q)) return true
        if (category(d.id)?.let { norm(it).contains(q) } == true) return true
        return text(d.id)?.let { norm(it).contains(q) } == true
    }

    /** Kurzer Ausschnitt rund um den Treffer im Text. */
    fun snippet(id: Long, query: String): String? {
        val t = text(id) ?: return null
        val i = t.indexOf(query.trim(), ignoreCase = true)
        if (i < 0) return null
        val s = (i - 40).coerceAtLeast(0); val e = (i + query.length + 60).coerceAtMost(t.length)
        return (if (s > 0) "…" else "") + t.substring(s, e).replace(Regex("\\s+"), " ").trim() + (if (e < t.length) "…" else "")
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), "")

    companion object {
        val CATEGORIES = listOf("Krankenkasse", "Jobcenter", "Versicherung", "Bank", "Finanzamt", "Behörde", "Rechnung", "Vertrag", "Taxi", "Sonstiges")

        /** Kategorie ohne KI anhand typischer Wörter. */
        fun guessCategory(text: String): String? {
            val t = text.lowercase()
            fun any(vararg w: String) = w.any { t.contains(it) }
            return when {
                any("krankenkasse", " aok", "barmer", "techniker krankenkasse", " hek ", "dak-gesundheit", "ikk ", "krankenversicherung", "beitragskonto") -> "Krankenkasse"
                any("jobcenter", "bürgergeld", "arbeitsagentur", "agentur für arbeit") -> "Jobcenter"
                any("finanzamt", "steuernummer", "einkommensteuer", "umsatzsteuer") -> "Finanzamt"
                any("versicherung", "schadennummer", "versicherungsschein", "police") -> "Versicherung"
                any("taxi", "konzession", "personenbeförderung", "taxameter") -> "Taxi"
                any("kontoauszug", "sparkasse", "volksbank", "iban", "kreditkarte", "darlehen") -> "Bank"
                any("rechnung", "rechnungsnummer", "zahlbar bis", "mahnung") -> "Rechnung"
                any("vertrag", "kündigung", "laufzeit") -> "Vertrag"
                any("amt", "bezirksamt", "landesamt", "bescheid", "aktenzeichen", "investitionsbank") -> "Behörde"
                else -> null
            }
        }

        /** Unverständliche Namen wie „19693631572137“, „DOC-2026…“, „Scan_…“, „IMG…“. */
        fun isGenericName(name: String): Boolean {
            val b = name.removeSuffix(".pdf").removeSuffix(".PDF").trim()
            return b.matches(Regex("[\\d _.-]{6,}")) || b.startsWith("DOC-", true) || b.startsWith("Scan", true) ||
                b.startsWith("IMG", true) || b.startsWith("document", true) || b.startsWith("Dokument", true) ||
                b.matches(Regex(".*\\d{10,}.*")) || b.length < 4
        }
    }
}
