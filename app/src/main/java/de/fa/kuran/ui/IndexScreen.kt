package de.fa.kuran.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Quran
import de.fa.kuran.data.Surah
import java.util.Locale

private val TR = Locale("tr")

fun norm(s: String): String = s.lowercase(TR)
    .replace('â', 'a').replace('î', 'i').replace('û', 'u').replace('ı', 'i')
    .replace('ş', 's').replace('ğ', 'g').replace('ç', 'c').replace('ö', 'o').replace('ü', 'u')
    .replace("'", "").replace("’", "").replace("-", " ")

private data class Hit(val idx: Int, val s: Int, val a: Int, val page: Int, val text: String)

@Composable
fun IndexScreen(q: Quran, st: AppState, onGo: (Int) -> Unit, onBack: () -> Unit) {
    val pal = palette(st.theme)
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableIntStateOf(q.ayahsOnPage(st.lastPage).firstOrNull()?.s ?: 1) }
    val mealId = st.meal
    val pack = st.pack

    val nq = norm(query.trim())
    val surahs = remember(nq) {
        if (nq.isEmpty()) q.surahs
        else q.surahs.filter { (norm(it.tr).contains(nq) || norm(it.en).contains(nq)) || it.n.toString() == nq }
    }
    val hits = remember(nq, mealId, pack) {
        if (nq.length < 3) emptyList()
        else {
            val list = q.ayahs.indices.map { i ->
                (if (mealId == "feridun") FeridunMeal.meal?.getOrNull(i) else if (mealId == "tr.diyanet") DibMeal.meal?.getOrNull(i) else if (q.hasMeal(mealId)) q.mealText(mealId, i) else if (mealId == "hayrat") pack?.hayratMeal?.getOrNull(i) else pack?.elmalili?.get(i)) ?: q.builtinMeal(i)
            }
            val out = ArrayList<Hit>()
            for (i in list.indices) {
                if (list[i].isNotEmpty() && norm(list[i]).contains(nq)) {
                    val ay = q.ayahs[i]
                    out += Hit(i, ay.s, ay.a, q.pageOf(i), list[i].replace(Regex("⁽\\d+⁾"), ""))
                    if (out.size >= 200) break
                }
            }
            out
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.app).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .then(if (wide) Modifier.width(400.dp) else Modifier.fillMaxWidth())
                    .fillMaxHeight()
                    .background(pal.card.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp),
            ) {
                Row(Modifier.padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Glass(pal) { RoundIcon(pal, Icons.AutoMirrored.Filled.ArrowBack, t("Kitaba dön"), onClick = onBack) }
                    Column {
                        Text(t("Fihrist"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = pal.ink)
                        Text(t("114 sûre · 30 cüz · %1 sayfa", q.pageCount), fontSize = 13.sp, color = pal.muted)
                    }
                }
                Segmented(pal, listOf(0 to t("Sûreler"), 1 to t("Cüzler"), 2 to t("Yer imleri")), tab, Modifier.fillMaxWidth()) { tab = it }
                Spacer(Modifier.height(10.dp))
                if (tab == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(t("Sûre adı, numarası veya mealde kelime")) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) RoundIcon(pal, Icons.Filled.Close, t("Temizle"), size = 36.dp) { query = "" } },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = pal.card, unfocusedContainerColor = pal.card,
                            focusedTextColor = pal.ink, unfocusedTextColor = pal.ink,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                when (tab) {
                    0 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(surahs, key = { "s${it.n}" }) { s ->
                            SurahRow(s, q.pageLabel(q.surahPage(s.n)), pal, sel = wide && detail == s.n) { if (wide) detail = s.n else onGo(q.surahPage(s.n)) }
                        }
                        if (hits.isNotEmpty()) {
                            item(key = "h") {
                                Text(t("Mealde geçen yerler (%1)", "${hits.size}${if (hits.size >= 200) "+" else ""}"), fontWeight = FontWeight.Bold, color = pal.ink, modifier = Modifier.padding(vertical = 12.dp))
                            }
                            items(hits, key = { "a${it.idx}" }) { h ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onGo(h.page) }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                ) {
                                    Text("${q.surah(h.s).name()} ${h.s}:${h.a} · " + t("s. %1", q.pageLabel(h.page)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.accent)
                                    Text(h.text, fontSize = 14.sp, color = pal.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    1 -> JuzGrid(q, pal, Modifier.weight(1f), onGo)
                    else -> {
                        val marks = st.bookmarks.sorted()
                        if (marks.isEmpty()) {
                            Text(t("Henüz yer imi yok. Okurken üst çubuktaki yıldıza dokunun."), color = pal.muted, modifier = Modifier.padding(16.dp))
                        }
                        LazyColumn(Modifier.weight(1f)) {
                            items(marks, key = { it }) { p ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onGo(p) }.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Star, null, tint = pal.gold)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(t("Sayfa %1", q.pageLabel(p)), fontWeight = FontWeight.Bold, color = pal.ink)
                                        Text(q.surahsOnPage(p).joinToString(" · ") { it.name() }, fontSize = 13.sp, color = pal.muted)
                                    }
                                    RoundIcon(pal, Icons.Filled.Close, t("Sil"), tint = pal.muted) { st.toggleBookmark(p) }
                                }
                            }
                        }
                    }
                }
            }
            if (wide) {
                SurahDetail(q, st, pal, q.surah(detail), onGo, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SurahRow(s: Surah, pageLabel: String, pal: Palette, sel: Boolean, onClick: () -> Unit) {
    val fg = if (sel) Color.White else pal.ink
    val sub = if (sel) Color.White.copy(alpha = 0.85f) else pal.muted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (sel) pal.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (sel) Color.White.copy(alpha = 0.22f) else Color(0xFFF3EBD6)),
            contentAlignment = Alignment.Center,
        ) { Text("${s.n}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else Color(0xFF6B5222)) }
        Column(Modifier.weight(1f)) {
            Row {
                Text(s.name(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.weight(1f))
                Text(t("s. %1", pageLabel), fontSize = 13.sp, color = sub)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("%1 · %2 âyet", s.typeName(), s.ayat), fontSize = 13.sp, color = sub, modifier = Modifier.weight(1f))
                Text(s.ar, style = TextStyle(fontFamily = Fonts.amiri, fontSize = 17.sp, color = if (sel) Color.White else pal.band, textDirection = TextDirection.Rtl), maxLines = 1)
            }
        }
    }
}

@Composable
private fun JuzGrid(q: Quran, pal: Palette, modifier: Modifier, onGo: (Int) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(96.dp), modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(30) { i ->
            val j = i + 1
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(pal.card).clickable { onGo(q.juzStartPage[j]) }.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t("Cüz %1", j), fontWeight = FontWeight.Bold, color = pal.ink)
                Text(t("s. %1", q.pageLabel(q.juzStartPage[j])), fontSize = 12.sp, color = pal.muted)
            }
        }
    }
}

