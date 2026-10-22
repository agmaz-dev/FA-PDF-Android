package de.fa.taximeter.domain

import de.fa.taximeter.domain.gps.DistanceTracker
import de.fa.taximeter.domain.gps.GapReason
import de.fa.taximeter.domain.gps.Geo
import de.fa.taximeter.domain.gps.GpsFilterConfig
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.gps.GpsQuality
import de.fa.taximeter.domain.gps.RejectReason
import de.fa.taximeter.domain.gps.TrackerState
import de.fa.taximeter.domain.tariff.MeterInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DistanceTrackerTest {

    private val cfg = GpsFilterConfig()
    private val lat0 = 52.5200
    private val lon0 = 13.4050

    private fun fix(northM: Double, eastM: Double, tMs: Long, acc: Double = 5.0, speed: Double? = null): GpsFix {
        val (la, lo) = Geo.offset(lat0, lon0, northM, eastM)
        return GpsFix(la, lo, acc, tMs, 1_700_000_000_000 + tMs, speed)
    }

    private class Run(var state: TrackerState) {
        val intervals = mutableListOf<MeterInterval>()
        val rejects = mutableListOf<RejectReason>()
        var gaps = 0
        val distance get() = intervals.sumOf { it.distanceM }
    }

    private fun feed(run: Run, f: GpsFix, now: Long = f.elapsedMs) {
        val r = DistanceTracker.onFix(run.state, f, now, cfg)
        run.state = r.state
        r.interval?.let { run.intervals += it }
        r.rejected?.let { run.rejects += it }
        if (r.gap != null) run.gaps++
    }

    @Test fun stillstandsRauschenErzeugtKeineStrecke() {
        val rnd = Random(42)
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, acc = 8.0, speed = 0.0), 0))
        // 10 Minuten, 1 Hz, Rauschen bis 6 m Radius, gemeldete Genauigkeit 6..12 m, Doppler ~0
        for (t in 1..600) {
            val r = rnd.nextDouble() * 6.0
            val a = rnd.nextDouble() * 2 * Math.PI
            feed(run, fix(r * Math.cos(a), r * Math.sin(a), t * 1000L, acc = 6.0 + rnd.nextDouble() * 6, speed = rnd.nextDouble() * 0.3))
        }
        assertEquals(0.0, run.distance, 0.0)
        assertEquals(600, run.intervals.size)
        assertEquals(600_000L, run.intervals.sumOf { it.durationMs })
    }

    @Test fun stillstandsRauschenOhneDopplerAuchNicht() {
        val rnd = Random(7)
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, acc = 5.0), 0))
        for (t in 1..600) {
            feed(run, fix(rnd.nextDouble() * 8 - 4, rnd.nextDouble() * 8 - 4, t * 1000L, acc = 5.0))
        }
        assertEquals(0.0, run.distance, 0.0)
    }

    @Test fun normaleFahrtWirdVollstaendigGezaehlt() {
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, speed = 13.9), 0))
        for (t in 1..300) feed(run, fix(0.0, t * 13.9, t * 1000L, speed = 13.9))
        // ~4170 m; Abweichung höchstens eine Ankerschwelle
        assertEquals(300 * 13.9, run.distance, 6.0)
    }

    @Test fun langsameFahrtWirdNichtVerschluckt() {
        // 0,8 m/s (Schritttempo) ohne Doppler-Bewegungsflag: Anker wächst und wird dann voll gezählt.
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, speed = 0.8), 0))
        for (t in 1..300) feed(run, fix(t * 0.8, 0.0, t * 1000L, speed = 0.8))
        assertEquals(240.0, run.distance, 2 * 5.0 + 1)
        assertTrue(run.distance > 220)
    }

    @Test fun ungenaueVeralteteUndDoppelteMessungenWerdenVerworfen() {
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0), 0))
        feed(run, fix(10.0, 0.0, 1000, acc = 45.0))
        feed(run, fix(10.0, 0.0, 2000), now = 9_000) // 7 s alt
        feed(run, fix(20.0, 0.0, 3000))
        feed(run, fix(20.0, 0.0, 3000)) // doppelt
        feed(run, fix(20.0, 0.0, 2500), now = 3000) // rückwärts
        assertEquals(
            listOf(RejectReason.INACCURATE, RejectReason.STALE, RejectReason.DUPLICATE, RejectReason.DUPLICATE),
            run.rejects,
        )
        assertEquals(1, run.intervals.size)
    }

    @Test fun gpsSprungWirdVerworfen() {
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, speed = 10.0), 0))
        for (t in 1..10) feed(run, fix(0.0, t * 10.0, t * 1000L, speed = 10.0))
        feed(run, fix(800.0, 100.0, 11_000, speed = 10.0)) // 800 m Sprung in 1 s
        for (t in 12..20) feed(run, fix(0.0, t * 10.0, t * 1000L, speed = 10.0))
        assertEquals(listOf(RejectReason.JUMP), run.rejects)
        assertEquals(200.0, run.distance, 6.0)
    }

    @Test fun dauerhafterVersatzWirdOhneStreckeNeuVerankert() {
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, speed = 0.0), 0))
        // Ab t=1 liegt jede Messung 2 km entfernt (z. B. erster Fix war falsch)
        for (t in 1..6) feed(run, fix(2_000.0, 0.0, t * 1000L, speed = 0.0))
        assertEquals(4, run.rejects.size)
        assertEquals(1, run.gaps)
        assertEquals(0.0, run.distance, 0.0)
    }

    @Test fun gpsAusfallUndWiederempfang_langeLueckeOhneLuftlinie() {
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, speed = 10.0), 0))
        for (t in 1..10) feed(run, fix(0.0, t * 10.0, t * 1000L, speed = 10.0))
        // 2 Minuten kein Fix (Tunnel), dann 1200 m weiter
        val r = DistanceTracker.onFix(run.state, fix(0.0, 1_300.0, 130_000, speed = 10.0), 130_000, cfg)
        val gap = r.gap!!
        assertEquals(GapReason.GPS_LOST, gap.reason)
        assertEquals(120_000L, gap.durationMs)
        assertEquals(0.0, gap.bridgedM, 0.0)              // keine ungeprüfte Luftlinienbrücke
        assertEquals(1_200.0, gap.straightLineM!!, 1.0)    // nur dokumentiert
        assertFalse(r.interval!!.measured)                 // Lücke ≠ Stillstand
        assertEquals(0.0, r.interval!!.distanceM, 0.0)
        // Danach wird normal weitergemessen
        run.state = r.state
        for (t in 131..140) feed(run, fix(0.0, 1_300.0 + (t - 130) * 10.0, t * 1000L, speed = 10.0))
        assertEquals(200.0, run.distance, 12.0)
    }

    @Test fun kurzeLueckeWirdGeprueftUeberbrueckt() {
        val run = Run(DistanceTracker.seed(fix(0.0, 0.0, 0, speed = 10.0), 0))
        for (t in 1..10) feed(run, fix(0.0, t * 10.0, t * 1000L, speed = 10.0))
        val r = DistanceTracker.onFix(run.state, fix(0.0, 250.0, 25_000, speed = 10.0), 25_000, cfg)
        assertEquals(150.0, r.gap!!.bridgedM, 1.0)
        assertFalse(r.interval!!.measured)
        assertEquals(150.0, r.interval!!.distanceM, 1.0)
    }

    @Test fun abschlussBeiStoppOhneStrecke() {
        val s = DistanceTracker.seed(fix(0.0, 0.0, 0), 0)
        val r = DistanceTracker.closeAt(s, 4_000, 0, cfg)
        assertTrue(r.interval!!.measured)
        assertEquals(0.0, r.interval!!.distanceM, 0.0)
        assertNull(r.gap)
        val r2 = DistanceTracker.closeAt(s, 60_000, 0, cfg)
        assertFalse(r2.interval!!.measured)
        assertEquals(GapReason.GPS_LOST, r2.gap!!.reason)
    }

    @Test fun qualitaetsanzeige() {
        val f = fix(0.0, 0.0, 10_000, acc = 6.0)
        assertEquals(GpsQuality.GOOD, DistanceTracker.quality(f, 10_000, 10_500, cfg))
        assertEquals(GpsQuality.FAIR, DistanceTracker.quality(fix(0.0, 0.0, 10_000, acc = 20.0), 10_000, 10_500, cfg))
        assertEquals(GpsQuality.UNRELIABLE, DistanceTracker.quality(fix(0.0, 0.0, 10_000, acc = 50.0), 10_000, 10_500, cfg))
        assertEquals(GpsQuality.UNRELIABLE, DistanceTracker.quality(f, 10_000, 14_000, cfg))
        assertEquals(GpsQuality.LOST, DistanceTracker.quality(f, 10_000, 30_000, cfg))
        assertEquals(GpsQuality.LOST, DistanceTracker.quality(null, null, 30_000, cfg))
    }

    @Test fun startNurMitBrauchbaremFix() {
        assertTrue(DistanceTracker.isUsableForStart(fix(0.0, 0.0, 1000, acc = 8.0), 2000, cfg))
        assertFalse(DistanceTracker.isUsableForStart(fix(0.0, 0.0, 1000, acc = 80.0), 2000, cfg))
        assertFalse(DistanceTracker.isUsableForStart(fix(0.0, 0.0, 1000), 20_000, cfg))
        assertFalse(DistanceTracker.isUsableForStart(null, 2000, cfg))
        assertFalse(DistanceTracker.isUsableForStart(fix(0.0, 0.0, 1000).copy(isMock = true), 2000, cfg))
    }
}
