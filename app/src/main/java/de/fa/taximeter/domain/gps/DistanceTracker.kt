package de.fa.taximeter.domain.gps

import de.fa.taximeter.domain.tariff.MeterInterval

/**
 * Filtergrenzen der GPS-Streckenmessung. Alle Werte sind im README begründet.
 */
data class GpsFilterConfig(
    /** Messungen mit schlechterer Genauigkeit werden verworfen. */
    val maxAccuracyM: Double = 30.0,
    /** Bis zu dieser Genauigkeit gilt der Empfang als "gut". */
    val goodAccuracyM: Double = 10.0,
    /** Messungen, die älter sind, werden als veraltet verworfen. */
    val maxFixAgeMs: Long = 5_000,
    /** Plausibilitätsgrenze (252 km/h) nach Abzug der Messungenauigkeit. */
    val maxSpeedMps: Double = 70.0,
    /** Größerer Abstand zwischen akzeptierten Messungen = Messlücke. */
    val gapMs: Long = 10_000,
    /** Ohne akzeptierte Messung seit dieser Zeit: "GPS unzuverlässig". */
    val unreliableAfterMs: Long = 3_000,
    /** Kurze Lücken bis zu dieser Dauer dürfen geprüft per Luftlinie (Untergrenze) überbrückt werden. */
    val bridgeMaxGapMs: Long = 30_000,
    /** Maximal plausible Durchschnittsgeschwindigkeit für eine Überbrückung (180 km/h). */
    val bridgeMaxSpeedMps: Double = 50.0,
    /** Mindest-/Höchstschwelle für das Fortschreiben des Ankerpunkts. */
    val minCommitM: Double = 5.0,
    val maxCommitM: Double = 25.0,
    /** Ab dieser Doppler-Geschwindigkeit gilt das Fahrzeug als in Bewegung. */
    val movingSpeedMps: Double = 1.0,
    /** Nach so vielen aufeinanderfolgenden "Sprüngen" wird neu verankert (ohne Strecke). */
    val jumpResetCount: Int = 5,
    /** Simulierte Standorte zulassen (nur Testmodus). */
    val allowMock: Boolean = false,
)

enum class RejectReason(val label: String) {
    MOCK("Simulierter Standort"),
    INACCURATE("Ungenau"),
    STALE("Veraltet"),
    DUPLICATE("Doppelt/zeitlich rückwärts"),
    JUMP("Unplausibler Sprung"),
}

enum class GapReason(val label: String) {
    GPS_LOST("GPS-Empfang verloren"),
    JUMP_RESET("Positionssprung – neu verankert"),
    PROCESS_RESTART("App-Prozess beendet – nicht gemessen"),
}

/** Dokumentierte Messlücke einer Fahrt. */
data class GapRecord(
    val startWallMs: Long,
    val durationMs: Long,
    /** Geprüft überbrückte Strecke (Luftlinie als Untergrenze), sonst 0. */
    val bridgedM: Double,
    /** Luftlinie über die Lücke (nur Information, nicht berechnet wenn bridgedM == 0). */
    val straightLineM: Double?,
    val reason: GapReason,
)

/** Zustand des Filters (unveränderlich, damit er gesichert und getestet werden kann). */
data class TrackerState(
    val anchor: GpsFix? = null,
    val lastAccepted: GpsFix? = null,
    /** Zeitcursor (monoton) bis zu dem Intervalle bereits ausgegeben wurden. */
    val cursorMs: Long = 0,
    val lastSpeedMps: Double? = null,
    val consecutiveJumps: Int = 0,
)

data class TrackResult(
    val state: TrackerState,
    val interval: MeterInterval? = null,
    val gap: GapRecord? = null,
    val rejected: RejectReason? = null,
)

enum class GpsQuality(val label: String) {
    GOOD("GPS gut"),
    FAIR("GPS mäßig"),
    UNRELIABLE("GPS unzuverlässig"),
    LOST("GPS verloren"),
}

/**
 * GPS-Streckenmessung mit Rausch- und Sprungfilter.
 *
 * Kernidee gegen Stillstandsrauschen: Strecke wird nur vom letzten *Ankerpunkt* aus gemessen.
 * Der Anker wird erst weitergesetzt, wenn die Verschiebung eine genauigkeitsabhängige Schwelle
 * überschreitet (bei Doppler-Bewegung 1×, sonst 2× Schwelle). Rauschen innerhalb des
 * Genauigkeitskreises erzeugt so keine Kilometer; langsame echte Fahrt wird nicht verschluckt,
 * weil die Verschiebung vom Anker aus wächst und dann vollständig gezählt wird.
 */
object DistanceTracker {

    fun isUsableForStart(fix: GpsFix?, nowMs: Long, cfg: GpsFilterConfig): Boolean =
        fix != null &&
            (cfg.allowMock || !fix.isMock) &&
            fix.accuracyM != null && fix.accuracyM <= cfg.maxAccuracyM &&
            nowMs - fix.elapsedMs in 0..cfg.maxFixAgeMs

    /** Startet bzw. setzt die Messung mit einem brauchbaren Fix fort (nach BESETZT/Fortsetzen). */
    fun seed(fix: GpsFix, nowMs: Long): TrackerState =
        TrackerState(anchor = fix, lastAccepted = fix, cursorMs = maxOf(nowMs, fix.elapsedMs), lastSpeedMps = fix.speedMps)

