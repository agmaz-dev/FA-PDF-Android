package de.fa.taximeter.domain.tariff

import de.fa.taximeter.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Ein Messintervall zwischen zwei akzeptierten Zeitpunkten (monotone Zeitbasis).
 *
 * @param durationMs Dauer des Intervalls.
 * @param distanceM  in diesem Intervall gemessene (bzw. bei kurzer Lücke geprüft überbrückte) Strecke.
 * @param speedMps   Geschwindigkeitsschätzung für das Intervall; null = unbekannt.
 * @param measured   false = Messlücke (GPS verloren). Eine Lücke erzeugt niemals Wartezeit.
 */
data class MeterInterval(
    val durationMs: Long,
    val distanceM: Double,
    val speedMps: Double?,
    val measured: Boolean,
) {
    init {
        require(durationMs >= 0) { "durationMs < 0" }
        require(distanceM >= 0.0 && !distanceM.isNaN()) { "distanceM ungültig" }
    }
}

/** Unveränderlicher Zustand der Tarifberechnung einer Fahrt. */
data class MeterState(
    /** Gesamte gemessene Strecke (Position für die Kilometerstaffeln). */
    val odometerM: Double = 0.0,
    /** Strecke, die nach Kilometerpreis berechnet wurde. */
    val distanceChargedM: Double = 0.0,
    /** Exakter (ungerundeter) Streckenanteil in Euro. */
    val distanceCharge: BigDecimal = BigDecimal.ZERO,
    /** Exakter (ungerundeter) Zeitanteil in Euro. */
    val timeCharge: BigDecimal = BigDecimal.ZERO,
    /** Berechnete Wartezeit (Zeitmodus nach Karenz) in ms. */
    val chargedWaitingMs: Long = 0,
    /** Gesamte berechnete Laufzeit (BESETZT, inkl. Messlücken) in ms. */
    val runningMs: Long = 0,
    /** Davon Messlücken in ms. */
    val gapMs: Long = 0,
    /** Läuft gerade ein "Halt" (langsamer als Umschaltgeschwindigkeit)? */
    val haltActive: Boolean = false,
    /** Bisherige Dauer des aktuellen Halts in ms (für die Karenz). */
    val haltElapsedMs: Long = 0,
    /** Wie lange am Stück schneller als die Umschaltgeschwindigkeit (Hysterese). */
    val fastStreakMs: Long = 0,
) {
    val variableCharge: BigDecimal get() = distanceCharge.add(timeCharge, Money.MC)
}

/**
 * Tarif-Engine (eigenständig testbar, ohne Android-Abhängigkeiten).
 *
 * Verfahren (Modus [CalculationMode.ESTIMATE_SWITCH_SPEED]):
 *  1. Umschaltgeschwindigkeit v_u = Wartezeitpreis je Stunde / aktueller Kilometerpreis.
 *  2. Ein Intervall mit Geschwindigkeit >= v_u wird nach Strecke berechnet.
 *  3. Ein Intervall mit Geschwindigkeit < v_u gehört zu einem "Halt". Die ersten
 *     [TariffProfile.waitingGraceMs] eines Halts sind Karenz: In dieser Zeit wird weiterhin
 *     (nur) die Strecke berechnet. Danach wird (nur) die Zeit berechnet – nie beides gleichzeitig.
 *  4. Ein Halt endet erst, wenn mindestens [HALT_RESET_MS] am Stück schneller als v_u
 *     gefahren wurde (Hysterese gegen GPS-Schwankungen) oder eine Messlücke auftritt.
 *  5. Messlücken erzeugen keine Wartezeit und setzen den Halt zurück.
 *  6. Kilometerstaffeln werden abschnittsweise angewendet (keine rückwirkende Umbewertung).
 *  7. Angezeigter Fahrpreis = Grundpreis + 0,20 € je weiterer angefangener 0,20-€-Einheit
 *     des exakten variablen Betrags (Grundpreis enthält die erste Einheit).
 */
object FareEngine {

    const val HALT_RESET_MS: Long = 3_000

    private val MS_PER_HOUR = BigDecimal(3_600_000)
    private val M_PER_KM = BigDecimal(1_000)

    /** Umschaltgeschwindigkeit in km/h an der Streckenposition [odometerM]. */
    fun switchSpeedKmh(profile: TariffProfile, odometerM: Double): Double {
        val price = profile.bandAt(odometerM).pricePerKm
        if (profile.waitingPerHour.signum() == 0) return 0.0
        return profile.waitingPerHour.divide(price, Money.MC).toDouble()
    }

    /** Länge einer Teilstrecke (0,20 €) in Metern für eine Staffel. */
    fun stepLengthM(profile: TariffProfile, band: DistanceBand): Double =
        profile.stepAmount.multiply(M_PER_KM).divide(band.pricePerKm, Money.MC).toDouble()

    /** Dauer einer Zeit-Einheit (0,20 €) in Sekunden. */
    fun stepSeconds(profile: TariffProfile): Double =
        if (profile.waitingPerHour.signum() == 0) Double.POSITIVE_INFINITY
        else profile.stepAmount.multiply(BigDecimal(3600)).divide(profile.waitingPerHour, Money.MC).toDouble()

