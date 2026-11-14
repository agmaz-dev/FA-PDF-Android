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
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma 4 (E2B klein / E4B klüger) – frei herunterladbare Google-Modelle, laufen komplett auf dem Handy.
 * Lesen im Gegensatz zu Gemini Nano auch lange Dokumente auf einmal.
 */
object GemmaLocal {
    enum class Variant(val title: String, val fileName: String, val repo: String, val sizeBytes: Long, val sizeText: String) {
        E2B("Gemma 4 E2B", "gemma-4-E2B-it.litertlm", "litert-community/gemma-4-E2B-it-litert-lm", 2_588_147_712L, "2,6 GB"),
        E4B("Gemma 4 E4B", "gemma-4-E4B-it.litertlm", "litert-community/gemma-4-E4B-it-litert-lm", 3_659_530_240L, "3,7 GB");

        val url get() = "https://huggingface.co/$repo/resolve/main/$fileName"
    }

    lateinit var app: Context

    /** Kontextfenster: Eingabe + Antwort. 16k Bausteine ≈ 10–12 dichte Seiten. */
    private const val MAX_TOKENS = 16_384
    private const val MAX_INPUT_CHARS = 42_000

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gemma_local", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "models").apply { mkdirs() }
    fun file(ctx: Context, v: Variant) = File(dir(ctx), v.fileName)
    fun installed(ctx: Context, v: Variant) = file(ctx, v).let { it.exists() && it.length() == v.sizeBytes }
    fun anyInstalled(ctx: Context) = Variant.entries.any { installed(ctx, it) }

    /** Gewählte Variante; ist nur die andere geladen, wird diese genommen. */
    fun selected(ctx: Context): Variant {
        val want = runCatching { Variant.valueOf(prefs(ctx).getString("variant", "E2B")!!) }.getOrDefault(Variant.E2B)
        return if (installed(ctx, want)) want else Variant.entries.firstOrNull { installed(ctx, it) } ?: want
    }
    fun select(ctx: Context, v: Variant) { prefs(ctx).edit().putString("variant", v.name).apply() }

    // ---------- Herunterladen (Android-Download-Manager: läuft auch im Hintergrund weiter) ----------

    private fun dlKey(v: Variant) = "dl_${v.name}"
    private fun downloadId(ctx: Context, v: Variant): Long? {
        // Download aus 1.16.0–1.16.3 (nur E2B, alter Schlüssel) übernehmen
        val p = prefs(ctx)
        if (v == Variant.E2B && p.contains("dl")) { p.edit().putLong(dlKey(v), p.getLong("dl", -1L)).remove("dl").apply() }
        return p.getLong(dlKey(v), -1L).takeIf { it >= 0 }
    }

