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

            val texts = HashMap<String, String>()
            for (n in listOf("hayrat_meal.json", "hasiyeler.json", "elmalili.json")) read(ctx, files[n])?.let { texts[n] = it }
            return build(images, texts, surahFirstIdx)
        }

        private fun build(images: Map<Int, Uri>, texts: Map<String, String>, surahFirstIdx: (Int) -> Int?): Pack? {
            var pages: List<IntRange>? = null
            var meal: List<String>? = null
            var group: List<List<Int>>? = null
            texts["hayrat_meal.json"]?.let { s ->
                val o = JSONObject(s)
                val pa = o.getJSONArray("pages")
                pages = List(pa.length()) { val e = pa.getJSONArray(it); e.getInt(0)..e.getInt(1) }
                val ma = o.getJSONArray("meal"); meal = List(ma.length()) { ma.getString(it) }
                val ga = o.getJSONArray("group")
                group = List(ga.length()) { i -> val g = ga.getJSONArray(i); List(g.length()) { g.getInt(it) } }
            }
            val has = ArrayList<Hasiye>()
            texts["hasiyeler.json"]?.let { s ->
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    has.add(Hasiye(e.getInt("h"), e.getInt("n"), if (e.isNull("ayah")) null else e.getInt("ayah"), e.getString("t")))
                }
            }
            val el = HashMap<Int, String>()
            val intro = HashMap<Int, String>()
            texts["elmalili.json"]?.let { s ->
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

        private fun open(ctx: Context, uri: Uri): java.io.InputStream? =
            if (uri.scheme == "asset") ctx.assets.open(uri.path!!.trimStart('/')) else ctx.contentResolver.openInputStream(uri)

        fun loadImage(ctx: Context, uri: Uri, maxDim: Int): ImageBitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(ctx, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            open(ctx, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        }.getOrNull()

        /** Uygulama içine aktarılmış paket (filesDir/pack) */
        fun loadDir(dir: java.io.File, surahFirstIdx: (Int) -> Int?): Pack? {
            val fs = dir.listFiles() ?: return null
            val images = HashMap<Int, Uri>(); val texts = HashMap<String, String>()
            for (f in fs) {
                val m = img.matchEntire(f.name.lowercase())
                if (m != null) images[m.groupValues[1].toInt()] = Uri.fromFile(f)
                else if (f.name.endsWith(".json")) texts[f.name.lowercase()] = f.readText(Charsets.UTF_8)
            }
            return build(images, texts, surahFirstIdx)
        }

        /** Seçilen zip dosyalarını uygulamanın içine açar; açılan dosya sayısını döndürür */
        fun importZips(ctx: Context, uris: List<Uri>, onProgress: (Int) -> Unit): Int {
            val dir = java.io.File(ctx.filesDir, "pack").apply { mkdirs() }
            var n = 0
            for (u in uris) {
                ctx.contentResolver.openInputStream(u)?.use { ins ->
                    java.util.zip.ZipInputStream(ins.buffered()).use { z ->
                        while (true) {
                            val e = z.nextEntry ?: break
                            val name = e.name.substringAfterLast('/')
                            if (!e.isDirectory && name.isNotEmpty() && (img.matches(name.lowercase()) || name.endsWith(".json"))) {
                                java.io.File(dir, name).outputStream().use { z.copyTo(it) }
                                n++; onProgress(n)
                            }
                        }
                    }
                }
            }
            return n
        }

        /** Uygulamanın içine gömülü paket (assets/pack/) */
        fun loadAssets(ctx: Context, surahFirstIdx: (Int) -> Int?): Pack? {
            val names = runCatching { ctx.assets.list("pack")?.toList() }.getOrNull() ?: return null
            if (names.isEmpty()) return null
            val texts = HashMap<String, String>()
            val images = HashMap<Int, Uri>()
            for (n in names) {
                val m = img.matchEntire(n.lowercase())
                if (m != null) images[m.groupValues[1].toInt()] = Uri.parse("asset:///pack/$n")
                else if (n.endsWith(".json")) texts[n.lowercase()] = ctx.assets.open("pack/$n").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            return build(images, texts, surahFirstIdx)
        }
    }
}
