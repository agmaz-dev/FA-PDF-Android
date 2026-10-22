package de.fa.taximeter.domain.trip

import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.gps.DistanceTracker
import de.fa.taximeter.domain.gps.GapReason
import de.fa.taximeter.domain.gps.GapRecord
import de.fa.taximeter.domain.gps.GpsFilterConfig
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.gps.TrackerState
import de.fa.taximeter.domain.tariff.FareEngine
import de.fa.taximeter.domain.tariff.MeterInterval
import de.fa.taximeter.domain.tariff.MeterState
import de.fa.taximeter.domain.tariff.TariffProfile
import java.math.BigDecimal

enum class Phase { FREI, BESETZT, STOPP, KASSE }

/** Laufende Fahrt. Enthält eine vollständige Kopie des Tarifprofils (Tarifversion bleibt fest). */
data class TripRun(
    val tripId: String,
    val profile: TariffProfile,
    val startWallMs: Long,
    val simulation: Boolean,
    val meter: MeterState,
    val tracker: TrackerState,
    val surcharges: SurchargeSelection,
    val gaps: List<GapRecord>,
    val restoredAfterProcessDeath: Boolean = false,
    /** Höchster bisher angezeigter Fahrpreis – die Anzeige läuft nie rückwärts (Tunnel-Schätzung). */
    val fareFloor: BigDecimal = BigDecimal.ZERO,
    /** Nach dem ersten STOPP zeigt die große Anzeige die Summe inkl. Zuschlägen (KASSE). */
    val showTotal: Boolean = false,
)

/** Abgeschlossene Fahrt (KASSE). Fahrpreis, Zuschläge und Gesamtbetrag stammen aus diesem einen Datensatz. */
data class TripRecord(
    val id: String,
    val profile: TariffProfile,
    val startWallMs: Long,
    val endWallMs: Long,
    val durationMs: Long,
    val distanceM: Double,
    val waitingMs: Long,
    val gapMs: Long,
    val fare: BigDecimal,
    val surcharges: List<SurchargeDef>,
    val surchargeTotal: BigDecimal,
    val total: BigDecimal,
    val gaps: List<GapRecord>,
    val simulation: Boolean,
    val restoredAfterProcessDeath: Boolean,
) {
    val hasGaps: Boolean get() = gaps.isNotEmpty()
}

data class MeterMachineState(
    val phase: Phase = Phase.FREI,
    val selectedTariff: TariffProfile,
    /** In FREI für die nächste Fahrt vorgewählte Zuschläge. */
    val pendingSurcharges: SurchargeSelection = SurchargeSelection(),
    val run: TripRun? = null,
    val completed: TripRecord? = null,
    /** Zeitpunkt (Uhrzeit) der letzten Sicherung – für die Wiederherstellung. */
    val savedWallMs: Long = 0,
) {
    /** Zuschläge, die aktuell angezeigt werden. */
    val activeSurcharges: SurchargeSelection
        get() = when (phase) {
            Phase.FREI -> pendingSurcharges
            Phase.BESETZT, Phase.STOPP -> run?.surcharges ?: SurchargeSelection()
            Phase.KASSE -> SurchargeSelection(completed?.surcharges ?: emptyList())
        }
}

data class CommandResult(val state: MeterMachineState, val message: String? = null, val changed: Boolean = true)

/**
 * Zustandsautomat FREI → BESETZT ⇄ STOPP → KASSE → (Neue Fahrt) → FREI.
 * Alle Funktionen sind rein (ohne Seiteneffekte) und damit testbar.
 */
object MeterMachine {

    fun initial(tariff: TariffProfile) = MeterMachineState(selectedTariff = tariff)

    fun selectTariff(s: MeterMachineState, profile: TariffProfile): CommandResult =
        if (s.phase != Phase.FREI) CommandResult(s, "Tarifwechsel während der Fahrt gesperrt", changed = false)
        else CommandResult(s.copy(selectedTariff = profile))

    fun toggleSurcharge(s: MeterMachineState, def: SurchargeDef): CommandResult = when (s.phase) {
        Phase.FREI -> CommandResult(s.copy(pendingSurcharges = s.pendingSurcharges.toggle(def)))
        Phase.BESETZT, Phase.STOPP -> {
            val run = s.run!!
            CommandResult(s.copy(run = run.copy(surcharges = run.surcharges.toggle(def))))
        }
        Phase.KASSE -> CommandResult(s, "Fahrt ist abgeschlossen – Zuschläge nicht mehr änderbar", changed = false)
    }

