package de.fa.kuran.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

data class Hasiye(val h: Int, val n: Int, val ayah: Int?, val text: String)

/**
 * Kişisel sayfa paketi (telefonda bir klasörde):
 *   h000.webp … h604.webp   – Hayrat tevâfuklu mushaf sayfaları
 *   hayrat_meal.json         – Hayrat Muhtasar Meâli (âyet âyet) + sayfa tablosu
 *   hasiyeler.json           – Risale-i Nur hâşiyeleri
 *   elmalili.json            – Elmalılı Hamdi Yazır meali
 * Paket uygulamanın içinde değildir; sadece kullanıcının telefonunda durur.
 */
class Pack(
    val images: Map<Int, Uri>,
    val pages: List<IntRange>?,
    val hayratMeal: List<String>?,
    val hayratGroup: List<List<Int>>?,
    val hasiyeler: List<Hasiye>,
    val elmalili: Map<Int, String>,
    val elmaliliIntro: Map<Int, String>,
) {
    private val hasByAyah = hasiyeler.filter { it.ayah != null }.groupBy { it.ayah!! }
    private val hasByPage = hasiyeler.groupBy { it.h }

    fun hasiyeForAyah(idx: Int): List<Hasiye> = hasByAyah[idx] ?: emptyList()
    fun hasiyeForHPage(h: Int): List<Hasiye> = hasByPage[h] ?: emptyList()

    val summary: String
        get() = buildString {
            append("${images.size} sayfa")
            if (hayratMeal != null) append(" · Hayrat meali")
            if (elmalili.isNotEmpty()) append(" · Elmalılı meali")
            if (hasiyeler.isNotEmpty()) append(" · ${hasiyeler.size} hâşiye")
        }

    companion object {
        private val img = Regex("h(\\d{3})\\.(webp|jpg|jpeg|png)", RegexOption.IGNORE_CASE)

        private fun read(ctx: Context, f: DocumentFile?): String? =
            f?.let { runCatching { ctx.contentResolver.openInputStream(it.uri)?.bufferedReader(Charsets.UTF_8)?.use { r -> r.readText() } }.getOrNull() }

        fun load(ctx: Context, tree: Uri, surahFirstIdx: (Int) -> Int?): Pack? {
            val root = DocumentFile.fromTreeUri(ctx, tree) ?: return null
            val files = HashMap<String, DocumentFile>()
            fun scan(d: DocumentFile, depth: Int) {
                for (f in d.listFiles()) {
                    val n = f.name ?: continue
                    if (f.isDirectory && depth < 2) scan(f, depth + 1) else files[n.lowercase()] = f
                }
            }
            scan(root, 0)
            val images = HashMap<Int, Uri>()
            for ((n, f) in files) img.matchEntire(n)?.let { images[it.groupValues[1].toInt()] = f.uri }

            var pages: List<IntRange>? = null
            var meal: List<String>? = null
            var group: List<List<Int>>? = null
            read(ctx, files["hayrat_meal.json"])?.let { s ->
                val o = JSONObject(s)
                val pa = o.getJSONArray("pages")
                pages = List(pa.length()) { val e = pa.getJSONArray(it); e.getInt(0)..e.getInt(1) }
                val ma = o.getJSONArray("meal"); meal = List(ma.length()) { ma.getString(it) }
                val ga = o.getJSONArray("group")
                group = List(ga.length()) { i -> val g = ga.getJSONArray(i); List(g.length()) { g.getInt(it) } }
            }
            val has = ArrayList<Hasiye>()
            read(ctx, files["hasiyeler.json"])?.let { s ->
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    has.add(Hasiye(e.getInt("h"), e.getInt("n"), if (e.isNull("ayah")) null else e.getInt("ayah"), e.getString("t")))
                }
            }
            val el = HashMap<Int, String>()
            val intro = HashMap<Int, String>()
            read(ctx, files["elmalili.json"])?.let { s ->
                val o = JSONObject(s)
                for (k in o.keys()) {
                    val sn = k.toInt()
                    val so = o.getJSONObject(k)
                    intro[sn] = so.optString("intro")
                    val first = surahFirstIdx(sn) ?: continue
                    val ay = so.getJSONArray("ayat")
                    for (i in 0 until ay.length()) {
                        val e = ay.getJSONArray(i)
                        val nums = e.getJSONArray(0)
                        val t = e.getString(1)
                        for (j in 0 until nums.length()) el[first + nums.getInt(j) - 1] = if (j == 0) t else ""
                    }
                }
            }
            if (images.isEmpty() && meal == null && el.isEmpty()) return null
            return Pack(images, pages, meal, group, has, el, intro)
        }

        fun loadImage(ctx: Context, uri: Uri, maxDim: Int): ImageBitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        }.getOrNull()
    }
}
