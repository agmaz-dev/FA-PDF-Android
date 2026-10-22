package de.fa.taximeter.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.gps.SimulatedRoute

/**
 * Bündelt die Standortquelle. Mehrere Nutzer (sichtbare Oberfläche, Foreground-Service)
 * melden sich mit einem Kennzeichen an; die Messung läuft, solange mindestens einer angemeldet ist.
 *
 * Normalbetrieb: Android LocationManager, Anbieter GPS, Intervall 1 s, keine Mindeststrecke
 * (die Filterung übernimmt der DistanceTracker). Keine Google-Dienste, kein Internet.
 * Testmodus: synthetische Fahrt (SimulatedRoute), dauerhaft als Simulation gekennzeichnet.
 */
class GpsHub(
    private val context: Context,
    private val onFix: (GpsFix) -> Unit,
    private val onProviderChanged: (enabled: Boolean) -> Unit,
) {
    private val holders = mutableSetOf<String>()
    private val main = Handler(Looper.getMainLooper())
    private var runningReal = false
    private var runningSim = false
    private var simulation = false
    private var simRoute: SimulatedRoute? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onFix(location.toFix())
        }

        override fun onProviderEnabled(provider: String) {
            onProviderChanged(true)
        }

        override fun onProviderDisabled(provider: String) {
            onProviderChanged(false)
        }

        // Muss für Android 8–10 explizit implementiert sein (dort noch abstrakt).
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    private val simTick = object : Runnable {
        override fun run() {
            val route = simRoute ?: return
            onFix(route.next(SystemClock.elapsedRealtime(), System.currentTimeMillis()))
            main.postDelayed(this, 1000)
        }
    }

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAnyLocationPermission(): Boolean = hasFinePermission() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun isGpsProviderEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    fun acquire(tag: String) {
        holders += tag
        refresh()
    }

    fun release(tag: String) {
        holders -= tag
        refresh()
    }

    fun setSimulation(enabled: Boolean) {
        if (simulation == enabled) return
        simulation = enabled
        stopAll()
        refresh()
    }

    /** Startet oder stoppt die Quelle passend zu Anmeldungen, Berechtigung und Modus. */
    fun refresh() {
        val want = holders.isNotEmpty()
        if (!want) {
            stopAll()
            return
        }
        if (simulation) {
            if (runningReal) stopReal()
            if (!runningSim) {
                simRoute = SimulatedRoute(seed = (System.currentTimeMillis() % 1000).toInt())
                runningSim = true
                main.post(simTick)
            }
        } else {
            if (runningSim) stopSim()
            if (!runningReal) startReal()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startReal() {
        if (!hasAnyLocationPermission()) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            runningReal = true
            onProviderChanged(isGpsProviderEnabled())
        } catch (e: SecurityException) {
            Log.e(TAG, "Keine Standortberechtigung", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "GPS-Anbieter nicht vorhanden", e)
            onProviderChanged(false)
        }
    }

    private fun stopReal() {
        if (!runningReal) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.removeUpdates(listener)
        } catch (_: Exception) {
        }
        runningReal = false
    }

    private fun stopSim() {
        main.removeCallbacks(simTick)
        runningSim = false
        simRoute = null
    }

    private fun stopAll() {
        stopReal()
        stopSim()
    }

    companion object {
        private const val TAG = "GpsHub"

        @Suppress("DEPRECATION")
        fun Location.toFix(): GpsFix = GpsFix(
            lat = latitude,
            lon = longitude,
            accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
            elapsedMs = elapsedRealtimeNanos / 1_000_000L,
            wallTimeMs = time,
            speedMps = if (hasSpeed()) speed.toDouble() else null,
            isMock = if (Build.VERSION.SDK_INT >= 31) isMock else isFromMockProvider,
        )
    }
}
