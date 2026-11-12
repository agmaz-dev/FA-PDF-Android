package de.faki

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fi.iki.elonen.NanoHTTPD

/** Hält die lokale KI-Adresse im Hintergrund erreichbar (dauerhafte Benachrichtigung wie bei einem Musik-Player). */
class KiService : Service() {
    private var server: KiServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android erlaubt den Vordergrunddienst nicht, wenn das System den Dienst im Hintergrund neu startet –
        // dann sauber beenden statt abzustürzen. Beim nächsten Öffnen von FA KI läuft er wieder.
        val ok = runCatching { startInForeground() }.onFailure { lastError = null }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (server == null) {
            server = KiServer(applicationContext).also {
                runCatching { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }.onFailure { e -> lastError = e.message }
            }
        }
        running = server?.isAlive == true
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "FA KI", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, KiService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val model = Settings.active(this)?.title ?: "kein Modell eingeschaltet"
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("FA KI läuft")
            .setContentText("$model · ${KiServer.BASE_URL}")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Beenden", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        running = false
        Engine.unloadNow()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "faki"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "de.faki.STOP"
        @Volatile var running = false
        @Volatile var lastError: String? = null

        fun start(c: Context) { c.startForegroundService(Intent(c, KiService::class.java)) }
        fun stop(c: Context) { c.stopService(Intent(c, KiService::class.java)) }
        /** Aktualisiert die Benachrichtigung (z. B. nach Modellwechsel). */
        fun refresh(c: Context) { if (running) start(c) }
    }
}
