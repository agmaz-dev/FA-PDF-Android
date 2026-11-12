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

    /** Liefert den Bericht des letzten Absturzes (einmalig) oder null. */
    fun takeReport(c: Context): String? {
        val parts = ArrayList<String>()
        File(c.filesDir, "crash.txt").takeIf { it.exists() }?.let { parts += it.readText(); it.delete() }
        stepFile(c).takeIf { it.exists() }?.let { parts += "Die App ist abgestürzt bei: " + it.readText(); it.delete() }
        return parts.joinToString("\n\n").ifBlank { null }
    }
}
