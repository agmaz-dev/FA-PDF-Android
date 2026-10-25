package de.fa.kuran.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Quran

/** Meal sayfasındaki satırlar */
private sealed interface MItem {
    data class Title(val text: String) : MItem
    data class Sub(val text: String) : MItem
    data object Basmala : MItem
    data object BasmalaTr : MItem
    data class Line(val idx: Int, val text: AnnotatedString) : MItem
}

private fun mealItems(q: Quran, page: Int, mealId: String, selected: Int?, hl: Color): List<MItem> {
    val out = ArrayList<MItem>()
    for (ay in q.ayahsOnPage(page)) {
        if (ay.a == 1) {
            val s = q.surah(ay.s)
            out += MItem.Title("${s.n}- ${s.tr.uppercase(java.util.Locale("tr"))} SÛRESİ")
            val yer = if (s.type == "Mekkî") "Mekke" else "Medîne"
            out += MItem.Sub("[$yer devrinde nâzil olmuştur, ${s.ayat} âyettir.]")
            if (s.n != 1 && s.n != 9) {
                out += MItem.Basmala
                out += MItem.BasmalaTr
            }
        }
        val t = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${ay.a}- ") }
            append(q.meal(mealId, ay.idx))
            if (ay.idx == selected) addStyle(SpanStyle(background = hl), 0, length)
        }
        out += MItem.Line(ay.idx, t)
    }
    return out
}

private fun mealStyle(size: Float, color: Color) = TextStyle(fontSize = size.sp, lineHeight = (size * 1.42f).sp, color = color)

private fun itemStyle(it: MItem, size: Float, pal: Palette): TextStyle = when (it) {
    is MItem.Title -> mealStyle(size * 1.18f, pal.ink).copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    is MItem.Sub -> mealStyle(size * 0.86f, pal.muted).copy(textAlign = TextAlign.Center)
    MItem.Basmala -> arabicStyle(Fonts.amiri, size * 1.3f, pal.red, TextAlign.Center)
    MItem.BasmalaTr -> mealStyle(size, pal.ink).copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    is MItem.Line -> mealStyle(size, pal.ink)
}

private fun itemText(it: MItem): AnnotatedString = when (it) {
    is MItem.Title -> AnnotatedString(it.text)
    is MItem.Sub -> AnnotatedString(it.text)
    MItem.Basmala -> AnnotatedString(BASMALA)
    MItem.BasmalaTr -> AnnotatedString("Rahmân, Rahîm olan Allah'ın ismiyle")
    is MItem.Line -> it.text
}

private fun fitMeal(m: TextMeasurer, items: List<MItem>, pal: Palette, w: Int, h: Int, gapPx: Int): Float {
    if (w <= 0 || h <= 0) return 15f
    var lo = 11f
    var hi = 20f
    repeat(8) {
        val mid = (lo + hi) / 2f
        var total = 0
        items.forEachIndexed { i, it ->
            if (i > 0) total += gapPx
            total += m.measure(itemText(it), itemStyle(it, mid, pal), constraints = Constraints(maxWidth = w)).size.height
        }
        if (total <= h) lo = mid else hi = mid
    }
    return lo
}

