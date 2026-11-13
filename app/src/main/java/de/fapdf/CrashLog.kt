package de.fapdf

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hält Abstürze fest (Java und nativ), damit sie beim nächsten Start angezeigt werden können. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")
    private fun stamp() = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                file(ctx).writeText("FA PDF ${BuildConfig.VERSION_NAME} · ${stamp()} · Thread ${t.name}\n\n" + e.stackTraceToString().take(12_000))
            }
            prev?.uncaughtException(t, e)
        }
        // Native Abstürze (z. B. in der KI-Bibliothek) kann nur Android selbst melden
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val am = ctx.getSystemService(ActivityManager::class.java)
            val seen = ctx.getSharedPreferences("crashlog", Context.MODE_PRIVATE)
            val info = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull() ?: return@runCatching
            if (info.timestamp <= seen.getLong("seen", 0)) return@runCatching
            seen.edit().putLong("seen", info.timestamp).apply()
            if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || info.reason == ApplicationExitInfo.REASON_CRASH ||
                info.reason == ApplicationExitInfo.REASON_ANR || info.reason == ApplicationExitInfo.REASON_LOW_MEMORY
            ) {
                if (info.reason == ApplicationExitInfo.REASON_CRASH && file(ctx).exists()) return@runCatching
                val trace = if (Build.VERSION.SDK_INT >= 31) runCatching {
                    info.traceInputStream?.use { String(it.readBytes().take(8000).toByteArray()) }
                }.getOrNull().orEmpty() else ""
                val why = when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Nativer Absturz"
                    ApplicationExitInfo.REASON_ANR -> "App reagierte nicht (ANR)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "Zu wenig Arbeitsspeicher"
                    else -> "Absturz"
                }
                file(ctx).writeText(
                    "FA PDF ${BuildConfig.VERSION_NAME} · " +
                        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(info.timestamp)) +
                        " · $why\n${info.description.orEmpty()}\nSpeicher: ${info.pss / 1024} MB\n\n" + trace.filter { it.code in 9..126 || it.code > 160 }
                )
            }
        }
    }

    fun take(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.let { f -> f.readText().also { f.delete() } }
}
