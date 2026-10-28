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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Pack
import de.fa.kuran.data.Quran

/** Meal metni: kaynak seçimi ve gruplanmış âyetler */
data class MealLine(val idx: Int, val label: String, val text: String)

fun mealLines(q: Quran, pack: Pack?, source: String, ayahIdx: List<Int>): List<MealLine> {
    val out = ArrayList<MealLine>()
    val useHayrat = source == "hayrat" && pack?.hayratMeal != null
    for (i in ayahIdx) {
        val ay = q.ayahs[i]
        if (useHayrat) {
            val t = pack!!.hayratMeal!!.getOrNull(i) ?: ""
            if (t.isBlank()) continue
            val g = pack.hayratGroup?.getOrNull(i) ?: listOf(i)
            out += MealLine(i, g.joinToString(", ") { q.ayahs[it].a.toString() }, t)
        } else if (source == "feridun" && FeridunMeal.meal != null) {
            val t = FeridunMeal.meal!!.getOrNull(i) ?: ""
            if (t.isBlank()) continue
            val n = FeridunMeal.notes?.getOrNull(i).orEmpty()
            out += MealLine(i, "${ay.a}", if (n.isNotBlank()) "$t\u2063$n" else t)
        } else if (source == "tr.diyanet" && DibMeal.meal != null) {
            val m = DibMeal.meal!!
            val t = m.getOrNull(i) ?: ""
            if (t.isBlank()) continue
            var j = i + 1
            while (j < q.ayahs.size && q.ayahs[j].s == ay.s && m.getOrNull(j) == "") j++
            val label = if (j - 1 > i) "${ay.a}-${q.ayahs[j - 1].a}" else "${ay.a}"
            out += MealLine(i, label, t)
        } else if (q.hasMeal(source)) {
            val t = q.mealText(source, i)
            if (t.isBlank()) continue
            out += MealLine(i, "${ay.a}", t)
        } else {
            val t = pack?.elmalili?.get(i) ?: q.builtinMeal(i)
            if (t.isBlank()) continue
            // sonraki boş âyetler bu âyetle birlikte verilmiştir
            var j = i + 1
            while (j < q.ayahs.size && q.ayahs[j].s == ay.s && pack?.elmalili?.get(j) == "") j++
            val label = if (j - 1 > i) "${ay.a}-${q.ayahs[j - 1].a}" else "${ay.a}"
            out += MealLine(i, label, t)
        }
    }
    return out
}

private sealed interface MItem {
    data class Title(val text: String) : MItem
    data class Sub(val text: String) : MItem
    data class Intro(val text: String) : MItem
    data object Basmala : MItem
    data object BasmalaTr : MItem
    data class Line(val idx: Int, val text: AnnotatedString) : MItem
}

private val fnRegex = Regex("⁽(\\d+)⁾")

fun mealAnnotated(label: String, text: String, red: Color, selected: Boolean, hl: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label- ") }
    val note = if (text.contains('\u2063')) text.substringAfter('\u2063') else null
    @Suppress("NAME_SHADOWING") val text = text.substringBefore('\u2063')
    var last = 0
    for (m in fnRegex.findAll(text)) {
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = red, fontSize = 11.sp, baselineShift = BaselineShift.Superscript)) { append("(${m.groupValues[1]})") }
        last = m.range.last + 1
    }
    append(text.substring(last))
    if (!note.isNullOrBlank()) withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = 0.82.em, color = Color(0xFF8A8478))) { append("\n" + note) }
    if (selected) addStyle(SpanStyle(background = hl), 0, length)
}

private fun mealItems(q: Quran, pack: Pack?, page: Int, source: String, selected: Int?, pal: Palette): List<MItem> {
    val out = ArrayList<MItem>()
    val ayahs = q.ayahsOnPage(page)
    val lines = mealLines(q, pack, source, ayahs.map { it.idx }).associateBy { it.idx }
    for (ay in ayahs) {
        if (ay.a == 1) {
            val s = q.surah(ay.s)
            out += MItem.Title(t("%1- %2 SÛRESİ", s.n, s.name().uppercase(java.util.Locale(L.lang))))
            val yer = if (s.type == "Mekkî") t("Mekke") else t("Medîne")
            out += MItem.Sub(t("[%1 devrinde nâzil olmuştur, %2 âyettir.]", yer, s.ayat))
            if (source == "elmalili") pack?.elmaliliIntro?.get(s.n)?.takeIf { it.isNotBlank() }?.let { out += MItem.Intro(it) }
            if (s.n != 1 && s.n != 9) { out += MItem.Basmala; out += MItem.BasmalaTr }
        }
        val l = lines[ay.idx] ?: continue
        out += MItem.Line(ay.idx, mealAnnotated(l.label, l.text, pal.red, ay.idx == selected, pal.highlight))
    }
    return out
}

