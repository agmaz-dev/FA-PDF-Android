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
        val noMd = clean(s)
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

    /** Erste Zeichen normalisiert – zum Erkennen, ob ein Stück von vorn wiederholt wurde. */
    fun head(s: String, n: Int = 40) = s.lowercase().replace(Regex("[^a-zäöüß0-9]"), "").take(n)
}
