package de.fa.kuran.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Okuyucular. Arap kâriler âyet âyet (EveryAyah), Türk hafızlar sûre sûre (archive.org) kaydedilmiştir.
 *  Sûre kayıtlarında her âyetin başlangıç/bitiş zamanı assets/timing/{id}.json içindedir. */
data class Reciter(
    val id: String, val name: String, val folder: String, val approxMb: Int,
    val surah: Boolean = false, val base: String = "", val fallback: String? = null,
)

val RECITERS = listOf(
    Reciter("sudais", "Abdurrahman es-Südeys", "Abdurrahmaan_As-Sudais_192kbps", 1300),
    Reciter("shuraym", "Su'ûd eş-Şüreym", "Saood_ash-Shuraym_128kbps", 800),
    Reciter("maher", "Mâhir el-Muaykılî", "MaherAlMuaiqly128kbps", 750),
    Reciter("rifai", "Hânî er-Rifâî", "Hani_Rifai_192kbps", 1500),
    Reciter("basfar", "Abdullah Basfar", "Abdullah_Basfar_192kbps", 1400),
    Reciter("afasy", "Mişârî Râşid el-Afâsî", "Alafasy_128kbps", 800),
    Reciter("husary", "Mahmûd Halîl el-Husarî", "Husary_128kbps", 900),
    // Kaynak: archive.org/details/ishak_danish (Âsım – Hafs, 114 sûre)
    Reciter("danis", "İshak Danış", "tr_ishak_danis", 890, surah = true, base = "https://archive.org/download/ishak_danish/"),
    // Kaynak: archive.org/details/fatihcollakk „Sure Sure Komple Hatim“ (Âsım – Hafs); kaydı olmayan sûreyi İshak Danış okur
    Reciter("collak", "Fatih Çollak", "tr_fatih_collak", 700, surah = true, base = "https://archive.org/download/fatihcollakk/", fallback = "danis"),
)

/** Sûre kayıtlarının dosya adları ve âyet zamanları (ms) */
object Timing {
    class T(val file: String, val start: IntArray, val end: IntArray)
    private val cache = HashMap<String, Map<Int, T>>()
    fun of(ctx: Context, id: String): Map<Int, T> = cache.getOrPut(id) {
        runCatching {
            val o = org.json.JSONObject(ctx.assets.open("timing/$id.json").bufferedReader().readText())
            val m = HashMap<Int, T>()
            o.keys().forEach { k ->
                val v = o.getJSONObject(k); val a = v.getJSONArray("t")
                m[k.toInt()] = T(v.getString("f"), IntArray(a.length()) { a.getJSONArray(it).getInt(0) }, IntArray(a.length()) { a.getJSONArray(it).getInt(1) })
            }
            m
        }.getOrElse { emptyMap() }
    }
}


fun reciterById(id: String): Reciter = RECITERS.firstOrNull { it.id == id } ?: RECITERS[0]

private fun code(s: Int, a: Int) = "%03d%03d".format(s, a)

/** Sesli okuma: âyetleri sırayla çalar; dosya telefonda varsa oradan, yoksa internetten.
 *  mode: "ar" = sadece Arapça, "ar_meal" = Arapça + meal, "meal" = sadece meal (telefonun konuşma sesiyle) */
object Audio {
    var reciter by mutableStateOf("sudais")
    var mode by mutableStateOf("ar")
    var playing by mutableStateOf<Int?>(null)
    var paused by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** "ar" ya da "meal": şu an ne okunuyor */
    var phase by mutableStateOf("ar")

    /** Okuma ekranı verir: âyetin seçili mealdeki metni ve dili */
    var mealOf: (Int) -> String = { "" }
    var mealLang: String = "tr"

    private var app: Context? = null
    private var mp: MediaPlayer? = null
    private var q: Quran? = null
    private var basmalaFirst = false

    fun init(ctx: Context) { app = ctx.applicationContext; Speech.init(ctx) }

    fun url(r: Reciter, s: Int, a: Int) = "https://everyayah.com/data/${r.folder}/${code(s, a)}.mp3"
    fun dir(r: Reciter) = File(app!!.filesDir, "audio/${r.folder}")
    fun file(r: Reciter, s: Int, a: Int) = File(dir(r), "${code(s, a)}.mp3")

    // ---- sûre sûre okuyan hafızlar ----
    fun timing(r: Reciter) = Timing.of(app!!, r.id)
    /** Bu sûreyi kim okur: okuyucunun kaydı yoksa yedek okuyucu (Çollak → Danış) */
    fun reciterFor(r: Reciter, s: Int): Reciter =
        if (timing(r).containsKey(s) || r.fallback == null) r else reciterById(r.fallback)
    fun missing(r: Reciter): List<Int> = if (!r.surah) emptyList() else (1..114).filter { !timing(r).containsKey(it) }
    fun surahUrl(r: Reciter, s: Int) = r.base + (timing(r)[s]?.file ?: "%03d.mp3".format(s))
    fun surahFile(r: Reciter, s: Int) = File(dir(r), "%03d.mp3".format(s))

