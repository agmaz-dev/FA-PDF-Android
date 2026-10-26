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

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.app).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Glass(pal) { RoundIcon(pal, Icons.AutoMirrored.Filled.ArrowBack, t("Geri"), onClick = onBack) }
                Text(t("Ayarlar"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = pal.ink)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 880.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    Section(pal, "Dil · Sprache · Language", t("Menülerin dili. Meal de bu dile geçer, aşağıdan ayrıca değiştirebilirsiniz.")) {
                        Segmented(pal, LANGS, L.lang, Modifier.fillMaxWidth()) { st.lang = it; st.meal = defaultMealFor(it) }
                    }
                    Section(pal, t("Sayfa paketi"), t("Kitap sayfaları, Hayrat meali, Elmalılı meali ve Risale-i Nur hâşiyeleri bu klasörde durur.")) {
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
                    Section(pal, t("Arapça yazı stili"), t("Size en kolay okunan yazıyı seçin. Yazı tiplerinde Allah lafızları kırmızıdır.")) {
                        fonts.chunked(if (wide) 2 else 1).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { f ->
                                    FontCard(pal, f, st.font == f.key, st.pack?.images?.size ?: 0, Modifier.weight(1f), onPick = { picker.launch(null) }) { st.font = f.key }
                                }
                            }
                        }
                    }
                    Section(pal, t("Sesli okuma"), t("Okuyucuyu seçin. Sesleri telefona indirirseniz internetsiz dinlersiniz; indirmezseniz internetten çalınır.")) {
                        AudioSettings(q, st, pal)
                    }
                    Section(pal, t("Meal"), t("Sol sayfada gösterilecek meal.")) {
                        MEALLER.forEach { m -> RadioRow(pal, m.name, st.meal == m.id) { st.meal = m.id } }
                    }
                    Section(pal, t("Açıklama"), t("Âyete dokunup \"Açıklama\"yı açınca Risale-i Nur hâşiyeleri gösterilir (Hayrat baskısının hâşiye bölümünden).")) {}
                    Section(pal, t("Tema"), null) {
                        Segmented(pal, listOf("krem" to t("Krem kâğıt"), "beyaz" to t("Beyaz"), "gece" to t("Gece")), st.theme, Modifier.fillMaxWidth()) { st.theme = it }
                    }
                    Section(pal, t("Yazı boyutu"), t("Okurken iki parmakla da büyütüp küçültebilirsiniz.")) {
                        SizeSlider(pal, t("Arapça"), st.arZoom, 0.6f..2.5f) { st.arZoom = it }
                        SizeSlider(pal, t("Meal ve açıklama"), st.mealZoom, 0.6f..2.2f) { st.mealZoom = it }
                    }
                    Section(pal, t("Kaynaklar"), null) {
                        Text(
                            "Arapça metin (yazı tipleri için): Tanzil Uthmani metni. Kitap sayfaları, Hayrat meali ve hâşiyeler: Kur'ân-ı Kerîm ve Muhtasar Kelime Meâli, Hayrat Neşriyat (kişisel kullanım). " +
                                "Hayrat meali ve hâşiyeler yazı tanıma ile çıkarılıp otomatik düzeltildi; hata görürseniz bildirin. " +
                                "Yazı tipleri: Amiri Quran, Scheherazade New, Noto Naskh Arabic (SIL Open Font License).",
                            fontSize = 13.sp, color = pal.muted, lineHeight = 19.sp,
                        )
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
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
            .clip(RoundedCornerShape(22.dp))
            .background(pal.card)
            .border(2.5.dp, if (sel) pal.accent else pal.chip, RoundedCornerShape(22.dp))
            .clickable(onClick = onSelect)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = pal.ink)
                Text(f.sub, fontSize = 13.sp, color = pal.muted)
            }
            Radio(pal, sel)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(pal.paper)
                .border(1.5.dp, pal.gold, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (f.family != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(BASMALA, style = arabicStyle(f.family, 22f, pal.red, TextAlign.Center))
                    Text(SAMPLE, style = arabicStyle(f.family, 22f, pal.ink, TextAlign.Center))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (scanCount > 0) t("%1 kitap sayfası hazır.", scanCount) else t("Sayfa paketi seçilmedi."),
                        color = pal.ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    )
                    Text(
                        t("Kitap sayfaları sayfa paketinden gelir. Paket yoksa yazı tipiyle gösterilir."),
                        color = pal.muted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
                    )
                    if (scanCount == 0) SmallButton(pal, t("Paket klasörü seç"), onClick = onPick)
                }
            }
        }
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
