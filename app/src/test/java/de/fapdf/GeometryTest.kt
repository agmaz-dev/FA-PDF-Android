package de.fapdf

import de.fapdf.pdf.Affine
import de.fapdf.pdf.Box
import de.fapdf.pdf.PageGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    private fun near(a: Float, b: Float) = assertEquals(b.toDouble(), a.toDouble(), 0.01)

    @Test fun unrotatedPageMapsTopLeft() {
        val g = PageGeometry(0f, 0f, 595f, 842f, 0)
        val (u, v) = g.userToView(0f, 842f)
        near(u, 0f); near(v, 0f)
        val (x, y) = g.viewToUser(100f, 50f)
        near(x, 100f); near(y, 792f)
    }

    @Test fun roundTripAllRotationsAndCropOffsets() {
        for (rot in listOf(0, 90, 180, 270, -90, 450)) {
            val g = PageGeometry(30f, 40f, 500f, 700f, rot)
            for ((x, y) in listOf(30f to 40f, 530f to 740f, 100f to 300f, 400f to 90f)) {
                val (u, v) = g.userToView(x, y)
                assert(u in -0.01f..g.viewWidth + 0.01f && v in -0.01f..g.viewHeight + 0.01f) { "rot=$rot außerhalb: $u,$v" }
                val (x2, y2) = g.viewToUser(u, v)
                near(x2, x); near(y2, y)
            }
        }
    }

    @Test fun rotation90PutsUnrotatedTopLeftAtTopRight() {
        val g = PageGeometry(0f, 0f, 600f, 800f, 90)
        near(g.viewWidth, 800f); near(g.viewHeight, 600f)
        val (u, v) = g.userToView(0f, 800f)
        near(u, 800f); near(v, 0f)
    }

    @Test fun uprightMatrixPointsRightInView() {
        for (rot in listOf(0, 90, 180, 270)) {
            val g = PageGeometry(0f, 0f, 600f, 800f, rot)
            val m = g.uprightMatrixAt(100f, 200f)
            // Ursprung landet am Ansichtspunkt, Einheitsvektor x zeigt in der Ansicht nach rechts, y nach oben
            val o = m.apply(0f, 0f); val ex = m.apply(10f, 0f); val ey = m.apply(0f, 10f)
            val (ou, ov) = g.userToView(o.first, o.second)
            val (xu, xv) = g.userToView(ex.first, ex.second)
            val (yu, yv) = g.userToView(ey.first, ey.second)
            near(ou, 100f); near(ov, 200f)
            near(xu, 110f); near(xv, 200f)
            near(yu, 100f); near(yv, 190f)
        }
    }

    @Test fun affineThenAppliesFirstThenSecond() {
        val t = Affine.translate(10f, 0f).then(Affine.scale(2f, 2f))
        val (x, y) = t.apply(1f, 1f)
        near(x, 22f); near(y, 2f)
    }

    @Test fun boxOperations() {
        val a = Box(0f, 0f, 10f, 10f)
        val b = Box(5f, 5f, 15f, 15f)
        assert(a.intersects(b))
        near(a.intersectionArea(b), 25f)
        assertEquals(Box(0f, 0f, 15f, 15f), a.union(b))
    }
}
