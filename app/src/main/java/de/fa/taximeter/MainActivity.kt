package de.fa.taximeter

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.fa.taximeter.data.OrientationMode
import de.fa.taximeter.ui.TaximeterRoot

class MainActivity : ComponentActivity() {

    private val repo get() = (application as FaTaximeterApp).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        applyOrientation(repo.settings.current.orientation)
        // Nicht erneut verarbeiten, wenn die Activity neu aufgebaut oder aus dem Verlauf gestartet wird
        val fromHistory = (intent.flags and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) handleSumUp(intent)
        setContent {
            TaximeterRoot(
                repo = repo,
                onOrientation = ::applyOrientation,
                onPermissionResult = { repo.gpsHub.refresh() },
                onLeaveToBackground = { moveTaskToBack(true) },
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSumUp(intent)
    }

    /** Rückkehr aus der SumUp-App (fataximeter://sumup-callback?...). */
    private fun handleSumUp(intent: android.content.Intent?) {
        val result = de.fa.taximeter.location.SumUpPayment.parse(intent) ?: return
        repo.onCardResult(result)
        intent?.data = null // nicht doppelt verarbeiten (z. B. nach Drehung)
    }

    override fun onStart() {
        super.onStart()
        repo.gpsHub.acquire(UI_HOLDER)
        repo.ensureServiceState()
    }

    override fun onStop() {
        repo.gpsHub.release(UI_HOLDER)
        super.onStop()
    }

    private fun applyOrientation(mode: OrientationMode) {
        val wanted = when (mode) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    private companion object {
        const val UI_HOLDER = "ui"
    }
}
