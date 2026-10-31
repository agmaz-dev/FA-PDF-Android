package de.fa.kuran.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Quran
import de.fa.kuran.data.ReadMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(q: Quran, st: AppState, onIndex: () -> Unit, onSettings: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(pal0(st))) {
        val wide = maxWidth >= 600.dp
        // Mushaf her zaman tek sayfa, ekranı dolduracak kadar büyük
        val twoUp = false
        androidx.compose.runtime.key(twoUp) {
            ReaderBody(q, st, wide, twoUp, onIndex, onSettings)
        }
    }
}

@Composable
private fun pal0(st: AppState) = palette(st.theme).app

@Composable
private fun ReaderBody(q: Quran, st: AppState, wide: Boolean, twoUp: Boolean, onIndex: () -> Unit, onSettings: () -> Unit) {
    val pal = palette(st.theme)
    val ack = st.mode == ReadMode.ACIKLAMA
    val audio = de.fa.kuran.data.Audio
    var selected by remember { mutableStateOf<Int?>(null) }
    var bars by remember { mutableStateOf(true) }
    var touch by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    // Açıklamada her sayfa bir âyet; diğerlerinde bir mushaf sayfası
    val startAyah = remember(ack) { selected ?: audio.playing ?: q.ayahsOnPage(st.lastPage).firstOrNull()?.idx ?: 0 }
    val count = if (ack) q.ayahs.size else q.pageCount
    val pager = androidx.compose.runtime.key(ack) { rememberPagerState(initialPage = if (ack) startAyah else (st.lastPage - 1).coerceIn(0, q.pageCount - 1)) { count } }
    val page = if (ack) q.pageOf(pager.currentPage) else pager.currentPage + 1
    val curAyah: Int? = if (ack) pager.currentPage else null

    LaunchedEffect(page) { st.lastPage = page }
    LaunchedEffect(st.jump) {
        st.jump?.let { p -> if (ack) pager.scrollToPage(q.ayahsOnPage(p).firstOrNull()?.idx ?: 0) else pager.scrollToPage(p - 1); st.lastPage = p; st.jump = null }
    }
    // Menüler bir süre dokunulmazsa kendiliğinden kapanır
    LaunchedEffect(bars, touch, st.mode) { if (bars) { delay(6000); bars = false } }

    val goPage: (Int) -> Unit = { p ->
        val pp = p.coerceIn(1, q.pageCount); touch++
        scope.launch { if (ack) pager.scrollToPage(q.ayahsOnPage(pp).firstOrNull()?.idx ?: 0) else pager.scrollToPage(pp - 1) }
    }
    val select: (Int) -> Unit = { idx ->
        selected = if (selected == idx && !ack) null else idx
        scope.launch { if (ack) pager.animateScrollToPage(idx) else { val p = q.pageOf(idx); if (p - 1 != pager.currentPage) pager.animateScrollToPage(p - 1) } }
    }
    // Sayfaya dokunmak menüleri açar / kapatır
    val toggle: () -> Unit = { bars = !bars }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val share: (Int) -> Unit = { idx -> shareAyah(ctx, q, st, idx) }
    val playIdx = audio.playing
    val hl = playIdx ?: selected
    audio.mealOf = { i -> mealOnly(mealLines(q, st.pack, st.meal, listOf(i)).firstOrNull()?.text ?: "") }
    audio.ackOf = { i -> val a = q.ayahs[i]; AckData.get(a.s, a.a)?.let { it.title + ". " + it.paras.joinToString(" ") } }
    audio.onAckPage = { st.mode == ReadMode.ACIKLAMA }
    audio.mealLang = mealSpeechLang(st.meal)
    val onPlay: () -> Unit = {
        val start = curAyah ?: selected?.takeIf { q.pageOf(it) == page } ?: q.ayahsOnPage(page).firstOrNull()?.idx ?: 0
        // durdurulup başka sayfaya geçildiyse şimdi açık olan yerden başla
        val moved = playIdx != null && audio.paused && (if (ack) playIdx != curAyah else q.pageOf(playIdx) != page)
        if (moved) audio.stop()
        if (audio.playing == null) audio.play(q, start) else audio.toggle()
    }
    // okunan âyet başka yerdeyse oraya geç
    LaunchedEffect(playIdx, ack) {
        val i = playIdx ?: return@LaunchedEffect
        if (ack) { if (i != pager.currentPage) pager.animateScrollToPage(i) }
        else { val p = q.pageOf(i) - 1; if (p != pager.currentPage) pager.animateScrollToPage(p) }
    }

    Box(Modifier.fillMaxSize().background(if (wide) pal.app else pal.paper)) {
        // Kitap: sayfalar sağdan sola çevrilir. Menüler sayfanın üstünde yüzer, sayfayı küçültmez.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Top)),
                beyondViewportPageCount = 1,
                pageSpacing = if (wide) 12.dp else 0.dp,
            ) { idx ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    if (ack) AckSpread(q, idx, st, pal, wide, playIdx == idx, hl, select, toggle)
                    else Spread(q, idx + 1, st, pal, wide, hl, select, toggle)
                }
            }
        }

        val bottomPad = if (bars) 74.dp else 20.dp
        // Seçili âyet kartı (Mushaf)
        val sel = selected
        if (sel != null && st.mode == ReadMode.MUSHAF && q.pageOf(sel) == page) {
            val ay = q.ayahs[sel]
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad, start = 16.dp, end = 16.dp).widthIn(max = 560.dp).fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(pal.glass).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("%1 · %2. ÂYET", q.surah(ay.s).name().uppercase(java.util.Locale(L.lang)), ay.a), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.accent, modifier = Modifier.weight(1f))
                    RoundIcon(pal, Icons.Filled.Close, t("Kapat"), size = 36.dp) { selected = null }
                }
                Text(
                    mealLines(q, st.pack, st.meal, listOf(ay.idx)).firstOrNull()?.text?.let { mealOnly(it) }?.replace(Regex("⁽\\d+⁾"), "") ?: t("Bu âyetin meâli bir önceki âyetle birlikte verilmiştir."),
                    style = mealStyle(16f, pal.ink), modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(pal, t("Paylaş")) { share(ay.idx) }
                    SmallButton(pal, t("Açıklamayı aç")) { st.mode = ReadMode.ACIKLAMA }
                }
            }
        }
        // Mealde: seçili âyet için paylaş
        if (sel != null && st.mode == ReadMode.MEAL && q.pageOf(sel) == page) {
            val ay = q.ayahs[sel]
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad).shadow(8.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp)).background(pal.glass).border(1.dp, pal.glassLine, RoundedCornerShape(26.dp)).padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(pal.accent).clickable { share(ay.idx) }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(t("Paylaş") + " · " + q.surah(ay.s).name() + " " + ay.s + ":" + ay.a, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                RoundIcon(pal, Icons.Filled.Close, t("Kapat"), size = 42.dp) { selected = null }
            }
        }
        audio.error?.let {
            LaunchedEffect(it) { delay(4000); audio.error = null }
            Text(
                t("Ses çalınamadı. İnternet bağlantısını kontrol edin ya da sesleri Ayarlar'dan indirin."),
                color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad + 50.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xE0B03030)).padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }

        // Üstte tek ince sıra + sayfa çubuğu
        AnimatedVisibility(
            visible = bars, modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Column(
                Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Top))
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); touch++ } },
            ) {
                TopRow(q, st, pal, page, wide, onIndex, onSettings, onPlay)
                PageSlider(q, pal, page, goPage)
            }
        }
        // Altta: Meal / Mushaf / Açıklama
        AnimatedVisibility(
            visible = bars, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(
                Modifier.padding(bottom = 14.dp).height(42.dp).shadow(8.dp, RoundedCornerShape(21.dp)).clip(RoundedCornerShape(21.dp))
                    .background(pal.glass).border(1.dp, pal.glassLine, RoundedCornerShape(21.dp)).padding(3.dp)
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); touch++ } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(ReadMode.MEAL to t("Meal"), ReadMode.MUSHAF to t("Mushaf"), ReadMode.ACIKLAMA to t("Açıklama")).forEach { (m, label) ->
                    ModePill(pal, label, st.mode == m) { if (m == ReadMode.ACIKLAMA && selected != null) Unit; st.mode = m }
                }
            }
        }
    }
}

