package de.fa.taximeter.domain

import de.fa.taximeter.domain.gps.Geo
import de.fa.taximeter.domain.gps.GpsFilterConfig
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.persist.Codecs
import de.fa.taximeter.domain.tariff.TariffCatalog
import de.fa.taximeter.domain.trip.MeterMachine
import de.fa.taximeter.domain.trip.MeterMachineState
import de.fa.taximeter.domain.trip.PaymentMethod
import de.fa.taximeter.domain.trip.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CardPaymentTest {
    private val cfg = GpsFilterConfig()

    private fun fix(eastM: Double, t: Long): GpsFix {
        val (la, lo) = Geo.offset(52.45, 13.45, 0.0, eastM)
        return GpsFix(la, lo, 5.0, t, 1_700_000_000_000 + t, 13.9)
    }

    private fun stopped(): MeterMachineState {
        var s = MeterMachine.pressBesetzt(MeterMachine.initial(TariffCatalog.BERLIN), fix(0.0, 0), 0, 0, cfg, false) { "trip-abc" }.state
        for (i in 1..120) s = MeterMachine.onFix(s, fix(i * 13.9, i * 1000L), i * 1000L, cfg).state
        repeat(3) { s = MeterMachine.addSurchargeStep(s).state }
        return MeterMachine.pressStopp(s, 120_000, 120_000, cfg).state
    }

    @Test fun erfolgreicheKartenzahlungSchliesstMitSummeAb() {
        val s = stopped()
        val shown = MeterMachine.displayAmount(s, 120_000)
        val (r, req) = MeterMachine.startCardPayment(s)
        assertEquals(shown, req!!.amount)
        val (done, rec) = MeterMachine.onCardResult(r.state, true, req.paymentId, "TX123", 130_000)
        assertEquals(Phase.FREI, done.state.phase)
        assertEquals(PaymentMethod.KARTE, rec!!.payment)
        assertEquals("TX123", rec.paymentRef)
        assertEquals(shown, rec.total)
        assertEquals(rec, Codecs.historyFromJson(Codecs.historyToJson(listOf(rec)))[0])
    }

    @Test fun abbruchBleibtInStopp() {
        val (r, req) = MeterMachine.startCardPayment(stopped())
        val (after, rec) = MeterMachine.onCardResult(r.state, false, req!!.paymentId, null, 1)
        assertNull(rec)
        assertEquals(Phase.STOPP, after.state.phase)
        assertNull(after.state.run!!.pendingPaymentId)
        // neuer Versuch bekommt neue ID
        val (_, req2) = MeterMachine.startCardPayment(after.state)
        assertNotEquals(req.paymentId, req2!!.paymentId)
    }

    @Test fun fremdeOderVerspaeteteRueckmeldungWirdIgnoriert() {
        val (r, _) = MeterMachine.startCardPayment(stopped())
        val (x, rec) = MeterMachine.onCardResult(r.state, true, "anders-1", "TX", 1)
        assertNull(rec)
        assertFalse(x.changed)
        val free = MeterMachine.initial(TariffCatalog.BERLIN)
        assertNull(MeterMachine.onCardResult(free, true, "x", "TX", 1).second)
    }

    @Test fun nurInStopp() {
        var s = MeterMachine.pressBesetzt(MeterMachine.initial(TariffCatalog.BERLIN), fix(0.0, 0), 0, 0, cfg, false) { "t" }.state
        assertNull(MeterMachine.startCardPayment(s).second)
        s = MeterMachine.pressStopp(s, 1000, 1000, cfg).state
        val (r, req) = MeterMachine.startCardPayment(s)
        assertEquals(BigDecimal("4.30"), req!!.amount)
        // Weiterfahren verwirft die offene Zahlung
        val resumed = MeterMachine.pressBesetzt(r.state, fix(0.0, 2000), 2000, 2000, cfg, false) { "t" }.state
        assertNull(resumed.run!!.pendingPaymentId)
        assertEquals(r.state.run!!.pendingPaymentId, Codecs.machineFromJson(Codecs.machineToJson(r.state)).run!!.pendingPaymentId)
    }

    @Test fun barGilfFuerFrei() {
        val (_, rec) = MeterMachine.pressFrei(stopped(), 1)
        assertEquals(PaymentMethod.BAR, rec!!.payment)
    }
}
