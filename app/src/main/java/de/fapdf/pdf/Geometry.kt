package de.fapdf.pdf

import kotlin.math.max
import kotlin.math.min

/** Einfaches Rechteck in Gleitkomma-Koordinaten (unabhängig von android.graphics, damit testbar). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    fun intersects(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun union(o: Box) = Box(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    fun inset(d: Float) = Box(left - d, top - d, right + d, bottom + d)
    fun intersectionArea(o: Box): Float {
        val w = min(right, o.right) - max(left, o.left)
        val h = min(bottom, o.bottom) - max(top, o.top)
        return if (w > 0 && h > 0) w * h else 0f
    }

    companion object {
        fun of(x1: Float, y1: Float, x2: Float, y2: Float) = Box(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
        fun bounding(points: List<Pair<Float, Float>>): Box =
            Box(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
    }
}

/**
 * Affine Transformation im PDF-Sinne: [a b 0; c d 0; e f 1], Punkte als Zeilenvektor.
 * x' = a*x + c*y + e ; y' = b*x + d*y + f
 */
data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
    fun apply(x: Float, y: Float): Pair<Float, Float> = Pair(a * x + c * y + e, b * x + d * y + f)

    /** Zuerst this, dann [o]. */
    fun then(o: Affine) = Affine(
        a * o.a + b * o.c, a * o.b + b * o.d,
        c * o.a + d * o.c, c * o.b + d * o.d,
        e * o.a + f * o.c + o.e, e * o.b + f * o.d + o.f,
    )

    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)
        fun translate(x: Float, y: Float) = Affine(1f, 0f, 0f, 1f, x, y)
        fun scale(sx: Float, sy: Float) = Affine(sx, 0f, 0f, sy, 0f, 0f)

        /** Abbildung, die die drei Punkte Ursprung, X-Achse, Y-Achse festlegt (Einheitsquadrat → Parallelogramm). */
        fun fromBasis(origin: Pair<Float, Float>, xEnd: Pair<Float, Float>, yEnd: Pair<Float, Float>) = Affine(
            xEnd.first - origin.first, xEnd.second - origin.second,
            yEnd.first - origin.first, yEnd.second - origin.second,
            origin.first, origin.second,
        )
    }
}

/**
 * Umrechnung zwischen PDF-Benutzerkoordinaten (Ursprung unten links, ungedreht) und
 * Ansichtskoordinaten in Punkten (Ursprung oben links, so wie die Seite angezeigt wird –
 * also nach CropBox und /Rotate).
 */
class PageGeometry(
    val cropLeft: Float,
    val cropBottom: Float,
    val cropWidth: Float,
    val cropHeight: Float,
    rotation: Int,
) {
    val rotation: Int = ((rotation % 360) + 360) % 360
    val viewWidth: Float get() = if (rotation == 90 || rotation == 270) cropHeight else cropWidth
    val viewHeight: Float get() = if (rotation == 90 || rotation == 270) cropWidth else cropHeight

    fun userToView(x: Float, y: Float): Pair<Float, Float> {
        val xp = x - cropLeft
        val yp = y - cropBottom
        val w = cropWidth
        val h = cropHeight
        return when (rotation) {
            90 -> Pair(yp, xp)
            180 -> Pair(w - xp, yp)
            270 -> Pair(h - yp, w - xp)
            else -> Pair(xp, h - yp)
        }
    }

    fun viewToUser(u: Float, v: Float): Pair<Float, Float> {
        val w = cropWidth
        val h = cropHeight
        val (xp, yp) = when (rotation) {
            90 -> Pair(v, u)
            180 -> Pair(w - u, v)
            270 -> Pair(w - v, h - u)
            else -> Pair(u, h - v)
        }
        return Pair(xp + cropLeft, yp + cropBottom)
    }

    /** Rechteck in Benutzerkoordinaten → Ansichtsrechteck. */
    fun userBoxToView(b: Box): Box = Box.bounding(
        listOf(userToView(b.left, b.top), userToView(b.right, b.top), userToView(b.left, b.bottom), userToView(b.right, b.bottom))
    )

    fun viewBoxToUser(b: Box): Box = Box.bounding(
        listOf(viewToUser(b.left, b.top), viewToUser(b.right, b.top), viewToUser(b.left, b.bottom), viewToUser(b.right, b.bottom))
    )

    /**
     * Matrix für Inhalte, die in Ansichtsrichtung aufrecht erscheinen sollen:
     * Ursprung am Ansichtspunkt (u, v), x-Achse nach rechts, y-Achse nach oben (in der Ansicht).
     */
    fun uprightMatrixAt(u: Float, v: Float): Affine {
        val o = viewToUser(u, v)
        val xe = viewToUser(u + 1f, v)
        val ye = viewToUser(u, v - 1f)
        return Affine.fromBasis(o, xe, ye)
    }

    /** Matrix, die das Einheitsquadrat eines Bildes auf ein (gedrehtes) Ansichtsrechteck legt. */
    fun imageMatrix(cx: Float, cy: Float, w: Float, h: Float, rotationDeg: Float): Affine {
        val rad = Math.toRadians(rotationDeg.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        fun rot(px: Float, py: Float): Pair<Float, Float> =
            Pair(cx + px * cos - py * sin, cy + px * sin + py * cos)
        val bl = rot(-w / 2, h / 2)   // unten links (Ansicht: y nach unten)
        val br = rot(w / 2, h / 2)
        val tl = rot(-w / 2, -h / 2)
        return Affine.fromBasis(
            viewToUser(bl.first, bl.second), viewToUser(br.first, br.second), viewToUser(tl.first, tl.second)
        )
    }
}