@Composable
private fun SurahDetail(q: Quran, st: AppState, pal: Palette, s: Surah, onGo: (Int) -> Unit, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(t("%1. sûre", s.n), fontSize = 14.sp, color = pal.muted)
        SurahHeader(s, pal, Fonts.amiri, 110.dp)
        Text(t("%1 Sûresi", s.name()), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = pal.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(s.typeName(), t("%1 âyet", s.ayat), t("Cüz %1", q.juzOfPage(q.surahPage(s.n))), t("Sayfa %1", q.pageLabel(q.surahPage(s.n)))).forEach {
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.ink, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(pal.chip).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(24.dp)).background(pal.accent).clickable { onGo(q.surahPage(s.n)) }.padding(horizontal = 24.dp, vertical = 14.dp),
        ) { Text(t("Okumaya başla"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card).clickable { onGo(st.lastPage) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.Star, null, tint = pal.accent)
            Column(Modifier.weight(1f)) {
                Text(t("Kaldığınız yer"), fontWeight = FontWeight.Bold, color = pal.ink)
                Text(t("Sayfa %1", q.pageLabel(st.lastPage)) + " · " + q.surahsOnPage(st.lastPage).joinToString(" · ") { it.name() }, fontSize = 13.sp, color = pal.muted)
            }
            Text(t("Devam et"), color = pal.accent, fontWeight = FontWeight.Bold)
        }
        Text(t("Cüze git"), fontWeight = FontWeight.Bold, color = pal.ink)
        val cur = q.juzOfPage(st.lastPage)
        (1..30).chunked(10).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { j ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (j == cur) pal.accent else pal.card)
                            .clickable { onGo(q.juzStartPage[j]) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$j", fontWeight = FontWeight.Bold, color = if (j == cur) Color.White else pal.ink) }
                }
            }
        }
    }
}
