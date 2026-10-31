package de.fa.kuran.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.kuran.data.AppState
import kotlinx.coroutines.launch
import de.fa.kuran.data.Quran

private const val SAMPLE = "إِذَا ٱلسَّمَآءُ ٱنشَقَّتْ ۝١ وَأَذِنَتْ لِرَبِّهَا وَحُقَّتْ ۝٢"

private data class FontOpt(val key: String, val title: String, val sub: String, val family: FontFamily?)

@Composable
fun SettingsScreen(q: Quran, st: AppState, onBack: () -> Unit) {
    val pal = palette(st.theme)
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            st.packUri = uri.toString()
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) scope.launch {
            st.importInfo = "Sayfalar aktarılıyor…"
            val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { de.fa.kuran.data.Pack.importZips(ctx, uris) { k -> st.importInfo = "Sayfalar aktarılıyor… $k dosya" } }.getOrDefault(-1)
            }
            st.importInfo = if (n > 0) "$n dosya aktarıldı. Zip dosyalarını artık silebilirsiniz." else "Aktarma başarısız. Lütfen zip dosyalarını tekrar seçin."
            st.packVersion++
        }
    }
    val fonts = listOf(
        FontOpt("hayrat", t("Hayrat tevâfuklu mushaf"), t("Kitabın kendi sayfaları, kelime mealiyle"), null),
    ) + listOfNotNull(
        Fonts.husrev?.let { FontOpt("husrev", t("Hüsrev hattı"), t("Hüsrev hattı üslûbunda nesih"), it) },
    ) + listOf(
        FontOpt("amiri", t("Klasik nesih"), t("Geleneksel matbaa mushafı görünümü"), Fonts.amiri),
        FontOpt("scheherazade", t("Büyük ve açık"), t("İri harekeler, kolay okunur"), Fonts.scheherazade),
        FontOpt("naskh", t("Sade, modern"), t("Ekranda en net görünen yazı"), Fonts.naskh),
    ) + listOfNotNull(
        Fonts.medina?.let { FontOpt("medina", t("Medine Mushafı"), t("Kral Fahd Kur'an Matbaası'nın yazısı"), it) },
    ) + listOf(
        FontOpt("amiri_classic", "Amiri", t("Klasik kitap neshi"), Fonts.amiriClassic),
        FontOpt("lateef", "Lateef", t("Hint–Sind mushaf üslûbu"), Fonts.lateef),
        FontOpt("harmattan", "Harmattan", t("Batı Afrika mushaf üslûbu"), Fonts.harmattan),
        FontOpt("mada", "Mada", t("Sade ve modern"), Fonts.mada),
        FontOpt("kufi", t("Kûfî"), t("Modern kûfî tarz"), Fonts.kufi),
        FontOpt("katibeh", "Katibeh", t("El yazısı havasında"), Fonts.katibeh),
    ) + listOfNotNull(
        Fonts.taha?.let { FontOpt("taha", t("Osman Taha neshi"), t("Kral Fahd · bazı işaretler eksik"), it) },
    ) + listOf(
        FontOpt("markazi", "Markazi", t("Gazete neshi"), Fonts.markazi),
        FontOpt("reem_kufi", "Reem Kufi", t("Kûfî"), Fonts.reemKufi),
        FontOpt("kufam", "Kufam", t("Kûfî"), Fonts.kufam),
        FontOpt("ruqaa", "Aref Ruqaa", t("Rık'a"), Fonts.ruqaa),
        FontOpt("gulzar", "Gulzar", t("Nesta'lîk"), Fonts.gulzar),
        FontOpt("nastaliq", "Noto Nastaliq", t("Nesta'lîk"), Fonts.nastaliq),
        FontOpt("messiri", "El Messiri", t("Modern"), Fonts.messiri),
        FontOpt("rakkas", "Rakkas", t("Dekoratif"), Fonts.rakkas),
        FontOpt("qahiri", "Qahiri", t("Eski kûfî"), Fonts.qahiri),
    )

    val cats = listOf(
        Triple("view", t("Görünüm"), Color(0xFF2F7D6D)),
        Triple("meal", t("Meal"), Color(0xFFC48A2C)),
        Triple("lang", t("Dil"), Color(0xFF3A6FD8)),
        Triple("audio", t("Sesli okuma"), Color(0xFFD2475F)),
        Triple("speech", t("Meal sesi"), Color(0xFF8A5CD6)),
        Triple("pack", t("Sayfa paketi"), Color(0xFF6D6A64)),
        Triple("about", t("Hakkında ve kaynaklar"), Color(0xFF9A948A)),
    )
    val glyph = mapOf("view" to "Aa", "meal" to "M", "lang" to "A", "audio" to "▶", "speech" to "♪", "pack" to "▣", "about" to "i")
    fun value(c: String): String = when (c) {
        "view" -> (fonts.firstOrNull { it.key == st.font }?.title ?: "")
        "meal" -> mealInfo(st.meal).short
        "lang" -> LANGS.firstOrNull { it.first == L.lang }?.second ?: ""
        "audio" -> de.fa.kuran.data.reciterById(st.reciter).name.split(' ', '-').last()
        "speech" -> when (st.audioMode) { "ar_meal" -> t("Arapça + meal"); "meal" -> t("Meal"); "ack" -> t("Açıklama"); else -> t("Arapça") }
        "pack" -> if (st.pack != null) t("Paket yüklü") else t("Paket seçilmedi")
        else -> ""
    }
    var sel by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.shBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 600.dp
        val cur = sel ?: if (wide) "view" else null
        if (!wide && sel != null) androidx.activity.compose.BackHandler { sel = null }
        val curName = cats.firstOrNull { it.first == cur }?.second ?: ""

        val list: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                IGroup(pal) {
                    cats.forEachIndexed { i, (k, name, color) ->
                        if (i > 0) ISep(pal, 58.dp)
                        Row(
                            Modifier.fillMaxWidth().background(if (wide && k == cur) pal.chip else Color.Transparent).clickable { sel = k }.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
                                Text(glyph[k] ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(name, color = pal.ink, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(value(k), color = pal.shMuted, fontSize = 15.sp, maxLines = 1, modifier = Modifier.widthIn(max = 150.dp), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("›", color = pal.shMuted, fontSize = 22.sp)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        val detail: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).widthIn(max = 900.dp)) {
                when (cur) {
                    "view" -> {
                        ILabel(pal, t("Tema"))
                        ISeg(pal, listOf("krem" to t("Krem"), "beyaz" to t("Beyaz"), "gece" to t("Gece")), st.theme) { st.theme = it }
                        ILabel(pal, t("Yazı büyüklüğü"))
                        IGroup(pal) {
                            ISlider(pal, t("Arapça"), st.arZoom, 0.6f..2.5f) { st.arZoom = it }
                            ISep(pal)
                            ISlider(pal, t("Meal"), st.mealZoom, 0.6f..2.2f) { st.mealZoom = it }
                        }
                        IFoot(pal, t("Sayfada iki parmakla da büyütüp küçültebilirsiniz."))
                        ILabel(pal, t("Arapça yazı şekli"))
                        val perRow = if (wide) 4 else 2
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            fonts.chunked(perRow).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { f -> FontCard(pal, f, st.font == f.key, st.pack?.images?.size ?: 0, Modifier.weight(1f), onPick = { picker.launch(null) }) { st.font = f.key } }
                                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    "meal" -> {
                        ILabel(pal, t("Meal"))
                        IGroup(pal) { MEALLER.forEachIndexed { i, m -> if (i > 0) ISep(pal); ICheck(pal, m.short, m.name, st.meal == m.id) { st.meal = m.id } } }
                    }
                    "lang" -> {
                        ILabel(pal, t("Dil"))
                        IGroup(pal) { LANGS.forEachIndexed { i, (k, n) -> if (i > 0) ISep(pal); ICheck(pal, n, null, L.lang == k) { st.lang = k; st.meal = defaultMealFor(k) } } }
                        IFoot(pal, t("Menülerin dili. Meal de bu dile geçer, aşağıdan ayrıca değiştirebilirsiniz."))
                    }
                    "audio" -> AudioSettings(q, st, pal)
                    "speech" -> SpeechSettings(st, pal)
                    "pack" -> {
                        ILabel(pal, t("Sayfa paketi"))
                        IGroup(pal) {
                            IRow(pal, t("Durum"), when { st.packLoading -> t("Paket yükleniyor…"); st.pack != null -> t("Paket yüklü"); else -> t("Paket seçilmedi") })
                            ISep(pal)
                            IAction(pal, t("Zip dosyalarını seç")) { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                        }
                        st.importInfo?.let { IFoot(pal, it) }
                        IFoot(pal, t("Kitap sayfaları, Hayrat meali ve tevâfuk kırmızı kelimeleri bu pakettedir. İndirdiğiniz Kuran-Parca zip dosyalarının hepsini birden seçin."))
                    }
                    else -> {
                        ILabel(pal, t("Hakkında ve kaynaklar"))
                        IGroup(pal) {
                            IRow(pal, "Kur'an metni", null, "Tanzil / alquran.cloud (Osmanî hat)"); ISep(pal)
                            IRow(pal, t("Mealler"), null, "Elmalılı, Gölpınarlı, Bubenheim, Saheeh, Müyesser, Diyanet"); ISep(pal)
                            IRow(pal, "Feridun A.", null, "Uzun Meal ve Kur'an Açıklamaları"); ISep(pal)
                            IRow(pal, t("Sesler"), null, "EveryAyah, archive.org (İshak Danış, Fatih Çollak)"); ISep(pal)
                            IRow(pal, t("Kişisel paket"), null, "Hayrat Neşriyat sayfaları ve meali (kişisel kullanım)")
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }

        Column(Modifier.fillMaxSize()) {
            // Üst çubuk: ‹ Ayarlar · başlık · Bitti
            Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (!wide && sel != null) Text("‹ " + t("Ayarlar"), color = pal.shAcc, fontSize = 17.sp, maxLines = 1, modifier = Modifier.clickable { sel = null })
                }
                Text(if (!wide && sel != null) curName else t("Ayarlar"), color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Text(t("Bitti"), color = pal.shAcc, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBack() })
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(360.dp).fillMaxHeight())
                    detail(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                if (cur == null) list(Modifier.fillMaxSize()) else detail(Modifier.fillMaxSize())
            }
        }
    }
}

// ---- Apple tarzı satırlar ----
@Composable
fun ILabel(pal: Palette, text: String) {
    Text(text.uppercase(java.util.Locale(L.lang)), color = pal.shMuted, fontSize = 13.sp, letterSpacing = 0.3.sp, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
}
@Composable
fun IFoot(pal: Palette, text: String) {
    Text(text, color = pal.shMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
}
@Composable
fun IGroup(pal: Palette, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.shCard), content = content)
}
@Composable
fun ISep(pal: Palette, start: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(0.6.dp).background(pal.shSep))
}
@Composable
fun ICheck(pal: Palette, title: String, sub: String?, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) pal.ink else pal.shMuted, fontSize = 17.sp)
            if (sub != null) Text(sub, color = pal.shMuted, fontSize = 13.sp)
        }
        if (on) Text("✓", color = pal.shAcc, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun IRow(pal: Palette, title: String, value: String?, sub: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.ink, fontSize = 17.sp)
            if (sub != null) Text(sub, color = pal.shMuted, fontSize = 13.sp)
        }
        if (value != null) Text(value, color = pal.shMuted, fontSize = 15.sp)
    }
}
@Composable
fun IAction(pal: Palette, title: String, red: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        title, color = if (!enabled) pal.shMuted else if (red) Color(0xFFD33A2C) else pal.shAcc, fontSize = 17.sp,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
@Composable
fun ISlider(pal: Palette, label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = pal.ink, fontSize = 17.sp, modifier = Modifier.widthIn(min = 90.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = pal.shAcc, inactiveTrackColor = pal.shSep), modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", color = pal.shMuted, fontSize = 15.sp, modifier = Modifier.widthIn(min = 44.dp), textAlign = TextAlign.End)
    }
}
@Composable
fun ISeg(pal: Palette, items: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(pal.shSeg).padding(2.dp)) {
        items.forEach { (k, n) ->
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(7.dp)).background(if (k == value) pal.shCard else Color.Transparent).clickable { onPick(k) },
                contentAlignment = Alignment.Center,
            ) { Text(n, color = pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}

@Composable
private fun Section(pal: Palette, title: String, sub: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = pal.ink)
        if (sub != null) Text(sub, fontSize = 13.sp, color = pal.muted)
        content()
    }
}

