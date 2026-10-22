package de.fa.taximeter.domain

import de.fa.taximeter.domain.gps.GapReason
import de.fa.taximeter.domain.gps.Geo
import de.fa.taximeter.domain.gps.GpsFilterConfig
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.persist.Codecs
import de.fa.taximeter.domain.tariff.TariffCatalog
import de.fa.taximeter.domain.trip.MeterMachine
import de.fa.taximeter.domain.trip.MeterMachineState
import de.fa.taximeter.domain.trip.Phase
import de.fa.taximeter.domain.trip.SurchargeDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MeterMachineTest {

    private val cfg = GpsFilterConfig()
    private var idCounter = 0
    private val newId = { "trip-${++idCounter}" }

    private fun fix(eastM: Double, tMs: Long, speed: Double? = 13.9, acc: Double = 5.0): GpsFix {
        val (la, lo) = Geo.offset(52.52, 13.405, 0.0, eastM)
        return GpsFix(la, lo, acc, tMs, 1_700_000_000_000 + tMs, speed)
    }

    private fun eur(s: String) = BigDecimal(s)

    /** Startet eine Fahrt bei t=0 und fährt [meters] mit 13,9 m/s (50 km/h). Liefert Zustand und Zeit. */
    private fun startAndDrive(meters: Int, start: MeterMachineState = MeterMachine.initial(TariffCatalog.BERLIN)): Pair<MeterMachineState, Long> {
        var s = MeterMachine.pressBesetzt(start, fix(0.0, 0), 0, 0, cfg, false, newId).state
        val steps = meters / 13.9
        var t = 0L
        var pos = 0.0
        for (i in 1..steps.toInt()) {
            t = i * 1000L
            pos = i * 13.9
            s = MeterMachine.onFix(s, fix(pos, t), t, cfg).state
        }
        return s to t
    }

    @Test fun besetztStartetGenauEineFahrtMitEinemGrundpreis() {
        val s0 = MeterMachine.initial(TariffCatalog.BERLIN)
        val r1 = MeterMachine.pressBesetzt(s0, fix(0.0, 0), 0, 0, cfg, false, newId)
        assertEquals(Phase.BESETZT, r1.state.phase)
        assertEquals(eur("4.30"), MeterMachine.liveFare(r1.state))
        val r2 = MeterMachine.pressBesetzt(r1.state, fix(0.0, 500), 500, 500, cfg, false, newId)
        val r3 = MeterMachine.pressBesetzt(r2.state, fix(0.0, 900), 900, 900, cfg, false, newId)
        assertFalse(r2.changed)
        assertFalse(r3.changed)
        assertEquals(r1.state.run!!.tripId, r3.state.run!!.tripId)
        assertEquals(eur("4.30"), MeterMachine.liveFare(r3.state))
        assertEquals(1, idCounter)
    }

    @Test fun keinStartOhneBrauchbarenFix() {
        val s0 = MeterMachine.initial(TariffCatalog.BERLIN)
        assertEquals(Phase.FREI, MeterMachine.pressBesetzt(s0, null, 0, 0, cfg, false, newId).state.phase)
        assertEquals(Phase.FREI, MeterMachine.pressBesetzt(s0, fix(0.0, 0, acc = 60.0), 0, 0, cfg, false, newId).state.phase)
    }

    @Test fun tarifwechselWaehrendFahrtGesperrt() {
        val (s, _) = startAndDrive(500)
        val r = MeterMachine.selectTariff(s, TariffCatalog.BER)
        assertFalse(r.changed)
        assertEquals(4, r.state.run!!.profile.displayNumber)
        assertEquals("Tarifwechsel während der Fahrt gesperrt", r.message)
        val free = MeterMachine.initial(TariffCatalog.BERLIN)
        assertEquals(3, MeterMachine.selectTariff(free, TariffCatalog.BER).state.selectedTariff.displayNumber)
    }

    @Test fun stoppFriertEinUndFortsetzenBerechnetStoppStreckeNicht() {
        val (s1, t1) = startAndDrive(1_000)
        val stopped = MeterMachine.pressStopp(s1, t1, t1, cfg).state
        assertEquals(Phase.STOPP, stopped.phase)
        val fareAtStop = MeterMachine.liveFare(stopped)
        val kmAtStop = stopped.run!!.meter.odometerM

        // Während STOPP fährt das Fahrzeug 2 km weiter – Messungen werden ignoriert.
        var s = stopped
        for (i in 1..150) s = MeterMachine.onFix(s, fix(1_000 + i * 13.9, t1 + i * 1000L), t1 + i * 1000L, cfg).state
        assertEquals(fareAtStop, MeterMachine.liveFare(s))
        assertEquals(kmAtStop, s.run!!.meter.odometerM, 0.0)

        // Fortsetzen an neuer Position: kein Nachberechnen der Lücke.
        val tResume = t1 + 151_000L
        val posResume = 1_000 + 151 * 13.9
        s = MeterMachine.pressBesetzt(s, fix(posResume, tResume), tResume, tResume, cfg, false, newId).state
        assertEquals(Phase.BESETZT, s.phase)
        for (i in 1..36) s = MeterMachine.onFix(s, fix(posResume + i * 13.9, tResume + i * 1000L), tResume + i * 1000L, cfg).state
        assertEquals(kmAtStop + 36 * 13.9, s.run!!.meter.odometerM, 6.0)
        assertEquals(1, idCounter)
    }

    @Test fun kasseNurNachStoppUndGesamtbetragGenauEinmal() {
        val ber = TariffCatalog.SURCHARGE_BER
        val s0 = MeterMachine.toggleSurcharge(MeterMachine.initial(TariffCatalog.BER), ber).state
        val (s1, t1) = startAndDrive(10_000, s0)
        assertTrue(s1.pendingSurcharges.items.isEmpty())
        assertTrue(s1.run!!.surcharges.contains(ber.id))

        // KASSE während BESETZT ist nicht möglich
        assertEquals(Phase.BESETZT, MeterMachine.pressKasse(s1, t1).state.phase)

        val stopped = MeterMachine.pressStopp(s1, t1, t1, cfg).state
        val kasse = MeterMachine.pressKasse(stopped, t1 + 1).state
        assertEquals(Phase.KASSE, kasse.phase)
        val rec = kasse.completed!!
        assertEquals(eur("1.50"), rec.surchargeTotal)
        assertEquals(rec.fare.add(rec.surchargeTotal), rec.total)
        assertEquals(listOf(ber), rec.surcharges)
        // Fahrpreis enthält keine Zuschläge
        assertEquals(MeterMachine.liveFare(stopped), rec.fare)
        // erneutes KASSE ändert nichts
        val again = MeterMachine.pressKasse(kasse, t1 + 2)
        assertFalse(again.changed)
        assertEquals(rec, again.state.completed)
    }

    @Test fun zuschlaegeEinzelnUndOhneDoppelbuchung() {
        var s = MeterMachine.initial(TariffCatalog.BERLIN)
        val g = TariffCatalog.SURCHARGE_GROSSRAUM
        val b = TariffCatalog.SURCHARGE_BER
        s = MeterMachine.toggleSurcharge(s, g).state
        s = MeterMachine.toggleSurcharge(s, b).state
        assertEquals(eur("7.50"), s.activeSurcharges.total)
        assertEquals(eur("7.50"), s.pendingSurcharges.add(g).total) // add() bucht nicht doppelt
        s = MeterMachine.toggleSurcharge(s, b).state // abwählen
        assertEquals(eur("6.00"), s.activeSurcharges.total)
        val custom = SurchargeDef("custom-1", "Sonderfahrt", eur("2.00"), "manuell", "-", custom = true)
        s = MeterMachine.toggleSurcharge(s, custom).state
        assertEquals("Sonderfahrt (benutzerdefiniert)", s.activeSurcharges.items.last().displayLabel)
        assertEquals(eur("8.00"), s.activeSurcharges.total)
    }

    @Test fun zuschlagWaehrendFahrtAendertFahrpreisNicht() {
        val (s1, t1) = startAndDrive(2_000)
        val before = MeterMachine.liveFare(s1)
        val s2 = MeterMachine.toggleSurcharge(s1, TariffCatalog.SURCHARGE_GROSSRAUM).state
        assertEquals(before, MeterMachine.liveFare(s2))
        assertEquals(Phase.BESETZT, s2.phase)
        // Messung läuft weiter
        val pos = (t1 / 1000) * 13.9
        val s3 = MeterMachine.onFix(s2, fix(pos + 13.9, t1 + 1_000), t1 + 1_000, cfg).state
        assertTrue(s3.run!!.meter.runningMs > s2.run!!.meter.runningMs)
    }

    @Test fun neueFahrtSetztZurueckHistorieBleibt() {
        val (s1, t1) = startAndDrive(1_000, MeterMachine.toggleSurcharge(MeterMachine.initial(TariffCatalog.BERLIN), TariffCatalog.SURCHARGE_GROSSRAUM).state)
        val kasse = MeterMachine.pressKasse(MeterMachine.pressStopp(s1, t1, t1, cfg).state, t1).state
        val rec = kasse.completed!!
        // Kein automatisches Zurücksetzen: andere Befehle ändern KASSE nicht
        assertFalse(MeterMachine.pressStopp(kasse, t1 + 5, t1 + 5, cfg).changed)
        assertFalse(MeterMachine.pressBesetzt(kasse, fix(0.0, t1 + 5), t1 + 5, t1 + 5, cfg, false, newId).changed)
        val free = MeterMachine.newTrip(kasse).state
        assertEquals(Phase.FREI, free.phase)
        assertNull(free.run)
        assertNull(free.completed)
        assertTrue(free.activeSurcharges.items.isEmpty())
        assertEquals(BigDecimal.ZERO.setScale(2), MeterMachine.liveFare(free))
        assertEquals(4, free.selectedTariff.displayNumber)
        assertNotNull(rec) // Datensatz existiert unabhängig (Historie speichert ihn)
    }

    // ---------------------------------------------------------------- Speicherung / Wiederherstellung

    @Test fun zustandUeberlebtSerialisierung_zBBildschirmdrehungOderProzessende() {
        val (s1, _) = startAndDrive(3_500, MeterMachine.toggleSurcharge(MeterMachine.initial(TariffCatalog.BER), TariffCatalog.SURCHARGE_BER).state)
        val json = Codecs.machineToJson(s1)
        val back = Codecs.machineFromJson(json)
        assertEquals(s1, back)
        assertEquals(MeterMachine.liveFare(s1), MeterMachine.liveFare(back))
    }

    @Test fun wiederherstellungNachProzessabbruch() {
        val (s1, _) = startAndDrive(2_000)
        val saved = s1.copy(savedWallMs = 1_000_000)
        val restored = MeterMachine.restoreAfterProcessDeath(Codecs.machineFromJson(Codecs.machineToJson(saved)), 1_000_000 + 300_000)
        assertEquals(Phase.STOPP, restored.phase)
        val run = restored.run!!
        assertTrue(run.restoredAfterProcessDeath)
        val gap = run.gaps.last()
        assertEquals(GapReason.PROCESS_RESTART, gap.reason)
        assertEquals(300_000L, gap.durationMs)
        // Keine erfundene Strecke, keine Wartezeit, kein höherer Preis
        assertEquals(s1.run!!.meter.odometerM, run.meter.odometerM, 0.0)
        assertEquals(s1.run!!.meter.chargedWaitingMs, run.meter.chargedWaitingMs)
        assertEquals(MeterMachine.liveFare(s1), MeterMachine.liveFare(restored))
        // Danach regulär abschließbar; die Lücke ist im Datensatz gekennzeichnet
        val rec = MeterMachine.pressKasse(restored, 2_000_000).state.completed!!
        assertTrue(rec.hasGaps)
        assertTrue(rec.restoredAfterProcessDeath)
    }

    @Test fun historieRoundTrip() {
        val (s1, t1) = startAndDrive(1_500)
        val rec = MeterMachine.pressKasse(MeterMachine.pressStopp(s1, t1, t1, cfg).state, t1).state.completed!!
        val list = Codecs.historyFromJson(Codecs.historyToJson(listOf(rec, rec.copy(id = "x"))))
        assertEquals(2, list.size)
        assertEquals(rec, list[0])
    }

    @Test fun gespeicherteFahrtBehaeltTarifversion() {
        val (s1, t1) = startAndDrive(1_000)
        val custom = TariffCatalog.customCopy(TariffCatalog.BERLIN, eur("9.99"), TariffCatalog.BERLIN.bands, eur("50.00"), "01.01.2027")
        // Tarifauswahl ändert sich erst für neue Fahrten
        val s2 = MeterMachine.selectTariff(s1, custom).state
        assertEquals(TariffCatalog.BERLIN, s2.run!!.profile)
        val rec = MeterMachine.pressKasse(MeterMachine.pressStopp(s2, t1, t1, cfg).state, t1).state.completed!!
        assertEquals(TariffCatalog.BERLIN.version, rec.profile.version)
        assertTrue(custom.isCustom)
        assertFalse(TariffCatalog.BERLIN.isCustom)
        assertEquals(eur("4.30"), TariffCatalog.BERLIN.baseFare) // Original unverändert
    }
}
