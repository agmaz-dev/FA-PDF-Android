package de.fapdf.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma 4 E2B – frei herunterladbares Google-Modell, läuft komplett auf dem Handy (Grafikeinheit).
 * Liest im Gegensatz zu Gemini Nano auch lange Dokumente auf einmal.
 */
object GemmaLocal {
    const val TITLE = "Gemma 4 E2B"
    lateinit var app: Context
    private const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    private const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE_NAME"
    const val SIZE_BYTES = 2_588_147_712L
    /** Kontextfenster: Eingabe + Antwort. 16k Bausteine ≈ 10–12 dichte Seiten. */
    private const val MAX_TOKENS = 16_384
    private const val MAX_INPUT_CHARS = 42_000

    private fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "models").apply { mkdirs() }
    fun file(ctx: Context) = File(dir(ctx), FILE_NAME)
    fun installed(ctx: Context) = file(ctx).let { it.exists() && it.length() == SIZE_BYTES }

    // ---------- Herunterladen (über den Android-Download-Manager: läuft auch im Hintergrund weiter) ----------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gemma_local", Context.MODE_PRIVATE)

    fun startDownload(ctx: Context) {
        if (installed(ctx) || downloadId(ctx) != null) return
        file(ctx).delete()
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(URL))
            .setTitle("FA PDF – $TITLE wird geladen")
            .setDescription("KI-Modell für lange Dokumente (2,6 GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(ctx, null, "models/$FILE_NAME")
        prefs(ctx).edit().putLong("dl", dm.enqueue(req)).apply()
    }

    private fun downloadId(ctx: Context): Long? = prefs(ctx).getLong("dl", -1L).takeIf { it >= 0 }

    /** Fortschritt 0..1, null = kein Download aktiv. Fehlertext, wenn fehlgeschlagen. */
    data class DlState(val fraction: Float?, val error: String?)

    fun downloadState(ctx: Context): DlState {
        val id = downloadId(ctx) ?: return DlState(null, null)
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) { prefs(ctx).edit().remove("dl").apply(); return DlState(null, null) }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: SIZE_BYTES
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> { prefs(ctx).edit().remove("dl").apply(); DlState(null, null) }
                DownloadManager.STATUS_FAILED -> {
                    prefs(ctx).edit().remove("dl").apply(); file(ctx).delete()
                    DlState(null, "Download fehlgeschlagen (Code ${c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}). Bitte erneut versuchen.")
                }
                else -> DlState((done.toFloat() / total).coerceIn(0f, 1f), null)
            }
        }
        return DlState(null, null)
    }

    fun cancelDownload(ctx: Context) {
        downloadId(ctx)?.let { ctx.getSystemService(DownloadManager::class.java).remove(it) }
        prefs(ctx).edit().remove("dl").apply()
        file(ctx).delete()
    }

    /** Modell löschen und Speicher freigeben. */
    suspend fun delete(ctx: Context) = lock.withLock {
        release()
        prefs(ctx).edit().remove("gpu_bad").remove("gpu_starting").apply()
        cancelDownload(ctx)
        file(ctx).delete()
    }

    // ---------- Antworten ----------

    private val lock = Mutex()
    private var engine: Engine? = null
    var backendName: String = ""
        private set

    private fun release() {
        runCatching { engine?.close() }
        engine = null
    }

    private fun engine(ctx: Context): Engine {
        engine?.let { return it }
        val path = file(ctx).absolutePath
        // Zuerst Grafikeinheit (schnell), sonst Prozessor. Ist die App beim letzten GPU-Start abgestürzt
        // (Merker noch gesetzt), gleich den Prozessor nehmen.
        val p = prefs(ctx)
        // Der Absturz in 1.16.1 lag nicht an der Grafikeinheit – Merker einmalig zurücksetzen
        if (!p.getBoolean("gpu_reset_v1", false)) p.edit().remove("gpu_bad").remove("gpu_starting").putBoolean("gpu_reset_v1", true).commit()
        val gpuCrashed = p.getBoolean("gpu_starting", false) || p.getBoolean("gpu_bad", false)
        if (p.getBoolean("gpu_starting", false)) p.edit().putBoolean("gpu_bad", true).putBoolean("gpu_starting", false).commit()
        val e = try {
            if (gpuCrashed) throw IllegalStateException("GPU übersprungen")
            p.edit().putBoolean("gpu_starting", true).commit()
            Engine(EngineConfig(modelPath = path, backend = Backend.GPU(), maxNumTokens = MAX_TOKENS, cacheDir = ctx.cacheDir.path))
                .also { it.initialize(); backendName = "Grafikeinheit" }
                .also { p.edit().putBoolean("gpu_starting", false).commit() }
        } catch (t: Throwable) {
            p.edit().putBoolean("gpu_starting", false).commit()
            Engine(EngineConfig(modelPath = path, backend = Backend.CPU(threadCount = 4), maxNumTokens = MAX_TOKENS, cacheDir = ctx.cacheDir.path))
                .also { it.initialize(); backendName = "Prozessor" }
        }
        engine = e
        return e
    }

    suspend fun complete(
        ctx: Context, system: String, messages: List<ChatMessage>, maxTokens: Int, onDelta: ((String) -> Unit)? = null,
    ): String = lock.withLock {
        if (!installed(ctx)) throw AiException("$TITLE ist noch nicht heruntergeladen (Einstellungen → KI-Funktionen).")
        withContext(Dispatchers.Default) {
            val e = try { engine(ctx) } catch (t: Throwable) {
                release(); throw AiException("$TITLE konnte nicht gestartet werden: ${t.message}")
            }
            val history = messages.dropLast(1).map { if (it.role == "assistant") Message.model(it.content) else Message.user(it.content) }
            var last = messages.lastOrNull()?.content.orEmpty()
            if (last.length > MAX_INPUT_CHARS) last = last.take(MAX_INPUT_CHARS) + "\n</dokument>"
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                initialMessages = history,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                maxOutputToken = maxTokens.coerceIn(256, 4096),
                thinkingConfig = ThinkingConfig(enableThinking = false),
            )
            val sb = StringBuilder()
            var looped = false
            val onGpu = backendName == "Grafikeinheit"
            if (onGpu) prefs(ctx).edit().putBoolean("gpu_starting", true).commit()
            try {
                e.createConversation(config).use { conv ->
                    // Rückruf-Variante statt Flow: die Flow-Variante braucht eine neuere Coroutines-Version
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                        var lastUi = 0L
                        val cb = object : com.google.ai.edge.litertlm.MessageCallback {
                            override fun onMessage(message: Message) {
                                val now = synchronized(sb) { sb.append(message.toString()); sb.toString() }
                                // Endlosschleife erkennen („0000…“, „.0.0.0…“) und abbrechen
                                if (degenerate(now)) { looped = true; runCatching { conv.cancelProcess() }; if (cont.isActive) cont.resumeWith(Result.success(Unit)); return }
                                // Oberfläche höchstens ~4× pro Sekunde aktualisieren (sonst ruckelt es)
                                val t = System.currentTimeMillis()
                                if (t - lastUi > 250) { lastUi = t; onDelta?.invoke(now) }
                            }
                            override fun onDone() { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
                            override fun onError(throwable: Throwable) { if (cont.isActive) cont.resumeWith(Result.failure(throwable)) }
                        }
                        conv.sendMessageAsync(
                            last, cb, emptyMap(),
                            com.google.ai.edge.litertlm.RepetitionPenaltyConfig(repetitionPenalty = 1.15f, windowSize = 256),
                            com.google.ai.edge.litertlm.NoRepeatNgramConfig(noRepeatNgramSize = 10, windowSize = 1024),
                        )
                        cont.invokeOnCancellation { runCatching { conv.cancelProcess() } }
                    }
                }
            } catch (t: Throwable) {
                if (onGpu) prefs(ctx).edit().putBoolean("gpu_starting", false).commit()
                if (sb.isEmpty()) throw AiException("$TITLE: ${t.message ?: "Fehler"}")
            }
            if (onGpu) prefs(ctx).edit().putBoolean("gpu_starting", false).commit()
            if (looped) throw AiException("$TITLE ist in eine Wiederholungsschleife geraten.")
            onDelta?.invoke(sb.toString())
            AiText.clean(sb.toString()).ifEmpty { throw AiException("$TITLE hat keine Antwort geliefert.") }
        }
    }

    /** Text endet mit sinnlosen Wiederholungen (z. B. 80 Zeichen aus höchstens 3 verschiedenen Zeichen). */
    private fun degenerate(t: String): Boolean {
        if (t.length < 120) return false
        val tail = t.takeLast(80)
        return tail.toSet().size <= 3 || Regex("(.{2,12})\\1{6,}$").containsMatchIn(tail)
    }
}
