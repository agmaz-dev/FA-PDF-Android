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

    fun arabic(key: String): FontFamily = when (key) {
        "scheherazade" -> scheherazade
        "naskh" -> naskh
        else -> amiri
    }
}

const val BASMALA = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

fun arabicDigits(n: Int): String = n.toString().map { c -> '٠' + (c - '0') }.joinToString("")

fun ayahMarker(n: Int): String = "۝" + arabicDigits(n)

data class TefsirInfo(val id: String, val short: String, val name: String, val work: String)

val TEFSIRLER = listOf(
    TefsirInfo("elmalili", "Elmalılı", "Elmalılı Hamdi Yazır", "Hak Dini Kur'an Dili"),
    TefsirInfo("bilmen", "Bilmen", "Ömer Nasûhi Bilmen", "Kur'ân-ı Kerîm'in Türkçe Meâl-i Âlîsi ve Tefsiri"),
)

fun tefsirInfo(id: String?): TefsirInfo = TEFSIRLER.firstOrNull { it.id == id } ?: TEFSIRLER[0]