    private var sRec: Reciter? = null
    private var sNum = 0
    private var sFirst = 0
    private var sT: Timing.T? = null
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { onTick(); if (mp != null) h.postDelayed(this, 80) }
    }

    fun play(quran: Quran, idx: Int) {
        q = quran
        val ay = quran.ayahs[idx]
        basmalaFirst = ay.a == 1 && ay.s != 1 && ay.s != 9
        if (mode == "meal") speakMeal(idx)
        else if (reciterById(reciter).surah) startSurah(idx)
        else start(idx)
    }

    /** Sûre dosyasını açar (telefonda varsa oradan) ve istenen âyete atlar */
    private fun startSurah(idx: Int, fromStart: Boolean = false) {
        val quran = q ?: return
        val ay = quran.ayahs[idx]
        val r = reciterFor(reciterById(reciter), ay.s)
        val t = timing(r)[ay.s]
        Speech.stop()
        playing = idx; paused = false; error = null; phase = "ar"
        val seek = if (fromStart || ay.a == 1 || t == null) 0 else (t.start[ay.a - 1] - 150).coerceAtLeast(0)
        val cur = mp
        if (cur != null && sRec?.id == r.id && sNum == ay.s && !loading) {
            cur.seekTo(seek); if (!cur.isPlaying) cur.start(); h.removeCallbacks(tick); h.post(tick); return
        }
        release()
        sRec = r; sNum = ay.s; sT = t; sFirst = quran.ayahs.indexOfFirst { it.s == ay.s }
        loading = true
        val f = surahFile(r, ay.s)
        val p = MediaPlayer()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        try {
            if (f.exists() && f.length() > 0) p.setDataSource(f.path) else p.setDataSource(surahUrl(r, ay.s))
        } catch (e: Exception) {
            error = "net"; playing = null; loading = false; p.release(); return
        }
        p.setOnPreparedListener {
            loading = false
            if (seek > 0) it.seekTo(seek)
            if (!paused) it.start()
            h.removeCallbacks(tick); h.post(tick)
        }
        p.setOnCompletionListener { onSurahDone() }
        p.setOnErrorListener { _, _, _ -> error = "net"; loading = false; playing = null; release(); true }
        p.prepareAsync()
        mp = p
    }

    /** O an okunan âyeti bulur; "Arapça + meal" kipinde âyet bitince durup meali okutur */
    private fun onTick() {
        val p = mp ?: return
        val t = sT ?: return
        if (loading || phase != "ar" || paused) return
        val pos = runCatching { p.currentPosition }.getOrDefault(0)
        var k = 0
        while (k + 1 < t.start.size && pos >= t.start[k + 1] - 120) k++
        val g = sFirst + k
        val cur = playing ?: return
        if (g != cur) {
            if (mode == "ar_meal" && g == cur + 1) {
                runCatching { p.pause() }
                p.seekTo((t.start[k] - 120).coerceAtLeast(0))
                speakMeal(cur, resume = g); return
            }
            playing = g
        }
    }

    private fun onSurahDone() {
        val quran = q ?: return
        val cur = playing ?: return
        val nextFirst = quran.ayahs.indexOfFirst { it.s == sNum + 1 }
        if (mode == "ar_meal") { speakMeal(cur, resume = if (nextFirst >= 0) -nextFirst - 2 else -1); return }
        if (nextFirst < 0) { stop(); return }
        startSurah(nextFirst, fromStart = true)
    }

    private fun start(idx: Int) {
        val quran = q ?: return
        release(); Speech.stop()
        playing = idx; paused = false; error = null; loading = true; phase = "ar"
        val ay = quran.ayahs[idx]
        val r = reciterById(reciter)
        val (s, a) = if (basmalaFirst) 1 to 1 else ay.s to ay.a
        val f = file(r, s, a)
        val p = MediaPlayer()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        try {
            if (f.exists() && f.length() > 0) p.setDataSource(f.path) else p.setDataSource(url(r, s, a))
        } catch (e: Exception) {
            error = "net"; playing = null; loading = false; p.release(); return
        }
        p.setOnPreparedListener { loading = false; if (!paused) it.start() }
        p.setOnCompletionListener { onArabicDone() }
        p.setOnErrorListener { _, _, _ -> error = "net"; loading = false; playing = null; release(); true }
        p.prepareAsync()
        mp = p
    }

    private fun onArabicDone() {
        val cur = playing ?: return
        if (basmalaFirst) { basmalaFirst = false; start(cur); return }
        if (mode == "ar_meal") speakMeal(cur) else next(cur)
    }

    /** resume: sûre kaydında meal bitince devam edilecek âyet (>=0: aynı dosyada, <=-2: sonraki sûre, -1: son) */
    private var resumeTo: Int? = null
    private fun speakMeal(idx: Int, resume: Int? = null) {
        if (resume == null) release()
        resumeTo = resume
        playing = idx; paused = false; loading = false; phase = "meal"
        val text = mealOf(idx).replace(Regex("⁽\\d+⁾|\\(\\d+\\)|\\[\\d+\\]"), "").trim()
        if (text.isBlank()) { afterMeal(idx); return }
        Speech.speak(text, mealLang) { if (playing == idx && phase == "meal" && !paused) afterMeal(idx) }
    }

    private fun afterMeal(idx: Int) {
        val r = resumeTo ?: run { next(idx); return }
        resumeTo = null
        when {
            r >= 0 -> { phase = "ar"; playing = r; mp?.start(); h.removeCallbacks(tick); h.post(tick) }
            r == -1 -> stop()
            else -> startSurah(-r - 2, fromStart = true)
        }
    }

    private fun next(cur: Int) {
        val quran = q ?: return
        val n = cur + 1
        if (n >= quran.ayahs.size) { stop(); return }
        val ay = quran.ayahs[n]
        basmalaFirst = ay.a == 1 && ay.s != 1 && ay.s != 9
        if (mode == "meal") speakMeal(n) else if (reciterById(reciter).surah) startSurah(n, fromStart = ay.a == 1) else start(n)
    }

    fun toggle() {
        val cur = playing ?: return
        if (phase == "meal") {
            if (paused) { paused = false; speakMeal(cur, resumeTo) } else { paused = true; Speech.stop() }
            return
        }
        val p = mp ?: return
        if (paused) { if (!loading) p.start(); paused = false } else { if (p.isPlaying) p.pause(); paused = true }
    }

    fun stop() { release(); Speech.stop(); playing = null; paused = false; loading = false; phase = "ar"; resumeTo = null }

    private fun release() { h.removeCallbacks(tick); mp?.let { runCatching { it.stop() }; it.release() }; mp = null; sRec = null; sT = null }
}

