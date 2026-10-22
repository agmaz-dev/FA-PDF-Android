package de.fa.taximeter.domain

import de.fa.taximeter.domain.gps.Geo
import de.fa.taximeter.domain.gps.GpsFilterConfig
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.persist.Codecs
import de.fa.taximeter.domain.tariff.TariffCatalog
import de.fa.taximeter.domain.trip.MeterMachine
import de.fa.taximeter.domain.trip.MeterMachineState
import de.fa.taximeter.domain.trip.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Tunnel-Schätzung, Zuschlag in 0,50-€-Schritten, STOPP mit Summe und FREI zum Abschließen. */
class TunnelAndCashTest {

    private val cfg = GpsFilterConfig()
    private fun eur(s: String) = BigDecimal(s)

    private fun fix(eastM: Double, tMs: Long, speed: Double = 22.2): GpsFix {
        val (la, lo) = Geo.offset(52.45, 13.45, 0.0, eastM)
        return GpsFix(la, lo, 5.0, tMs, 1_700_000_000_000 + tMs, speed)
    }

    /** 60 s mit 80 km/h fahren, danach Stand t=60 s, Position 1332 m. */
    private fun drive80(): MeterMachineState {
        var s = MeterMachine.pressBesetzt(MeterMachine.initial(TariffCatalog.BERLIN), fix(0.0, 0), 0, 0, cfg, false) { "t" }.state
        for (i in 1..60) s = MeterMachine.onFix(s, fix(i * 22.2, i * 1000L), i * 1000L, cfg).state
        return s
    }

    @Test fun zaehlerLaeuftImTunnelWeiterUndSpringtNieZurueck() {
        var s = drive80()
        val before = MeterMachine.liveFare(s, 60_000)
        assertFalse(MeterMachine.isEstimating(s, 62_000))
        // Im Tunnel: kein Fix, Zähler läuft mit 80 km/h weiter
        var last = before
        for (t in 64_000L..137_000L step 1_000L) {
            s = MeterMachine.tick(s, t).state
            val f = MeterMachine.liveFare(s, t)
            assertTrue("Preis darf nicht sinken", f >= last)
            last = f
        }
        assertTrue(MeterMachine.isEstimating(s, 100_000))
        // 77 s * 22,2 m/s = 1709 m -> ca. +4,80 € bei 2,80 €/km
        assertTrue("im Tunnel geschätzt: $last", last >= before.add(eur("4.00")))
        // Ausfahrt nach 1,7 km: echte Messung per Luftlinie
        s = MeterMachine.onFix(s, fix(1_332.0 + 1_700.0, 137_000), 137_000, cfg).state
        val after = MeterMachine.liveFare(s, 137_000)
        assertTrue(after >= last)
        assertEquals(1_332.0 + 1_700.0, s.run!!.meter.odometerM, 30.0)
        assertEquals(0L, s.run!!.meter.chargedWaitingMs)
        assertEquals(1, s.run!!.gaps.size)
        assertEquals(1_700.0, s.run!!.gaps[0].bridgedM, 2.0)
    }

    @Test fun ohneFahrtImTunnelKeineSchaetzung() {
        var s = MeterMachine.pressBesetzt(MeterMachine.initial(TariffCatalog.BERLIN), fix(0.0, 0, 0.0), 0, 0, cfg, false) { "t" }.state
        for (i in 1..30) s = MeterMachine.onFix(s, fix(0.0, i * 1000L, 0.0), i * 1000L, cfg).state
        val f = MeterMachine.liveFare(s, 30_000)
        assertEquals(f, MeterMachine.liveFare(s, 120_000))
        assertFalse(MeterMachine.isEstimating(s, 120_000))
    }

    @Test fun schaetzungIstZeitlichBegrenzt() {
        val s = drive80()
        assertEquals(MeterMachine.liveFare(s, 60_000 + 180_000), MeterMachine.liveFare(s, 60_000 + 900_000))
    }

    @Test fun zuschlagIn50CentSchrittenUndZuruecksetzen() {
        var s = MeterMachine.initial(TariffCatalog.BER)
        repeat(3) { s = MeterMachine.addSurchargeStep(s).state }
        assertEquals(eur("1.50"), s.activeSurcharges.total)
        assertEquals(1, s.activeSurcharges.items.size)
        s = MeterMachine.resetSurcharges(s).state
        assertEquals(eur("0.00"), s.activeSurcharges.total)
        repeat(2) { s = MeterMachine.addSurchargeStep(s).state }
        s = MeterMachine.pressBesetzt(s, fix(0.0, 0), 0, 0, cfg, false) { "t" }.state
        assertEquals(eur("1.00"), s.activeSurcharges.total)
        s = MeterMachine.addSurchargeStep(s).state
        assertEquals(eur("1.50"), s.activeSurcharges.total)
    }

    @Test fun stoppZeigtSummeFortsetzenLaeuftMitSummeWeiterFreiSchliesstAb() {
        var s = drive80()
        repeat(3) { s = MeterMachine.addSurchargeStep(s).state } // 1,50 €
        val fareBefore = MeterMachine.liveFare(s, 60_000)
        assertEquals(fareBefore, MeterMachine.displayAmount(s, 60_000)) // vor STOPP: nur Fahrpreis
        s = MeterMachine.pressStopp(s, 60_000, 60_000, cfg).state
        assertTrue(MeterMachine.showsTotal(s))
        assertEquals(fareBefore.add(eur("1.50")), MeterMachine.displayAmount(s, 60_000))
        // Kunde fährt weiter: BESETZT -> Zuschlag wandert wieder nach oben, unten nur Fahrpreis
        s = MeterMachine.pressBesetzt(s, fix(1_332.0, 61_000), 61_000, 61_000, cfg, false) { "x" }.state
        assertEquals(Phase.BESETZT, s.phase)
        assertFalse(MeterMachine.showsTotal(s))
        assertEquals(fareBefore, MeterMachine.displayAmount(s, 61_000))
        assertEquals(eur("1.50"), s.activeSurcharges.total)
        for (i in 1..30) s = MeterMachine.onFix(s, fix(1_332.0 + i * 22.2, 61_000 + i * 1000L), 61_000 + i * 1000L, cfg).state
        val fareNow = MeterMachine.displayAmount(s, 91_000)
        assertEquals(MeterMachine.liveFare(s, 91_000), fareNow)
        assertTrue(fareNow > fareBefore)
        // STOPP, dann FREI: Fahrt gespeichert, Zähler leer
        s = MeterMachine.pressStopp(s, 91_000, 91_000, cfg).state
        val shown = MeterMachine.displayAmount(s, 91_000)
        assertEquals(fareNow.add(eur("1.50")), shown)
        val (r, rec) = MeterMachine.pressFrei(s, 92_000)
        assertNotNull(rec)
        assertEquals(Phase.FREI, r.state.phase)
        assertEquals(eur("0.00"), r.state.activeSurcharges.total)
        assertEquals(rec!!.fare.add(eur("1.50")), rec.total)
        assertEquals(shown, rec.total)
        // FREI während BESETZT beendet nichts
        val running = drive80()
        assertFalse(MeterMachine.pressFrei(running, 1).first.changed)
    }

    @Test fun neueFelderWerdenGespeichert() {
        var s = drive80()
        s = MeterMachine.tick(s, 100_000).state
        s = MeterMachine.pressStopp(s, 100_000, 100_000, cfg).state
        val back = Codecs.machineFromJson(Codecs.machineToJson(s))
        assertEquals(s, back)
        assertTrue(back.run!!.showTotal)
        assertTrue(back.run!!.fareFloor > BigDecimal.ZERO)
    }
}
