package de.fa.taximeter.domain.gps

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Erzeugt eine synthetische Stadtfahrt für den TESTMODUS (Simulation).
 * Wird ausschließlich verwendet, wenn der Testmodus ausdrücklich eingeschaltet ist;
 * die Oberfläche zeigt dann dauerhaft "SIMULATION".
 *
 * Ablauf (wiederholt): 60 s mit 40 km/h, 90 s Halt (Ampel/Stau), 40 s Schritttempo 8 km/h,
 * 120 s mit 50 km/h, 30 s Halt.
 */
class SimulatedRoute(
    private val startLat: Double = 52.5219,
    private val startLon: Double = 13.4132,
    seed: Int = 1,
) {
    private data class Leg(val seconds: Int, val speedKmh: Double)

    private val legs = listOf(Leg(60, 40.0), Leg(90, 0.0), Leg(40, 8.0), Leg(120, 50.0), Leg(30, 0.0))
    private val cycle = legs.sumOf { it.seconds }
    private val rnd = Random(seed)

    private var north = 0.0
    private var east = 0.0
    private var heading = 0.6 // rad
    private var second = 0

    /** Nächste Messung (1 Hz). */
    fun next(elapsedMs: Long, wallMs: Long): GpsFix {
        val leg = legAt(second % cycle)
        val v = leg.speedKmh / 3.6
        if (v > 0) {
            heading += (rnd.nextDouble() - 0.5) * 0.08
            north += v * cos(heading)
            east += v * sin(heading)
        }
        second++
        val noiseN = (rnd.nextDouble() - 0.5) * 4.0
        val noiseE = (rnd.nextDouble() - 0.5) * 4.0
        val (lat, lon) = Geo.offset(startLat, startLon, north + noiseN, east + noiseE)
        val reportedSpeed = if (v > 0) v + (rnd.nextDouble() - 0.5) * 0.4 else rnd.nextDouble() * 0.2
        return GpsFix(
            lat = lat,
            lon = lon,
            accuracyM = 4.0 + rnd.nextDouble() * 3.0,
            elapsedMs = elapsedMs,
            wallTimeMs = wallMs,
            speedMps = reportedSpeed,
            isMock = true,
        )
    }

    private fun legAt(sec: Int): Leg {
        var acc = 0
        for (l in legs) {
            acc += l.seconds
            if (sec < acc) return l
        }
        return legs.last()
    }
}
