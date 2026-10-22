package de.fa.taximeter.domain

import de.fa.taximeter.ui.components.SegmentGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentGeometryTest {

    @Test fun kommaGehoertZurZifferDavorUndRechtsbuendig() {
        val c = SegmentGeometry.cells("43,80", 7)
        assertEquals(7, c.size)
        assertEquals(listOf(null, null, null, '4', '3', '8', '0'), c.map { it.ch })
        assertEquals(listOf(false, false, false, false, true, false, false), c.map { it.comma })
    }

    @Test fun leereAnzeigeNurUnbeleuchtet() {
        assertTrue(SegmentGeometry.cells("", 6).all { it.ch == null && !it.comma })
        assertEquals(0, SegmentGeometry.mask(null))
        assertEquals(127, SegmentGeometry.mask('8'))
    }

    @Test fun segmenteBleibenInnerhalbDerZelle() {
        val w = 62f
        val h = 100f
        for (x0 in listOf(0f, 62f * 6)) {
            val polys = SegmentGeometry.segments(x0, 0f, w, h) + listOf(SegmentGeometry.comma(x0, 0f, w, h))
            polys.forEach { p ->
                var i = 0
                while (i < p.size) {
                    assertTrue("x ${p[i]} außerhalb", p[i] >= x0 - 0.01f && p[i] <= x0 + w + 0.01f)
                    assertTrue("y ${p[i + 1]} außerhalb", p[i + 1] >= -0.01f && p[i + 1] <= h + 0.01f)
                    i += 2
                }
            }
        }
    }
}
