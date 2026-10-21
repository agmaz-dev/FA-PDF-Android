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
}

data class AppSettings(
    val biometricLock: Boolean = false,
    val aiProvider: AiProvider = AiProvider.ANTHROPIC,
    val aiModel: String = AiProvider.ANTHROPIC.defaultModel,
    val hasAiKey: Boolean = false,
)
