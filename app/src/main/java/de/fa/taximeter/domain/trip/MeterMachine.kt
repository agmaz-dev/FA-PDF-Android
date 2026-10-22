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
        val run = s.run!!
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
        val fare = FareEngine.displayedFare(run.profile, run.meter)
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
        val run = s.run!!
        val r = DistanceTracker.onFix(run.tracker, fix, nowMs, cfg)
        if (r.rejected != null) {
            return CommandResult(s.copy(run = run.copy(tracker = r.state)), r.rejected.label, changed = r.state != run.tracker)
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
        Phase.BESETZT, Phase.STOPP -> FareEngine.displayedFare(s.run!!.profile, s.run.meter)
        Phase.KASSE -> s.completed!!.fare
    }
}
