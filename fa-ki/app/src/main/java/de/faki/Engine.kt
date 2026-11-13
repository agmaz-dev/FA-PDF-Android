package de.faki

import android.content.Context
import java.io.File
import java.util.Timer
import java.util.TimerTask

/** Zugang zur nativen KI (llama.cpp). */
object Native {
    @Volatile private var ready = false

    /** Lädt die Bibliotheken und sucht die Rechenwerke (Prozessor, Grafik) im App-Ordner. */
    @Synchronized fun ensure(context: Context) {
        if (ready) return
        System.loadLibrary("fa-ki")
        nativeInit(context.applicationInfo.nativeLibraryDir)
        ready = true
    }

    @JvmStatic external fun nativeInit(libDir: String)
    @JvmStatic external fun devices(): String
    @JvmStatic external fun progress(): String
    @JvmStatic external fun load(path: String, nCtx: Int, nGpuLayers: Int): Int
    @JvmStatic external fun complete(roles: Array<String>, contents: Array<String>, maxTokens: Int, temp: Float): String
    @JvmStatic external fun abort()
    @JvmStatic external fun unload()
    @JvmStatic external fun describe(): String
}

data class Msg(val role: String, val content: String)

class KiException(message: String) : Exception(message)

/** Eine Anfrage nach der anderen; das Modell wird nach Leerlauf automatisch aus dem Speicher genommen. */
object Engine {
    private val lock = Any()
    @Volatile var loadedId: String? = null
        private set
    private var loadedGpu = -1
    @Volatile var busy = false
        private set
    private var idleTimer: Timer? = null
    private const val IDLE_UNLOAD_MS = 10 * 60 * 1000L
    private const val CONTEXT = 8192

    fun modelFile(context: Context, m: KiModel): File = File(Models.dir(context), m.fileName)

    /** Lädt das eingeschaltete Modell, falls nötig. */
    fun ensureLoaded(context: Context): KiModel {
        val m = Settings.active(context) ?: throw KiException("Kein Modell eingeschaltet. Bitte in FA KI ein Modell einschalten.")
        val f = modelFile(context, m)
        if (!f.exists() || f.length() < 100_000_000L) throw KiException("Das Modell „${m.title}“ ist noch nicht heruntergeladen.")
        Native.ensure(context)
        val gpu = gpuLayers(context, m)
        synchronized(lock) {
            if (loadedId != m.id || loadedGpu != gpu) {
                if (loadedId != null) Native.unload()
                loadedId = null
                Crash.step(context, "Modell „${m.title}“ wird geladen")
                val rc = Native.load(f.absolutePath, CONTEXT, gpu)
                Crash.done(context)
                if (rc != 0) throw KiException("Das Modell „${m.title}“ konnte nicht geladen werden (Code $rc).")
                loadedId = m.id
                loadedGpu = gpu
            }
        }
        return m
    }

    fun complete(context: Context, messages: List<Msg>, maxTokens: Int = 1024, temperature: Float = 0.3f): String {
        val m = ensureLoaded(context)
        synchronized(lock) {
            busy = true
            try {
                cancelIdle()
                // Grob vorkürzen (etwa 3 Zeichen pro Token), damit lange Dokumente in den Kontext passen
                val maxChars = (CONTEXT - maxTokens) * 3
                val trimmed = shorten(messages, maxChars)
                Crash.step(context, "Antwort mit „${m.title}“")
                val out = Native.complete(
                    trimmed.map { it.role }.toTypedArray(),
                    trimmed.map { it.content }.toTypedArray(),
                    maxTokens.coerceIn(16, 4096), temperature,
                )
                Crash.done(context)
                if (out.startsWith("\u0001FEHLER:")) throw KiException(out.removePrefix("\u0001FEHLER:"))
                return cleanup(out)
            } finally {
                busy = false
                scheduleIdle()
            }
        }
    }

    /** Anzahl der Schichten auf der Grafikeinheit laut Regler (0 = nur Prozessor). */
    fun gpuLayers(context: Context, m: KiModel): Int = when (val p = Settings.gpuPercent(context)) {
        0 -> 0
        100 -> 999
        else -> (m.layers * p / 100).coerceAtLeast(1)
    }

    /** Fortschritt der laufenden bzw. letzten Anfrage als lesbarer Text. */
    fun progressText(): String? {
        if (loadedId == null && !busy) return null
        val p = runCatching { Native.progress().split(";").map { it.toLong() } }.getOrNull() ?: return null
        fun v(i: Int) = p.getOrElse(i) { 0L }
        val ph = v(0); val done = v(1); val total = v(2); val out = v(3); val readMs = v(4); val genMs = v(5)
        val speed = if (genMs > 0) "%.1f".format(out * 1000.0 / genMs) else "–"
        return when (ph) {
            1L -> "Liest Eingabe: $done von $total Wörterteilen (${readMs / 1000} s)"
            2L -> "Schreibt Antwort: $out Wörterteile · $speed pro Sekunde (Lesen dauerte ${readMs / 1000} s)"
            else -> if (total > 0) "Letzte Anfrage: $total gelesen in ${"%.1f".format(readMs / 1000.0)} s · $out geschrieben in ${"%.1f".format(genMs / 1000.0)} s ($speed pro Sekunde)" else null
        }
    }

    /** Beschreibung der gefundenen Rechenwerke für die Anzeige. */
    fun devices(context: Context): String = runCatching { Native.ensure(context); Native.devices() }.getOrElse { "Fehler: ${it.message}" }

    fun hasGpu(context: Context): Boolean = devices(context).contains("[GPU]")

    fun unloadNow() {
        synchronized(lock) {
            if (loadedId != null) Native.unload()
            loadedId = null
        }
    }

    private fun cleanup(s: String): String =
        s.replace(Regex("(?s)<think>.*?</think>"), "").replace("<think>", "").replace("</think>", "").trim()

    private fun shorten(messages: List<Msg>, maxChars: Int): List<Msg> {
        val total = messages.sumOf { it.content.length }
        if (total <= maxChars) return messages
        val longest = messages.indices.maxByOrNull { messages[it].content.length } ?: return messages
        val over = total - maxChars
        val c = messages[longest].content
        val keep = (c.length - over).coerceAtLeast(2000)
        val head = keep * 2 / 3
        val tail = keep - head
        val short = c.take(head) + "\n\n[… gekürzt …]\n\n" + c.takeLast(tail)
        return messages.mapIndexed { i, msg -> if (i == longest) msg.copy(content = short) else msg }
    }

    @Synchronized private fun cancelIdle() { idleTimer?.cancel(); idleTimer = null }

    @Synchronized private fun scheduleIdle() {
        idleTimer?.cancel()
        idleTimer = Timer("ki-idle", true).apply {
            schedule(object : TimerTask() { override fun run() { if (!busy) unloadNow() } }, IDLE_UNLOAD_MS)
        }
    }
}
