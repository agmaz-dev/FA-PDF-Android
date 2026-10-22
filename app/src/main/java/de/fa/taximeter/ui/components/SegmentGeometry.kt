package de.fa.taximeter.ui.components

/**
 * Reine Geometrie der Siebensegment-Anzeige (ohne Android-Abhängigkeit, dadurch testbar).
 * Segmente: a=oben, b=oben rechts, c=unten rechts, d=unten, e=unten links, f=oben links, g=Mitte.
 */
object SegmentGeometry {

    /** Breite/Höhe einer Ziffernzelle. */
    const val CELL_ASPECT = 0.62f
    private const val SKEW = 0.10f

    data class Cell(val ch: Char?, val comma: Boolean)

    private const val A = 1
    private const val B = 2
    private const val C = 4
    private const val D = 8
    private const val E = 16
    private const val F = 32
    private const val G = 64

    fun mask(ch: Char?): Int = when (ch) {
        '0', 'O' -> A or B or C or D or E or F
        '1' -> B or C
        '2' -> A or B or G or E or D
        '3' -> A or B or G or C or D
        '4' -> F or G or B or C
        '5', 'S' -> A or F or G or C or D
        '6' -> A or F or G or E or D or C
        '7' -> A or B or C
        '8' -> A or B or C or D or E or F or G
        '9' -> A or B or C or D or F or G
        '-' -> G
        'E' -> A or F or G or E or D
        'P' -> A or B or F or G or E
        else -> 0
    }

    /**
     * Zerlegt einen Text wie "43,80" in rechtsbündige Zellen. Ein Komma (oder Punkt) gehört
     * zur Ziffer davor. Freie Zellen (ch == null) werden als unbeleuchtete "8" gezeichnet.
     */
    fun cells(text: String, count: Int): List<Cell> {
        val raw = mutableListOf<Cell>()
        for (c in text) {
            if (c == ',' || c == '.') {
                if (raw.isNotEmpty()) raw[raw.size - 1] = raw.last().copy(comma = true)
                else raw += Cell(null, true)
            } else if (c != ' ') {
                raw += Cell(c, false)
            } else {
                raw += Cell(null, false)
            }
        }
        val trimmed = if (raw.size > count) raw.takeLast(count) else raw
        return List(count - trimmed.size) { Cell(null, false) } + trimmed
    }

    /** Liefert die 7 Segment-Polygone (a..g) als [x0,y0,x1,y1,...] für eine Zelle. */
    fun segments(x0: Float, y0: Float, w: Float, h: Float): List<FloatArray> {
        val gw = w * 0.62f
        val t = gw * 0.2f
        val g = t * 0.18f
        val left = x0 + w * 0.08f
        val right = left + gw
        val top = y0 + t / 2 + h * 0.03f
        val bottom = y0 + h - t / 2 - h * 0.03f
        val mid = (top + bottom) / 2
        val baseY = y0 + h

        fun skew(p: FloatArray): FloatArray {
            val out = p.copyOf()
            var i = 0
            while (i < out.size) {
                out[i] = out[i] + (baseY - out[i + 1]) * SKEW
                i += 2
            }
            return out
        }

        fun hSeg(xa: Float, xb: Float, yc: Float) = skew(
            floatArrayOf(
                xa + g, yc,
                xa + g + t / 2, yc - t / 2,
                xb - g - t / 2, yc - t / 2,
                xb - g, yc,
                xb - g - t / 2, yc + t / 2,
                xa + g + t / 2, yc + t / 2,
            )
        )

        fun vSeg(xc: Float, ya: Float, yb: Float) = skew(
            floatArrayOf(
                xc, ya + g,
                xc + t / 2, ya + g + t / 2,
                xc + t / 2, yb - g - t / 2,
                xc, yb - g,
                xc - t / 2, yb - g - t / 2,
                xc - t / 2, ya + g + t / 2,
            )
        )

        return listOf(
            hSeg(left, right, top),      // a
            vSeg(right, top, mid),       // b
            vSeg(right, mid, bottom),    // c
            hSeg(left, right, bottom),   // d
            vSeg(left, mid, bottom),     // e
            vSeg(left, top, mid),        // f
            hSeg(left, right, mid),      // g
        )
    }

    /** Dezimalkomma rechts unten an der Zelle. */
    fun comma(x0: Float, y0: Float, w: Float, h: Float): FloatArray {
        val gw = w * 0.62f
        val t = gw * 0.2f
        val s = t * 1.05f
        val left = x0 + w * 0.08f
        val bottom = y0 + h - t / 2 - h * 0.03f
        val cx = left + gw + t * 1.15f
        val cy = bottom
        val baseY = y0 + h
        val p = floatArrayOf(
            cx - s / 2, cy - s / 2,
            cx + s / 2, cy - s / 2,
            cx + s / 2, cy + s / 2,
            cx - s * 0.35f, cy + s * 0.8f,
            cx - s * 0.55f, cy + s * 0.7f,
            cx - s / 2, cy + s / 2,
        )
        var i = 0
        while (i < p.size) {
            p[i] = p[i] + (baseY - p[i + 1]) * SKEW
            i += 2
        }
        return p
    }

    fun isLit(mask: Int, index: Int): Boolean = (mask shr index) and 1 == 1
}