    /** BESETZT: startet in FREI genau eine Fahrt, setzt in STOPP dieselbe Fahrt fort, sonst ohne Wirkung. */
    fun pressBesetzt(
        s: MeterMachineState,
        fix: GpsFix?,
        nowMs: Long,
        nowWallMs: Long,
        cfg: GpsFilterConfig,
        simulation: Boolean,
        newId: () -> String,
    ): CommandResult = when (s.phase) {
        Phase.BESETZT -> CommandResult(s, null, changed = false)
        Phase.KASSE -> CommandResult(s, "Erst „Neue Fahrt“ wählen", changed = false)
        Phase.FREI -> {
            if (!DistanceTracker.isUsableForStart(fix, nowMs, cfg)) {
                CommandResult(s, "Kein brauchbarer GPS-Empfang – Fahrt kann noch nicht starten", changed = false)
            } else {
                val run = TripRun(
                    tripId = newId(),
                    profile = s.selectedTariff,
                    startWallMs = nowWallMs,
                    simulation = simulation,
                    meter = MeterState(),
                    tracker = DistanceTracker.seed(fix!!, nowMs),
                    surcharges = s.pendingSurcharges,
                    gaps = emptyList(),
                )
                CommandResult(s.copy(phase = Phase.BESETZT, run = run, pendingSurcharges = SurchargeSelection()))
            }
        }
        Phase.STOPP -> {
            if (!DistanceTracker.isUsableForStart(fix, nowMs, cfg)) {
                CommandResult(s, "Kein brauchbarer GPS-Empfang – Fortsetzen nicht möglich", changed = false)
            } else {
                val run = s.run!!
                // Neu verankern: Die während STOPP zurückgelegte Strecke wird nicht nachberechnet.
                CommandResult(
                    s.copy(phase = Phase.BESETZT, run = run.copy(tracker = DistanceTracker.seed(fix!!, nowMs)))
                )
            }
        }
    }

    fun pressStopp(s: MeterMachineState, nowMs: Long, nowWallMs: Long, cfg: GpsFilterConfig): CommandResult {
        if (s.phase != Phase.BESETZT) return CommandResult(s, null, changed = false)
        val run = withFloor(s.run!!, nowMs).copy(showTotal = true)
        val r = DistanceTracker.closeAt(run.tracker, nowMs, nowWallMs, cfg)
        val meter = r.interval?.let { FareEngine.apply(run.meter, it, run.profile) } ?: run.meter
        val gaps = if (r.gap != null) run.gaps + r.gap else run.gaps
        return CommandResult(
            s.copy(phase = Phase.STOPP, run = run.copy(meter = meter, tracker = r.state, gaps = gaps))
        )
    }

    fun pressKasse(s: MeterMachineState, nowWallMs: Long): CommandResult {
        if (s.phase != Phase.STOPP) {
            return CommandResult(s, if (s.phase == Phase.BESETZT) "Erst STOPP drücken" else null, changed = false)
        }
        val run = s.run!!
        val fare = maxOf(run.fareFloor, FareEngine.displayedFare(run.profile, run.meter)).setScale(2)
        val surchargeTotal = run.surcharges.total
        val record = TripRecord(
            id = run.tripId,
            profile = run.profile,
            startWallMs = run.startWallMs,
            endWallMs = nowWallMs,
            durationMs = run.meter.runningMs,
            distanceM = run.meter.odometerM,
            waitingMs = run.meter.chargedWaitingMs,
            gapMs = run.meter.gapMs,
            fare = fare,
            surcharges = run.surcharges.items,
            surchargeTotal = surchargeTotal,
            total = Money.cents(fare.add(surchargeTotal)),
            gaps = run.gaps,
            simulation = run.simulation,
            restoredAfterProcessDeath = run.restoredAfterProcessDeath,
        )
        return CommandResult(s.copy(phase = Phase.KASSE, run = null, completed = record))
    }

    /** Neue Fahrt: nur aus KASSE, setzt alles für die nächste Fahrt zurück. Der Datensatz bleibt in der Historie. */
    fun newTrip(s: MeterMachineState): CommandResult =
        if (s.phase != Phase.KASSE) CommandResult(s, null, changed = false)
        else CommandResult(
            MeterMachineState(phase = Phase.FREI, selectedTariff = s.selectedTariff, savedWallMs = s.savedWallMs)
        )

