package de.fapdf.ai

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import de.fapdf.data.AiProvider
import de.fapdf.pdf.OcrEngine
import de.fapdf.pdf.PageAnalyzer
import de.fapdf.pdf.PageRaster
import de.fapdf.pdf.PdfIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val role: String, val content: String) // role: "user" | "assistant"

class AiException(message: String) : Exception(message)

/**
 * Minimaler Client für Cloud-KI. Es gibt KEINEN eingebauten Schlüssel: Der Benutzer hinterlegt
 * seinen eigenen API-Schlüssel (verschlüsselt im Android Keystore). Jede Übertragung erfolgt
 * erst nach ausdrücklicher Zustimmung in der Oberfläche.
 */
class AiClient(private val provider: AiProvider, private val apiKey: String, private val model: String) {

    suspend fun complete(system: String, messages: List<ChatMessage>, maxTokens: Int = 2048): String = withContext(Dispatchers.IO) {
        if (model.isBlank()) throw AiException("Bitte in den Einstellungen einen Modellnamen eintragen.")
        when (provider) {
            AiProvider.ANTHROPIC -> anthropic(system, messages, maxTokens)
            AiProvider.OPENAI -> openai(system, messages, maxTokens)
        }
    }

    private fun anthropic(system: String, messages: List<ChatMessage>, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
        val json = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body,
        )
        val content = json.optJSONArray("content") ?: throw AiException("Unerwartete Antwort des Dienstes.")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val part = content.getJSONObject(i)
            if (part.optString("type") == "text") sb.append(part.optString("text"))
        }
        return sb.toString().trim()
    }

    private fun openai(system: String, messages: List<ChatMessage>, maxTokens: Int): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", model).put("messages", msgs).put("max_completion_tokens", maxTokens)
        val json = post("https://api.openai.com/v1/chat/completions", mapOf("Authorization" to "Bearer $apiKey"), body)
        val choices = json.optJSONArray("choices") ?: throw AiException("Unerwartete Antwort des Dienstes.")
        return choices.getJSONObject(0).getJSONObject("message").optString("content").trim()
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                throw AiException(
                    when (code) {
                        401, 403 -> "Der API-Schlüssel wurde abgelehnt (HTTP $code). Bitte in den Einstellungen prüfen."
                        429 -> "Zu viele Anfragen oder Guthaben erschöpft (HTTP 429). ${msg ?: ""}"
                        else -> "Fehler des KI-Dienstes (HTTP $code): ${msg ?: text.take(200)}"
                    }
                )
            }
            return JSONObject(text)
        } catch (e: java.io.IOException) {
            throw AiException("Keine Verbindung zum KI-Dienst: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Grobe Schätzung (ca. 4 Zeichen pro Token bei europäischen Sprachen). */
        fun estimateTokens(chars: Int) = (chars / 4) + 1
    }
}

/** KI-Übersetzung vieler Absätze in wenigen Anfragen; jeder Absatz behält seine Nummer. */
object AiBatchTranslator {
    private val marker = Regex("<<(\\d+)>>")

    suspend fun translateAll(client: AiClient, texts: List<String>, target: Lang, onProgress: (Int, Int) -> Unit): List<String> {
        val result = texts.toMutableList()
        // Pakete von ca. 5000 Zeichen, damit die Antwort sicher vollständig zurückkommt
        val batches = ArrayList<List<Int>>()
        var cur = ArrayList<Int>(); var len = 0
        texts.indices.forEach { i ->
            if (len > 5000 && cur.isNotEmpty()) { batches.add(cur); cur = ArrayList(); len = 0 }
            cur.add(i); len += texts[i].length
        }
        if (cur.isNotEmpty()) batches.add(cur)
        batches.forEachIndexed { b, idx ->
            onProgress(b + 1, batches.size)
            val input = idx.joinToString("\n\n") { "<<$it>> ${texts[it]}" }
            val system = "Übersetze jeden nummerierten Abschnitt vollständig und sinngetreu ins ${target.inSentence}. " +
                "Antworte ausschließlich im selben Format: jede Übersetzung beginnt mit ihrer Markierung <<n>>. " +
                "Keine Erklärungen, keine zusätzlichen Abschnitte."
            val answer = client.complete(system, listOf(ChatMessage("user", input)), maxTokens = 8000)
            val matches = marker.findAll(answer).toList()
            matches.forEachIndexed { k, m ->
                val n = m.groupValues[1].toIntOrNull() ?: return@forEachIndexed
                val end = if (k + 1 < matches.size) matches[k + 1].range.first else answer.length
                val t = answer.substring(m.range.last + 1, end).trim()
                if (n in result.indices && t.isNotEmpty()) result[n] = t
            }
        }
        return result
    }
}

object Prompts {
    const val CHAT = "Du bist ein sorgfältiger Assistent für Dokumente. Beantworte Fragen ausschließlich anhand des " +
        "folgenden Dokumentinhalts. Wenn die Antwort nicht im Dokument steht, sage das ausdrücklich und erfinde nichts. " +
        "Nenne, wo möglich, die Seitenzahl (Format: „Seite N“). Antworte in der Sprache der Frage."

