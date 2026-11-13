package de.fapdf.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/**
 * Gemini Nano – die in Android (AICore) eingebaute KI. Läuft komplett auf dem Handy,
 * ohne Schlüssel und ohne Internet. Kann nur kurze Eingaben (< 4000 Tokens) verarbeiten.
 */
object GeminiNano {
    /** Etwa 3 Zeichen pro Token bei deutschem Text; Rest für Anweisung und Antwort. */
    private const val MAX_INPUT_CHARS = 8_500

    private var model: GenerativeModel? = null
    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }

    /** Lesbarer Zustand für die Einstellungen. */
    suspend fun statusText(): String = try {
        when (client().checkStatus()) {
            FeatureStatus.AVAILABLE -> "Gemini Nano ist bereit ✓" + runCatching { " (" + client().getBaseModelName() + ")" }.getOrDefault("")
            FeatureStatus.DOWNLOADABLE -> "Gemini Nano ist verfügbar und wird bei der ersten Anfrage einmalig von Android geladen."
            FeatureStatus.DOWNLOADING -> "Gemini Nano wird gerade von Android geladen – bitte kurz warten."
            else -> "Gemini Nano ist auf diesem Handy derzeit nicht verfügbar. Tipp: Die App „AICore“ im Play Store aktualisieren und das Handy neu starten."
        }
    } catch (e: Exception) {
        "Gemini Nano konnte nicht geprüft werden: ${e.message}"
    }

    private suspend fun ensureReady() {
        when (client().checkStatus()) {
            FeatureStatus.AVAILABLE -> return
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                var failed: String? = null
                client().download().collect { st ->
                    if (st is DownloadStatus.DownloadFailed) failed = st.e.message ?: "unbekannter Fehler"
                }
                failed?.let { throw AiException("Gemini Nano konnte nicht geladen werden: $it") }
            }
            else -> throw AiException(
                "Gemini Nano ist auf diesem Handy derzeit nicht verfügbar. Bitte die App „AICore“ im Play Store aktualisieren " +
                    "oder in den Einstellungen einen anderen KI-Dienst wählen."
            )
        }
    }

    suspend fun complete(system: String, messages: List<ChatMessage>, maxTokens: Int): String {
        ensureReady()
        // Alles in eine Eingabe packen; zu lange Dokumente auf den Anfang kürzen
        val convo = messages.joinToString("\n\n") { (if (it.role == "assistant") "Antwort: " else "") + it.content }
        var prompt = "$system\n\n$convo"
        var cut = false
        if (prompt.length > MAX_INPUT_CHARS) {
            prompt = prompt.take(MAX_INPUT_CHARS - 200) + "\n</dokument>\n\nAntworte auf Deutsch, falls nicht anders verlangt."
            cut = true
        }
        // Gemini Nano schreibt höchstens 256 Tokens pro Aufruf – längere Antworten in Stücken fortsetzen
        val rounds = (maxTokens / 256).coerceIn(1, 8)
        val sb = StringBuilder()
        for (r in 0 until rounds) {
            val input = if (r == 0) prompt else
                prompt.take(MAX_INPUT_CHARS - sb.length.coerceAtMost(2500) - 200) +
                    "\n\nBisherige Antwort:\n" + sb.takeLast(2500) +
                    "\n\nSetze die Antwort genau an dieser Stelle fort, ohne Wiederholung. Wenn sie schon vollständig ist, antworte nur mit ENDE."
            val part = AiText.clean(generate(input))
            if (part.isEmpty() || part.startsWith("ENDE")) break
            // Nano beginnt manchmal von vorn: Wiederholung erkennen und aufhören
            if (r > 0) {
                val h = AiText.head(part)
                if (h.length >= 15 && AiText.head(sb.toString(), 4000).contains(h)) break
            }
            if (sb.isNotEmpty()) sb.append(if (part.first().isLetterOrDigit() && !sb.last().isWhitespace()) " " else "")
            sb.append(part.removeSuffix("ENDE").trimEnd())
            // Fertig erst, wenn der Text mit einem Satzende aufhört – sonst mitten im Satz weiterschreiben lassen
            if (part.trimEnd().last() in ".!?)“\"" && (part.length < 600 || r == rounds - 1)) break
            if (part.trimEnd().last() in ".!?)“\"" && sb.contains("Grüß")) break
        }
        val text = AiText.clean(sb.toString())
        if (text.isEmpty()) throw AiException("Gemini Nano hat keine Antwort geliefert.")
        return if (cut) "$text\n\n(Hinweis: Gemini Nano kann nur kurze Texte lesen – berücksichtigt wurde nur der Anfang des Dokuments.)" else text
    }

    private suspend fun generate(prompt: String): String {
        val response = try {
            client().generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.2f
                    topK = 10
                    maxOutputTokens = 256
                }
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            throw AiException(
                when {
                    msg.contains("quota", true) || msg.contains("BUSY", true) ->
                        "Gemini Nano ist gerade ausgelastet (Android-Limit pro App). Bitte gleich noch einmal versuchen."
                    msg.contains("foreground", true) -> "Gemini Nano arbeitet nur, solange FA PDF im Vordergrund ist."
                    else -> "Gemini Nano: $msg"
                }
            )
        }
        return response.candidates.firstOrNull()?.text.orEmpty()
    }
}
