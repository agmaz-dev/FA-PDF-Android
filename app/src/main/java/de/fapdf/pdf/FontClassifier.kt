package de.fapdf.pdf

enum class FontFamily(val title: String) { SANS("Serifenlos"), SERIF("Serif"), MONO("Monospace") }

/** Aus einer PDF ermittelte Schriftinformationen. */
data class FontMeta(
    val rawName: String,
    val family: FontFamily,
    val bold: Boolean,
    val italic: Boolean,
    val embedded: Boolean,
) {
    /** Name ohne Subset-Präfix (z. B. „ABCDEF+Arial-BoldMT“ → „Arial-BoldMT“). */
    val displayName: String get() = cleanName(rawName)

    companion object {
        fun cleanName(name: String): String =
            if (name.length > 7 && name[6] == '+' && name.substring(0, 6).all { it in 'A'..'Z' }) name.substring(7) else name
    }
}

/** Reine Klassifikationslogik – ohne Android-Abhängigkeit, damit sie per Unit-Test geprüft werden kann. */
object FontClassifier {
    private val serifHints = listOf(
        "times", "serif", "roman", "garamond", "georgia", "cambria", "book", "palatino", "minion",
        "baskerville", "bodoni", "caslon", "century", "constantia", "didot", "liberationserif", "tinos",
        "notoserif", "sourceserif", "charter", "antiqua", "bembo",
    )
    private val sansOverrides = listOf("sans", "grotesk", "helvetica", "arial", "verdana", "segoe", "calibri", "roboto")
    private val monoHints = listOf("courier", "mono", "consolas", "menlo", "typewriter", "lucidaconsole", "inconsolata", "cousine")
    private val boldHints = listOf("bold", "black", "heavy", "semibold", "demibold", "extrabold", "ultrabold", "fett")
    private val italicHints = listOf("italic", "oblique", "kursiv", "slanted")

    fun classify(
        rawName: String, fixedPitch: Boolean = false, serifFlag: Boolean = false,
        forceBold: Boolean = false, weight: Float = 0f, italicFlag: Boolean = false, italicAngle: Float = 0f,
    ): Triple<FontFamily, Boolean, Boolean> {
        val n = FontMeta.cleanName(rawName).lowercase().replace(Regex("[^a-z]"), "")
        val family = when {
            monoHints.any { it in n } || fixedPitch -> FontFamily.MONO
            sansOverrides.any { it in n } -> FontFamily.SANS
            serifHints.any { it in n } || serifFlag -> FontFamily.SERIF
            else -> FontFamily.SANS
        }
        val bold = boldHints.any { it in n } || forceBold || weight >= 600f
        val italic = italicHints.any { it in n } || italicFlag || kotlin.math.abs(italicAngle) > 0.5f
        return Triple(family, bold, italic)
    }
}

