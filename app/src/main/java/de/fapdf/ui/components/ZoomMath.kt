package de.fapdf.ui.components

/**
 * Reine Rechenlogik für den Zoom um einen Fokuspunkt (ohne Android-Abhängigkeit, damit per Unit-Test prüfbar).
 * Alle Werte in Pixeln. Der Fokuspunkt (die Mitte zwischen den zwei Fingern) bleibt beim Zoomen unter den Fingern.
 */
object ZoomMath {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f
    const val DOUBLE_TAP_ZOOM = 2.5f

    fun clampZoom(z: Float) = z.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * Neue Scroll-Position auf einer Achse.
     * [oldScroll] = aktuelle Verschiebung (>= 0), [focal] = Fokuspunkt im Sichtfenster,
     * [pan] = Fingerbewegung seit dem letzten Ereignis, [ratio] = neuer Zoom / alter Zoom,
     * [pad] = fester Rand vor dem Inhalt (skaliert nicht mit), [newContent] / [viewport] = Größen nach dem Zoomen.
     */
    fun newScroll(oldScroll: Float, focal: Float, pan: Float, ratio: Float, pad: Float, newContent: Float, viewport: Float): Float {
        val contentPoint = oldScroll + focal - pad          // Punkt relativ zum skalierenden Inhalt
        val target = contentPoint * ratio + pad - (focal + pan)
        return target.coerceIn(0f, (newContent - viewport).coerceAtLeast(0f))
    }

    /** Höhe jedes Eintrags der Seitenliste in Pixeln (Seite + fester Abstand). */
    fun itemHeights(pageSizes: List<Pair<Float, Float>>, pageWidthPx: Float, gapPx: Float): List<Float> =
        pageSizes.map { (w, h) -> pageWidthPx / w * h + gapPx }

    /** Absolute Scroll-Position (px vom Listenanfang) aus Index und Versatz. */
    fun absoluteScroll(heights: List<Float>, index: Int, offset: Int): Float {
        var y = offset.toFloat()
        for (i in 0 until index.coerceAtMost(heights.size)) y += heights[i]
        return y
    }

    /** Umkehrung: absolute Position → (Index, Versatz im Eintrag). */
    fun indexAndOffset(heights: List<Float>, absolute: Float): Pair<Int, Int> {
        var rest = absolute.coerceAtLeast(0f)
        for (i in heights.indices) {
            if (rest < heights[i] || i == heights.lastIndex) return i to rest.coerceAtMost(heights[i] - 1f).coerceAtLeast(0f).toInt()
            rest -= heights[i]
        }
        return 0 to 0
    }

    /** Neue vertikale Position nach Zoom/Verschieben, begrenzt auf die Gesamthöhe. */
    fun newVerticalScroll(oldAbsolute: Float, focal: Float, pan: Float, ratio: Float, newHeights: List<Float>, viewport: Float): Float {
        val total = newHeights.sum()
        // Der Abstand zwischen den Seiten (gapPx) skaliert nicht exakt mit; die Abweichung ist je Seite < 12 dp und unmerklich.
        val target = (oldAbsolute + focal) * ratio - (focal + pan)
        return target.coerceIn(0f, (total - viewport).coerceAtLeast(0f))
    }
}
