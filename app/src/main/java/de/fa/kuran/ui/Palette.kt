package de.fa.kuran.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import de.fa.kuran.R

data class Palette(
    val app: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val gold: Color,
    val goldLight: Color,
    val band: Color,
    val cartouche: Color,
    val cartoucheInk: Color,
    val glass: Color,
    val glassLine: Color,
    val accent: Color,
    val red: Color,
    val card: Color,
    val highlight: Color,
    val chip: Color,
)

fun palette(name: String): Palette = when (name) {
    "beyaz" -> Palette(
        app = Color(0xFFEEF0F4), paper = Color(0xFFFFFFFF), ink = Color(0xFF111111), muted = Color(0xFF5A5A5A),
        gold = Color(0xFFA8843C), goldLight = Color(0xFFD8B66A), band = Color(0xFF1F4E48),
        cartouche = Color(0xFFFAF6EA), cartoucheInk = Color(0xFF1F3A35),
        glass = Color(0xE6FFFFFF), glassLine = Color(0xFFFFFFFF), accent = Color(0xFF0A66D8),
        red = Color(0xFFB3263A), card = Color(0xFFFFFFFF), highlight = Color(0x2E0A66D8), chip = Color(0x0F000000),
    )
    "gece" -> Palette(
        app = Color(0xFF0F1113), paper = Color(0xFF1C1E21), ink = Color(0xFFECE6D6), muted = Color(0xFFA9A293),
        gold = Color(0xFFC9A45A), goldLight = Color(0xFFD8B66A), band = Color(0xFF21423C),
        cartouche = Color(0xFF2A2C2F), cartoucheInk = Color(0xFFE7D7A8),
        glass = Color(0xE62C2E32), glassLine = Color(0x1FFFFFFF), accent = Color(0xFF4C9BFF),
        red = Color(0xFFE0707F), card = Color(0xFF26282C), highlight = Color(0x554C9BFF), chip = Color(0x1FFFFFFF),
    )
    else -> Palette(
        app = Color(0xFFECE9F2), paper = Color(0xFFFBF6EA), ink = Color(0xFF1B1A17), muted = Color(0xFF5F584C),
        gold = Color(0xFFB08A3E), goldLight = Color(0xFFD8B66A), band = Color(0xFF1F4E48),
        cartouche = Color(0xFFF7F0DC), cartoucheInk = Color(0xFF1F3A35),
        glass = Color(0xE0FFFFFF), glassLine = Color(0xFFFFFFFF), accent = Color(0xFF0A66D8),
        red = Color(0xFFB3263A), card = Color(0xFFFFFFFF), highlight = Color(0x2E0A66D8), chip = Color(0x0F000000),
    )
}

object Fonts {
    val amiri = FontFamily(Font(R.font.amiri_quran))
    val scheherazade = FontFamily(Font(R.font.scheherazade))
    val naskh = FontFamily(Font(R.font.noto_naskh))
    val amiriClassic = FontFamily(Font(R.font.amiri_regular))
    val lateef = FontFamily(Font(R.font.lateef))
    val harmattan = FontFamily(Font(R.font.harmattan))
    val mada = FontFamily(Font(R.font.mada))
    val kufi = FontFamily(Font(R.font.noto_kufi))
    val katibeh = FontFamily(Font(R.font.katibeh))
    val markazi = FontFamily(Font(R.font.markazi))
    val reemKufi = FontFamily(Font(R.font.reem_kufi))
    val kufam = FontFamily(Font(R.font.kufam))
    val ruqaa = FontFamily(Font(R.font.aref_ruqaa))
    val gulzar = FontFamily(Font(R.font.gulzar))
    val nastaliq = FontFamily(Font(R.font.noto_nastaliq))
    val messiri = FontFamily(Font(R.font.el_messiri))
    val rakkas = FontFamily(Font(R.font.rakkas))
    val qahiri = FontFamily(Font(R.font.qahiri))

    /** Osman Taha neshi (Kral Fahd) – kişisel pakette */
    var taha: FontFamily? = null
        private set

    /** Medine Mushafı yazısı (Kral Fahd Kur'an Matbaası) – kişisel pakette */
    var medina: FontFamily? = null
        private set

    /** Hüsrev hattı: sadece kişisel pakette (assets/pack veya filesDir/pack) bulunur */
    var husrev: FontFamily? = null
        private set

