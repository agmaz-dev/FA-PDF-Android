package de.fa.taximeter.domain.persist

/**
 * Kleiner, abhängigkeitsfreier JSON-Leser/-Schreiber für die lokale Speicherung.
 * Zahlen werden als String-Rohtext gelesen ([JsonNumber]), damit BigDecimal-Werte verlustfrei bleiben.
 * Geldbeträge werden ohnehin als Strings gespeichert.
 */
object MiniJson {

    class JsonNumber(val raw: String) {
        fun toLong(): Long = raw.toBigDecimal().toLong()
        fun toDouble(): Double = raw.toDouble()
        fun toInt(): Int = raw.toBigDecimal().toInt()
        override fun toString(): String = raw
    }

    class ParseException(msg: String) : Exception(msg)

    // ------------------------------------------------------------------ Schreiben

    fun write(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(v, sb)
            is Boolean -> sb.append(v.toString())
            is Int, is Long -> sb.append(v.toString())
            is Double -> {
                require(!v.isNaN() && !v.isInfinite()) { "NaN/Infinity nicht erlaubt" }
                sb.append(v.toString())
            }
            is JsonNumber -> sb.append(v.raw)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(k.toString(), sb)
                    sb.append(':')
                    write(value, sb)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, e ->
                    if (i > 0) sb.append(',')
                    write(e, sb)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Nicht serialisierbar: ${v::class}")
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ------------------------------------------------------------------ Lesen

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) throw ParseException("Unerwartete Zeichen am Ende bei ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length
        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            if (atEnd()) throw ParseException("Unerwartetes Ende")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseException("Unerwartet '$c' bei $pos")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Erwartet $word bei $pos")
            pos += word.length
            return v
        }

        fun num(): JsonNumber {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val raw = s.substring(start, pos)
            raw.toBigDecimalOrNull() ?: throw ParseException("Ungültige Zahl '$raw'")
            return JsonNumber(raw)
        }

        fun str(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Unbeendeter String")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Unbeendetes Escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Ungültiges \\u")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Ungültiges Escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun obj(): Map<String, Any?> {
            pos++ // {
            val m = LinkedHashMap<String, Any?>()
            skipWs()
            if (!atEnd() && s[pos] == '}') {
                pos++; return m
            }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') throw ParseException("Schlüssel erwartet bei $pos")
                val k = str()
                skipWs()
                if (atEnd() || s[pos] != ':') throw ParseException("':' erwartet bei $pos")
                pos++
                m[k] = value()
                skipWs()
                if (atEnd()) throw ParseException("Unbeendetes Objekt")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> throw ParseException("',' oder '}' erwartet bei ${pos - 1}")
                }
            }
        }

        fun arr(): List<Any?> {
            pos++ // [
            val l = ArrayList<Any?>()
            skipWs()
            if (!atEnd() && s[pos] == ']') {
                pos++; return l
            }
            while (true) {
                l.add(value())
                skipWs()
                if (atEnd()) throw ParseException("Unbeendetes Array")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> throw ParseException("',' oder ']' erwartet bei ${pos - 1}")
                }
            }
        }
    }
}
