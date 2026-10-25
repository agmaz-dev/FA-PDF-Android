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
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Quran
import de.fa.kuran.data.ReadMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(q: Quran, st: AppState, onIndex: () -> Unit, onSettings: () -> Unit) {
    val pal = palette(st.theme)
    val pager = rememberPagerState(initialPage = (st.lastPage - 1).coerceIn(0, q.pageCount - 1)) { q.pageCount }
    val scope = rememberCoroutineScope()
    var bars by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var hint by remember { mutableStateOf(true) }
    val page = pager.currentPage + 1

    LaunchedEffect(pager.currentPage) { st.lastPage = pager.currentPage + 1 }
    LaunchedEffect(st.jump) {
        st.jump?.let { pager.scrollToPage((it - 1).coerceIn(0, q.pageCount - 1)); st.jump = null }
    }
    LaunchedEffect(Unit) { delay(3500); hint = false }

    val select: (Int) -> Unit = { idx ->
        selected = if (selected == idx && st.mode != ReadMode.ACIKLAMA) null else idx
        val p = q.pageOf(idx)
        if (p != page) scope.launch { pager.animateScrollToPage(p - 1) }
    }
    val toggle: () -> Unit = { bars = !bars; hint = false }
    val goTo: (Int) -> Unit = { p -> scope.launch { pager.animateScrollToPage((p - 1).coerceIn(0, q.pageCount - 1)) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.app)) {
        val wide = maxWidth >= 600.dp

        // Kitap: sayfalar sağdan sola çevrilir
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(top = if (bars) (if (wide) 84.dp else 132.dp) else 6.dp, bottom = if (bars) 84.dp else 6.dp),
                beyondViewportPageCount = 1,
                pageSpacing = 12.dp,
            ) { idx ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Spread(q, idx + 1, st, pal, wide, selected, select, toggle)
                }
            }
        }

        // Seçili âyet kartı
        val sel = selected
        if (sel != null && st.mode != ReadMode.ACIKLAMA && q.pageOf(sel) == page) {
            val ay = q.ayahs[sel]
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (bars) 92.dp else 20.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(pal.glass)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${q.surah(ay.s).tr.uppercase(java.util.Locale("tr"))} · ${ay.a}. ÂYET",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.accent, modifier = Modifier.weight(1f),
                    )
                    RoundIcon(pal, Icons.Filled.Close, "Kapat", size = 36.dp) { selected = null }
                }
                Text(
                    mealLines(q, st.pack, st.meal, listOf(ay.idx)).firstOrNull()?.text?.replace(Regex("⁽\\d+⁾"), "")
                        ?: "Bu âyetin meâli bir önceki âyetle birlikte verilmiştir.",
                    fontSize = 16.sp, lineHeight = 22.sp, color = pal.ink,
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(pal, "Açıklamayı aç") { st.mode = ReadMode.ACIKLAMA }
                }
            }
        }

        if (hint && !bars) {
            Text(
                "Menü için sayfaya dokunun",
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
            TopBars(q, st, pal, page, wide, onIndex, onSettings)
        }
        AnimatedVisibility(
            visible = bars,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            BottomBar(q, st, pal, page, wide, goTo)
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
    Row(Modifier.fillMaxSize().padding(horizontal = if (wide) 10.dp else 4.dp)) {
        if (wide) {
            when (st.mode) {
                ReadMode.MUSHAF -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MushafPage(q, page, st, pal, Side.SINGLE, selected, select, toggle, Modifier.fillMaxHeight().fillMaxWidth(0.64f))
                }
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
private fun TopBars(q: Quran, st: AppState, pal: Palette, page: Int, wide: Boolean, onIndex: () -> Unit, onSettings: () -> Unit) {
    val modes = listOf(ReadMode.MEAL to (if (wide) "Meal + Mushaf" else "Meal"), ReadMode.ACIKLAMA to "Açıklama", ReadMode.MUSHAF to "Mushaf")
    val title = "Cüz ${q.juzOfPage(page)} · Sayfa ${q.pageLabel(page)}"
    val sub = q.surahsOnPage(page).joinToString(" · ") { it.tr }
    val marked = page in st.bookmarks
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Glass(pal, shape = RoundedCornerShape(26.dp)) { RoundIcon(pal, Icons.Filled.Menu, "Fihrist", onClick = onIndex) }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(pal.glass)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = pal.ink, maxLines = 1)
                Text(sub, fontSize = 12.sp, color = pal.muted, maxLines = 1)
            }
            if (wide) {
                Glass(pal) {
                    modes.forEach { (m, label) -> TextButtonPill(pal, label, st.mode == m) { st.mode = m } }
                }
                Glass(pal) { ZoomControls(st, pal) }
            }
            Glass(pal) {
                RoundIcon(pal, Icons.Filled.Star, if (marked) "Yer imini kaldır" else "Yer imi ekle", tint = if (marked) pal.gold else pal.muted) { st.toggleBookmark(page) }
                RoundIcon(pal, Icons.Filled.Settings, "Ayarlar", onClick = onSettings)
            }
        }
        if (!wide) {
            Glass(pal, Modifier.fillMaxWidth()) {
                modes.forEach { (m, label) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TextButtonPill(pal, label, st.mode == m) { st.mode = m }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomControls(st: AppState, pal: Palette) {
    TextButtonPill(pal, "A−") {
        st.arZoom = (st.arZoom - 0.1f).coerceIn(0.6f, 2.5f); st.mealZoom = (st.mealZoom - 0.1f).coerceIn(0.6f, 2.2f)
    }
    Text("${(st.arZoom * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.ink, modifier = Modifier.padding(horizontal = 4.dp))
    TextButtonPill(pal, "A+") {
        st.arZoom = (st.arZoom + 0.1f).coerceIn(0.6f, 2.5f); st.mealZoom = (st.mealZoom + 0.1f).coerceIn(0.6f, 2.2f)
    }
}

@Composable
private fun BottomBar(q: Quran, st: AppState, pal: Palette, page: Int, wide: Boolean, goTo: (Int) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag?.toInt() ?: page
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!wide) Glass(pal) { ZoomControls(st, pal) }
        Glass(pal, Modifier.weight(1f)) {
            // Kitap sağdan sola: sol ok = sonraki sayfa
            RoundIcon(pal, Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Sonraki sayfa") { goTo(page + 1) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Slider(
                    value = drag ?: page.toFloat(),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { goTo(it.toInt()) }; drag = null },
                    valueRange = 1f..q.pageCount.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(q.pageLabel(shown), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.ink, modifier = Modifier.width(36.dp))
            RoundIcon(pal, Icons.AutoMirrored.Filled.KeyboardArrowRight, "Önceki sayfa") { goTo(page - 1) }
        }
    }
    Spacer(Modifier.width(0.dp))
}
