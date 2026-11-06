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
    /** Açıklama metni (varsa) ve şu an Açıklama sayfasında mıyız */
    var ackOf: (Int) -> String? = { null }
    var onAckPage: () -> Boolean = { false }
    /** "Açıklama" seçeneği sadece Açıklama sayfasında geçerli; başka sayfada meal okunur */
    private fun eff(): String = if (mode == "ack" && !onAckPage()) "meal" else mode
    private fun textOnly() = eff() == "meal" || eff() == "ack"
    private fun needTexts() = eff() != "ar"

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
    /** atladıktan hemen sonra konum bir an eski değeri verir; bu sürede âyet takibi beklesin */
    private var guardUntil = 0L
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { onTick(); if (mp != null) h.postDelayed(this, 80) }
    }

    fun play(quran: Quran, idx: Int) {
        q = quran
        val ay = quran.ayahs[idx]
        basmalaFirst = ay.a == 1 && ay.s != 1 && ay.s != 9
        if (textOnly()) speakMeal(idx)
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
            guardUntil = android.os.SystemClock.uptimeMillis() + 700
            cur.seekTo(seek); if (!cur.isPlaying) cur.start(); h.removeCallbacks(tick); h.post(tick); return
        }
        release()
        sRec = r; sNum = ay.s; sT = t; sFirst = quran.ayahs.indexOfFirst { it.s == ay.s }
        loading = true
        val f = surahFile(r, ay.s)
        val my = ++tok
        if (f.exists() && f.length() > 0) { openSurah(f.path, seek, my); return }
        main.launch {
            val u = withContext(Dispatchers.IO) { resolve(surahUrl(r, ay.s)) }
            if (my != tok) return@launch
            if (u == null) { error = "net: ${lastErr ?: "?"}"; playing = null; loading = false; return@launch }
            openSurah(u, seek, my)
        }
    }

    /** Yönlendirmeleri (archive.org) önceden çözer */
    private fun resolve(u: String): String? = try {
        var url = u; var n = 0; var out: String? = null
        while (n++ < 6) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = false
            c.requestMethod = "HEAD"; c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) KuranKerim")
            val code = c.responseCode
            if (code in 300..399) { url = URL(URL(url), c.getHeaderField("Location")).toString(); c.disconnect(); continue }
            c.disconnect()
            if (code in 200..299) out = url else lastErr = "HTTP $code"
            break
        }
        out
    } catch (e: Exception) { lastErr = e.javaClass.simpleName + (e.message?.let { ": " + it.take(60) } ?: ""); null }

    private fun openSurah(src: String, seek: Int, my: Int) {
        if (my != tok) return
        val p = MediaPlayer()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        try {
            if (src.startsWith("http")) p.setDataSource(app!!, android.net.Uri.parse(src), mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android) KuranKerim"))
            else p.setDataSource(src)
        } catch (e: Exception) {
            error = "src: ${e.javaClass.simpleName}"; playing = null; loading = false; p.release(); return
        }
        p.setOnPreparedListener {
            loading = false
            guardUntil = android.os.SystemClock.uptimeMillis() + 700
            if (seek > 0) it.seekTo(seek)
            if (!paused) it.start()
            h.removeCallbacks(tick); h.post(tick)
        }
        p.setOnCompletionListener { onSurahDone() }
        p.setOnErrorListener { _, w, x -> error = "mp $w/$x"; loading = false; playing = null; release(); true }
        p.prepareAsync()
        mp = p
    }

    /** O an okunan âyeti bulur; "Arapça + meal" kipinde âyet bitince durup meali okutur */
    private fun onTick() {
        val p = mp ?: return
        val t = sT ?: return
        if (loading || phase != "ar" || paused) return
        if (android.os.SystemClock.uptimeMillis() < guardUntil) return
        val pos = runCatching { p.currentPosition }.getOrDefault(0)
        var k = 0
        while (k + 1 < t.start.size && pos >= t.start[k + 1] - 120) k++
        val g = sFirst + k
        val cur = playing ?: return
        if (g != cur) {
            if (needTexts() && g == cur + 1) {
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
        if (needTexts()) { speakMeal(cur, resume = if (nextFirst >= 0) -nextFirst - 2 else -1); return }
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
        val my = ++tok
        if (f.exists() && f.length() > 0) { playFile(f, my); prefetch(idx + 1); return }
        main.launch {
            val c = withContext(Dispatchers.IO) { cached(url(r, s, a), "${r.folder}_${code(s, a)}.mp3") }
            if (my != tok) return@launch
            if (c == null) { error = "net: ${lastErr ?: "?"}"; playing = null; loading = false; return@launch }
            playFile(c, my); prefetch(idx + 1)
        }
    }

    private var tok = 0
    private var lastErr: String? = null
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun streamDir() = File(app!!.cacheDir, "stream").apply { mkdirs() }

    /** İnternetten âyet dosyasını önce indirir (MediaPlayer'ın kendi bağlantısı bazı telefonlarda çalışmıyor) */
    private fun cached(u: String, name: String): File? {
        val f = File(streamDir(), name)
        if (f.exists() && f.length() > 1000) return f
        return try {
            var url = u; var c: HttpURLConnection; var n = 0
            while (true) {
                c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) KuranKerim")
                val code = c.responseCode
                if (code in 300..399 && n++ < 5) { url = URL(URL(url), c.getHeaderField("Location")).toString(); c.disconnect(); continue }
                if (code != 200) { lastErr = "HTTP $code"; c.disconnect(); return null }
                break
            }
            val tmp = File(f.parentFile, f.name + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            c.disconnect()
            if (tmp.length() > 1000 && tmp.renameTo(f)) f else { lastErr = "boş dosya"; null }
        } catch (e: Exception) { lastErr = e.javaClass.simpleName + (e.message?.let { ": " + it.take(60) } ?: ""); null }
    }

    private fun prefetch(idx: Int) {
        val quran = q ?: return
        if (idx >= quran.ayahs.size) return
        val ay = quran.ayahs[idx]; val r = reciterById(reciter)
        if (r.surah || file(r, ay.s, ay.a).exists()) return
        main.launch(Dispatchers.IO) { runCatching { cached(url(r, ay.s, ay.a), "${r.folder}_${code(ay.s, ay.a)}.mp3") } }
        // önbellek 60 MB'ı geçerse eski dosyaları sil
        main.launch(Dispatchers.IO) {
            runCatching {
                val fs = streamDir().listFiles()?.sortedBy { it.lastModified() } ?: return@runCatching
                var total = fs.sumOf { it.length() }
                for (x in fs) { if (total < 60L shl 20) break; total -= x.length(); x.delete() }
            }
        }
    }

    private fun playFile(f: File, my: Int) {
        if (my != tok) return
        val p = MediaPlayer()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        try { p.setDataSource(f.path) } catch (e: Exception) {
            error = "file: ${e.javaClass.simpleName}"; playing = null; loading = false; p.release(); return
        }
        p.setOnPreparedListener { loading = false; if (!paused) it.start() }
        p.setOnCompletionListener { onArabicDone() }
        p.setOnErrorListener { _, w, x -> f.delete(); error = "mp $w/$x"; loading = false; playing = null; release(); true }
        p.prepareAsync()
        mp = p
    }

    private fun onArabicDone() {
        val cur = playing ?: return
        if (basmalaFirst) { basmalaFirst = false; start(cur); return }
        if (needTexts()) speakMeal(cur) else next(cur)
    }

    /** resume: sûre kaydında meal bitince devam edilecek âyet (>=0: aynı dosyada, <=-2: sonraki sûre, -1: son) */
    private var resumeTo: Int? = null
    private fun speakMeal(idx: Int, resume: Int? = null) {
        if (resume == null) release()
        resumeTo = resume
        playing = idx; paused = false; loading = false; phase = "meal"
        val meal = mealOf(idx).replace(Regex("⁽\\d+⁾|\\(\\d+\\)|\\[\\d+\\]"), "").trim()
        val text = if (eff() == "ack") listOfNotNull(meal.takeIf { it.isNotBlank() }, ackOf(idx)).joinToString(" ") else meal
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
        if (textOnly()) speakMeal(n) else if (reciterById(reciter).surah) startSurah(n, fromStart = ay.a == 1) else start(n)
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

    private fun release() { tok++; h.removeCallbacks(tick); mp?.let { runCatching { it.stop() }; it.release() }; mp = null; sRec = null; sT = null }
}

/** Doğal erkek sesi (Piper, telefonun içinde çalışır – sherpa-onnx). Her dil bir kez indirilir, sonra internetsiz çalışır. */
object Speech {
    class NV(val lang: String, val name: String, val pkg: String, val mb: Int) { val model get() = pkg.removePrefix("vits-piper-") + ".onnx" }
    val NVOICES = listOf(
        NV("tr", "Türkçe", "vits-piper-tr_TR-dfki-medium", 64),
        NV("de", "Deutsch", "vits-piper-de_DE-thorsten-medium", 64),
        NV("en", "English", "vits-piper-en_US-ryan-medium", 64),
        NV("ar", "العربية", "vits-piper-ar_JO-kareem-medium", 64),
    )

    var ready by mutableStateOf(true)
    var voiceName by mutableStateOf<String?>(null)   // eski ayar (kullanılmıyor)
    var pitch by mutableStateOf(1f)                  // eski ayar (kullanılmıyor)
    var rate by mutableStateOf(0.95f)
    var dl by mutableStateOf<String?>(null)          // indirilen dil
    var dlPct by mutableIntStateOf(0)
    var dlErr by mutableStateOf<String?>(null)
    var ver by mutableIntStateOf(0)

    private var app: Context? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var gen = 0
    @Volatile private var track: android.media.AudioTrack? = null
    private var engine: com.k2fsa.sherpa.onnx.OfflineTts? = null
    private var engineLang: String? = null
    private val waiting = ArrayList<() -> Unit>()

    fun init(ctx: Context) { app = ctx.applicationContext }
    fun voiceFor(l: String) = NVOICES.firstOrNull { it.lang == l } ?: NVOICES[0]
    private fun dir(v: NV) = File(app!!.filesDir, "tts/${v.pkg}")
    fun installed(l: String) = app != null && File(dir(voiceFor(l)), "ok").exists()

    /** Sesi GitHub'dan (sherpa-onnx) indirip açar */
    fun download(l: String, then: (() -> Unit)? = null) {
        if (then != null) waiting += then
        if (dl != null) return
        val v = voiceFor(l)
        dl = v.lang; dlPct = 0; dlErr = null
        Thread {
            var err: String? = null
            try {
                val d = dir(v); d.deleteRecursively(); d.mkdirs()
                var url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${v.pkg}.tar.bz2"
                var c: HttpURLConnection; var n = 0
                while (true) {
                    c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 20000; c.readTimeout = 60000; c.instanceFollowRedirects = false
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) KuranKerim")
                    val code = c.responseCode
                    if (code in 300..399 && n++ < 6) { url = URL(URL(url), c.getHeaderField("Location")).toString(); c.disconnect(); continue }
                    if (code != 200) throw java.io.IOException("HTTP $code")
                    break
                }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: (v.mb * 1_000_000L)
                var read = 0L
                val counting = object : java.io.FilterInputStream(java.io.BufferedInputStream(c.inputStream, 1 shl 16)) {
                    private fun add(k: Int): Int { if (k > 0) { read += k; val p = (read * 100 / total).toInt().coerceAtMost(99); if (p != dlPct) main.post { dlPct = p } }; return k }
                    override fun read(): Int = super.read().also { if (it >= 0) add(1) }
                    override fun read(b: ByteArray, o: Int, len: Int): Int = add(super.read(b, o, len))
                }
                val tar = org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                    org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(counting))
                tar.use { t ->
                    while (true) {
                        val e = t.nextEntry ?: break
                        val rel = e.name.removePrefix("./").substringAfter('/', "")
                        if (rel.isEmpty() || rel.contains("..")) continue
                        val out = File(d, rel)
                        if (e.isDirectory) { out.mkdirs(); continue }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { o -> t.copyTo(o) }
                    }
                }
                c.disconnect()
                if (!File(d, v.model).exists()) throw java.io.IOException("model yok")
                File(d, "ok").writeText("1")
            } catch (e: Throwable) { err = e.javaClass.simpleName + (e.message?.let { ": " + it.take(60) } ?: "") }
            main.post {
                dl = null; dlErr = err; ver++
                val w = ArrayList(waiting); waiting.clear()
                if (err == null) w.forEach { it() }
            }
        }.start()
    }

    fun delete(l: String) {
        val v = voiceFor(l)
        exec.execute { if (engineLang == l) { engine?.release(); engine = null; engineLang = null }; dir(v).deleteRecursively(); main.post { ver++ } }
    }

    private fun engineFor(l: String): com.k2fsa.sherpa.onnx.OfflineTts {
        if (engineLang == l) engine?.let { return it }
        engine?.release(); engine = null
        val v = voiceFor(l); val d = dir(v)
        val cfg = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
            model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
                vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
                    model = File(d, v.model).path, tokens = File(d, "tokens.txt").path, dataDir = File(d, "espeak-ng-data").path,
                ),
                numThreads = 4, provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return com.k2fsa.sherpa.onnx.OfflineTts(null, cfg).also { engine = it; engineLang = l }
    }

    fun speak(text: String, l: String, onDone: () -> Unit) {
        stop()
        val my = ++gen
        if (app == null || text.isBlank()) { onDone(); return }
        if (!installed(l)) { download(l) { if (my == gen) speak(text, l, onDone) }; return }
        val speed = rate
        exec.execute {
            var tr: android.media.AudioTrack? = null
            try {
                val tts = engineFor(l)
                if (my != gen) return@execute
                val sr = tts.sampleRate()
                val min = android.media.AudioTrack.getMinBufferSize(sr, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_FLOAT)
                tr = android.media.AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sr).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(min, sr * 4 / 2))
                    .build()
                track = tr
                tr.play()
                var frames = 0L
                // cümle cümle üret; bir cümle çalarken sonraki üretilir
                val q = java.util.concurrent.LinkedBlockingQueue<FloatArray>(3)
                val END = FloatArray(0)
                val player = Thread {
                    try {
                        while (true) {
                            val x = q.take()
                            if (x === END || my != gen) break
                            tr.write(x, 0, x.size, android.media.AudioTrack.WRITE_BLOCKING); frames += x.size
                        }
                    } catch (_: Throwable) {}
                }
                player.start()
                for (part in sentences(text)) {
                    if (my != gen) break
                    val au = tts.generate(part, 0, speed)
                    if (my != gen) break
                    while (!q.offer(au.samples, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) if (my != gen) break
                }
                while (player.isAlive && !q.offer(END, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) { if (my != gen) break }
                player.join(if (my == gen) 0L else 500L)
                // çalma bitene kadar bekle
                var last = -1; var still = 0
                while (my == gen) {
                    val pos = tr.playbackHeadPosition
                    if (pos >= frames) break
                    if (pos == last) { if (++still > 100) break } else still = 0
                    last = pos; Thread.sleep(30)
                }
            } catch (e: Throwable) {
                main.post { if (my == gen) dlErr = "ses: " + e.javaClass.simpleName }
            } finally {
                runCatching { tr?.stop() }; runCatching { tr?.release() }
                if (track === tr) track = null
            }
            main.post { if (my == gen) onDone() }
        }
    }

    /** Metni kısa cümlelere böler (ilk ses çabuk gelsin diye) */
    private fun sentences(text: String): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val out = ArrayList<String>()
        Regex("[^.!?;:…]+[.!?;:…]*[\"”’)]*\\s*").findAll(clean).forEach { m ->
            var x = m.value.trim()
            while (x.length > 220) {
                val cut = x.lastIndexOf(", ", 220).takeIf { it > 60 } ?: x.lastIndexOf(' ', 220).takeIf { it > 60 } ?: 220
                out += x.substring(0, cut + 1).trim(); x = x.substring(cut + 1).trim()
            }
            if (x.any { it.isLetterOrDigit() }) out += x
        }
        return out
    }

    fun sample(l: String) { speak(when (l) { "de" -> "Im Namen Allahs, des Allerbarmers, des Barmherzigen."; "en" -> "In the name of Allah, the Entirely Merciful, the Especially Merciful."; "ar" -> "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"; else -> "Rahmân ve Rahîm olan Allah'ın adıyla." }, l) {} }

    fun stop() {
        gen++
        waiting.clear()
        track?.let { runCatching { it.pause(); it.flush() } }
    }
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