@Composable
private fun AckSpread(q: Quran, idx: Int, st: AppState, pal: Palette, wide: Boolean, playing: Boolean, hl: Int?, select: (Int) -> Unit, toggle: () -> Unit) {
    if (wide) Row(Modifier.fillMaxSize().padding(6.dp)) {
        AciklamaAyah(q, idx, st, pal, Side.LEFT, playing, toggle, Modifier.weight(1f).fillMaxHeight())
        Spine(pal)
        MushafPage(q, q.pageOf(idx), st, pal, Side.RIGHT, hl, select, toggle, Modifier.weight(1f).fillMaxHeight())
    } else AciklamaAyah(q, idx, st, pal, Side.SINGLE, playing, toggle, Modifier.fillMaxSize())
}

@Composable
private fun Spread(q: Quran, page: Int, st: AppState, pal: Palette, wide: Boolean, selected: Int?, select: (Int) -> Unit, toggle: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = if (wide) 6.dp else 0.dp, vertical = if (wide) 6.dp else 0.dp)) {
        if (wide && st.mode == ReadMode.MEAL) {
            MealPage(q, page, st, pal, Side.LEFT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
            Spine(pal)
            MushafPage(q, page, st, pal, Side.RIGHT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
        } else when (st.mode) {
            ReadMode.MEAL -> MealPage(q, page, st, pal, Side.SINGLE, selected, select, toggle, Modifier.fillMaxSize())
            else -> MushafPage(q, page, st, pal, Side.SINGLE, selected, select, toggle, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(width = 5.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.size(width = 5.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
private fun PlayCircle(pal: Palette, onClick: () -> Unit) {
    val playing = de.fa.kuran.data.Audio.playing != null && !de.fa.kuran.data.Audio.paused
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(pal.glass).border(1.dp, pal.glassLine, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) PauseGlyph(pal.ink)
        else androidx.compose.material3.Icon(Icons.Filled.PlayArrow, t("Sesli oku"), tint = pal.ink, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun GlassCircle(pal: Palette, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = pal.ink, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(pal.glass).border(1.dp, pal.glassLine, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun GlassPill(pal: Palette, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(pal.glass).border(1.dp, pal.glassLine, RoundedCornerShape(26.dp)).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ModePill(pal: Palette, text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(21.dp)).background(if (selected) pal.accent else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Color.White else pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun SmallCircle(pal: Palette, onClick: () -> Unit, bg: Color = pal.glass, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(bg).border(1.dp, pal.glassLine, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TopRow(q: Quran, st: AppState, pal: Palette, page: Int, wide: Boolean, onIndex: () -> Unit, onSettings: () -> Unit, onPlay: () -> Unit) {
    val marked = page in st.bookmarks
    val playing = de.fa.kuran.data.Audio.playing != null && !de.fa.kuran.data.Audio.paused
    Row(
        Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(start = 10.dp, end = 10.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallCircle(pal, onIndex) { androidx.compose.material3.Icon(Icons.Filled.Menu, t("Fihrist"), tint = pal.ink, modifier = Modifier.size(20.dp)) }
        Row(
            Modifier.weight(1f).height(40.dp).shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(pal.glass)
                .border(1.dp, pal.glassLine, RoundedCornerShape(20.dp)).clickable(onClick = onIndex).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(q.surahsOnPage(page).firstOrNull()?.name() ?: "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = pal.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  · " + q.pageLabel(page), fontSize = 15.sp, color = pal.muted, maxLines = 1)
        }
        SmallCircle(pal, onPlay, bg = pal.accent) {
            if (playing) PauseGlyph(Color.White) else androidx.compose.material3.Icon(Icons.Filled.PlayArrow, t("Sesli oku"), tint = Color.White, modifier = Modifier.size(22.dp))
        }
        SmallCircle(pal, { st.toggleBookmark(page) }) { androidx.compose.material3.Icon(Icons.Filled.Star, if (marked) t("Yer imini kaldır") else t("Yer imi ekle"), tint = if (marked) pal.gold else pal.ink, modifier = Modifier.size(20.dp)) }
        SmallCircle(pal, onSettings) { androidx.compose.material3.Icon(Icons.Filled.Settings, t("Ayarlar"), tint = pal.ink, modifier = Modifier.size(20.dp)) }
    }
}

/** İnce sayfa çubuğu (üst sıranın altında) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSlider(q: Quran, pal: Palette, page: Int, goTo: (Int) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val range = 1f..q.pageCount.toFloat()
    val v = drag ?: page.toFloat()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Slider(
            value = v, onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { goTo(it.toInt()) }; drag = null },
            valueRange = range, modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(horizontal = 28.dp).height(26.dp),
            thumb = { Box(Modifier.size(16.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(pal.accent).border(2.dp, Color.White, CircleShape)) },
            track = {
                val frac = ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(pal.muted.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(pal.accent))
                }
            },
        )
    }
}


/** Açık kitap: sağda tek numaralı sayfa, solda bir sonraki sayfa – ekranı tamamen doldurur */
@Composable
private fun TwoUp(q: Quran, right: Int, st: AppState, pal: Palette, selected: Int?, select: (Int) -> Unit, toggle: () -> Unit) {
    val left = right + 1
    Row(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
        if (left <= q.pageCount) {
            MushafPage(q, left, st, pal, Side.LEFT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
        } else {
            Box(Modifier.weight(1f).fillMaxHeight())
        }
        Spine(pal)
        MushafPage(q, right, st, pal, Side.RIGHT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
    }
}


/** Âyeti Arapçası ve seçili mealiyle paylaşır (WhatsApp vb.) */
fun shareAyah(ctx: android.content.Context, q: Quran, st: AppState, idx: Int) {
    val ay = q.ayahs[idx]
    val meal = mealLines(q, st.pack, st.meal, listOf(idx)).firstOrNull()?.text?.let { mealOnly(it) }?.replace(Regex("⁽\\d+⁾|\\(\\d+\\)"), "")?.trim() ?: ""
    val ref = q.surah(ay.s).name() + " " + ay.s + ":" + ay.a
    val text = buildString {
        append(ay.ar).append(" ﴿").append(arabicDigits(ay.a)).append("﴾")
        if (meal.isNotBlank()) append("\n\n").append(meal)
        append("\n\n— ").append(ref)
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        putExtra(android.content.Intent.EXTRA_SUBJECT, ref)
    }
    ctx.startActivity(android.content.Intent.createChooser(send, t("Paylaş")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}


fun mealSpeechLang(meal: String): String = when {
    meal.startsWith("de.") -> "de"
    meal.startsWith("en.") -> "en"
    meal.startsWith("ar.") -> "ar"
    else -> "tr"
}
