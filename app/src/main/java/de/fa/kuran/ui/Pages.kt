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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Share
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
    data class Head(val text: String) : MItem
    data class Note(val text: String) : MItem
    data object Basmala : MItem
    data object BasmalaTr : MItem
    data class Line(val idx: Int, val text: AnnotatedString) : MItem
}

private val fnRegex = Regex("⁽(\\d+)⁾")

fun mealAnnotated(label: String, text: String, red: Color, selected: Boolean, hl: Color, gray: Color? = null): AnnotatedString = buildAnnotatedString {
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
    // âyet olmayan açıklamalar (parantez içi) açık gri
    if (gray != null) {
        val base = length - text.length
        Regex("\\([^()]*\\)").findAll(text).forEach { m ->
            if (!m.value.matches(Regex("\\(\\d+\\)"))) addStyle(SpanStyle(color = gray), base + m.range.first, base + m.range.last + 1)
        }
    }
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
            if (source == "feridun") FeridunMeal.intro[s.n]?.takeIf { it.isNotBlank() }?.let { out += MItem.Intro(it) }
            if (s.n != 1 && s.n != 9) { out += MItem.Basmala; out += MItem.BasmalaTr }
        }
        if (source == "feridun") FeridunMeal.sections[ay.idx]?.let { (h, n) ->
            out += MItem.Head(h)
            if (n.isNotBlank()) out += MItem.Note(n)
        }
        val l = lines[ay.idx] ?: continue
        out += MItem.Line(ay.idx, mealAnnotated(l.label, l.text, pal.red, ay.idx == selected, pal.highlight))
    }
    return out
}

fun mealStyle(size: Float, color: Color) = TextStyle(fontFamily = Fonts.serif, fontSize = size.sp, lineHeight = (size * 1.42f).sp, color = color)

private fun itemStyle(it: MItem, size: Float, pal: Palette): TextStyle = when (it) {
    is MItem.Title -> mealStyle(size * 1.18f, pal.ink).copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    is MItem.Sub -> mealStyle(size * 0.86f, pal.muted).copy(textAlign = TextAlign.Center)
    is MItem.Intro -> mealStyle(size * 0.86f, pal.muted).copy(fontStyle = FontStyle.Italic)
    is MItem.Head -> mealStyle(size, if (pal.ink.red + pal.ink.green + pal.ink.blue > 1.5f) Color(0xFF6CC070) else Color(0xFF2E7D32)).copy(fontWeight = FontWeight.Bold)
    is MItem.Note -> mealStyle(size * 0.8f, pal.muted)
    MItem.Basmala -> arabicStyle(Fonts.amiri, size * 1.3f, pal.ink, TextAlign.Center)
    MItem.BasmalaTr -> mealStyle(size, pal.ink).copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    is MItem.Line -> mealStyle(size, pal.ink)
}

private fun itemText(it: MItem, pal: Palette): AnnotatedString = when (it) {
    is MItem.Title -> AnnotatedString(it.text)
    is MItem.Sub -> AnnotatedString(it.text)
    is MItem.Intro -> AnnotatedString(it.text)
    is MItem.Head -> AnnotatedString(it.text)
    is MItem.Note -> AnnotatedString(it.text)
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
                    else Modifier.fillMaxWidth().padding(top = if (item is MItem.Head) 8.dp else 0.dp).pointerInput(Unit) { detectTapGestures { _ -> onT() } }
                    Text(itemText(item, pal), style = itemStyle(item, size, pal), modifier = mod)
                }
                if (source == "hayrat" && pack?.hayratMeal == null) {
                    Text(t("Hayrat meali sayfa paketinde – şimdilik Elmalılı meali gösteriliyor."), fontSize = 11.sp, color = pal.muted)
                }
            }
        }
    }
}

