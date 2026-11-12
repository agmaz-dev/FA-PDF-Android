package de.faki

import android.content.Context
import java.io.File
import java.util.Timer
import java.util.TimerTask

/** Zugang zur nativen KI (llama.cpp). */
object Native {
    init {
        System.loadLibrary("fa-ki")
        nativeInit()
    }

    @JvmStatic external fun nativeInit()
    @JvmStatic external fun load(path: String, nCtx: Int): Int
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
        synchronized(lock) {
            if (loadedId != m.id) {
                if (loadedId != null) Native.unload()
                loadedId = null
                Crash.step(context, "Modell „${m.title}“ wird geladen")
                val rc = Native.load(f.absolutePath, CONTEXT)
                Crash.done(context)
                if (rc != 0) throw KiException("Das Modell „${m.title}“ konnte nicht geladen werden (Code $rc).")
                loadedId = m.id
            }
        }
        return m
    }

    fun complete(context: Context, messages: List<Msg>, maxTokens: Int = 1024, temperature: Float = 0.3f): String {
        ensureLoaded(context)
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