/** Telefonun konuşma motoru (meal sesi). Erkek sesi tercih edilir, ses kalınlığı ayarlanabilir. */
object Speech {
    data class V(val name: String, val label: String, val male: Boolean)

    var ready by mutableStateOf(false)
    var voices by mutableStateOf<List<V>>(emptyList())
    var voiceName by mutableStateOf<String?>(null)
    var pitch by mutableStateOf(0.8f)
    var rate by mutableStateOf(0.95f)

    private var tts: android.speech.tts.TextToSpeech? = null
    private var lang = "tr"
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val done = HashMap<String, () -> Unit>()
    private var n = 0

    fun init(ctx: Context) {
        if (tts != null) return
        tts = android.speech.tts.TextToSpeech(ctx.applicationContext) { st ->
            if (st == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String) {}
                    override fun onDone(id: String) { main.post { done.remove(id)?.invoke() } }
                    @Deprecated("") override fun onError(id: String) { main.post { done.remove(id)?.invoke() } }
                })
                main.post { ready = true; loadVoices("tr") }
            }
        }
    }

    private fun locale(l: String) = when (l) { "de" -> java.util.Locale.GERMANY; "en" -> java.util.Locale.US; "ar" -> java.util.Locale("ar"); else -> java.util.Locale("tr", "TR") }

    /** Erkek sesi tahmini: ses adında "male" / Samsung "m00" / Google'ın bilinen erkek kodları */
    private fun guessMale(name: String): Boolean {
        val n = name.lowercase()
        if (n.contains("female") || Regex("smtf\\d|#female").containsMatchIn(n)) return false
        if (n.contains("male") || Regex("smtm\\d").containsMatchIn(n)) return true
        return listOf("-x-cfs", "-x-tmc", "-x-mfm", "-x-iom", "-x-iol", "-x-tpd", "-x-gbd", "-x-rjs", "-x-ard", "-x-deb", "-x-arz").any { n.contains(it) }
    }

    fun loadVoices(l: String) {
        val t = tts ?: return
        lang = l
        val loc = locale(l)
        val list = runCatching { t.voices?.filter { it.locale.language == loc.language && !it.features.contains("notInstalled") } }.getOrNull().orEmpty()
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
        var mi = 0; var fi = 0
        voices = list.map { v ->
            val m = guessMale(v.name)
            val lbl = (if (m) "Erkek sesi " + (++mi) else "Ses " + (++fi)) + if (v.isNetworkConnectionRequired) " (internet)" else ""
            V(v.name, lbl, m)
        }.let { all -> if (all.any { it.male }) all.filter { it.male } else all }
    }

    fun speak(text: String, l: String, onDone: () -> Unit) {
        val t = tts ?: run { onDone(); return }
        if (l != lang || voices.isEmpty()) loadVoices(l)
        t.language = locale(l)
        val chosen = t.voices?.firstOrNull { it.name == voiceName && it.locale.language == locale(l).language }
            ?: voices.firstOrNull { it.male }?.let { m -> t.voices?.firstOrNull { it.name == m.name } }
        chosen?.let { t.voice = it }
        t.setPitch(pitch); t.setSpeechRate(rate)
        val id = "u" + (++n)
        done[id] = onDone
        // uzun metni parçalara böl (motor sınırı)
        val max = android.speech.tts.TextToSpeech.getMaxSpeechInputLength() - 10
        val parts = if (text.length <= max) listOf(text) else text.chunked(max)
        parts.forEachIndexed { i, p ->
            t.speak(p, if (i == 0) android.speech.tts.TextToSpeech.QUEUE_FLUSH else android.speech.tts.TextToSpeech.QUEUE_ADD, null, if (i == parts.lastIndex) id else "$id-$i")
        }
    }

    fun sample(l: String) { speak(when (l) { "de" -> "Im Namen Allahs, des Allerbarmers, des Barmherzigen."; "en" -> "In the name of Allah, the Entirely Merciful, the Especially Merciful."; "ar" -> "بسم الله الرحمن الرحيم"; else -> "Rahmân ve Rahîm olan Allah'ın adıyla." }, l) {} }

    fun stop() { done.clear(); tts?.stop() }
}

