package de.fapdf.ai

import de.fapdf.data.AiProvider
import de.fapdf.data.SettingsRepository

/**
 * Automatische Ersatz-Kette: zuerst der gewählte KI-Dienst, dann (falls eingerichtet) der eigene PC,
 * dann Groq (falls Schlüssel vorhanden), zuletzt Gemini Nano auf dem Handy.
 */
object AiRouter {
    data class Answer(val text: String, val provider: AiProvider, val fallback: Boolean)

    suspend fun complete(settings: SettingsRepository, system: String, messages: List<ChatMessage>, maxTokens: Int, onDelta: ((String) -> Unit)? = null): Answer {
        val s = settings.current()
        val primary = s.aiProvider
        val order = LinkedHashSet<AiProvider>().apply {
            add(primary)
            if (primary == AiProvider.GEMMA) add(AiProvider.NANO)
            if (s.pcUrl.isNotBlank()) add(AiProvider.PC)
            if (AiProvider.GROQ in s.savedKeys) add(AiProvider.GROQ)
            add(AiProvider.NANO)
        }
        val errors = ArrayList<String>()
        for (p in order) {
            try {
                val text = when (p) {
                    AiProvider.NANO -> GeminiNano.complete(system, messages, maxTokens)
                    AiProvider.GEMMA -> GemmaLocal.complete(GemmaLocal.app, system, messages, maxTokens, onDelta)
                    AiProvider.PC -> AiClient(p, "ollama", s.pcModel, s.pcUrl).complete(system, messages, maxTokens, onDelta)
                    else -> {
                        val key = settings.apiKey(p) ?: if (p == AiProvider.CUSTOM || p.isLocal) "lokal" else null
                            ?: throw AiException("Für ${shortName(p)} ist kein Schlüssel hinterlegt.")
                        val model = if (p == primary) s.aiModel else p.defaultModel
                        AiClient(p, key, model, s.aiBaseUrl).complete(system, messages, maxTokens, onDelta)
                    }
                }
                return Answer(text, p, p != primary)
            } catch (e: Exception) {
                errors += "${shortName(p)}: ${e.message ?: "Fehler"}"
            }
        }
        throw AiException(errors.joinToString("\n"))
    }

    /** Kurzname für Hinweise („Antwort von …“). */
    fun shortName(p: AiProvider) = p.title.substringBefore(" –").substringBefore(" (")
}
