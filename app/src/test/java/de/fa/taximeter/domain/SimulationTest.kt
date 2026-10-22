package de.fa.taximeter.domain

import de.fa.taximeter.domain.gps.GpsFilterConfig
import de.fa.taximeter.domain.gps.SimulatedRoute
import de.fa.taximeter.domain.tariff.TariffCatalog
import de.fa.taximeter.domain.trip.MeterMachine
import de.fa.taximeter.domain.trip.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ende-zu-Ende über Tracker, Engine und Zustandsautomat mit synthetischer Fahrt. */
class SimulationTest {

    @Test fun simulierteStadtfahrtPlausibel() {
        val cfg = GpsFilterConfig(allowMock = true)
        val route = SimulatedRoute(seed = 3)
        var t = 0L
        var s = MeterMachine.initial(TariffCatalog.BERLIN)
        s = MeterMachine.pressBesetzt(s, route.next(t, t), t, t, cfg, true, { "sim" }).state
        assertEquals(Phase.BESETZT, s.phase)
        // Ein voller Zyklus = 340 s
        for (i in 1..340) {
            t = i * 1000L
            s = MeterMachine.onFix(s, route.next(t, t), t, cfg).state
        }
        val m = s.run!!.meter
        // Sollstrecke: 60 s * 11,11 + 40 s * 2,22 + 120 s * 13,89 = 2 422 m
        assertEquals(2_422.0, m.odometerM, 60.0)
        // Wartezeit: Halt 90 s (Karenz 60 s) + Schritttempo 40 s schließt direkt an -> 70 s,
        // Schlusshalt 30 s < Karenz. Toleranz für Anfahren/Abbremsen.
        assertTrue("Wartezeit ${m.chargedWaitingMs}", m.chargedWaitingMs in 60_000L..80_000L)
        assertEquals(340_000L, m.runningMs)
        s = MeterMachine.pressStopp(s, t, t, cfg).state
        val rec = MeterMachine.pressKasse(s, t).state.completed!!
        assertTrue(rec.simulation)
        assertTrue("Fahrpreis ${rec.fare}", rec.fare > java.math.BigDecimal("10.00") && rec.fare < java.math.BigDecimal("13.00"))
    }
}