    fun apply(state: MeterState, interval: MeterInterval, profile: TariffProfile): MeterState {
        val dt = interval.durationMs
        if (dt == 0L && interval.distanceM == 0.0) return state

        if (!interval.measured) {
            // Messlücke: keine Wartezeit; eine geprüfte Überbrückungsstrecke (Untergrenze) wird
            // nach Strecke berechnet; Halt wird zurückgesetzt.
            val charged = distanceCharge(profile, state.odometerM, interval.distanceM)
            return state.copy(
                odometerM = state.odometerM + interval.distanceM,
                distanceChargedM = state.distanceChargedM + interval.distanceM,
                distanceCharge = state.distanceCharge.add(charged, Money.MC),
                runningMs = state.runningMs + dt,
                gapMs = state.gapMs + dt,
                haltActive = false,
                haltElapsedMs = 0,
                fastStreakMs = 0,
            )
        }

        val speedMps = interval.speedMps
            ?: if (dt > 0) interval.distanceM / (dt / 1000.0) else 0.0
        val speedKmh = speedMps * 3.6
        val vSwitch = switchSpeedKmh(profile, state.odometerM)
        val slow = speedKmh < vSwitch

        var haltActive = state.haltActive
        var haltElapsed = state.haltElapsedMs
        var fastStreak = state.fastStreakMs
        val distanceFraction: Double
        val timeMs: Long

        if (slow) {
            fastStreak = 0
            if (!haltActive) {
                haltActive = true
                haltElapsed = 0
            }
            val graceLeft = (profile.waitingGraceMs - haltElapsed).coerceAtLeast(0)
            val gracePart = minOf(graceLeft, dt)
            timeMs = dt - gracePart
            distanceFraction = if (dt > 0) gracePart.toDouble() / dt.toDouble() else 1.0
            haltElapsed += dt
        } else {
            distanceFraction = 1.0
            timeMs = 0
            if (haltActive) {
                fastStreak += dt
                if (fastStreak >= HALT_RESET_MS) {
                    haltActive = false
                    haltElapsed = 0
                    fastStreak = 0
                }
            }
        }

        // Strecke: nur der nach Strecke berechnete Anteil kostet; die Position für die
        // Staffeln läuft mit der gesamten gemessenen Strecke weiter.
        val distStart = state.odometerM
        val distEnd = distStart + interval.distanceM
        val fullSegmentCharge = distanceCharge(profile, distStart, interval.distanceM)
        val chargedDistanceCharge = if (distanceFraction >= 1.0) fullSegmentCharge
        else fullSegmentCharge.multiply(BigDecimal(distanceFraction), Money.MC)

        val timeCharge = if (timeMs > 0) profile.waitingPerHour.multiply(BigDecimal(timeMs), Money.MC)
            .divide(MS_PER_HOUR, Money.MC) else BigDecimal.ZERO

        return state.copy(
            odometerM = distEnd,
            distanceChargedM = state.distanceChargedM + interval.distanceM * distanceFraction,
            distanceCharge = state.distanceCharge.add(chargedDistanceCharge, Money.MC),
            timeCharge = state.timeCharge.add(timeCharge, Money.MC),
            chargedWaitingMs = state.chargedWaitingMs + timeMs,
            runningMs = state.runningMs + dt,
            haltActive = haltActive,
            haltElapsedMs = haltElapsed,
            fastStreakMs = fastStreak,
        )
    }

    /** Exakter Streckenpreis für [lengthM] ab Position [startM], abschnittsweise über die Staffeln. */
    fun distanceCharge(profile: TariffProfile, startM: Double, lengthM: Double): BigDecimal {
        if (lengthM <= 0.0) return BigDecimal.ZERO
        var sum = BigDecimal.ZERO
        var pos = startM
        val end = startM + lengthM
        for (band in profile.bands) {
            val bandEnd = band.toM?.toDouble() ?: Double.POSITIVE_INFINITY
            if (pos >= bandEnd) continue
            val segStart = maxOf(pos, band.fromM.toDouble())
            val segEnd = minOf(end, bandEnd)
            if (segEnd > segStart) {
                sum = sum.add(
                    BigDecimal(segEnd - segStart).multiply(band.pricePerKm, Money.MC).divide(M_PER_KM, Money.MC),
                    Money.MC,
                )
                pos = segEnd
            }
            if (pos >= end) break
        }
        return sum
    }

    /**
     * Angezeigter Fahrpreis (ohne Zuschläge) nach der Fortschaltregel.
     * Der exakte Betrag wird zuvor auf 6 Nachkommastellen gerundet, damit binäre
     * Gleitkommareste (z. B. 8,40000000001) keine zusätzliche Einheit auslösen.
     */
    fun displayedFare(profile: TariffProfile, state: MeterState): BigDecimal {
        val variable = state.variableCharge.setScale(6, RoundingMode.HALF_UP)
        val step = profile.stepAmount
        val units = variable.divide(step, 0, RoundingMode.CEILING).toLong()
        return if (profile.baseIncludesFirstStep) {
            val extra = (maxOf(1L, units) - 1L)
            profile.baseFare.add(step.multiply(BigDecimal(extra)))
        } else {
            profile.baseFare.add(step.multiply(BigDecimal(units)))
        }.setScale(2, RoundingMode.UNNECESSARY)
    }
}
