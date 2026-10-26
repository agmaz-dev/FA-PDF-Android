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

/** Âyet âyet kaydedilmiş okuyucular (EveryAyah arşivi) */
data class Reciter(val id: String, val name: String, val folder: String, val approxMb: Int)

val RECITERS = listOf(
    Reciter("sudais", "Abdurrahman es-Südeys", "Abdurrahmaan_As-Sudais_192kbps", 1300),
    Reciter("shuraym", "Su'ûd eş-Şüreym", "Saood_ash-Shuraym_128kbps", 800),
    Reciter("maher", "Mâhir el-Muaykılî", "MaherAlMuaiqly128kbps", 750),
    Reciter("rifai", "Hânî er-Rifâî", "Hani_Rifai_192kbps", 1500),
    Reciter("basfar", "Abdullah Basfar", "Abdullah_Basfar_192kbps", 1400),
    Reciter("afasy", "Mişârî Râşid el-Afâsî", "Alafasy_128kbps", 800),
    Reciter("husary", "Mahmûd Halîl el-Husarî", "Husary_128kbps", 900),
)

fun reciterById(id: String): Reciter = RECITERS.firstOrNull { it.id == id } ?: RECITERS[0]

private fun code(s: Int, a: Int) = "%03d%03d".format(s, a)

/** Sesli okuma: âyetleri sırayla çalar; dosya telefonda varsa oradan, yoksa internetten */
object Audio {
    var reciter by mutableStateOf("sudais")
    var playing by mutableStateOf<Int?>(null)
    var paused by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private var app: Context? = null
    private var mp: MediaPlayer? = null
    private var q: Quran? = null
    private var basmalaFirst = false

    fun init(ctx: Context) { app = ctx.applicationContext }

    fun url(r: Reciter, s: Int, a: Int) = "https://everyayah.com/data/${r.folder}/${code(s, a)}.mp3"
    fun dir(r: Reciter) = File(app!!.filesDir, "audio/${r.folder}")
    fun file(r: Reciter, s: Int, a: Int) = File(dir(r), "${code(s, a)}.mp3")

    fun play(quran: Quran, idx: Int) {
        q = quran
        val ay = quran.ayahs[idx]
        basmalaFirst = ay.a == 1 && ay.s != 1 && ay.s != 9
        start(idx)
    }

    private fun start(idx: Int) {
        val quran = q ?: return
        release()
        playing = idx; paused = false; error = null; loading = true
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
        p.setOnCompletionListener { onDone() }
        p.setOnErrorListener { _, _, _ -> error = "net"; loading = false; playing = null; release(); true }
        p.prepareAsync()
        mp = p
    }

    private fun onDone() {
        val cur = playing ?: return
        if (basmalaFirst) { basmalaFirst = false; start(cur); return }
        val quran = q ?: return
        val next = cur + 1
        if (next >= quran.ayahs.size) { stop(); return }
        val ay = quran.ayahs[next]
        basmalaFirst = ay.a == 1 && ay.s != 1 && ay.s != 9
        start(next)
    }

    fun toggle() {
        val p = mp ?: return
        if (paused) { if (!loading) p.start(); paused = false } else { if (p.isPlaying) p.pause(); paused = true }
    }

    fun stop() { release(); playing = null; paused = false; loading = false }

    private fun release() { mp?.let { runCatching { it.stop() }; it.release() }; mp = null }
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

    fun start(r: Reciter, items: List<Pair<Int, Int>>) {
        job?.cancel()
        active = r.id; total = items.size; done = 0; failed = 0
        job = scope.launch {
            Audio.dir(r).mkdirs()
            for ((s, a) in items) {
                if (!isActive) break
                val f = Audio.file(r, s, a)
                var ok = f.exists() && f.length() > 0
                var tries = 0
                while (!ok && tries < 3 && isActive) {
                    ok = fetch(Audio.url(r, s, a), f)
                    if (!ok) { tries++; delay(1500L * tries) }
                }
                withContext(Dispatchers.Main) { done++; if (!ok) failed++ }
            }
            withContext(Dispatchers.Main) { active = null; version++ }
        }
    }

    fun cancel() { job?.cancel(); active = null; version++ }

    fun count(r: Reciter): Int = Audio.dir(r).list()?.count { it.endsWith(".mp3") } ?: 0

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
