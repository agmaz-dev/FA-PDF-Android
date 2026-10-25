package de.fa.kuran.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Ayah
import de.fa.kuran.data.Quran
import de.fa.kuran.data.Scans
import de.fa.kuran.data.Surah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Blk {
    data class Header(val surah: Surah) : Blk
    data object Basmala : Blk
    data class Para(val ayahs: List<Ayah>) : Blk
}

fun buildBlocks(q: Quran, page: Int): List<Blk> {
    val out = ArrayList<Blk>()
    var cur = ArrayList<Ayah>()
    for (ay in q.ayahsOnPage(page)) {
        if (ay.a == 1) {
            if (cur.isNotEmpty()) { out += Blk.Para(cur); cur = ArrayList() }
            out += Blk.Header(q.surah(ay.s))
            if (ay.s != 1 && ay.s != 9) out += Blk.Basmala
        }
        cur += ay
    }
    if (cur.isNotEmpty()) out += Blk.Para(cur)
    return out
}

fun arabicStyle(family: FontFamily, size: Float, color: Color, align: TextAlign = TextAlign.Justify) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = (size * 1.95f).sp,
    textAlign = align,
    textDirection = TextDirection.Rtl,
    color = color,
)

fun paraText(b: Blk.Para, selected: Int?, hl: Color): AnnotatedString = buildAnnotatedString {
    b.ayahs.forEachIndexed { i, ay ->
        val start = length
        append(ay.ar)
        append(" ")
        append(ayahMarker(ay.a))
        val end = length
        if (i < b.ayahs.lastIndex) append(" ")
        addStringAnnotation("ayah", ay.idx.toString(), start, end)
        if (ay.idx == selected) addStyle(SpanStyle(background = hl), start, end)
    }
}

private fun arabicHeight(m: TextMeasurer, blocks: List<Blk>, family: FontFamily, size: Float, w: Int, headerPx: Int, gapPx: Int): Int {
    var total = 0
    blocks.forEachIndexed { i, b ->
        if (i > 0) total += gapPx
        total += when (b) {
            is Blk.Header -> headerPx
            Blk.Basmala -> m.measure(
                AnnotatedString(BASMALA), arabicStyle(family, size, Color.Black, TextAlign.Center),
                constraints = Constraints(maxWidth = w),
            ).size.height
            is Blk.Para -> m.measure(
                paraText(b, null, Color.Transparent), arabicStyle(family, size, Color.Black),
                constraints = Constraints(maxWidth = w),
            ).size.height
        }
    }
    return total
}

private fun fitArabic(m: TextMeasurer, blocks: List<Blk>, family: FontFamily, w: Int, h: Int, headerPx: Int, gapPx: Int): Float {
    if (w <= 0 || h <= 0) return 22f
    var lo = 12f
    var hi = 34f
    repeat(9) {
        val mid = (lo + hi) / 2f
        if (arabicHeight(m, blocks, family, mid, w, headerPx, gapPx) <= h) lo = mid else hi = mid
    }
    return lo
}

@Composable
fun SurahHeader(s: Surah, pal: Palette, family: FontFamily, height: Dp) {
    val shape = RoundedCornerShape(3.dp)
    val rev = if (s.type == "Mekkî") "مَكِّيَّةٌ" else "مَدَنِيَّةٌ"
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(pal.band)
            .border(2.dp, pal.gold, shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ornament(pal.goldLight, Modifier.size(height * 0.55f))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = height * 0.1f)
                .clip(RoundedCornerShape(50))
                .background(pal.cartouche)
                .border(1.5.dp, pal.goldLight, RoundedCornerShape(50)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val big = (height.value * 0.26f)
            Text(
                "${s.ar} $rev",
                style = TextStyle(fontFamily = family, fontSize = big.sp, lineHeight = (big * 1.3f).sp, color = pal.cartoucheInk, textAlign = TextAlign.Center, textDirection = TextDirection.Rtl),
                maxLines = 1,
            )
            Text(
                "آيَاتُهَا ${arabicDigits(s.ayat)}",
                style = TextStyle(fontFamily = family, fontSize = (big * 0.72f).sp, lineHeight = (big * 0.95f).sp, color = pal.cartoucheInk, textAlign = TextAlign.Center, textDirection = TextDirection.Rtl),
                maxLines = 1,
            )
        }
        Ornament(pal.goldLight, Modifier.size(height * 0.55f))
    }
}