    const val SUMMARY = "Fasse das folgende Dokument auf Deutsch zusammen. Verwende genau diese Abschnitte mit Überschriften:\n" +
        "1. Worum geht es (2–3 Sätze)\n2. Wichtigste Aussagen\n3. Termine und Daten\n4. Beträge\n5. Fristen\n" +
        "6. Erforderliche Maßnahmen\nWenn ein Abschnitt im Dokument nicht vorkommt, schreibe „Keine Angaben im Dokument“. " +
        "Erfinde nichts. Gib bei Fristen und Beträgen die Seitenzahl an."

    const val QUESTIONS = "Erstelle auf Grundlage des folgenden Dokuments 10 Verständnisfragen mit kurzer Musterantwort. " +
        "Format: „Frage N: …“ und darunter „Antwort: … (Seite X)“. Nur Inhalte aus dem Dokument verwenden."

    fun translate(target: String) = "Übersetze den folgenden Text vollständig und sinngetreu ins $target. " +
        "Behalte Absätze und Seitenmarkierungen ([Seite N]) bei. Gib nur die Übersetzung aus."

    fun withDocument(base: String, doc: String) = "$base\n\n<dokument>\n$doc\n</dokument>"
}

/** Extrahiert den tatsächlichen Text eines Dokuments – auf dem Gerät, bei Scans per OCR. */
object DocumentText {
    data class Result(val text: String, val pages: Int, val ocrPages: Int, val truncated: Boolean)

    suspend fun extract(file: File, tmpDir: File, maxChars: Int = 150_000, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val sb = StringBuilder()
        var ocrPages = 0
        var pages = 0
        var truncated = false
        val ocr = OcrEngine()
        try {
            // Text aller Seiten in einem Durchgang lesen (Dokument nur einmal öffnen)
            val texts = withContext(PdfIo.pdfDispatcher) {
                PdfIo.load(file).use { doc -> (0 until doc.numberOfPages).map { PageAnalyzer.analyze(doc, it).plainText() } }
            }
            pages = texts.size
            for (i in texts.indices) {
                onProgress(i + 1, texts.size)
                var text = texts[i]
                if (text.trim().length < 20) {
                    val bmp = withContext(Dispatchers.IO) { PageRaster.render(file, i, OcrEngine.OCR_SCALE, tmpDir) }
                    val res = ocr.recognize(i, bmp, OcrEngine.OCR_SCALE)
                    bmp.recycle()
                    if (res.lines.isNotEmpty()) { text = res.lines.joinToString("\n") { it.text }; ocrPages++ }
                }
                sb.append("[Seite ${i + 1}]\n").append(text.trim()).append("\n\n")
                if (sb.length > maxChars) { truncated = true; break }
            }
        } finally {
            ocr.close()
        }
        val out = if (sb.length > maxChars) sb.substring(0, maxChars) else sb.toString()
        return Result(out, pages, ocrPages, truncated)
    }
}

enum class Lang(val title: String, val mlKit: String, val inSentence: String) {
    DE("Deutsch", TranslateLanguage.GERMAN, "Deutsche"),
    EN("Englisch", TranslateLanguage.ENGLISH, "Englische"),
    TR("Türkisch", TranslateLanguage.TURKISH, "Türkische"),
}

/** Übersetzung auf dem Gerät mit ML Kit – kostenlos, Dokumente verlassen das Gerät nicht. */
class OfflineTranslator(@Suppress("unused") private val context: Context) {
    /** Übersetzt jeden Absatz einzeln (ganze Sätze → deutlich bessere Qualität als Zeile für Zeile). */
    suspend fun translateAll(texts: List<String>, from: Lang, to: Lang, onProgress: (Int, Int) -> Unit): List<String> {
        if (from == to) return texts
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(from.mlKit).setTargetLanguage(to.mlKit).build()
        )
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return texts.mapIndexed { i, t ->
                onProgress(i + 1, texts.size)
                runCatching { translator.translate(t).await() }.getOrDefault(t)
            }
        } finally {
            translator.close()
        }
    }

    suspend fun translate(text: String, from: Lang, to: Lang, onProgress: (Int, Int) -> Unit): String {
        if (from == to) return text
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(from.mlKit).setTargetLanguage(to.mlKit).build()
        )
        try {
            // Einmaliger Download des Sprachmodells (ca. 30 MB) – enthält keine Dokumentdaten
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val paragraphs = text.split("\n")
            val out = StringBuilder()
            paragraphs.forEachIndexed { i, p ->
                onProgress(i + 1, paragraphs.size)
                if (p.isBlank() || p.matches(Regex("\\[Seite \\d+]"))) out.append(p).append('\n')
                else out.append(translator.translate(p).await()).append('\n')
            }
            return out.toString()
        } finally {
            translator.close()
        }
    }
}