    /** Verarbeitet eine Standortmessung (nur während BESETZT). */
    fun onFix(s: MeterMachineState, fix: GpsFix, nowMs: Long, cfg: GpsFilterConfig): CommandResult {
        if (s.phase != Phase.BESETZT) return CommandResult(s, null, changed = false)
        val run = withFloor(s.run!!, nowMs)
        val r = DistanceTracker.onFix(run.tracker, fix, nowMs, cfg)
        if (r.rejected != null) {
            return CommandResult(s.copy(run = run.copy(tracker = r.state)), r.rejected.label, changed = r.state != run.tracker || run != s.run)
        }
        val meter = r.interval?.let { FareEngine.apply(run.meter, it, run.profile) } ?: run.meter
        val gaps = if (r.gap != null) run.gaps + r.gap else run.gaps
        return CommandResult(s.copy(run = run.copy(meter = meter, tracker = r.state, gaps = gaps)))
    }

    /**
     * Nach einem Prozessabbruch: Eine Fahrt, die BESETZT war, wird im Zustand STOPP angeboten.
     * Die nicht gemessene Zeit wird als Messlücke ohne Strecke und ohne Wartezeit dokumentiert.
     */
    fun restoreAfterProcessDeath(s: MeterMachineState, nowWallMs: Long): MeterMachineState {
        if (s.phase != Phase.BESETZT) return s
        val run = s.run ?: return s.copy(phase = Phase.FREI)
        val lostMs = (nowWallMs - s.savedWallMs).coerceAtLeast(0)
        val gap = GapRecord(
            startWallMs = s.savedWallMs,
            durationMs = lostMs,
            bridgedM = 0.0,
            straightLineM = null,
            reason = GapReason.PROCESS_RESTART,
        )
        val meter = FareEngine.apply(run.meter, MeterInterval(lostMs, 0.0, null, measured = false), run.profile)
        return s.copy(
            phase = Phase.STOPP,
            run = run.copy(meter = meter, gaps = run.gaps + gap, restoredAfterProcessDeath = true),
        )
    }

    /** Laufzeit inkl. der seit der letzten Messung verstrichenen Zeit (nur Anzeige). */
    fun liveDurationMs(s: MeterMachineState, nowMs: Long): Long {
        val run = s.run ?: return s.completed?.durationMs ?: 0
        val open = if (s.phase == Phase.BESETZT) (nowMs - run.tracker.cursorMs).coerceAtLeast(0) else 0
        return run.meter.runningMs + open
    }

    fun liveFare(s: MeterMachineState): BigDecimal = when (s.phase) {
        Phase.FREI -> BigDecimal.ZERO.setScale(2)
        Phase.BESETZT, Phase.STOPP -> maxOf(s.run!!.fareFloor, FareEngine.displayedFare(s.run.profile, s.run.meter)).setScale(2)
        Phase.KASSE -> s.completed!!.fare
    }

    /** Fahrpreis inkl. laufender Tunnel-/GPS-Lücken-Schätzung zum Zeitpunkt [nowMs]. */
    fun liveFare(s: MeterMachineState, nowMs: Long): BigDecimal {
        if (s.phase != Phase.BESETZT) return liveFare(s)
        val run = s.run!!
        return maxOf(run.fareFloor, estimatedFare(run, nowMs)).setScale(2)
    }

    /**
     * Betrag der großen Anzeige: in STOPP die Summe inkl. Zuschlägen (KASSE), sonst nur der Fahrpreis.
     * Bei BESETZT wandert der Zuschlag wieder nach oben in die ZUSCHLAG-Anzeige.
     */
    fun displayAmount(s: MeterMachineState, nowMs: Long): BigDecimal {
        val fare = liveFare(s, nowMs)
        val run = s.run ?: return fare
        return if (showsTotal(s)) Money.cents(fare.add(run.surcharges.total)) else fare
    }

    fun showsTotal(s: MeterMachineState): Boolean = s.phase == Phase.STOPP && s.run != null

    /** Läuft gerade eine Schätzung, weil kein GPS empfangen wird (z. B. Tunnel)? */
    fun isEstimating(s: MeterMachineState, nowMs: Long): Boolean =
        s.phase == Phase.BESETZT && s.run != null && estimatedGapM(s.run, nowMs) > 0.0

    /** Speichert den aktuell angezeigten Preis als Untergrenze (regelmäßig aufrufen). */
    fun tick(s: MeterMachineState, nowMs: Long): CommandResult {
        if (s.phase != Phase.BESETZT) return CommandResult(s, null, changed = false)
        val run = s.run!!
        val updated = withFloor(run, nowMs)
        return if (updated == run) CommandResult(s, null, changed = false) else CommandResult(s.copy(run = updated))
    }

    // ---------------------------------------------------------------- Zuschlag in 0,50-€-Schritten

