package de.fa.kuran.data

import android.content.Context
import org.json.JSONObject

data class Surah(val n: Int, val tr: String, val ar: String, val ayat: Int, val type: String, val firstIdx: Int)

data class Ayah(val idx: Int, val s: Int, val a: Int, val juz: Int, val ar: String)

/**
 * Kur'an metni + sayfalama. Sayfalama değiştirilebilir:
 * - Varsayılan: Medine mushafı (604 sayfa)
 * - Sayfa paketi yüklüyse: Hayrat tevâfuklu mushaf sayfaları
 */
class Quran(
    val surahs: List<Surah>,
    val ayahs: List<Ayah>,
    private val builtinMeal: List<String>,
    pages: List<IntRange>,
    val hayrat: Boolean,
) {
    private val pageRanges: List<IntRange> = pages
    val pageCount: Int get() = pageRanges.size
    private val pageOfAyahArr = IntArray(ayahs.size)
    val juzStartPage = IntArray(31) { 1 }

    init {
        pageRanges.forEachIndexed { i, r -> for (k in r) if (k in pageOfAyahArr.indices) pageOfAyahArr[k] = i + 1 }
        // boş kalanları önceki sayfaya bağla
        var last = 1
        for (k in pageOfAyahArr.indices) { if (pageOfAyahArr[k] == 0) pageOfAyahArr[k] = last else last = pageOfAyahArr[k] }
        val seen = BooleanArray(31)
        for (ay in ayahs) if (ay.juz in 1..30 && !seen[ay.juz]) { seen[ay.juz] = true; juzStartPage[ay.juz] = pageOfAyahArr[ay.idx] }
    }

    fun withPages(pages: List<IntRange>, hayrat: Boolean) = Quran(surahs, ayahs, builtinMeal, pages, hayrat)

    fun ayahsOnPage(p: Int): List<Ayah> = pageRanges.getOrNull(p - 1)?.map { ayahs[it] } ?: emptyList()

    fun pageOf(idx: Int): Int = pageOfAyahArr.getOrElse(idx) { 1 }

    fun surah(n: Int): Surah = surahs[(n - 1).coerceIn(0, surahs.lastIndex)]

    fun surahPage(n: Int): Int = pageOf(surah(n).firstIdx)

    fun surahsOnPage(p: Int): List<Surah> = ayahsOnPage(p).map { it.s }.distinct().map { surah(it) }

    fun juzOfPage(p: Int): Int = ayahsOnPage(p).firstOrNull()?.juz ?: 1

    /** Sayfanın kitaptaki numarası (Hayrat: Fâtiha = 0) */
    fun pageLabel(p: Int): String = if (hayrat) (if (p == 1) "Fâtiha" else "${p - 1}") else "$p"

    fun builtinMeal(idx: Int): String = builtinMeal.getOrNull(idx) ?: ""

    companion object {
        fun load(ctx: Context): Quran {
            val txt = ctx.assets.open("quran.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(txt)
            val aa = o.getJSONArray("ayahs")
            val ayahs = ArrayList<Ayah>(aa.length())
            val medina = ArrayList<Int>(aa.length())
            for (i in 0 until aa.length()) {
                val e = aa.getJSONArray(i)
                ayahs.add(Ayah(i, e.getInt(0), e.getInt(1), e.getInt(3), e.getString(4)))
                medina.add(e.getInt(2))
            }
            val firstIdx = HashMap<Int, Int>()
            ayahs.forEach { if (!firstIdx.containsKey(it.s)) firstIdx[it.s] = it.idx }
            val sa = o.getJSONArray("surahs")
            val surahs = List(sa.length()) { i ->
                val e = sa.getJSONArray(i)
                Surah(e.getInt(0), e.getString(1), e.getString(2), e.getInt(3), e.getString(4), firstIdx[e.getInt(0)] ?: 0)
            }
            val mt = o.getJSONObject("mealTexts")
            val key = if (mt.has("tr.yazir")) "tr.yazir" else mt.keys().next()
            val arr = mt.getJSONArray(key)
            val meal = List(arr.length()) { arr.getString(it) }
            // Medine sayfalaması
            val pages = ArrayList<IntRange>()
            var start = 0
            for (i in 1..ayahs.size) {
                if (i == ayahs.size || medina[i] != medina[i - 1]) { pages.add(start until i); start = i }
            }
            return Quran(surahs, ayahs, meal, pages, hayrat = false)
        }
    }
}
