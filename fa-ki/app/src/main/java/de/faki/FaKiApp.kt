package de.faki

import android.app.Application
import android.content.Context
import java.io.File

/** Merkt sich Abstürze, damit sie beim nächsten Öffnen angezeigt werden können. */
class FaKiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, "crash.txt").writeText("Absturz (${t.name}):\n" + android.util.Log.getStackTraceString(e).take(4000)) }
            previous?.uncaughtException(t, e)
        }
    }
}

object Crash {
    private fun stepFile(c: Context) = File(c.filesDir, "step.txt")
    /** Vor riskanten Schritten in der KI (nativer Code) – stürzt die App dort ab, bleibt die Datei stehen. */
    fun step(c: Context, what: String) = runCatching { stepFile(c).writeText(what) }
    fun done(c: Context) = runCatching { stepFile(c).delete() }

    /** Warum Android den Vorgang zuletzt beendet hat (Speichermangel, Absturz …). */
    private fun exitReason(c: Context): String? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        val am = c.getSystemService(android.app.ActivityManager::class.java) ?: return null
        val e = runCatching { am.getHistoricalProcessExitReasons(c.packageName, 0, 1).firstOrNull() }.getOrNull() ?: return null
        val why = when (e.reason) {
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Android hat FA KI wegen Speichermangel beendet"
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Absturz im KI-Kern"
            android.app.ApplicationExitInfo.REASON_CRASH -> "Absturz"
            android.app.ApplicationExitInfo.REASON_SIGNALED -> "Vom System beendet (Signal ${e.status})"
            android.app.ApplicationExitInfo.REASON_ANR -> "Hing fest (keine Reaktion)"
            android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Zu hoher Verbrauch, vom System beendet"
            android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "Vom Benutzer beendet"
            android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "Vom Benutzer gestoppt"
            android.app.ApplicationExitInfo.REASON_OTHER -> "Vom System beendet"
            else -> "Grund ${e.reason}"
        }
        val pss = if (e.pss > 0) " · Speicher ${e.pss / 1024} MB" else ""
        return "Grund: $why$pss" + (e.description?.let { "\n$it" } ?: "")
    }

    /** Liefert den Bericht des letzten Absturzes (einmalig) oder null. */
    fun takeReport(c: Context): String? {
        val parts = ArrayList<String>()
        File(c.filesDir, "crash.txt").takeIf { it.exists() }?.let { parts += it.readText(); it.delete() }
        stepFile(c).takeIf { it.exists() }?.let {
            parts += "Die App ist abgestürzt bei: " + it.readText(); it.delete()
            exitReason(c)?.let { r -> parts += r }
        }
        File(c.filesDir, "native_crash.txt").takeIf { it.exists() }?.let { parts += "KI-Kern:\n" + it.readText().take(3000); it.delete() }
        return parts.joinToString("\n\n").ifBlank { null }
    }
}
