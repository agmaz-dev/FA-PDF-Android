package de.fapdf.ai

import de.fapdf.data.AiProvider
import de.fapdf.data.SettingsRepository

/**
 * Automatische Ersatz-Kette: zuerst der gewählte KI-Dienst (z. B. Groq). Ist er nicht erreichbar,
 * am Limit oder fehlt der Schlüssel, antwortet Gemini Nano auf dem Handy.
 */
object AiRouter {
    data class Answer(val text: String, val provider: AiProvider, val fallback: Boolean)

    suspend fun complete(settings: SettingsRepository, system: String, messages: List<ChatMessage>, maxTokens: Int): Answer {
        val s = settings.current()
        val p = s.aiProvider
        if (p == AiProvider.NANO) return Answer(GeminiNano.complete(system, messages, maxTokens), p, false)
        var first: Exception? = null
        val key = settings.apiKey() ?: if (p == AiProvider.CUSTOM || p.isLocal) "lokal" else null
        if (key != null) {
            try {
                return Answer(AiClient(p, key, s.aiModel, s.aiBaseUrl).complete(system, messages, maxTokens), p, false)
            } catch (e: Exception) {
                first = e
            }
        } else first = AiException("Für ${p.title} ist kein Schlüssel hinterlegt.")
        return try {
            Answer(GeminiNano.complete(system, messages, maxTokens), AiProvider.NANO, true)
        } catch (e: Exception) {
            throw AiException((first?.message ?: "KI nicht erreichbar.") + "\nErsatz Gemini Nano: " + (e.message ?: "nicht verfügbar"))
        }
    }

    /** Kurzname für Hinweise („Antwort von …“). */
    fun shortName(p: AiProvider) = p.title.substringBefore(" –").substringBefore(" (")
}