@Composable
fun MealPage(
    q: Quran,
    page: Int,
    st: AppState,
    pal: Palette,
    side: Side,
    selected: Int?,
    onAyah: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mealId = q.mealId(st.meal)
    PaperPage(pal, modifier, side, onTap, onPinch = { z -> st.mealZoom = (st.mealZoom * z).coerceIn(0.6f, 2.2f) }) {
        val surahs = q.surahsOnPage(page)
        val range = if (surahs.size > 1) "${surahs.first().n}–${surahs.last().n}" else "${surahs.firstOrNull()?.n ?: ""}"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$page", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.ink)
            Text("Cüz: ${q.juzOfPage(page)}, Sûre: $range (${surahs.joinToString(" – ") { it.tr }})", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = pal.ink, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).height(1.5.dp).background(pal.ink))
        val items = remember(page, mealId, selected, pal) { mealItems(q, page, mealId, selected, pal.highlight) }
        val m = rememberTextMeasurer()
        val onA by rememberUpdatedState(onAyah)
        val onT by rememberUpdatedState(onTap)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gapPx = 3
            val base = remember(page, mealId, constraints.maxWidth, constraints.maxHeight, pal) {
                fitMeal(m, mealItems(q, page, mealId, null, Color.Transparent), pal, constraints.maxWidth, constraints.maxHeight, gapPx)
            }
            val size = base * st.mealZoom
            val boxH = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = boxH),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items.forEach { it ->
                    val mod = if (it is MItem.Line) {
                        Modifier.fillMaxWidth().pointerInput(it.idx) { detectTapGestures { _ -> onA(it.idx) } }
                    } else {
                        Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { _ -> onT() } }
                    }
                    Text(itemText(it), style = itemStyle(it, size, pal), modifier = mod)
                }
            }
        }
    }
}

@Composable
fun TefsirPage(
    q: Quran,
    page: Int,
    st: AppState,
    pal: Palette,
    side: Side,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ayahs = q.ayahsOnPage(page)
    val cur = ayahs.firstOrNull { it.idx == selected } ?: ayahs.firstOrNull() ?: q.ayahs[0]
    val info = tefsirInfo(st.tefsir)
    val surah = q.surah(cur.s)
    val mealId = q.mealId(st.meal)
    val z = st.mealZoom
    PaperPage(pal, modifier, side, onTap, onPinch = { f -> st.mealZoom = (st.mealZoom * f).coerceIn(0.6f, 2.2f) }) {
        Segmented(pal, TEFSIRLER.map { it.id to it.short }, info.id, Modifier.fillMaxWidth()) { st.tefsir = it }
        Spacer(Modifier.height(10.dp))
        Text("TEFSİR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.band.copy(alpha = 1f), letterSpacing = 0.6.sp)
        Text(info.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = pal.ink)
        Text(info.work, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = pal.muted)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.5.dp).background(pal.ink))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${surah.n}- ${surah.tr} Sûresi, ${cur.a}. âyet", fontSize = (15 * z).sp, fontWeight = FontWeight.Bold, color = pal.ink)
            Text(
                cur.ar + " " + ayahMarker(cur.a),
                style = arabicStyle(Fonts.arabic(st.font), 25f * st.arZoom, pal.ink, TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Meal: ") }
                    append(q.meal(mealId, cur.idx))
                },
                style = mealStyle(15f * z, pal.ink),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.gold.copy(alpha = 0.07f))
                    .border(1.5.dp, pal.gold, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${info.short} tefsiri", fontWeight = FontWeight.Bold, color = pal.ink, fontSize = (15 * z).sp)
                Text(
                    "Bu âyetin ${info.name} tefsiri henüz uygulamaya eklenmedi. Tefsir metni izinli bir kaynaktan alındığında burada, kitap sayfası düzeninde gösterilecek.",
                    style = mealStyle(15f * z, pal.muted),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SmallButton(pal, "‹ Önceki âyet", enabled = cur.idx > 0) { onSelect(cur.idx - 1) }
            Text("Âyete dokunarak değiştirin", fontSize = 12.sp, color = pal.muted)
            SmallButton(pal, "Sonraki âyet ›", enabled = cur.idx < q.ayahs.lastIndex) { onSelect(cur.idx + 1) }
        }
    }
}

@Composable
fun SmallButton(pal: Palette, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(pal.card)
            .border(1.dp, pal.chip, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) pal.ink else pal.muted)
    }
}

@Suppress("unused")
private fun keepImports() = fillerNoop()
private fun fillerNoop() = Unit

@Composable
@Suppress("unused")
private fun Unused(pal: Palette) { Box(Modifier.fillMaxSize()) }