    fun onFix(state: TrackerState, fix: GpsFix, nowMs: Long, cfg: GpsFilterConfig): TrackResult {
        if (fix.isMock && !cfg.allowMock) return TrackResult(state, rejected = RejectReason.MOCK)
        val acc = fix.accuracyM
        if (acc == null || acc > cfg.maxAccuracyM) return TrackResult(state, rejected = RejectReason.INACCURATE)
        if (nowMs - fix.elapsedMs > cfg.maxFixAgeMs) return TrackResult(state, rejected = RejectReason.STALE)

        val last = state.lastAccepted
        val anchor = state.anchor
        if (last == null || anchor == null) {
            return TrackResult(seed(fix, fix.elapsedMs))
        }
        if (fix.elapsedMs <= last.elapsedMs || fix.elapsedMs <= state.cursorMs) {
            return TrackResult(state, rejected = RejectReason.DUPLICATE)
        }

        val dt = fix.elapsedMs - state.cursorMs
        val sinceLastS = (fix.elapsedMs - last.elapsedMs) / 1000.0
        val rawFromLast = Geo.distanceM(last, fix)
        val slack = (last.accuracyM ?: cfg.maxAccuracyM) + acc
        val impliedSpeed = (rawFromLast - slack).coerceAtLeast(0.0) / sinceLastS

        if (impliedSpeed > cfg.maxSpeedMps) {
            val jumps = state.consecutiveJumps + 1
            if (jumps < cfg.jumpResetCount) {
                return TrackResult(state.copy(consecutiveJumps = jumps), rejected = RejectReason.JUMP)
            }
            // Der bisherige Anker war offenbar falsch (oder echter Ortswechsel ohne Messung):
            // neu verankern, Zeit als Messlücke, keine Strecke.
            val gap = GapRecord(
                startWallMs = last.wallTimeMs,
                durationMs = dt,
                bridgedM = 0.0,
                straightLineM = Geo.distanceM(anchor, fix),
                reason = GapReason.JUMP_RESET,
            )
            return TrackResult(
                state = TrackerState(fix, fix, fix.elapsedMs, fix.speedMps, 0),
                interval = MeterInterval(dt, 0.0, null, measured = false),
                gap = gap,
            )
        }

        if (dt > cfg.gapMs) {
            // Messlücke: niemals Wartezeit; Überbrückung nur kurz, plausibel und mit guter Genauigkeit.
            val straight = Geo.distanceM(anchor, fix)
            val avgSpeed = straight / (dt / 1000.0)
            val goodBoth = (anchor.accuracyM ?: Double.MAX_VALUE) <= cfg.goodAccuracyM * 2 &&
                acc <= cfg.goodAccuracyM * 2
            val bridge = dt <= cfg.bridgeMaxGapMs && avgSpeed <= cfg.bridgeMaxSpeedMps && goodBoth
            val bridged = if (bridge) straight else 0.0
            val gap = GapRecord(
                startWallMs = last.wallTimeMs,
                durationMs = dt,
                bridgedM = bridged,
                straightLineM = straight,
                reason = GapReason.GPS_LOST,
            )
            return TrackResult(
                state = TrackerState(fix, fix, fix.elapsedMs, fix.speedMps, 0),
                interval = MeterInterval(dt, bridged, null, measured = false),
                gap = gap,
            )
        }

        val disp = Geo.distanceM(anchor, fix)
        val threshold = maxOf(anchor.accuracyM ?: acc, acc).coerceIn(cfg.minCommitM, cfg.maxCommitM)
        val moving = fix.speedMps != null && fix.speedMps >= cfg.movingSpeedMps
        val commit = disp >= if (moving) threshold else 2 * threshold

        val distance = if (commit) disp else 0.0
        val speed: Double = fix.speedMps ?: if (commit) {
            val s = (fix.elapsedMs - anchor.elapsedMs) / 1000.0
            if (s > 0) disp / s else 0.0
        } else 0.0

        return TrackResult(
            state = TrackerState(
                anchor = if (commit) fix else anchor,
                lastAccepted = fix,
                cursorMs = fix.elapsedMs,
                lastSpeedMps = speed,
                consecutiveJumps = 0,
            ),
            interval = MeterInterval(dt, distance, speed, measured = true),
        )
    }

    /**
     * Schließt die Messung bis [nowMs] ab (z. B. bei STOPP). Die Zeit seit der letzten Messung
     * wird ohne Strecke verbucht; war sie länger als die Lückengrenze, gilt sie als Messlücke.
     */
    fun closeAt(state: TrackerState, nowMs: Long, nowWallMs: Long, cfg: GpsFilterConfig): TrackResult {
        val dt = nowMs - state.cursorMs
        if (state.lastAccepted == null || dt <= 0) return TrackResult(state)
        val measured = dt <= cfg.gapMs
        val gap = if (measured) null else GapRecord(
            startWallMs = nowWallMs - dt,
            durationMs = dt,
            bridgedM = 0.0,
            straightLineM = null,
            reason = GapReason.GPS_LOST,
        )
        return TrackResult(
            state = state.copy(cursorMs = nowMs),
            interval = MeterInterval(dt, 0.0, if (measured) state.lastSpeedMps else null, measured),
            gap = gap,
        )
    }

    fun quality(
        lastRaw: GpsFix?,
        lastAcceptedMs: Long?,
        nowMs: Long,
        cfg: GpsFilterConfig,
    ): GpsQuality {
        if (lastRaw == null || nowMs - lastRaw.elapsedMs > cfg.gapMs) return GpsQuality.LOST
        val acc = lastRaw.accuracyM ?: return GpsQuality.UNRELIABLE
        if (acc > cfg.maxAccuracyM) return GpsQuality.UNRELIABLE
        if (nowMs - lastRaw.elapsedMs > cfg.unreliableAfterMs) return GpsQuality.UNRELIABLE
        if (lastAcceptedMs != null && nowMs - lastAcceptedMs > cfg.gapMs) return GpsQuality.UNRELIABLE
        return if (acc <= cfg.goodAccuracyM) GpsQuality.GOOD else GpsQuality.FAIR
    }
}