@Composable
private fun RadioRow(pal: Palette, text: String, sel: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Radio(pal, sel)
        Text(text, color = pal.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Radio(pal: Palette, sel: Boolean) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(if (sel) pal.accent else Color.Transparent)
            .border(2.dp, if (sel) pal.accent else pal.muted, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun FontCard(pal: Palette, f: FontOpt, sel: Boolean, scanCount: Int, modifier: Modifier, onPick: () -> Unit, onSelect: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pal.shCard)
            .border(2.5.dp, if (sel) pal.shAcc else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = { if (f.family == null && scanCount == 0) onPick() else onSelect() })
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            if (f.family != null) Text(BASMALA, style = arabicStyle(f.family, 19f, pal.ink, TextAlign.Center), maxLines = 1)
            else Text(if (scanCount > 0) t("%1 kitap sayfası hazır.", scanCount) else t("Paket klasörü seç"), color = pal.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Text(f.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = pal.ink, maxLines = 1)
        Text(f.sub, fontSize = 11.sp, color = pal.muted, maxLines = 1)
    }
}

@Composable
private fun SizeSlider(pal: Palette, label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = pal.ink, modifier = Modifier.widthIn(min = 120.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent),
            modifier = Modifier.weight(1f),
        )
        Text("${(value * 100).toInt()}%", color = pal.ink, fontWeight = FontWeight.SemiBold)
    }
}


