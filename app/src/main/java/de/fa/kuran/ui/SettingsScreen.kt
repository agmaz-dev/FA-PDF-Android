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
    val glyph = mapOf("view" to "ع", "meal" to "M", "lang" to "A", "audio" to "▶", "speech" to "♪", "pack" to "▣", "about" to "i")
    val keywords = mapOf(
        "view" to "tema gece beyaz krem yazı stil font hat hüsrev medine boyut büyüt arapça theme schrift größe خط مظهر",
        "meal" to "meal tefsir hayrat elmalılı çeviri açıklama hâşiye übersetzung translation تفسير",
        "lang" to "dil language sprache türkçe deutsch english arapça لغة",
        "audio" to "ses okuyucu hafız indir dinle sudais afasi husari audio rezitator reciter تلاوة قارئ",
        "speech" to "meal sesi konuşma erkek kalın hız ses stimme speech voice صوت",
        "pack" to "paket sayfa zip klasör paket pack paket حزمة",
        "about" to "kaynak hakkında lisans quellen sources مصادر",
    )
    fun value(c: String): String = when (c) {
        "view" -> (fonts.firstOrNull { it.key == st.font }?.title ?: "") + " · " + when (st.theme) { "gece" -> t("Gece"); "beyaz" -> t("Beyaz"); else -> t("Krem kâğıt") }
        "meal" -> mealInfo(st.meal).short
        "lang" -> LANGS.firstOrNull { it.first == L.lang }?.second ?: ""
        "audio" -> de.fa.kuran.data.reciterById(st.reciter).name.split(' ', '-').last()
        "speech" -> when (st.audioMode) { "ar_meal" -> t("Arapça + meal"); "meal" -> t("Sadece meal"); else -> t("Sadece Arapça") }
        "pack" -> if (st.pack != null) t("Paket yüklü") else t("Paket seçilmedi")
        else -> ""
    }
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var sel by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.app).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 600.dp
        val cur = sel ?: if (wide) "view" else null
        if (!wide && sel != null) androidx.activity.compose.BackHandler { sel = null }

        val list: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Glass(pal) { RoundIcon(pal, Icons.AutoMirrored.Filled.ArrowBack, t("Geri"), onClick = onBack) }
                    Text(t("Ayarlar"), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = pal.ink)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = pal.ink),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.card).padding(horizontal = 16.dp, vertical = 14.dp),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(t("Ayarlarda ara"), color = pal.muted, fontSize = 16.sp); inner() } },
                )
                val qn = query.trim().lowercase()
                val shown = cats.filter { (k, name, _) -> qn.isEmpty() || name.lowercase().contains(qn) || keywords[k].orEmpty().contains(qn) }
                val groups = listOf(listOf("view", "meal", "lang"), listOf("audio", "speech"), listOf("pack", "about"))
                groups.forEach { g ->
                    val items = shown.filter { it.first in g }
                    if (items.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card)) {
                        items.forEachIndexed { i, (k, name, color) ->
                            val on = wide && k == cur
                            Row(
                                Modifier.fillMaxWidth().background(if (on) pal.accent else Color.Transparent).clickable { sel = k }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(color), contentAlignment = Alignment.Center) {
                                    Text(glyph[k] ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Text(name, color = if (on) Color.White else pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(value(k), color = if (on) Color.White.copy(alpha = 0.85f) else pal.muted, fontSize = 13.sp, maxLines = 1)
                                Text("›", color = if (on) Color.White else pal.muted, fontSize = 20.sp)
                            }
                            if (i < items.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(pal.chip))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        val detail: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!wide) Glass(pal) { RoundIcon(pal, Icons.AutoMirrored.Filled.ArrowBack, t("Geri"), onClick = { sel = null }) }
                    Text(cats.firstOrNull { it.first == cur }?.second ?: "", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = pal.ink)
                }
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    if (cur == "view") Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.paper).border(2.dp, pal.gold, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val fam = Fonts.arabic(if (st.font == "hayrat") "amiri" else st.font)
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                append("ٱلْحَمْدُ "); withStyle(androidx.compose.ui.text.SpanStyle(color = pal.red)) { append("لِلَّهِ") }; append(" رَبِّ ٱلْعَٰلَمِينَ ﴿٢﴾ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ ﴿٣﴾")
                            },
                            style = arabicStyle(fam, 26f * st.arZoom.coerceIn(0.7f, 1.6f), pal.ink, TextAlign.Center), maxLines = 2,
                        )
                    }
                    if (cur == "lang") Section(pal, "Dil · Sprache · Language", t("Menülerin dili. Meal de bu dile geçer, aşağıdan ayrıca değiştirebilirsiniz.")) {
                        Segmented(pal, LANGS, L.lang, Modifier.fillMaxWidth()) { st.lang = it; st.meal = defaultMealFor(it) }
                    }
                    if (cur == "pack") Section(pal, t("Sayfa paketi"), t("Kitap sayfaları, Hayrat meali, Elmalılı meali ve Risale-i Nur hâşiyeleri bu klasörde durur.")) {
                        val pk = st.pack
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.card).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when { st.packLoading -> t("Paket yükleniyor…"); pk != null -> t("Paket yüklü"); else -> t("Paket seçilmedi") },
                                    fontWeight = FontWeight.Bold, color = pal.ink,
                                )
                                Text(pk?.summary ?: t("Paketi açtığınız klasörü seçin (içinde h000.webp … dosyaları olmalı)."), fontSize = 13.sp, color = pal.muted)
                            }
                            SmallButton(pal, t("Zip dosyalarını seç")) { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                        }
                        st.importInfo?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal.accent) }
                        Text(t("İndirdiğiniz Kuran-Parca zip dosyalarının hepsini birden seçin. Uygulama onları kendisi açar."), fontSize = 13.sp, color = pal.muted)
                    }
                    if (cur == "view") Section(pal, t("Arapça yazı stili"), t("Size en kolay okunan yazıyı seçin. Yazı tiplerinde Allah lafızları kırmızıdır.")) {
                        val perRow = if (wide) 4 else 2
                        fonts.chunked(perRow).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { f ->
                                    FontCard(pal, f, st.font == f.key, st.pack?.images?.size ?: 0, Modifier.weight(1f), onPick = { picker.launch(null) }) { st.font = f.key }
                                }
                                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    if (cur == "audio") Section(pal, t("Sesli okuma"), t("Okuyucuyu seçin. Sesleri telefona indirirseniz internetsiz dinlersiniz; indirmezseniz internetten çalınır.")) {
                        AudioSettings(q, st, pal)
                    }
                    if (cur == "speech") Section(pal, t("Meal sesi"), t("Okuyucu Arapçayı okuduktan sonra telefon o âyetin mealini sesli okur. Hangi meal seçiliyse o okunur.")) {
                        SpeechSettings(st, pal)
                    }
                    if (cur == "meal") Section(pal, t("Meal"), t("Sol sayfada gösterilecek meal.")) {
                        MEALLER.forEach { m -> RadioRow(pal, m.name, st.meal == m.id) { st.meal = m.id } }
                    }
                    if (cur == "meal") Section(pal, t("Açıklama"), t("Âyete dokunup \"Açıklama\"yı açınca Risale-i Nur hâşiyeleri gösterilir (Hayrat baskısının hâşiye bölümünden).")) {}
                    if (cur == "view") Section(pal, t("Tema"), null) {
                        Segmented(pal, listOf("krem" to t("Krem kâğıt"), "beyaz" to t("Beyaz"), "gece" to t("Gece")), st.theme, Modifier.fillMaxWidth()) { st.theme = it }
                    }
                    if (cur == "view") Section(pal, t("Yazı boyutu"), t("Okurken iki parmakla da büyütüp küçültebilirsiniz.")) {
                        SizeSlider(pal, t("Arapça"), st.arZoom, 0.6f..2.5f) { st.arZoom = it }
                        SizeSlider(pal, t("Meal ve açıklama"), st.mealZoom, 0.6f..2.2f) { st.mealZoom = it }
                    }
                    if (cur == "about") Section(pal, t("Kaynaklar"), null) {
                        Text(
                            "Arapça metin (yazı tipleri için): Tanzil Uthmani metni. Kitap sayfaları, Hayrat meali ve hâşiyeler: Kur'ân-ı Kerîm ve Muhtasar Kelime Meâli, Hayrat Neşriyat (kişisel kullanım). " +
                                "Hayrat meali ve hâşiyeler yazı tanıma ile çıkarılıp otomatik düzeltildi; hata görürseniz bildirin. " +
                                "Yazı tipleri: Amiri Quran, Scheherazade New, Noto Naskh Arabic (SIL Open Font License).",
                            fontSize = 13.sp, color = pal.muted, lineHeight = 19.sp,
                        )
                    }
                    Spacer(Modifier.height(30.dp))

                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(340.dp).fillMaxHeight().background(pal.chip.copy(alpha = 0.35f))) { list(Modifier.fillMaxSize()) }
                Box(Modifier.fillMaxHeight().width(1.dp).background(pal.chip))
                detail(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            if (cur == null) list(Modifier.fillMaxSize()) else detail(Modifier.fillMaxSize())
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
            .background(pal.card)
            .border(2.5.dp, if (sel) pal.accent else Color.Transparent, RoundedCornerShape(16.dp))
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
    de.fa.kuran.data.RECITERS.forEach { r ->
        val sel = st.reciter == r.id
        val cnt = remember(ver, active, de.fa.kuran.data.Downloads.done / 50) { de.fa.kuran.data.Downloads.count(r) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.card).clickable { st.reciter = r.id }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Radio(pal, sel)
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = pal.ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (cnt >= q.ayahs.size) t("Tamamı indirildi") else if (cnt > 0) t("%1 / %2 âyet indirildi", cnt, q.ayahs.size) else t("İnternetten çalınır · tamamı yaklaşık %1 MB", r.approxMb),
                        fontSize = 12.sp, color = pal.muted,
                    )
                }
            }
            if (active == r.id) {
                val d = de.fa.kuran.data.Downloads.done; val tot = de.fa.kuran.data.Downloads.total
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (tot > 0) d.toFloat() / tot else 0f },
                    modifier = Modifier.fillMaxWidth(), color = pal.accent,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("İndiriliyor… %1 / %2", d, tot), fontSize = 12.sp, color = pal.muted, modifier = Modifier.weight(1f))
                    SmallButton(pal, t("Durdur")) { de.fa.kuran.data.Downloads.cancel() }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(pal, t("Bu sûreyi indir"), enabled = active == null) { st.reciter = r.id; de.fa.kuran.data.Downloads.start(r, de.fa.kuran.data.Downloads.surahItems(q, curSurah)) }
                    SmallButton(pal, t("Tümünü indir"), enabled = active == null) { st.reciter = r.id; de.fa.kuran.data.Downloads.start(r, listOf(1 to 1) + de.fa.kuran.data.Downloads.allItems(q)) }
                    if (cnt > 0) SmallButton(pal, t("Sil")) { de.fa.kuran.data.Downloads.delete(r) }
                }
                if (de.fa.kuran.data.Downloads.failed > 0 && de.fa.kuran.data.Downloads.total > 0 && sel)
                    Text(t("%1 dosya indirilemedi. İnternet bağlantısını kontrol edip tekrar deneyin.", de.fa.kuran.data.Downloads.failed), fontSize = 12.sp, color = pal.red)
            }
        }
    }
}


