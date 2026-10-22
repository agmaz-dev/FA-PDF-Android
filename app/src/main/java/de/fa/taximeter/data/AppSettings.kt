package de.fa.taximeter.data

import android.content.Context
import android.util.Log
import de.fa.taximeter.domain.persist.Codecs
import de.fa.taximeter.domain.tariff.TariffCatalog
import de.fa.taximeter.domain.tariff.TariffProfile
import de.fa.taximeter.domain.trip.SurchargeDef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class OrientationMode(val label: String) { AUTO("Automatisch"), PORTRAIT("Hochformat"), LANDSCAPE("Querformat") }

data class Settings(
    val orientation: OrientationMode = OrientationMode.AUTO,
    val keepScreenOn: Boolean = true,
    val testMode: Boolean = false,
    /** Benutzerdefinierte Tarifkopien je Anzeige-Nummer (3, 4). */
    val customTariffs: Map<Int, TariffProfile> = emptyMap(),
    /** Ob die benutzerdefinierte Kopie statt des amtlichen Profils verwendet wird. */
    val useCustom: Set<Int> = emptySet(),
    val customSurcharges: List<SurchargeDef> = emptyList(),
    val lastTariffNumber: Int = 4,
) {
    /** Profil, das für eine neue Fahrt mit dieser Anzeige-Nummer gilt. */
    fun profileFor(number: Int): TariffProfile {
        val official = TariffCatalog.officialByNumber(number) ?: TariffCatalog.BERLIN
        val custom = customTariffs[number]
        return if (number in useCustom && custom != null) custom else official
    }
}

/** Einstellungen in SharedPreferences. Amtliche Ausgangsprofile werden hier nie gespeichert oder überschrieben. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings
    val current: Settings get() = _settings.value

    private fun load(): Settings {
        val customs = mutableMapOf<Int, TariffProfile>()
        for (n in listOf(3, 4)) {
            prefs.getString("customTariff_$n", null)?.let {
                try {
                    customs[n] = Codecs.tariffFromJson(it)
                } catch (e: Exception) {
                    Log.e("AppSettings", "Benutzerdefinierter Tarif $n unlesbar", e)
                }
            }
        }
        val surcharges = prefs.getString("customSurcharges", null)?.let {
            try {
                Codecs.surchargesFromJson(it)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
        return Settings(
            orientation = runCatching { OrientationMode.valueOf(prefs.getString("orientation", "AUTO")!!) }
                .getOrDefault(OrientationMode.AUTO),
            keepScreenOn = prefs.getBoolean("keepScreenOn", true),
            testMode = prefs.getBoolean("testMode", false),
            customTariffs = customs,
            useCustom = listOf(3, 4).filter { prefs.getBoolean("useCustom_$it", false) }.toSet(),
            customSurcharges = surcharges,
            lastTariffNumber = prefs.getInt("lastTariffNumber", 4),
        )
    }

    private fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        _settings.value = s
        val e = prefs.edit()
        e.putString("orientation", s.orientation.name)
        e.putBoolean("keepScreenOn", s.keepScreenOn)
        e.putBoolean("testMode", s.testMode)
        for (n in listOf(3, 4)) {
            val c = s.customTariffs[n]
            if (c != null) e.putString("customTariff_$n", Codecs.tariffToJson(c)) else e.remove("customTariff_$n")
            e.putBoolean("useCustom_$n", n in s.useCustom)
        }
        e.putString("customSurcharges", Codecs.surchargesToJson(s.customSurcharges))
        e.putInt("lastTariffNumber", s.lastTariffNumber)
        e.apply()
    }

    fun setOrientation(o: OrientationMode) = update { it.copy(orientation = o) }
    fun setKeepScreenOn(v: Boolean) = update { it.copy(keepScreenOn = v) }
    fun setTestMode(v: Boolean) = update { it.copy(testMode = v) }
    fun setLastTariff(n: Int) = update { it.copy(lastTariffNumber = n) }

    fun saveCustomTariff(number: Int, profile: TariffProfile) =
        update { it.copy(customTariffs = it.customTariffs + (number to profile)) }

    fun removeCustomTariff(number: Int) =
        update { it.copy(customTariffs = it.customTariffs - number, useCustom = it.useCustom - number) }

    fun setUseCustom(number: Int, use: Boolean) =
        update { it.copy(useCustom = if (use) it.useCustom + number else it.useCustom - number) }

    fun addCustomSurcharge(def: SurchargeDef) =
        update { it.copy(customSurcharges = it.customSurcharges.filterNot { s -> s.id == def.id } + def) }

    fun removeCustomSurcharge(id: String) =
        update { it.copy(customSurcharges = it.customSurcharges.filterNot { s -> s.id == id }) }
}