    private fun packFont(ctx: android.content.Context, name: String): FontFamily? = runCatching {
        val f = java.io.File(ctx.filesDir, "pack/$name")
        if (f.exists() && f.length() > 0) FontFamily(Font(f))
        else { ctx.assets.open("pack/$name").close(); FontFamily(Font("pack/$name", ctx.assets)) }
    }.getOrNull()

    fun init(ctx: android.content.Context) {
        if (husrev == null) husrev = packFont(ctx, "husrev.ttf")
        if (medina == null) medina = packFont(ctx, "kfgqpc_hafs.ttf")
        if (taha == null) taha = packFont(ctx, "kfgqpc_taha.otf")
    }

    fun arabic(key: String): FontFamily = when (key) {
        "husrev" -> husrev ?: amiri
        "scheherazade" -> scheherazade
        "naskh" -> naskh
        "medina" -> medina ?: amiri
        "amiri_classic" -> amiriClassic
        "lateef" -> lateef
        "harmattan" -> harmattan
        "mada" -> mada
        "kufi" -> kufi
        "katibeh" -> katibeh
        "taha" -> taha ?: amiri
        "markazi" -> markazi
        "reem_kufi" -> reemKufi
        "kufam" -> kufam
        "ruqaa" -> ruqaa
        "gulzar" -> gulzar
        "nastaliq" -> nastaliq
        "messiri" -> messiri
        "rakkas" -> rakkas
        "qahiri" -> qahiri
        else -> amiri
    }
}

/** Tevâfuklu mushafta kırmızı yazılan kelimeler (kişisel paketten: pack/tevafuk.json) */
object Tevafuk {
    private var red: HashSet<Long> = HashSet()
    fun init(ctx: android.content.Context) {
        val txt = runCatching {
            val f = java.io.File(ctx.filesDir, "pack/tevafuk.json")
            if (f.exists() && f.length() > 0) f.readText() else ctx.assets.open("pack/tevafuk.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        runCatching {
            val arr = org.json.JSONObject(txt).getJSONArray("r")
            val s = HashSet<Long>(arr.length() * 2)
            for (i in 0 until arr.length()) { val a = arr.getJSONArray(i); s.add(a.getInt(0).toLong() * 1000 + a.getInt(1)) }
            red = s
        }
    }
    fun isRed(ayahIdx: Int, wordIdx: Int): Boolean = red.contains(ayahIdx.toLong() * 1000 + wordIdx)
}

/** Diyanet İşleri Başkanlığı'nın yeni meali (Altuntaş–Şahin) – kişisel pakette: pack/diyanet.json */
object DibMeal {
    var meal: List<String>? = null
        private set
    fun init(ctx: android.content.Context) {
        if (meal != null) return
        val txt = runCatching {
            val f = java.io.File(ctx.filesDir, "pack/diyanet.json")
            if (f.exists() && f.length() > 0) f.readText() else ctx.assets.open("pack/diyanet.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        runCatching { val a = org.json.JSONObject(txt).getJSONArray("meal"); meal = List(a.length()) { a.getString(it) } }
    }
}

const val BASMALA = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

fun arabicDigits(n: Int): String = n.toString().map { c -> '٠' + (c - '0') }.joinToString("")

fun ayahMarker(n: Int): String = "۝" + arabicDigits(n)

data class MealInfo2(val id: String, val short: String, val name: String)

val MEALLER = listOf(
    MealInfo2("hayrat", "Hayrat", "Hayrat Muhtasar Meâli"),
    MealInfo2("elmalili", "Elmalılı", "Elmalılı Hamdi Yazır Meâli"),
    MealInfo2("tr.diyanet", "Diyanet", "Diyanet İşleri Başkanlığı Meâli (Altuntaş – Şahin)"),
    MealInfo2("tr.golpinarli", "Gölpınarlı", "Abdülbâki Gölpınarlı Meâli"),
    MealInfo2("de.bubenheim", "Bubenheim", "Deutsch – Bubenheim & Elyas"),
    MealInfo2("en.sahih", "Saheeh Intl.", "English – Saheeh International"),
    MealInfo2("ar.muyassar", "الميسر", "العربية – التفسير الميسر"),
)

fun mealInfo(id: String): MealInfo2 = MEALLER.firstOrNull { it.id == id } ?: MEALLER[0]

private val harakat = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

/** Allah lafzı mı? (الله، لله، بالله، والله، فالله، تالله، اللهم …) */
fun isAllahWord(w: String): Boolean {
    val n = harakat.replace(w, "").replace('\u0671', '\u0627')
    return n.contains("لله") && n.length <= 8
}
