package de.fa.kuran.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ReadMode { MEAL, ACIKLAMA, MUSHAF }

/** Uygulama ayarları; değişiklikler hemen kaydedilir. */
class AppState(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kuran", Context.MODE_PRIVATE)

    private val _welcomed = mutableStateOf(sp.getBoolean("welcomed2", false))
    var welcomed: Boolean
        get() = _welcomed.value
        set(v) { _welcomed.value = v; sp.edit().putBoolean("welcomed2", v).apply() }

    /** "hayrat" veya "elmalili" */
    private val _lang = mutableStateOf(sp.getString("lang", "tr") ?: "tr").also { de.fa.kuran.ui.L.lang = it.value }
    var lang: String
        get() = _lang.value
        set(v) { _lang.value = v; de.fa.kuran.ui.L.lang = v; sp.edit().putString("lang", v).apply() }

    private val _reciter = mutableStateOf(sp.getString("reciter", "sudais") ?: "sudais").also { Audio.reciter = it.value }
    var reciter: String
        get() = _reciter.value
        set(v) { _reciter.value = v; Audio.reciter = v; sp.edit().putString("reciter", v).apply() }

    private val _audioMode = mutableStateOf(sp.getString("audioMode", "ar") ?: "ar").also { Audio.mode = it.value }
    var audioMode: String
        get() = _audioMode.value
        set(v) { _audioMode.value = v; Audio.mode = v; sp.edit().putString("audioMode", v).apply() }

    init {
        Speech.voiceName = sp.getString("ttsVoice", null)
        Speech.pitch = sp.getFloat("ttsPitch", 0.8f)
        Speech.rate = sp.getFloat("ttsRate", 0.95f)
    }
    fun saveSpeech() { sp.edit().putString("ttsVoice", Speech.voiceName).putFloat("ttsPitch", Speech.pitch).putFloat("ttsRate", Speech.rate).apply() }

    private val _meal = mutableStateOf(sp.getString("meal2", "hayrat") ?: "hayrat")
    var meal: String
        get() = _meal.value
        set(v) { _meal.value = v; sp.edit().putString("meal2", v).apply() }

    /** Arapça yazı: "hayrat" (kitap sayfası), "amiri", "scheherazade", "naskh" */
    private val _font = mutableStateOf(sp.getString("font2", "hayrat") ?: "hayrat")
    var font: String
        get() = _font.value
        set(v) { _font.value = v; sp.edit().putString("font2", v).apply() }

    /** "krem", "beyaz", "gece" */
    private val _theme = mutableStateOf(sp.getString("theme", "krem") ?: "krem")
    var theme: String
        get() = _theme.value
        set(v) { _theme.value = v; sp.edit().putString("theme", v).apply() }

    private val _mode = mutableStateOf(
        runCatching { ReadMode.valueOf(sp.getString("mode2", "MEAL") ?: "MEAL") }.getOrDefault(ReadMode.MEAL)
    )
    var mode: ReadMode
        get() = _mode.value
        set(v) { _mode.value = v; sp.edit().putString("mode2", v.name).apply() }

    private val _arZoom = mutableFloatStateOf(sp.getFloat("arZoom", 1f))
    var arZoom: Float
        get() = _arZoom.floatValue
        set(v) { _arZoom.floatValue = v; sp.edit().putFloat("arZoom", v).apply() }

    /** Arapça harflerin inceltilmesi: 0 = yazı tipinin kendisi, 1–3 = gittikçe ince */
    private val _thin = mutableIntStateOf(sp.getInt("thin", 2))
    var thin: Int
        get() = _thin.intValue
        set(v) { _thin.intValue = v; sp.edit().putInt("thin", v).apply() }

    private val _mealZoom = mutableFloatStateOf(sp.getFloat("mealZoom", 1f))
    var mealZoom: Float
        get() = _mealZoom.floatValue
        set(v) { _mealZoom.floatValue = v; sp.edit().putFloat("mealZoom", v).apply() }

    private val _lastPage = mutableIntStateOf(sp.getInt("lastPage2", 1))
    var lastPage: Int
        get() = _lastPage.intValue
        set(v) { _lastPage.intValue = v; sp.edit().putInt("lastPage2", v).apply() }

    private val _bookmarks = mutableStateOf(
        (sp.getStringSet("bookmarks2", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    )
    val bookmarks: Set<Int> get() = _bookmarks.value

    fun toggleBookmark(page: Int) {
        val s = _bookmarks.value.toMutableSet()
        if (!s.add(page)) s.remove(page)
        _bookmarks.value = s
        sp.edit().putStringSet("bookmarks2", s.map { it.toString() }.toSet()).apply()
    }

    private val _packUri = mutableStateOf(sp.getString("packUri", null))
    var packUri: String?
        get() = _packUri.value
        set(v) { _packUri.value = v; sp.edit().putString("packUri", v).apply() }

    /** Yüklü sayfa paketi */
    var pack by mutableStateOf<Pack?>(null)
    var packLoading by mutableStateOf(false)
    var packVersion by mutableStateOf(0)
    var importInfo by mutableStateOf<String?>(null)

    /** Fihristten gelen sayfa atlama isteği */
    var jump by mutableStateOf<Int?>(null)
}
