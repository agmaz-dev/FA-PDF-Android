package de.fa.kuran.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ReadMode { MEAL, TEFSIR, MUSHAF }

/** Uygulama ayarları; değişiklikler hemen kaydedilir. */
class AppState(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kuran", Context.MODE_PRIVATE)

    private val _tefsir = mutableStateOf(sp.getString("tefsir", null))
    var tefsir: String?
        get() = _tefsir.value
        set(v) { _tefsir.value = v; sp.edit().putString("tefsir", v).apply() }

    private val _meal = mutableStateOf(sp.getString("meal", "") ?: "")
    var meal: String
        get() = _meal.value
        set(v) { _meal.value = v; sp.edit().putString("meal", v).apply() }

    /** "hat", "amiri", "scheherazade", "naskh" */
    private val _font = mutableStateOf(sp.getString("font", "amiri") ?: "amiri")
    var font: String
        get() = _font.value
        set(v) { _font.value = v; sp.edit().putString("font", v).apply() }

    /** "krem", "beyaz", "gece" */
    private val _theme = mutableStateOf(sp.getString("theme", "krem") ?: "krem")
    var theme: String
        get() = _theme.value
        set(v) { _theme.value = v; sp.edit().putString("theme", v).apply() }

    private val _mode = mutableStateOf(
        runCatching { ReadMode.valueOf(sp.getString("mode", "MEAL") ?: "MEAL") }.getOrDefault(ReadMode.MEAL)
    )
    var mode: ReadMode
        get() = _mode.value
        set(v) { _mode.value = v; sp.edit().putString("mode", v.name).apply() }

    private val _arZoom = mutableFloatStateOf(sp.getFloat("arZoom", 1f))
    var arZoom: Float
        get() = _arZoom.floatValue
        set(v) { _arZoom.floatValue = v; sp.edit().putFloat("arZoom", v).apply() }

    private val _mealZoom = mutableFloatStateOf(sp.getFloat("mealZoom", 1f))
    var mealZoom: Float
        get() = _mealZoom.floatValue
        set(v) { _mealZoom.floatValue = v; sp.edit().putFloat("mealZoom", v).apply() }

    private val _lastPage = mutableIntStateOf(sp.getInt("lastPage", 1))
    var lastPage: Int
        get() = _lastPage.intValue
        set(v) { _lastPage.intValue = v; sp.edit().putInt("lastPage", v).apply() }

    private val _bookmarks = mutableStateOf(
        (sp.getStringSet("bookmarks", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    )
    val bookmarks: Set<Int> get() = _bookmarks.value

    fun toggleBookmark(page: Int) {
        val s = _bookmarks.value.toMutableSet()
        if (!s.add(page)) s.remove(page)
        _bookmarks.value = s
        sp.edit().putStringSet("bookmarks", s.map { it.toString() }.toSet()).apply()
    }

    private val _scanUri = mutableStateOf(sp.getString("scanUri", null))
    var scanUri: String?
        get() = _scanUri.value
        set(v) { _scanUri.value = v; sp.edit().putString("scanUri", v).apply() }

    /** Taranmış mushaf sayfaları: sayfa numarası -> dosya */
    var scans by mutableStateOf<Map<Int, Uri>>(emptyMap())

    /** Fihristten gelen sayfa atlama isteği */
    var jump by mutableStateOf<Int?>(null)
}