private fun mealStyle(size: Float, color: Color) = TextStyle(fontSize = size.sp, lineHeight = (size * 1.42f).sp, color = color)

private fun itemStyle(it: MItem, size: Float, pal: Palette): TextStyle = when (it) {
    is MItem.Title -> mealStyle(size * 1.18f, pal.ink).copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    is MItem.Sub -> mealStyle(size * 0.86f, pal.muted).copy(textAlign = TextAlign.Center)
    is MItem.Intro -> mealStyle(size * 0.86f, pal.muted).copy(fontStyle = FontStyle.Italic)
    MItem.Basmala -> arabicStyle(Fonts.amiri, size * 1.3f, pal.ink, TextAlign.Center)
    MItem.BasmalaTr -> mealStyle(size, pal.ink).copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    is MItem.Line -> mealStyle(size, pal.ink)
}

private fun itemText(it: MItem, pal: Palette): AnnotatedString = when (it) {
    is MItem.Title -> AnnotatedString(it.text)
    is MItem.Sub -> AnnotatedString(it.text)
    is MItem.Intro -> AnnotatedString(it.text)
    MItem.Basmala -> basmalaText(pal.red)
    MItem.BasmalaTr -> AnnotatedString(t("Rahmân, Rahîm olan Allah'ın ismiyle"))
    is MItem.Line -> it.text
}

private fun fitMeal(m: TextMeasurer, items: List<MItem>, pal: Palette, w: Int, h: Int, gapPx: Int): Float {
    if (w <= 0 || h <= 0) return 15f
    var lo = 11f
    var hi = 21f
    repeat(8) {
        val mid = (lo + hi) / 2f
        var total = 0
        items.forEachIndexed { i, it ->
            if (i > 0) total += gapPx
            total += m.measure(itemText(it, pal), itemStyle(it, mid, pal), constraints = Constraints(maxWidth = w)).size.height
        }
        if (total <= h) lo = mid else hi = mid
    }
    return lo
}

@Composable
fun MealPage(q: Quran, page: Int, st: AppState, pal: Palette, side: Side, selected: Int?, onAyah: (Int) -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val pack = st.pack
    val source = st.meal
    PaperPage(pal, modifier, side, onTap, onPinch = { z -> st.mealZoom = (st.mealZoom * z).coerceIn(0.6f, 2.2f) }) {
        val surahs = q.surahsOnPage(page)
        val range = if (surahs.size > 1) "${surahs.first().n}–${surahs.last().n}" else "${surahs.firstOrNull()?.n ?: ""}"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(q.pageLabel(page), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.ink)
            Text(t("Cüz: %1, Sûre: %2 (%3)", q.juzOfPage(page), range, surahs.joinToString(" – ") { it.name() }), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = pal.ink, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).height(1.5.dp).background(pal.ink))
        val items = remember(q, page, source, pack, selected, pal) { mealItems(q, pack, page, source, selected, pal) }
        val m = rememberTextMeasurer()
        val onA by rememberUpdatedState(onAyah)
        val onT by rememberUpdatedState(onTap)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gapPx = 3
            val base = remember(q, page, source, pack, constraints.maxWidth, constraints.maxHeight, pal) {
                fitMeal(m, mealItems(q, pack, page, source, null, pal), pal, constraints.maxWidth, constraints.maxHeight, gapPx)
            }
            val size = base * st.mealZoom
            val boxH = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = boxH), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items.forEach { item ->
                    val mod = if (item is MItem.Line) Modifier.fillMaxWidth().pointerInput(item.idx) { detectTapGestures(onTap = { onT() }, onLongPress = { onA(item.idx) }) }
                    else Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { _ -> onT() } }
                    Text(itemText(item, pal), style = itemStyle(item, size, pal), modifier = mod)
                }
                if (source == "hayrat" && pack?.hayratMeal == null) {
                    Text(t("Hayrat meali sayfa paketinde – şimdilik Elmalılı meali gösteriliyor."), fontSize = 11.sp, color = pal.muted)
                }
            }
        }
    }
}