    fun startDownload(ctx: Context, v: Variant) {
        if (installed(ctx, v) || downloadId(ctx, v) != null) return
        file(ctx, v).delete()
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(v.url))
            .setTitle("FA PDF – ${v.title} wird geladen")
            .setDescription("KI-Modell für lange Dokumente (${v.sizeText})")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(ctx, null, "models/${v.fileName}")
        prefs(ctx).edit().putLong(dlKey(v), dm.enqueue(req)).apply()
    }

    data class DlState(val fraction: Float?, val error: String?)

    fun downloadState(ctx: Context, v: Variant): DlState {
        val id = downloadId(ctx, v) ?: return DlState(null, null)
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) { prefs(ctx).edit().remove(dlKey(v)).apply(); return DlState(null, null) }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: v.sizeBytes
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> { prefs(ctx).edit().remove(dlKey(v)).apply(); DlState(null, null) }
                DownloadManager.STATUS_FAILED -> {
                    prefs(ctx).edit().remove(dlKey(v)).apply(); file(ctx, v).delete()
                    DlState(null, "Download fehlgeschlagen (Code ${c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}). Bitte erneut versuchen.")
                }
                else -> DlState((done.toFloat() / total).coerceIn(0f, 1f), null)
            }
        }
        return DlState(null, null)
    }

    fun cancelDownload(ctx: Context, v: Variant) {
        downloadId(ctx, v)?.let { ctx.getSystemService(DownloadManager::class.java).remove(it) }
        prefs(ctx).edit().remove(dlKey(v)).apply()
        file(ctx, v).delete()
    }

    /** Modell löschen und Speicher freigeben. */
    suspend fun delete(ctx: Context, v: Variant) = lock.withLock {
        if (loaded == v) release()
        prefs(ctx).edit().remove("gpu_bad").remove("gpu_starting").apply()
        cancelDownload(ctx, v)
        file(ctx, v).delete()
    }

    // ---------- Antworten ----------

    private val lock = Mutex()
    private var engine: Engine? = null
    private var loaded: Variant? = null
    var backendName: String = ""
        private set
    /** Für die Anzeige über dem Ergebnis, z. B. „Gemma 4 E2B · Grafikeinheit · 38 s“. */
    var lastInfo: String = ""
        private set

    private fun release() {
        runCatching { engine?.close() }
        engine = null; loaded = null
    }

    private fun engine(ctx: Context, v: Variant): Engine {
        if (loaded == v) engine?.let { return it }
        release()
        val path = file(ctx, v).absolutePath
        val p = prefs(ctx)
        if (!p.getBoolean("gpu_reset_v2", false)) p.edit().remove("gpu_bad").remove("gpu_starting").putBoolean("gpu_reset_v2", true).commit()
        // Ist die App beim letzten Start auf der Grafikeinheit abgestürzt, gleich den Prozessor nehmen
        if (p.getBoolean("gpu_starting", false)) p.edit().putBoolean("gpu_bad", true).putBoolean("gpu_starting", false).commit()
        val e = try {
            if (p.getBoolean("gpu_bad", false)) throw IllegalStateException("GPU übersprungen")
            p.edit().putBoolean("gpu_starting", true).commit()
            Engine(EngineConfig(modelPath = path, backend = Backend.GPU(), maxNumTokens = MAX_TOKENS, cacheDir = ctx.cacheDir.path))
                .also { it.initialize(); backendName = "Grafikeinheit" }
                .also { p.edit().putBoolean("gpu_starting", false).commit() }
        } catch (t: Throwable) {
            p.edit().putBoolean("gpu_starting", false).commit()
            Engine(EngineConfig(modelPath = path, backend = Backend.CPU(), maxNumTokens = MAX_TOKENS, cacheDir = ctx.cacheDir.path))
                .also { it.initialize(); backendName = "Prozessor" }
        }
        engine = e; loaded = v
        return e
    }

    suspend fun complete(
        ctx: Context, system: String, messages: List<ChatMessage>, maxTokens: Int, onDelta: ((String) -> Unit)? = null,
    ): String = lock.withLock {
        val v = selected(ctx)
        if (!installed(ctx, v)) throw AiException("Gemma 4 ist noch nicht heruntergeladen (Einstellungen → KI-Funktionen).")
        withContext(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            val e = try { engine(ctx, v) } catch (t: Throwable) {
                release(); throw AiException("${v.title} konnte nicht gestartet werden: ${t.message}")
            }
            val history = messages.dropLast(1).map { if (it.role == "assistant") Message.model(it.content) else Message.user(it.content) }
            var last = messages.lastOrNull()?.content.orEmpty()
            if (last.length > MAX_INPUT_CHARS) last = last.take(MAX_INPUT_CHARS) + "\n</dokument>"
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                initialMessages = history,
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
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
                    suspendCancellableCoroutine<Unit> { cont ->
                        var lastUi = 0L
                        conv.sendMessageAsync(last, object : MessageCallback {
                            override fun onMessage(message: Message) {
                                val now = synchronized(sb) { sb.append(message.toString()); sb.toString() }
                                // Nur echte Endlosschleifen („0000…“) abbrechen – sonst nichts am Text verändern
                                if (degenerate(now)) { looped = true; runCatching { conv.cancelProcess() }; if (cont.isActive) cont.resumeWith(Result.success(Unit)); return }
                                val t = System.currentTimeMillis()
                                if (t - lastUi > 250) { lastUi = t; onDelta?.invoke(now) }
                            }
                            override fun onDone() { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
                            override fun onError(throwable: Throwable) { if (cont.isActive) cont.resumeWith(Result.failure(throwable)) }
                        })
                        cont.invokeOnCancellation { runCatching { conv.cancelProcess() } }
                    }
                }
            } catch (t: Throwable) {
                if (onGpu) prefs(ctx).edit().putBoolean("gpu_starting", false).commit()
                if (sb.isEmpty()) throw AiException("${v.title}: ${t.message ?: "Fehler"}")
            }
            if (onGpu) prefs(ctx).edit().putBoolean("gpu_starting", false).commit()
            if (looped) throw AiException("${v.title} ist in eine Wiederholungsschleife geraten.")
            lastInfo = "${v.title} · $backendName · ${(System.currentTimeMillis() - started) / 1000} s"
            onDelta?.invoke(sb.toString())
            AiText.clean(sb.toString()).ifEmpty { throw AiException("${v.title} hat keine Antwort geliefert.") }
        }
    }

    /** Text endet mit sinnlosen Wiederholungen (z. B. 80 Zeichen aus höchstens 3 verschiedenen Zeichen). */
    private fun degenerate(t: String): Boolean {
        if (t.length < 120) return false
        val tail = t.takeLast(80)
        return tail.toSet().size <= 3 || Regex("(.{2,12})\\1{6,}$").containsMatchIn(tail)
    }
}
