package de.fapdf.ai

/** Räumt KI-Antworten auf: keine Code-Zäune (```json …), keine Denk-Abschnitte, keine doppelten Leerzeilen. */
object AiText {
    fun clean(s: String): String = s
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .replace(Regex("(?m)^\\s*```[a-zA-Z]*\\s*$"), "")
        .replace("```json", "").replace("```", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Erste Zeichen normalisiert – zum Erkennen, ob ein Stück von vorn wiederholt wurde. */
    fun head(s: String, n: Int = 40) = s.lowercase().replace(Regex("[^a-zäöüß0-9]"), "").take(n)
}