@Composable
private fun SpeechSettings(st: AppState, pal: Palette) {
    val sp = de.fa.kuran.data.Speech
    val lang = mealSpeechLang(st.meal)
    androidx.compose.runtime.LaunchedEffect(sp.ready, lang) { if (sp.ready) sp.loadVoices(lang) }
    Segmented(pal, listOf("ar" to t("Sadece Arapça"), "ar_meal" to t("Arapça + meal"), "meal" to t("Sadece meal")), st.audioMode, Modifier.fillMaxWidth()) { st.audioMode = it }
    if (!sp.ready) {
        Text(t("Telefonun konuşma motoru hazırlanıyor…"), fontSize = 13.sp, color = pal.muted)
    } else if (sp.voices.isEmpty()) {
        Text(t("Bu dil için telefonda ses yüklü değil. Telefon Ayarları → Genel yönetim → Metin okuma'dan Google'ın sesini indirebilirsiniz."), fontSize = 13.sp, color = pal.red)
    } else {
        val current = sp.voiceName?.takeIf { n -> sp.voices.any { it.name == n } } ?: sp.voices.firstOrNull { it.male }?.name ?: sp.voices.first().name
        sp.voices.forEach { v ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.card).clickable { sp.voiceName = v.name; st.saveSpeech() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Radio(pal, v.name == current)
                Text(t(v.label), color = pal.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                SmallButton(pal, "▶") { sp.voiceName = v.name; st.saveSpeech(); sp.sample(lang) }
            }
        }
    }
    SizeSlider(pal, t("Ses kalınlığı"), 2f - sp.pitch, 0.9f..1.6f) { sp.pitch = 2f - it; st.saveSpeech() }
    SizeSlider(pal, t("Konuşma hızı"), sp.rate, 0.6f..1.4f) { sp.rate = it; st.saveSpeech() }
    SmallButton(pal, "▶ " + t("Örnek dinle")) { sp.sample(lang) }
}
