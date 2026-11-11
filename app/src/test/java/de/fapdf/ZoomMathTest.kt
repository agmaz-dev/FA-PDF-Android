package de.fapdf

import de.fapdf.ui.components.ZoomMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    private fun near(a: Float, b: Float) = assertEquals(b.toDouble(), a.toDouble(), 0.01)

    @Test fun focalPointStaysUnderFingersHorizontally() {
        // Sichtfenster 1000 px, Seitenrand 24 px, Inhalt bei Zoom 1 = 1000 px, bei Zoom 2 = 2*(1000-48)+48
        val pad = 24f
        val newContent = 2 * (1000f - 48f) + 48f
        val focal = 600f
        val s = ZoomMath.newScroll(0f, focal, 0f, 2f, pad, pad, newContent, 1000f)
        // Punkt unter dem Finger: vorher Inhalt x = 600 → relativ 576 → nachher 1152 + 24 = 1176; Sicht = 1176 - s
        near(1176f - s, focal)
    }

    @Test fun panMovesContentWithFingers() {
        val s = ZoomMath.newScroll(300f, 500f, -40f, 1f, 24f, 24f, 2000f, 1000f)
        near(s, 340f) // Finger nach links → Inhalt folgt → Scroll wächst
    }

    @Test fun scrollIsClamped() {
        near(ZoomMath.newScroll(0f, 100f, 500f, 1f, 24f, 24f, 1500f, 1000f), 0f)
        near(ZoomMath.newScroll(400f, 900f, -500f, 1f, 24f, 24f, 1500f, 1000f), 500f)
        near(ZoomMath.newScroll(0f, 100f, 0f, 1f, 24f, 24f, 800f, 1000f), 0f) // Inhalt kleiner als Fenster
    }

    @Test fun indexAndOffsetRoundTrip() {
        val h = ZoomMath.itemHeights(listOf(600f to 800f, 600f to 800f, 800f to 600f), 1200f, 24f)
        for (abs in listOf(0f, 500f, 1624f, 2000f, 3300f)) {
            val (i, o) = ZoomMath.indexAndOffset(h, abs)
            near(ZoomMath.absoluteScroll(h, i, o), abs.coerceAtMost(h.sum() - 1f))
        }
    }

    @Test fun verticalFocalPointStays() {
        val h1 = ZoomMath.itemHeights(listOf(600f to 800f, 600f to 800f), 1000f, 0f)
        val h2 = ZoomMath.itemHeights(listOf(600f to 800f, 600f to 800f), 2000f, 0f)
        val s = ZoomMath.newVerticalScroll(500f, 300f, 0f, 2f, h2, 800f)
        near(1600f - s, 300f)
        org.junit.Assert.assertTrue(h1.sum() * 2 == h2.sum())
    }

    @Test fun wideWindowShowsWholePageAtZoomOne() {
        // Fold innen ca. 960 x 870 dp, A4 hochkant: Seite so breit wie hoch passt
        val w = ZoomMath.basePageWidth(960f, 870f, 12f, 12f, 595f / 842f, wide = true)
        near(w, (870f - 12f) * 595f / 842f)
        // Handy: volle Breite
        near(ZoomMath.basePageWidth(400f, 800f, 12f, 12f, 595f / 842f, wide = false), 376f)
        // Querformat-Seite auf breitem Fenster: nie breiter als das Fenster
        near(ZoomMath.basePageWidth(960f, 870f, 12f, 12f, 842f / 595f, wide = true), 936f)
    }

    @Test fun centeredPageKeepsFocalPointWhenZoomingFromNarrowContent() {
        // Seite 600 px mittig in 960 px Fenster, Zoom 1→2: Fokus auf Seitenmitte (x = 480)
        val oldMargin = ZoomMath.margin(600f, 960f, 12f)       // 180
        val newMargin = ZoomMath.margin(1200f, 960f, 12f)      // 12
        val newContent = ZoomMath.contentWidth(1200f, 960f, 12f)
        val s = ZoomMath.newScroll(0f, 480f, 0f, 2f, oldMargin, newMargin, newContent, 960f)
        // Punkt lag 300 px rechts der Seitenkante → jetzt 600 px rechts; Sicht = margin + 600 - scroll
        near(newMargin + 600f - s, 480f)
    }
}
