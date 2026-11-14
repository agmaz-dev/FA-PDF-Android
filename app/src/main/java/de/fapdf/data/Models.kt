package de.fapdf.data

/** Entschlüsseltes Personenprofil (existiert nur im Arbeitsspeicher). */
data class PersonProfile(
    val id: Long = 0,
    val label: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val birthPlace: String = "",
    val street: String = "",
    val houseNumber: String = "",
    val zip: String = "",
    val city: String = "",
    val country: String = "",
    val phone: String = "",
    val email: String = "",
    val custom: List<CustomValue> = emptyList(),
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val streetLine: String get() = listOf(street, houseNumber).filter { it.isNotBlank() }.joinToString(" ")
    val zipCity: String get() = listOf(zip, city).filter { it.isNotBlank() }.joinToString(" ")
    val fullAddress: String get() = listOf(streetLine, zipCity).filter { it.isNotBlank() }.joinToString(", ")
}

data class CustomValue(val key: String, val value: String)

enum class AiProvider(val title: String, val endpointHost: String, val pricingUrl: String, val defaultModel: String) {
    ANTHROPIC("Anthropic (Claude)", "api.anthropic.com", "https://www.anthropic.com/pricing", "claude-haiku-5-5"),
    OPENAI("OpenAI", "api.openai.com", "https://openai.com/api/pricing/", ""),
    GROQ("Groq – Internet, kostenlos", "api.groq.com", "https://console.groq.com/keys", "openai/gpt-oss-120b"),
    PC("PC zu Hause – privat", "eigener Rechner mit Ollama · Adresse eintragen", "", "gpt-oss:20b"),
    NANO("Gemini Nano – auf diesem Handy, privat", "ohne Internet · in Android eingebaut", "", "gemini-nano"),
    GEMMA("Gemma 4 – auf diesem Handy, lange Dokumente", "ohne Internet · einmal 2,6 oder 3,7 GB laden", "", "gemma-4"),
    FAKI("FA KI – auf diesem Handy, privat", "ohne Internet · App FA KI muss laufen", "", "lokal"),
    /** Beliebiger OpenAI-kompatibler Dienst mit eigener Adresse, z. B. Groq (kostenloser Zugang). */
    CUSTOM("Eigene Adresse", "andere OpenAI-kompatible Dienste", "https://console.groq.com/keys", "openai/gpt-oss-120b");

    /** Läuft auf dem Handy – kein Schlüssel, keine Zustimmung zur Übertragung nötig. */
    val isLocal: Boolean get() = this == FAKI || this == NANO || this == PC || this == GEMMA
}

enum class ThemeMode(val title: String) { SYSTEM("Wie System"), LIGHT("Hell"), DARK("Dunkel") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val biometricLock: Boolean = false,
    val aiProvider: AiProvider = AiProvider.NANO,
    val aiModel: String = AiProvider.NANO.defaultModel,
    /** Der gewählte KI-Dienst ist einsatzbereit (Schlüssel vorhanden bzw. FA KI braucht keinen). */
    val hasAiKey: Boolean = false,
    /** Für welche Dienste ein Schlüssel gespeichert ist. */
    val savedKeys: Set<AiProvider> = emptySet(),
    val aiBaseUrl: String = DEFAULT_AI_BASE_URL,
    /** Eigener PC mit Ollama (OpenAI-kompatibel), z. B. http://192.168.178.20:11434/v1 */
    val pcUrl: String = "",
    val pcModel: String = "gpt-oss:20b",
)

const val DEFAULT_AI_BASE_URL = "https://api.groq.com/openai/v1"