    const val SURCHARGE_ID = "zuschlag"
    val SURCHARGE_STEP: BigDecimal = BigDecimal("0.50")
    val SURCHARGE_MAX: BigDecimal = BigDecimal("99.50")

    private fun stepDef(amount: BigDecimal) = SurchargeDef(
        id = SURCHARGE_ID,
        label = "ZUSCHLAG",
        amount = amount,
        condition = "Am Gerät in 0,50-€-Schritten eingegeben",
        legalRef = "manuell",
        custom = false,
    )

    private fun withSelection(s: MeterMachineState, f: (SurchargeSelection) -> SurchargeSelection): CommandResult =
        when (s.phase) {
            Phase.FREI -> CommandResult(s.copy(pendingSurcharges = f(s.pendingSurcharges)))
            Phase.BESETZT, Phase.STOPP -> {
                val run = s.run!!
                CommandResult(s.copy(run = run.copy(surcharges = f(run.surcharges))))
            }
            Phase.KASSE -> CommandResult(s, "Fahrt ist abgeschlossen", changed = false)
        }

    /** Ein Tippen auf ZUSCHLAG: +0,50 €. */
    fun addSurchargeStep(s: MeterMachineState): CommandResult = withSelection(s) { sel ->
        val current = sel.items.firstOrNull { it.id == SURCHARGE_ID }?.amount ?: BigDecimal.ZERO
        val next = current.add(SURCHARGE_STEP).min(SURCHARGE_MAX).setScale(2)
        SurchargeSelection(sel.items.filterNot { it.id == SURCHARGE_ID } + stepDef(next))
    }

    /** Langes Drücken auf ZUSCHLAG: alle Zuschläge auf 0. */
    fun resetSurcharges(s: MeterMachineState): CommandResult = withSelection(s) { SurchargeSelection() }

    /** FREI: aus STOPP wird die Fahrt abgeschlossen (Datensatz in [CommandResult.state].completed) und geleert. */
    fun pressFrei(s: MeterMachineState, nowWallMs: Long): Pair<CommandResult, TripRecord?> = when (s.phase) {
        Phase.STOPP -> {
            val rec = pressKasse(s, nowWallMs).state.completed!!
            CommandResult(MeterMachineState(phase = Phase.FREI, selectedTariff = s.selectedTariff, savedWallMs = s.savedWallMs)) to rec
        }
        Phase.KASSE -> newTrip(s) to null
        Phase.BESETZT -> CommandResult(s, "Fahrt läuft – erst STOPP drücken.", changed = false) to null
        Phase.FREI -> CommandResult(s, null, changed = false) to null
    }

    // ---------------------------------------------------------------- Tunnel-/GPS-Lücken-Schätzung

    /** Ab so langer Zeit ohne akzeptierte Messung wird geschätzt. */
    const val ESTIMATE_AFTER_MS = 3_000L
    /** Höchstdauer der Schätzung; danach bleibt der Zähler stehen, bis GPS wieder da ist. */
    const val ESTIMATE_MAX_MS = 180_000L
    /** Unterhalb dieser letzten Geschwindigkeit (7,2 km/h) wird nicht geschätzt (Stau/Stand). */
    const val ESTIMATE_MIN_SPEED_MPS = 2.0
    /** Obergrenze der Schätzgeschwindigkeit (100 km/h). */
    const val ESTIMATE_MAX_SPEED_MPS = 100.0 / 3.6

    fun estimatedGapM(run: TripRun, nowMs: Long): Double {
        val open = nowMs - run.tracker.cursorMs
        if (open <= ESTIMATE_AFTER_MS) return 0.0
        val v = run.tracker.lastSpeedMps ?: return 0.0
        if (v < ESTIMATE_MIN_SPEED_MPS) return 0.0
        return minOf(v, ESTIMATE_MAX_SPEED_MPS) * (minOf(open, ESTIMATE_MAX_MS) / 1000.0)
    }

    private fun estimatedFare(run: TripRun, nowMs: Long): BigDecimal {
        val est = estimatedGapM(run, nowMs)
        if (est <= 0.0) return FareEngine.displayedFare(run.profile, run.meter)
        val open = nowMs - run.tracker.cursorMs
        val m = FareEngine.apply(run.meter, MeterInterval(open, est, null, measured = false), run.profile)
        return FareEngine.displayedFare(run.profile, m)
    }

    private fun withFloor(run: TripRun, nowMs: Long): TripRun {
        val f = maxOf(run.fareFloor, estimatedFare(run, nowMs))
        return if (f.compareTo(run.fareFloor) == 0) run else run.copy(fareFloor = f)
    }
}
