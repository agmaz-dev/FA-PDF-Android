package de.faki

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

/** Die drei wählbaren Modelle (GGUF, 4-Bit Q4_K_M). */
data class KiModel(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val fileName: String,
    val sizeGb: Double,
    /** Anzahl der Schichten (für die Aufteilung Prozessor/Grafik). */
    val layers: Int = 32,
)

object Models {
    val all = listOf(
        KiModel(
            "qwen35-4b", "Qwen 3.5 – 4B",
            "Schnell und klug genug für Briefe, Zusammenfassungen und Umformulieren. Empfohlen als Standard.",
            "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/Qwen_Qwen3.5-4B-Q4_K_M.gguf",
            "Qwen_Qwen3.5-4B-Q4_K_M.gguf", 3.0, 32,
        ),
        KiModel(
            "qwen35-4b-q40", "Qwen 3.5 – 4B (für Grafik)",
            "Dasselbe Modell im Format Q4_0, das die Adreno-Grafik schnell rechnen kann. Zum Vergleich mit dem Regler „Grafik-Anteil“.",
            "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/Qwen_Qwen3.5-4B-Q4_0.gguf",
            "Qwen_Qwen3.5-4B-Q4_0.gguf", 2.8, 32,
        ),
        KiModel(
            "qwen35-9b", "Qwen 3.5 – 9B",
            "Profi-Modus für schwierige Dokumente und Verträge. Klüger, aber langsamer und braucht mehr Speicher.",
            "https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/main/Qwen_Qwen3.5-9B-Q4_K_M.gguf",
            "Qwen_Qwen3.5-9B-Q4_K_M.gguf", 6.2, 32,
        ),
        KiModel(
            "gemma4-e4b", "Gemma 4 – E4B",
            "Googles Handy-Modell. Natürliche Antworten, gut auf Deutsch und Türkisch.",
            "https://huggingface.co/ggml-org/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
            "gemma-4-E4B-it-Q4_K_M.gguf", 5.4, 42,
        ),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun isDownloaded(context: Context, m: KiModel): Boolean {
        if (Settings.downloadId(context, m) != -1L) return false
        val f = File(dir(context), m.fileName)
        return f.exists() && f.length() > 100_000_000L
    }

    fun startDownload(context: Context, m: KiModel) {
        val dm = context.getSystemService(DownloadManager::class.java)
        File(dir(context), m.fileName).delete()
        val req = DownloadManager.Request(Uri.parse(m.url))
            .setTitle("FA KI – ${m.title}")
            .setDescription("Modell wird heruntergeladen (${m.sizeGb} GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(context, "models", m.fileName)
        Settings.setDownloadId(context, m, dm.enqueue(req))
    }

    fun cancelDownload(context: Context, m: KiModel) {
        val id = Settings.downloadId(context, m)
        if (id != -1L) context.getSystemService(DownloadManager::class.java).remove(id)
        Settings.setDownloadId(context, m, -1L)
        File(dir(context), m.fileName).delete()
    }

    fun delete(context: Context, m: KiModel) {
        cancelDownload(context, m)
        if (Settings.active(context)?.id == m.id) { Settings.setActive(context, null); Engine.unloadNow() }
        File(dir(context), m.fileName).delete()
    }

    /** Fortschritt 0..1, oder null wenn kein Download läuft. Abgeschlossene Downloads werden hier erkannt. */
    fun progress(context: Context, m: KiModel): Float? {
        val id = Settings.downloadId(context, m)
        if (id == -1L) return null
        val dm = context.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) { Settings.setDownloadId(context, m, -1L); return null }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> { Settings.setDownloadId(context, m, -1L); null }
                DownloadManager.STATUS_FAILED -> { Settings.setDownloadId(context, m, -1L); File(dir(context), m.fileName).delete(); null }
                else -> if (total > 0) done.toFloat() / total else 0f
            }
        }
    }
}

object Settings {
    private fun p(c: Context) = c.getSharedPreferences("faki", Context.MODE_PRIVATE)
    fun active(c: Context): KiModel? = Models.byId(p(c).getString("active", null))
    fun setActive(c: Context, m: KiModel?) = p(c).edit().putString("active", m?.id).apply()
    fun downloadId(c: Context, m: KiModel): Long = p(c).getLong("dl_${m.id}", -1L)
    fun setDownloadId(c: Context, m: KiModel, id: Long) = p(c).edit().putLong("dl_${m.id}", id).apply()
    /** Grafik-Anteil in Prozent (0, 25, 50, 75, 100). */
    fun gpuPercent(c: Context): Int = p(c).getInt("gpu", 0)
    fun setGpuPercent(c: Context, v: Int) = p(c).edit().putInt("gpu", v).apply()
    fun serverOn(c: Context): Boolean = p(c).getBoolean("server", true)
    fun setServerOn(c: Context, on: Boolean) = p(c).edit().putBoolean("server", on).apply()
}