@Composable
fun AyahParagraph(b: Blk.Para, style: TextStyle, selected: Int?, hl: Color, onAyah: (Int) -> Unit, onTap: () -> Unit) {
    val text = remember(b, selected, hl) { paraText(b, selected, hl) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val onA by rememberUpdatedState(onAyah)
    val onT by rememberUpdatedState(onTap)
    Text(
        text,
        style = style,
        onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().pointerInput(text) {
            detectTapGestures { pos ->
                val l = layout
                if (l == null) { onT(); return@detectTapGestures }
                val off = l.getOffsetForPosition(pos)
                val ann = text.getStringAnnotations("ayah", off, off).firstOrNull()
                if (ann != null) onA(ann.item.toInt()) else onT()
            }
        },
    )
}

@Composable
fun MushafPage(
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
    val scan: Uri? = if (st.font == "hat") st.scans[page] else null
    val family = Fonts.arabic(st.font)
    PaperPage(pal, modifier, side, onTap, onPinch = { z -> st.arZoom = (st.arZoom * z).coerceIn(0.6f, 2.5f) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(arabicDigits(page), style = TextStyle(fontFamily = Fonts.amiri, fontSize = 16.sp, color = pal.ink))
            Text(
                q.surahsOnPage(page).lastOrNull()?.ar ?: "",
                style = TextStyle(fontFamily = Fonts.amiri, fontSize = 16.sp, color = pal.ink, textDirection = TextDirection.Rtl),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(4.dp))
        if (scan != null) {
            ScanImage(scan, st.arZoom, Modifier.weight(1f).fillMaxWidth())
        } else {
            if (st.font == "hat") {
                Text(
                    "Bu sayfanın taranmış resmi yok – yazı tipiyle gösteriliyor.",
                    fontSize = 11.sp, color = pal.muted, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            val frame = RoundedCornerShape(3.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(3.dp, pal.gold, frame)
                    .padding(5.dp)
                    .border(1.dp, pal.gold, frame)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                ArabicContent(q, page, family, pal, st.arZoom, selected, onAyah, onTap)
            }
        }
    }
}

@Composable
private fun ArabicContent(
    q: Quran,
    page: Int,
    family: FontFamily,
    pal: Palette,
    zoom: Float,
    selected: Int?,
    onAyah: (Int) -> Unit,
    onTap: () -> Unit,
) {
    val blocks = remember(q, page) { buildBlocks(q, page) }
    val m = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val headerH = 58.dp
        val headerPx = with(density) { headerH.roundToPx() }
        val gapPx = with(density) { 4.dp.roundToPx() }
        val base = remember(page, family, w, h) { fitArabic(m, blocks, family, w, h, headerPx, gapPx) }
        val size = base * zoom
        val boxH = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = boxH),
            verticalArrangement = if (zoom <= 1.01f) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp),
        ) {
            blocks.forEach { b ->
                when (b) {
                    is Blk.Header -> SurahHeader(b.surah, pal, family, headerH * zoom.coerceIn(1f, 1.6f))
                    Blk.Basmala -> Text(BASMALA, style = arabicStyle(family, size, pal.red, TextAlign.Center), modifier = Modifier.fillMaxWidth())
                    is Blk.Para -> AyahParagraph(b, arabicStyle(family, size, pal.ink), selected, pal.highlight, onAyah, onTap)
                }
            }
        }
    }
}

@Composable
fun ScanImage(uri: Uri, zoom: Float, modifier: Modifier) {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) { bmp = withContext(Dispatchers.IO) { Scans.load(ctx, uri, 2200) } }
    BoxWithConstraints(modifier) {
        val b = bmp
        if (b == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val z = zoom.coerceAtLeast(1f)
            val mw = maxWidth
            val mh = maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                Image(b, contentDescription = "Mushaf sayfası", modifier = Modifier.size(mw * z, mh * z), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Suppress("unused")
private val unusedBold = FontWeight.Bold