/** Kur'an Açıklamaları (Feridun A.) – assets/aciklama.json: sûre → âyet → başlık, Arapça ifadeler, paragraflar */
object AckData {
    class A(val title: String, val ar: List<String>, val paras: List<String>)
    private var m: Map<Int, Map<Int, A>> = emptyMap()
    fun init(ctx: android.content.Context) {
        if (m.isNotEmpty()) return
        runCatching {
            val o = org.json.JSONObject(ctx.assets.open("aciklama.json").bufferedReader().use { it.readText() })
            val out = HashMap<Int, Map<Int, A>>()
            o.keys().forEach { sk ->
                val ay = o.getJSONObject(sk).getJSONObject("ayat"); val mm = HashMap<Int, A>()
                ay.keys().forEach { ak ->
                    val e = ay.getJSONObject(ak); val ar = e.getJSONArray("ar"); val p = e.getJSONArray("p")
                    mm[ak.toInt()] = A(e.getString("t"), List(ar.length()) { ar.getString(it) }, List(p.length()) { p.getString(it) })
                }
                out[sk.toInt()] = mm
            }
            m = out
        }
    }
    fun get(s: Int, a: Int): A? = m[s]?.get(a)
    fun has() = m.isNotEmpty()
}

/** Açıklama: tek âyet (sağa sola kaydırınca bir âyet ilerler) */
@Composable
fun AciklamaAyah(q: Quran, idx: Int, st: AppState, pal: Palette, side: Side, playing: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val cur = q.ayahs[idx]
    val surah = q.surah(cur.s)
    val z = st.mealZoom
    val line = mealLines(q, st.pack, st.meal, listOf(cur.idx)).firstOrNull()
    val ack = AckData.get(cur.s, cur.a)
    val green = if (pal.ink.red + pal.ink.green + pal.ink.blue > 1.5f) Color(0xFF6CC070) else Color(0xFF2E7D32)
    PaperPage(pal, modifier, side, onTap, onPinch = { f -> st.mealZoom = (st.mealZoom * f).coerceIn(0.6f, 2.2f) }) {
        Text(t("AÇIKLAMA"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.band, letterSpacing = 0.6.sp)
        Text(t("Kur'an Açıklamaları"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = pal.ink, fontFamily = Fonts.serif)
        Text(t("Arapça anahtar ifadeler, kelime anlamları ve âyet açıklamaları"), fontSize = 12.sp, fontStyle = FontStyle.Italic, color = pal.muted)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.5.dp).background(pal.ink))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("%1- %2 Sûresi, %3. âyet", surah.n, surah.name(), cur.a), fontSize = (15 * z).sp, fontWeight = FontWeight.Bold, color = pal.ink, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(pal.accent).clickable { shareAyah(ctx, q, st, cur.idx) }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Share, t("Paylaş"), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                buildAnnotatedString { cur.ar.split(' ').forEachIndexed { i, w -> if (i > 0) append(" "); if (isAllahWord(w) || Tevafuk.isRed(cur.idx, i)) withStyle(SpanStyle(color = pal.red)) { append(w) } else append(w) }; append(" "); withStyle(SpanStyle(color = pal.gold)) { append(ayahMarker(cur.a)) } },
                style = arabicStyle(Fonts.arabic(if (st.font == "hayrat") "medina" else st.font), 25f * z.coerceIn(0.8f, 1.6f), pal.ink, TextAlign.Center),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (playing) pal.highlight else Color.Transparent),
            )
            if (line != null) Text(mealAnnotated(line.label, line.text, pal.red, false, pal.highlight), style = mealStyle(15f * z, pal.ink))
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.chip))
            if (ack != null) {
                Text(ack.title, style = mealStyle(15f * z, green).copy(fontWeight = FontWeight.Bold))
                if (ack.ar.isNotEmpty()) Text(
                    buildAnnotatedString { ack.ar.forEachIndexed { i, x -> if (i > 0) withStyle(SpanStyle(color = pal.gold)) { append("  /  ") }; append(x) } },
                    style = arabicStyle(Fonts.amiri, 20f * z.coerceIn(0.8f, 1.6f), pal.ink, TextAlign.Start), modifier = Modifier.fillMaxWidth(),
                )
                ack.paras.forEach { Text(it, style = mealStyle(15f * z, pal.ink).copy(lineHeight = (15f * z * 1.5f).sp)) }
            } else if (AckData.has()) {
                Note(pal, t("Bu sûrenin açıklaması henüz hazırlanmadı."))
            }
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
