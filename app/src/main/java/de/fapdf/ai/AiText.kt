package de.fapdf.ai

/** Räumt KI-Antworten auf: keine Code-Zäune (```json …), keine Denk-Abschnitte, keine doppelten Leerzeilen. */
object AiText {
    fun clean(s: String): String = s
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .replace(Regex("(?m)^\\s*```[a-zA-Z]*\\s*$"), "")
        .replace("```json", "").replace("```", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Für Brieftexte: Markdown-Zeichen weg und wiederholte Sätze nur einmal behalten. */
    fun letter(s: String): String {
        val noMd = clean(s).lines().filterNot { it.trim().startsWith("(Hinweis: Gemini Nano") }.joinToString("\n")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1").replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("(?m)^\\s*#{1,6}\\s*"), "").replace(Regex("(?m)^\\s*[*•]\\s+"), "- ")
            .replace("**", "")
        val seen = HashSet<String>()
        return noMd.split("\n\n").mapNotNull { para ->
            para.trim().lines().mapNotNull { line ->
                val kept = Regex("(?<=[.!?])[ \\t]+").split(line).filter { sen ->
                    val k = sen.lowercase().replace(Regex("[^a-zäöüß0-9]"), "")
                    k.length < 25 || seen.add(k)
                }
                kept.joinToString(" ").takeIf { it.isNotBlank() || line.isBlank() }
            }.joinToString("\n").trim().takeIf { it.isNotBlank() }
        }.joinToString("\n\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /**
     * Erkannten Scan-Text säubern: Zeilen ohne Buchstaben, die nur Strichcode-/Randnummern oder Zeichenmüll
     * sind (z. B. „110204“, „||||“, „8/N 45040“), sowie Druckvermerke entfernen. Zeilen mit Datum,
     * Betrag oder Text bleiben erhalten.
     */
    fun cleanOcr(t: String): String = t.lines().filterNot { line ->
        val s = line.trim()
        if (s.isEmpty() || s.startsWith("[Seite")) return@filterNot false
        val digitsOnly = s.all { it.isDigit() || it == ' ' }
        val n = s.count { it.isDigit() }
        // Seitenzahlen (1–3 Ziffern) und Strichcode-Nummern (5–7 Ziffern) allein in einer Zeile; Strich-Müll; Druckvermerk
        (digitsOnly && (n in 1..3 || n in 5..7)) || Regex("^[|Il!\\s]{4,}$").matches(s) || s.equals("DATEV", true)
    }.joinToString("\n")

    /** Erste Zeichen normalisiert – zum Erkennen, ob ein Stück von vorn wiederholt wurde. */
    fun head(s: String, n: Int = 40) = s.lowercase().replace(Regex("[^a-zäöüß0-9]"), "").take(n)
}
