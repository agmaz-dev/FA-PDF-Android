package de.fa.taximeter.domain.gps

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Eine Standortmessung, unabhängig von Android.
 *
 * @param elapsedMs  monotone Zeitbasis (Android: Location.elapsedRealtimeNanos / 1e6).
 * @param wallTimeMs Uhrzeit der Messung (nur zur Anzeige/Speicherung, nie für Differenzen).
 * @param accuracyM  horizontale Genauigkeit (68 %), null = unbekannt.
 * @param speedMps   Doppler-Geschwindigkeit des Empfängers, null = nicht vorhanden.
 */
data class GpsFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Double?,
    val elapsedMs: Long,
    val wallTimeMs: Long,
    val speedMps: Double?,
    val isMock: Boolean = false,
)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Großkreisdistanz (Haversine) in Metern. */
    fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val p1 = Math.toRadians(aLat)
        val p2 = Math.toRadians(bLat)
        val dp = p2 - p1
        val dl = Math.toRadians(bLon - aLon)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun distanceM(a: GpsFix, b: GpsFix): Double = distanceM(a.lat, a.lon, b.lat, b.lon)

    /** Verschiebt einen Punkt um [northM]/[eastM] Meter (für Tests und Simulation). */
    fun offset(lat: Double, lon: Double, northM: Double, eastM: Double): Pair<Double, Double> {
        val dLat = northM / EARTH_RADIUS_M
        val dLon = eastM / (EARTH_RADIUS_M * cos(Math.toRadians(lat)))
        return Pair(lat + Math.toDegrees(dLat), lon + Math.toDegrees(dLon))
    }
}