/** Okuyucu seslerini telefona indirir (yarıda kalırsa kaldığı yerden devam eder) */
object Downloads {
    var active by mutableStateOf<String?>(null)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var failed by mutableIntStateOf(0)
    var version by mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Bir sûrenin âyetleri (+ besmele) */
    fun surahItems(q: Quran, s: Int): List<Pair<Int, Int>> =
        listOf(1 to 1) + q.ayahs.filter { it.s == s }.map { it.s to it.a }

    fun allItems(q: Quran): List<Pair<Int, Int>> = q.ayahs.map { it.s to it.a }

    /** İndirilecek dosyalar (adres, telefondaki yeri). surah = null → bütün hatim */
    fun itemsFor(q: Quran, r: Reciter, surah: Int?): List<Pair<String, File>> =
        if (r.surah) (surah?.let { listOf(it) } ?: (1..114).toList()).map { s ->
            val rr = Audio.reciterFor(r, s); Audio.surahUrl(rr, s) to Audio.surahFile(rr, s)
        } else (if (surah != null) surahItems(q, surah) else listOf(1 to 1) + allItems(q)).map { (s, a) -> Audio.url(r, s, a) to Audio.file(r, s, a) }

    fun start(r: Reciter, items: List<Pair<String, File>>) {
        job?.cancel()
        active = r.id; total = items.size; done = 0; failed = 0
        job = scope.launch {
            for ((u, f) in items) {
                if (!isActive) break
                f.parentFile?.mkdirs()
                var ok = f.exists() && f.length() > 0
                var tries = 0
                while (!ok && tries < 3 && isActive) {
                    ok = fetch(u, f)
                    if (!ok) { tries++; delay(1500L * tries) }
                }
                withContext(Dispatchers.Main) { done++; if (!ok) failed++ }
            }
            withContext(Dispatchers.Main) { active = null; version++ }
        }
    }

    fun cancel() { job?.cancel(); active = null; version++ }

    fun count(r: Reciter): Int =
        if (r.surah) (1..114).count { s -> Audio.reciterFor(r, s).let { Audio.surahFile(it, s) }.let { it.exists() && it.length() > 0 } }
        else Audio.dir(r).list()?.count { it.endsWith(".mp3") } ?: 0

    fun hasSurah(q: Quran, r: Reciter, s: Int): Boolean = surahItems(q, s).all { (x, y) -> Audio.file(r, x, y).let { it.exists() && it.length() > 0 } }

    fun delete(r: Reciter) { if (active == r.id) cancel(); Audio.dir(r).deleteRecursively(); version++ }

    private fun fetch(u: String, f: File): Boolean = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "KuranKerim-Android")
        if (c.responseCode != 200) { c.disconnect(); false } else {
            val tmp = File(f.parentFile, f.name + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            c.disconnect()
            tmp.length() > 1000 && tmp.renameTo(f)
        }
    } catch (e: Exception) { false }
}