@Composable
private fun AudioSettings(q: de.fa.kuran.data.Quran, st: AppState, pal: Palette) {
    val ver = de.fa.kuran.data.Downloads.version
    val active = de.fa.kuran.data.Downloads.active
    val curSurah = q.surahsOnPage(st.lastPage).firstOrNull()?.n ?: 1
    ILabel(pal, t("Okuyucu"))
    de.fa.kuran.data.RECITERS.forEach { r ->
        val sel = st.reciter == r.id
        val cnt = remember(ver, active, de.fa.kuran.data.Downloads.done / 50) { de.fa.kuran.data.Downloads.count(r) }
        val all = if (r.surah) 114 else q.ayahs.size
        val stt = if (cnt >= all) t("Tamamı indirildi") else if (cnt > 0) (if (r.surah) t("%1 / %2 sûre indirildi", cnt, all) else t("%1 / %2 âyet indirildi", cnt, all)) else t("İnternetten çalınır · tamamı yaklaşık %1 MB", r.approxMb)
        IGroup(pal) {
            ICheck(pal, r.name, (if (r.surah) t("Türk hafız · sûre sûre") + " · " else "") + stt, sel) { st.reciter = r.id }
            if (sel) {
                if (active == r.id) {
                    val d = de.fa.kuran.data.Downloads.done; val tot = de.fa.kuran.data.Downloads.total
                    ISep(pal)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        androidx.compose.material3.LinearProgressIndicator(progress = { if (tot > 0) d.toFloat() / tot else 0f }, modifier = Modifier.fillMaxWidth(), color = pal.shAcc)
                        Text(t("İndiriliyor… %1 / %2", d, tot), fontSize = 13.sp, color = pal.shMuted, modifier = Modifier.padding(top = 6.dp))
                    }
                    ISep(pal); IAction(pal, t("Durdur"), red = true) { de.fa.kuran.data.Downloads.cancel() }
                } else {
                    ISep(pal); IAction(pal, t("Bu sûreyi indir"), enabled = active == null) { de.fa.kuran.data.Downloads.start(r, de.fa.kuran.data.Downloads.itemsFor(q, r, curSurah)) }
                    ISep(pal); IAction(pal, t("Tümünü indir"), enabled = active == null) { de.fa.kuran.data.Downloads.start(r, de.fa.kuran.data.Downloads.itemsFor(q, r, null)) }
                    if (cnt > 0) { ISep(pal); IAction(pal, t("Sil"), red = true) { de.fa.kuran.data.Downloads.delete(r) } }
                }
            }
        }
        if (sel && de.fa.kuran.data.Downloads.failed > 0 && de.fa.kuran.data.Downloads.total > 0)
            IFoot(pal, t("%1 dosya indirilemedi. İnternet bağlantısını kontrol edip tekrar deneyin.", de.fa.kuran.data.Downloads.failed))
        Spacer(Modifier.height(8.dp))
    }
    IFoot(pal, t("İndirilen sesler telefonda kalır ve internetsiz çalınır. Kaynaklar: EveryAyah, archive.org."))
}


