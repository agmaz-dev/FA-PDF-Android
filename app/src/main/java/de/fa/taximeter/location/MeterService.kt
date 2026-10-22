package de.fa.taximeter.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.fa.taximeter.FaTaximeterApp
import de.fa.taximeter.MainActivity
import de.fa.taximeter.R
import de.fa.taximeter.domain.trip.Phase

/**
 * Foreground-Service (Typ "location") für die Standortmessung während einer aktiven Fahrt.
 * Wird ausschließlich aus der sichtbaren App (Tippen auf BESETZT) gestartet, wie von
 * Android 14+ für Standort-Foreground-Services verlangt. Eine Garantie, dass die Messung nach
 * erzwungenem App-Stopp oder Neustart weiterläuft, gibt es nicht (START_NOT_STICKY).
 */
class MeterService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val repo get() = (application as FaTaximeterApp).repository

    private val updater = object : Runnable {
        override fun run() {
            val phase = repo.state.value.phase
            if (phase == Phase.FREI || phase == Phase.KASSE) {
                stopSelf()
                return
            }
            repo.periodicSave()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                nm.notify(NOTIFICATION_ID, buildNotification())
            } catch (_: SecurityException) {
            }
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground fehlgeschlagen", e)
            repo.message("Hintergrundmessung nicht möglich – App bitte im Vordergrund lassen.")
            stopSelf()
            return START_NOT_STICKY
        }
        repo.gpsHub.acquire(HOLDER)
        handler.removeCallbacks(updater)
        handler.postDelayed(updater, 5_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        repo.gpsHub.release(HOLDER)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_trip), NotificationManager.IMPORTANCE_LOW)
            ch.description = getString(R.string.channel_trip_desc)
            ch.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_meter)
            .setContentTitle("FA Taximeter – Fahrt aktiv (GPS-Schätzung, nicht geeicht)")
            .setContentText(repo.notificationText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val TAG = "MeterService"
        private const val CHANNEL_ID = "fahrt"
        private const val NOTIFICATION_ID = 42
        private const val HOLDER = "service"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MeterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeterService::class.java))
        }
    }
}
