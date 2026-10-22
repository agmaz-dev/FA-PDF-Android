package de.fa.taximeter.domain

import de.fa.taximeter.domain.tariff.FareEngine
import de.fa.taximeter.domain.tariff.MeterInterval
import de.fa.taximeter.domain.tariff.MeterState
import de.fa.taximeter.domain.tariff.TariffCatalog
import de.fa.taximeter.domain.tariff.TariffProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FareEngineTest {

    private val berlin = TariffCatalog.BERLIN // Anzeige 4, Tarifstufe 2
    private val ber = TariffCatalog.BER       // Anzeige 3, Tarifstufe 3

    private fun eur(s: String) = BigDecimal(s)

    /** Fährt [meters] zügig (50 km/h) in 1-m-genauen Sekundenschritten. */
    private fun drive(profile: TariffProfile, meters: Int, start: MeterState = MeterState()): MeterState {
        var s = start
        val speed = 50 / 3.6
        var left = meters.toDouble()
        while (left > 0) {
            val d = minOf(left, 10.0)
            s = FareEngine.apply(s, MeterInterval((d / speed * 1000).toLong().coerceAtLeast(1), d, speed, true), profile)
            left -= d
        }
        return s
    }

    private fun stand(profile: TariffProfile, seconds: Int, start: MeterState): MeterState {
        var s = start
        repeat(seconds) { s = FareEngine.apply(s, MeterInterval(1000, 0.0, 0.0, true), profile) }
        return s
    }

    private fun fare(p: TariffProfile, s: MeterState) = FareEngine.displayedFare(p, s)

    // ---------------------------------------------------------------- Grundpreis

    @Test fun grundpreisGenauEinmal() {
        assertEquals(eur("4.30"), fare(berlin, MeterState()))
        assertEquals(eur("4.30"), fare(ber, MeterState()))
        // Erste Teilstrecke (0,20 € = 71,43 m bei 2,80 €/km) ist im Grundpreis enthalten
        assertEquals(eur("4.30"), fare(berlin, drive(berlin, 71)))
        assertEquals(eur("4.50"), fare(berlin, drive(berlin, 72)))
    }

    // ---------------------------------------------------------------- Staffel 3 km (Tarif 4)

    @Test fun staffel3kmTarif4() {
        // 3000 m * 2,80 €/km = 8,40 € variabel -> 4,10 + 8,40 = 12,50 €
        assertEquals(eur("12.50"), fare(berlin, drive(berlin, 2_999)))
        assertEquals(eur("12.50"), fare(berlin, drive(berlin, 3_000)))
        assertEquals(eur("12.70"), fare(berlin, drive(berlin, 3_001)))
        assertEquals(eur("8.40"), Money.cents(drive(berlin, 3_000).distanceCharge))
    }

    // ---------------------------------------------------------------- Staffel 7 km (Tarif 4)

    @Test fun staffel7kmTarif4() {
        // 3 km * 2,80 + 4 km * 2,60 = 18,80 -> 22,90 €
        assertEquals(eur("22.90"), fare(berlin, drive(berlin, 6_999)))
        assertEquals(eur("22.90"), fare(berlin, drive(berlin, 7_000)))
        assertEquals(eur("23.10"), fare(berlin, drive(berlin, 7_001)))
        // Ab 7 km 2,10 €/km: 10 km = 18,80 + 6,30 = 25,10 € variabel = 125,5 Einheiten
        // -> 126 angefangene Einheiten, davon 1 im Grundpreis: 4,30 + 125 * 0,20 = 29,30 €
        assertEquals(eur("29.30"), fare(berlin, drive(berlin, 10_000)))
    }

    // ---------------------------------------------------------------- Staffel 5 km (Tarif 3)

    @Test fun staffel5kmTarif3() {
        // 5 km * 2,60 = 13,00 -> 17,10 €
        assertEquals(eur("17.10"), fare(ber, drive(ber, 4_999)))
        assertEquals(eur("17.10"), fare(ber, drive(ber, 5_000)))
        assertEquals(eur("17.30"), fare(ber, drive(ber, 5_001)))
        // 20 km: 13,00 + 15 * 2,00 = 43,00 -> 47,10 €
        assertEquals(eur("47.10"), fare(ber, drive(ber, 20_000)))
    }

    @Test fun staffelwechselNichtRueckwirkend() {
        // Ein Intervall, das die 3-km-Grenze überquert, wird abschnittsweise bepreist.
        val one = FareEngine.apply(MeterState(), MeterInterval(300_000, 4_000.0, 13.9, true), berlin)
        assertEquals(0, one.distanceCharge.compareTo(eur("11.00"))) // 3*2,80 + 1*2,60
        assertEquals(0, FareEngine.distanceCharge(berlin, 2_500.0, 1_000.0).compareTo(eur("2.70"))) // 0,5*2,8 + 0,5*2,6
    }

    @Test fun keineZwischenrundungJeGpsPunkt() {
        // 10 000 Intervalle à 0,3 m = 3 km. Würde jedes Intervall auf Cent gerundet, käme 0 heraus.
        var s = MeterState()
        repeat(10_000) { s = FareEngine.apply(s, MeterInterval(20, 0.3, 15.0, true), berlin) }
        assertEquals(eur("12.50"), fare(berlin, s))
    }

    // ---------------------------------------------------------------- Zeit / Karenz

    @Test fun umschaltgeschwindigkeitAusPreisen() {
        assertEquals(39.0 / 2.80, FareEngine.switchSpeedKmh(berlin, 0.0), 1e-9)   // 13,93 km/h
        assertEquals(15.0, FareEngine.switchSpeedKmh(berlin, 5_000.0), 1e-9)      // 39/2,60
        assertEquals(39.0 / 2.10, FareEngine.switchSpeedKmh(berlin, 8_000.0), 1e-9)
        assertEquals(36.0 / 2.60, FareEngine.switchSpeedKmh(ber, 0.0), 1e-9)
        assertEquals(18.0, FareEngine.switchSpeedKmh(ber, 6_000.0), 1e-9)
        assertEquals(18.461538, FareEngine.stepSeconds(berlin), 1e-5)            // 0,20 € bei 39 €/h
        assertEquals(20.0, FareEngine.stepSeconds(ber), 1e-9)                    // 0,20 € bei 36 €/h
    }

    @Test fun karenzErsteMinuteFrei() {
        val s1 = stand(berlin, 60, drive(berlin, 1_000))
        assertEquals(0L, s1.chargedWaitingMs)
        assertEquals(0, s1.timeCharge.signum())
        val s2 = stand(berlin, 1, s1)
        assertEquals(1_000L, s2.chargedWaitingMs)
    }

    @Test fun gemischteBerechnungOhneDoppelzaehlung() {
        // 1 km zügig, dann 5 Minuten Halt: 2,80 + 4 min * 39 €/h (2,60) = 5,40 -> 9,50 €
        val s = stand(berlin, 300, drive(berlin, 1_000))
        assertEquals(240_000L, s.chargedWaitingMs)
        assertEquals(0, Money.cents(s.timeCharge).compareTo(eur("2.60")))
        assertEquals(eur("9.50"), fare(berlin, s))
    }

    @Test fun langsameFahrtWirdNachZeitBerechnetNichtDoppelt() {
        // 3 Minuten mit 10 km/h (unter 13,93 km/h): 60 s Karenz nach Strecke, 120 s nach Zeit.
        val v = 10 / 3.6
        var s = MeterState()
        repeat(180) { s = FareEngine.apply(s, MeterInterval(1000, v, v, true), berlin) }
        val expectedDistance = BigDecimal(v * 60).multiply(eur("2.80")).divide(BigDecimal(1000), Money.MC)
        val expectedTime = eur("1.30") // 120 s * 39 €/h
        assertEquals(0, Money.cents(s.distanceCharge).compareTo(Money.cents(expectedDistance)))
        assertEquals(0, Money.cents(s.timeCharge).compareTo(expectedTime))
        assertEquals(120_000L, s.chargedWaitingMs)
        // Strecke zählt trotzdem für die Staffelposition
        assertEquals(v * 180, s.odometerM, 1e-6)
        // Beides gleichzeitig wäre teurer – darf nicht passieren:
        val both = BigDecimal(v * 180).multiply(eur("2.80")).divide(BigDecimal(1000), Money.MC).add(expectedTime)
        assertTrue(s.variableCharge < both)
    }

    @Test fun karenzSetztNachZuegigerFahrtZurueck() {
        var s = stand(berlin, 50, drive(berlin, 500))
        s = drive(berlin, 60, s) // ~4,3 s zügig > 3 s Hysterese -> Halt beendet
        s = stand(berlin, 50, s)
        assertEquals(0L, s.chargedWaitingMs)
    }

    @Test fun kurzesAnrollenBeendetHaltNicht() {
        var s = stand(berlin, 50, drive(berlin, 500))
        s = FareEngine.apply(s, MeterInterval(2_000, 30.0, 15.0, true), berlin) // 2 s zügig < 3 s
        s = stand(berlin, 20, s)
        assertEquals(10_000L, s.chargedWaitingMs) // 50 + 20 = 70 s Halt -> 10 s berechnet
    }

    @Test fun messlueckeErzeugtKeineWartezeit() {
        var s = stand(berlin, 30, drive(berlin, 500))
        s = FareEngine.apply(s, MeterInterval(120_000, 0.0, null, measured = false), berlin)
        s = stand(berlin, 40, s)
        assertEquals(0L, s.chargedWaitingMs)
        assertEquals(120_000L, s.gapMs)
        assertEquals(30_000L + 120_000 + 40_000 + drive(berlin, 500).runningMs, s.runningMs)
    }

    @Test fun zeitSchaltstufen() {
        // Nach der Karenz kostet jede angefangene 18,46 s weitere 0,20 € (Tarif 4).
        val base = stand(berlin, 60, MeterState())
        assertEquals(eur("4.30"), fare(berlin, base))
        assertEquals(eur("4.30"), fare(berlin, stand(berlin, 18, base)))  // 0,195 € -> erste Einheit (im Grundpreis)
        assertEquals(eur("4.50"), fare(berlin, stand(berlin, 19, base)))  // 0,2058 € -> zweite Einheit
        assertEquals(eur("4.50"), fare(berlin, stand(berlin, 36, base)))
        assertEquals(eur("4.70"), fare(berlin, stand(berlin, 37, base)))
    }
}