@Composable
fun AciklamaPage(q: Quran, page: Int, st: AppState, pal: Palette, side: Side, selected: Int?, onSelect: (Int) -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val ayahs = q.ayahsOnPage(page)
    val cur = ayahs.firstOrNull { it.idx == selected } ?: ayahs.firstOrNull() ?: q.ayahs[0]
    val surah = q.surah(cur.s)
    val pack = st.pack
    val z = st.mealZoom
    val line = mealLines(q, pack, st.meal, listOf(cur.idx)).firstOrNull()
    PaperPage(pal, modifier, side, onTap, onPinch = { f -> st.mealZoom = (st.mealZoom * f).coerceIn(0.6f, 2.2f) }) {
        Text(t("AÇIKLAMA"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.band, letterSpacing = 0.6.sp)
        Text(t("Risale-i Nur Hâşiyeleri"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = pal.ink)
        Text("Kur'ân-ı Kerîm ve Muhtasar Kelime Meâli (Hayrat Neşriyat)", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = pal.muted)
        if (L.lang != "tr") Text(t("Hâşiyeler sadece Türkçedir."), fontSize = 12.sp, color = pal.accent)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.5.dp).background(pal.ink))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("%1- %2 Sûresi, %3. âyet", surah.n, surah.name(), cur.a), fontSize = (15 * z).sp, fontWeight = FontWeight.Bold, color = pal.ink)
            Text(
                buildAnnotatedString { cur.ar.split(' ').forEachIndexed { i, w -> if (i > 0) append(" "); if (isAllahWord(w) || Tevafuk.isRed(cur.idx, i)) withStyle(SpanStyle(color = pal.red)) { append(w) } else append(w) }; append(" " + ayahMarker(cur.a)) },
                style = arabicStyle(Fonts.arabic(st.font), 25f, pal.ink, TextAlign.Center), modifier = Modifier.fillMaxWidth(),
            )
            if (line != null) Text(mealAnnotated(line.label, line.text, pal.red, false, pal.highlight), style = mealStyle(15f * z, pal.ink))
            if (pack == null || pack.hasiyeler.isEmpty()) {
                Note(pal, t("Hâşiyeler sayfa paketinde. Ayarlar'dan paketi seçince burada görünür."))
            } else {
                val forAyah = pack.hasiyeForAyah(cur.idx)
                val onPage = if (q.hayrat) pack.hasiyeForHPage(page - 1).filter { it !in forAyah } else emptyList()
                if (forAyah.isEmpty() && onPage.isEmpty()) Note(pal, t("Bu âyet ve sayfa için hâşiye yok."))
                forAyah.forEach { HasiyeCard(pal, "(${it.n})", it.text, z, true) }
                if (onPage.isNotEmpty()) {
                    Text(t("Bu sayfanın hâşiyeleri"), fontSize = (14 * z).sp, fontWeight = FontWeight.Bold, color = pal.muted)
                    onPage.forEach { HasiyeCard(pal, "(${it.n})", it.text, z, false) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SmallButton(pal, t("‹ Önceki"), enabled = cur.idx > 0) { onSelect(cur.idx - 1) }
            Spacer(Modifier.weight(1f))
            SmallButton(pal, t("Sonraki ›"), enabled = cur.idx < q.ayahs.lastIndex) { onSelect(cur.idx + 1) }
        }
    }
}

@Composable
private fun Note(pal: Palette, text: String) {
    Text(text, fontSize = 14.sp, color = pal.muted, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.chip).padding(14.dp))
}

@Composable
private fun HasiyeCard(pal: Palette, num: String, text: String, z: Float, strong: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.gold.copy(alpha = if (strong) 0.10f else 0.05f))
            .border(1.dp, pal.gold.copy(alpha = if (strong) 1f else 0.4f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(num, color = pal.red, fontWeight = FontWeight.Bold, fontSize = (13 * z).sp)
        Text(text, style = mealStyle(15f * z, pal.ink))
    }
}

@Composable
fun SmallButton(pal: Palette, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(pal.card).border(1.dp, pal.chip, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) pal.ink else pal.muted)
    }
}
