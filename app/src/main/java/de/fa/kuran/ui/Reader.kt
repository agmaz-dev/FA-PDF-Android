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
    val count = if (twoUp) (q.pageCount + 1) / 2 else q.pageCount
    fun toIdx(p: Int) = (if (twoUp) (p - 1) / 2 else p - 1).coerceIn(0, count - 1)
    fun toPage(i: Int) = if (twoUp) i * 2 + 1 else i + 1
    val pager = rememberPagerState(initialPage = toIdx(st.lastPage)) { count }
    val scope = rememberCoroutineScope()
    var bars by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var hint by remember { mutableStateOf(true) }
    val page = if (twoUp && (st.lastPage - 1) / 2 == pager.currentPage) st.lastPage else toPage(pager.currentPage)

    LaunchedEffect(pager.currentPage) {
        if (!(twoUp && (st.lastPage - 1) / 2 == pager.currentPage)) st.lastPage = toPage(pager.currentPage)
    }
    LaunchedEffect(st.jump) {
        st.jump?.let { pager.scrollToPage(toIdx(it)); st.lastPage = it; st.jump = null }
    }
    LaunchedEffect(Unit) { delay(3500); hint = false }
    // Menü çubukları bir süre dokunulmazsa kendiliğinden geri çekilir
    LaunchedEffect(bars, touch, page, st.mode, st.arZoom, st.mealZoom) {
        if (bars) { delay(6000); bars = false }
    }

    val select: (Int) -> Unit = { idx ->
        selected = if (selected == idx && st.mode != ReadMode.ACIKLAMA) null else idx
        val p = q.pageOf(idx)
        if (toIdx(p) != pager.currentPage) scope.launch { pager.animateScrollToPage(toIdx(p)) }
    }
    val toggle: () -> Unit = { bars = !bars; hint = false }
    val goTo: (Int) -> Unit = { p -> touch++; scope.launch { pager.animateScrollToPage(toIdx(p.coerceIn(1, q.pageCount))) } }
    val step = if (twoUp) 2 else 1

    Box(Modifier.fillMaxSize().background(pal.app)) {
        // Kitap: sayfalar sağdan sola çevrilir. Çubuklar sayfanın üstünde yüzer, sayfayı küçültmez.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(top = if (bars) (if (wide) 124.dp else 184.dp) else 0.dp),
                beyondViewportPageCount = 1,
                pageSpacing = if (twoUp) 0.dp else 12.dp,
            ) { idx ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    if (twoUp) TwoUp(q, toPage(idx), st, pal, selected, select, toggle)
                    else Spread(q, idx + 1, st, pal, wide, selected, select, toggle)
                }
            }
        }

        // Seçili âyet kartı
        val sel = selected
        if (sel != null && st.mode == ReadMode.MUSHAF && q.pageOf(sel) == page) {
            val ay = q.ayahs[sel]
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(pal.glass)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("%1 · %2. ÂYET", q.surah(ay.s).name().uppercase(java.util.Locale(L.lang)), ay.a),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.accent, modifier = Modifier.weight(1f),
                    )
                    RoundIcon(pal, Icons.Filled.Close, t("Kapat"), size = 36.dp) { selected = null }
                }
                Text(
                    mealLines(q, st.pack, st.meal, listOf(ay.idx)).firstOrNull()?.text?.replace(Regex("⁽\\d+⁾"), "")
                        ?: t("Bu âyetin meâli bir önceki âyetle birlikte verilmiştir."),
                    fontSize = 16.sp, lineHeight = 22.sp, color = pal.ink,
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(pal, t("Açıklamayı aç")) { st.mode = ReadMode.ACIKLAMA }
                }
            }
        }

        if (hint && !bars) {
            Text(
                t("Menü için dokunun · âyet için basılı tutun"),
                color = Color.White, fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x8C1B1A17))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }

        AnimatedVisibility(
            visible = bars,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            // Üstte: menü satırı ve hemen altında sayfa kaydırma çubuğu
            Column(Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); touch++ } }) {
                TopBars(q, st, pal, page, wide, onIndex, onSettings)
                BottomBar(q, st, pal, page, wide, step, goTo)
            }
        }
    }
}