@Composable
private fun SpeechSettings(st: AppState, pal: Palette) {
    val sp = de.fa.kuran.data.Speech
    val lang = mealSpeechLang(st.meal)
    androidx.compose.runtime.LaunchedEffect(sp.ready, lang) { if (sp.ready) sp.loadVoices(lang) }
    ILabel(pal, t("Ne okunsun?"))
    IGroup(pal) {
        listOf("ar" to t("Arapça"), "ar_meal" to t("Arapça + meal"), "meal" to t("Meal"), "ack" to t("Açıklama")).forEachIndexed { i, (k, n) ->
            if (i > 0) ISep(pal); ICheck(pal, n, null, st.audioMode == k) { st.audioMode = k }
        }
    }
    IFoot(pal, t("Açıklama seçeneği sadece Açıklama sayfasında geçerlidir: Arapça okunmaz, her âyette önce meal, sonra açıklama okunur. Meal ve Mushaf sayfasında bu seçenekle meal okunur."))
    ILabel(pal, t("Ses"))
    IGroup(pal) {
        if (!sp.ready) IRow(pal, t("Telefonun konuşma motoru hazırlanıyor…"), null)
        else if (sp.voices.isEmpty()) IRow(pal, t("Bu dil için telefonda ses yüklü değil."), null, t("Telefon Ayarları → Genel yönetim → Metin okuma'dan Google'ın sesini indirebilirsiniz."))
        else {
            val current = sp.voiceName?.takeIf { n -> sp.voices.any { it.name == n } } ?: sp.voices.firstOrNull { it.male }?.name ?: sp.voices.first().name
            sp.voices.forEachIndexed { i, v -> if (i > 0) ISep(pal); ICheck(pal, t(v.label), t("Google sesi"), v.name == current) { sp.voiceName = v.name; st.saveSpeech(); sp.sample(lang) } }
        }
    }
    Spacer(Modifier.height(8.dp))
    IGroup(pal) {
        ISlider(pal, t("Ses kalınlığı"), 2f - sp.pitch, 0.9f..1.6f) { sp.pitch = 2f - it; st.saveSpeech() }
        ISep(pal)
        ISlider(pal, t("Konuşma hızı"), sp.rate, 0.6f..1.4f) { sp.rate = it; st.saveSpeech() }
        ISep(pal)
        IAction(pal, "▶ " + t("Örnek dinle")) { sp.sample(lang) }
    }
}
