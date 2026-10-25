package de.fa.kuran.data

import android.content.Context
import org.json.JSONObject

data class Surah(val n: Int, val tr: String, val ar: String, val ayat: Int, val type: String, val page: Int)

data class Ayah(val idx: Int, val s: Int, val a: Int, val page: Int, val juz: Int, val ar: String)

data class MealInfo(val id: String, val name: String)

class Quran(
    val surahs: List<Surah>,
    val ayahs: List<Ayah>,
    val meals: List<MealInfo>,
    private val mealTexts: Map<String, List<String>>,
) {
    val pageCount = 604
    private val pageRanges: Array<IntRange>
    val juzStartPage = IntArray(31) { 1 }

    init {
        val first = IntArray(pageCount + 1) { -1 }
        val last = IntArray(pageCount + 1) { -1 }
        for (ay in ayahs) {
            if (ay.page in 1..pageCount) {
                if (first[ay.page] == -1) first[ay.page] = ay.idx
                last[ay.page] = ay.idx
            }
        }
        pageRanges = Array(pageCount + 1) { p -> if (p == 0 || first[p] == -1) IntRange.EMPTY else first[p]..last[p] }
        val seen = BooleanArray(31)
        for (ay in ayahs) {
            if (ay.juz in 1..30 && !seen[ay.juz]) {
                seen[ay.juz] = true
                juzStartPage[ay.juz] = ay.page
            }
        }
    }

    fun ayahsOnPage(p: Int): List<Ayah> = pageRanges.getOrNull(p)?.map { ayahs[it] } ?: emptyList()

    fun surah(n: Int): Surah = surahs[(n - 1).coerceIn(0, surahs.lastIndex)]

    fun surahsOnPage(p: Int): List<Surah> = ayahsOnPage(p).map { it.s }.distinct().map { surah(it) }

    fun juzOfPage(p: Int): Int = ayahsOnPage(p).firstOrNull()?.juz ?: 1

    fun mealId(preferred: String): String =
        if (meals.any { it.id == preferred }) preferred else meals.firstOrNull()?.id ?: ""

    fun meal(id: String, idx: Int): String = (mealTexts[id] ?: mealTexts.values.firstOrNull())?.getOrNull(idx) ?: ""

    fun mealList(id: String): List<String> = mealTexts[id] ?: mealTexts.values.firstOrNull() ?: emptyList()

    companion object {
        fun load(ctx: Context): Quran {
            val txt = ctx.assets.open("quran.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(txt)
            val sa = o.getJSONArray("surahs")
            val surahs = List(sa.length()) { i ->
                val e = sa.getJSONArray(i)
                Surah(e.getInt(0), e.getString(1), e.getString(2), e.getInt(3), e.getString(4), e.getInt(5))
            }
            val aa = o.getJSONArray("ayahs")
            val ayahs = ArrayList<Ayah>(aa.length())
            for (i in 0 until aa.length()) {
                val e = aa.getJSONArray(i)
                ayahs.add(Ayah(i, e.getInt(0), e.getInt(1), e.getInt(2), e.getInt(3), e.getString(4)))
            }
            val ma = o.getJSONArray("meals")
            val meals = List(ma.length()) { i ->
                val e = ma.getJSONObject(i)
                MealInfo(e.getString("id"), e.getString("name"))
            }
            val mt = o.getJSONObject("mealTexts")
            val texts = meals.associate { m ->
                val arr = mt.getJSONArray(m.id)
                m.id to List(arr.length()) { arr.getString(it) }
            }
            return Quran(surahs, ayahs, meals, texts)
        }
    }
}