@Composable
private fun Spread(
    q: Quran,
    page: Int,
    st: AppState,
    pal: Palette,
    wide: Boolean,
    selected: Int?,
    select: (Int) -> Unit,
    toggle: () -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = if (wide) 6.dp else 2.dp, vertical = 4.dp)) {
        if (wide) {
            when (st.mode) {
                ReadMode.MUSHAF -> MushafPage(q, page, st, pal, Side.SINGLE, selected, select, toggle, Modifier.fillMaxSize())
                ReadMode.MEAL -> {
                    MealPage(q, page, st, pal, Side.LEFT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
                    Spine(pal)
                    MushafPage(q, page, st, pal, Side.RIGHT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
                }
                ReadMode.ACIKLAMA -> {
                    AciklamaPage(q, page, st, pal, Side.LEFT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
                    Spine(pal)
                    MushafPage(q, page, st, pal, Side.RIGHT, selected, select, toggle, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            val m = Modifier.fillMaxSize()
            when (st.mode) {
                ReadMode.MUSHAF -> MushafPage(q, page, st, pal, Side.SINGLE, selected, select, toggle, m)
                ReadMode.MEAL -> MealPage(q, page, st, pal, Side.SINGLE, selected, select, toggle, m)
                ReadMode.ACIKLAMA -> AciklamaPage(q, page, st, pal, Side.SINGLE, selected, select, toggle, m)
            }
        }
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
private fun TopBars(q: Quran, st: AppState, pal: Palette, page: Int, wide: Boolean, onIndex: () -> Unit, onSettings: () -> Unit) {
    val modes = listOf(ReadMode.MEAL to (if (wide) t("Meal + Mushaf") else t("Meal")), ReadMode.ACIKLAMA to t("Açıklama"), ReadMode.MUSHAF to t("Mushaf"))
    val title = t("Cüz %1 · Sayfa %2", q.juzOfPage(page), q.pageLabel(page))
    val sub = q.surahsOnPage(page).joinToString(" · ") { it.name() }
    val marked = page in st.bookmarks
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCircle(pal, Icons.Filled.Menu, t("Fihrist"), onClick = onIndex)
            // başlık ve sûre adı aynı satırda
            GlassPill(pal, Modifier.weight(1f)) {
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = pal.ink, maxLines = 1, softWrap = false)
                if (sub.isNotBlank()) {
                    Box(Modifier.padding(horizontal = 8.dp).size(4.dp).clip(CircleShape).background(pal.muted))
                    Text(sub, fontSize = 15.sp, color = pal.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (wide) {
                GlassPill(pal) { modes.forEach { (m, label) -> ModePill(pal, label, st.mode == m) { st.mode = m } } }
            }
            GlassCircle(pal, Icons.Filled.Star, if (marked) t("Yer imini kaldır") else t("Yer imi ekle"), tint = if (marked) pal.gold else pal.ink) { st.toggleBookmark(page) }
            GlassCircle(pal, Icons.Filled.Settings, t("Ayarlar"), onClick = onSettings)
        }
        if (!wide) {
            GlassPill(pal, Modifier.fillMaxWidth()) {
                modes.forEach { (m, label) -> ModePill(pal, label, st.mode == m, Modifier.weight(1f)) { st.mode = m } }
            }
        }
    }
}

/** İnce sayfa çubuğu (menünün altında) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(q: Quran, st: AppState, pal: Palette, page: Int, wide: Boolean, step: Int, goTo: (Int) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag?.toInt() ?: page
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).height(36.dp)
            .clip(RoundedCornerShape(18.dp)).background(pal.glass).border(1.dp, pal.glassLine, RoundedCornerShape(18.dp)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Kitap sağdan sola: sol ok = sonraki sayfa
        Box(Modifier.size(30.dp).clip(CircleShape).clickable { goTo(page + step) }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Sonraki sayfa"), tint = pal.muted, modifier = Modifier.size(20.dp))
        }
        val range = 1f..q.pageCount.toFloat()
        val v = drag ?: page.toFloat()
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Slider(
                value = v,
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { goTo(it.toInt()) }; drag = null },
                valueRange = range,
                modifier = Modifier.weight(1f).height(30.dp),
                thumb = {
                    Box(Modifier.size(18.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
                },
                track = {
                    val frac = ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(pal.muted.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(pal.accent))
                    }
                },
            )
        }
        Text(q.pageLabel(shown), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.ink, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        Box(Modifier.size(30.dp).clip(CircleShape).clickable { goTo(page - step) }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Önceki sayfa"), tint = pal.muted, modifier = Modifier.size(20.dp))
        }
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
