package de.fapdf.pdf

/** Seitenbereiche wie „1-3, 5, 8-“ – rein, ohne Android, daher testbar. */
object PageRangeParser {
    /** @return Liste von Bereichen mit 0-basierten Seitenindizes. */
    fun parse(input: String, pageCount: Int): List<List<Int>> {
        val result = ArrayList<List<Int>>()
        for (rawPart in input.split(',', ';')) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            val range = if ('-' in part) {
                val (a, b) = part.split('-', limit = 2).map { it.trim() }
                val start = if (a.isEmpty()) 1 else a.toIntOrNull() ?: throw IllegalArgumentException("Ungültige Angabe: $part")
                val end = if (b.isEmpty()) pageCount else b.toIntOrNull() ?: throw IllegalArgumentException("Ungültige Angabe: $part")
                if (start > end) throw IllegalArgumentException("Bereich rückwärts: $part")
                (start..end)
            } else {
                val n = part.toIntOrNull() ?: throw IllegalArgumentException("Ungültige Angabe: $part")
                (n..n)
            }
            if (range.first < 1 || range.last > pageCount) throw IllegalArgumentException("Seite außerhalb des Dokuments (1–$pageCount): $part")
            result.add(range.map { it - 1 })
        }
        if (result.isEmpty()) throw IllegalArgumentException("Bitte Seiten angeben, z. B. 1-3, 5")
        return result
    }
}

