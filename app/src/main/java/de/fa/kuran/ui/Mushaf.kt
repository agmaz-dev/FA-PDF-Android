package de.fa.kuran.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Ayah
import de.fa.kuran.data.Pack
import de.fa.kuran.data.Quran
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

/** Âyet metni: Allah lafızları kırmızı, seçili âyet vurgulu */
fun paraText(b: Blk.Para, selected: Int?, hl: Color, red: Color, marker: Color, brackets: Boolean = false): AnnotatedString = buildAnnotatedString {
    b.ayahs.forEachIndexed { i, ay ->
        val start = length
        ay.ar.split(' ').forEachIndexed { wi, w ->
            if (wi > 0) append(" ")
            if (isAllahWord(w)) { val s = length; append(w); addStyle(SpanStyle(color = red), s, length) } else append(w)
        }
        append(" ")
        val ms = length
        append(if (brackets) "\uFD3F" + arabicDigits(ay.a) + "\uFD3E" else ayahMarker(ay.a))
        addStyle(SpanStyle(color = marker), ms, length)
        val end = length
        if (i < b.ayahs.lastIndex) append(" ")
        addStringAnnotation("ayah", ay.idx.toString(), start, end)
        if (ay.idx == selected) addStyle(SpanStyle(background = hl), start, end)
    }
}

fun basmalaText(red: Color): AnnotatedString = buildAnnotatedString {
    BASMALA.split(' ').forEachIndexed { i, w ->
        if (i > 0) append(" ")
        if (isAllahWord(w)) { val s = length; append(w); addStyle(SpanStyle(color = red), s, length) } else append(w)
    }
}

private fun arabicHeight(m: TextMeasurer, blocks: List<Blk>, family: FontFamily, size: Float, w: Int, headerPx: Int, gapPx: Int): Int {
    var total = 0
    blocks.forEachIndexed { i, b ->
        if (i > 0) total += gapPx
        total += when (b) {
            is Blk.Header -> headerPx
            Blk.Basmala -> m.measure(AnnotatedString(BASMALA), arabicStyle(family, size, Color.Black, TextAlign.Center), constraints = Constraints(maxWidth = w)).size.height
            is Blk.Para -> m.measure(paraText(b, null, Color.Transparent, Color.Black, Color.Black, family == Fonts.husrev), arabicStyle(family, size, Color.Black), constraints = Constraints(maxWidth = w)).size.height
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
        Modifier.fillMaxWidth().height(height).clip(shape).background(pal.band).border(2.dp, pal.gold, shape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ornament(pal.goldLight, Modifier.size(height * 0.55f))
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(vertical = height * 0.1f).clip(RoundedCornerShape(50))
                .background(pal.cartouche).border(1.5.dp, pal.goldLight, RoundedCornerShape(50)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val big = (height.value * 0.26f)
            Text("${s.ar} $rev", style = TextStyle(fontFamily = family, fontSize = big.sp, lineHeight = (big * 1.3f).sp, color = pal.cartoucheInk, textAlign = TextAlign.Center, textDirection = TextDirection.Rtl), maxLines = 1)
            Text("آيَاتُهَا ${arabicDigits(s.ayat)}", style = TextStyle(fontFamily = family, fontSize = (big * 0.72f).sp, lineHeight = (big * 0.95f).sp, color = pal.cartoucheInk, textAlign = TextAlign.Center, textDirection = TextDirection.Rtl), maxLines = 1)
        }
        Ornament(pal.goldLight, Modifier.size(height * 0.55f))
    }
}

@Composable
fun AyahParagraph(b: Blk.Para, style: TextStyle, selected: Int?, pal: Palette, onAyah: (Int) -> Unit, onTap: () -> Unit, thin: Int = 0, brackets: Boolean = false) {
    val text = remember(b, selected, pal, brackets) { paraText(b, selected, pal.highlight, pal.red, pal.gold, brackets) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val onA by rememberUpdatedState(onAyah)
    val onT by rememberUpdatedState(onTap)
    Box(Modifier.fillMaxWidth()) {
    Text(
        text,
        style = style,
        onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().pointerInput(text) {
            detectTapGestures(
                onTap = { onT() },
                onLongPress = { pos ->
                    val l = layout ?: return@detectTapGestures
                    val off = l.getOffsetForPosition(pos)
                    text.getStringAnnotations("ayah", off, off).firstOrNull()?.let { onA(it.item.toInt()) }
                },
            )
        },
    )
    ThinOverlay(text, style, thin, pal, selected = b.ayahs.firstOrNull { it.idx == selected }?.let { true } ?: false)
    }
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
    val pack: Pack? = st.pack
    val image: Uri? = if (st.font == "hayrat" && q.hayrat) pack?.images?.get(page - 1) else null
    val family = Fonts.arabic(st.font)
    if (image != null) {
        // Kitabın kendi sayfası: diziliş hiç değişmez, sadece büyür
        PaperPage(pal, modifier, side, onTap, onPinch = { z -> st.arZoom = (st.arZoom * z).coerceIn(1f, 4f) }, padding = 4.dp) {
            BookImage(image, st.arZoom, Modifier.weight(1f).fillMaxWidth(), fillWidth = side == Side.SINGLE)
        }
        return
    }
    PaperPage(pal, modifier, side, onTap, onPinch = { z -> st.arZoom = (st.arZoom * z).coerceIn(0.6f, 2.5f) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(q.pageLabel(page), style = TextStyle(fontSize = 13.sp, color = pal.muted))
            Text(q.surahsOnPage(page).lastOrNull()?.ar ?: "", style = TextStyle(fontFamily = Fonts.amiri, fontSize = 16.sp, color = pal.ink, textDirection = TextDirection.Rtl), maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        if (st.font == "hayrat") {
            Text(
                if (st.pack == null) "Kitap sayfaları için Ayarlar'dan sayfa paketini seçin – şimdilik yazı tipiyle gösteriliyor."
                else "Bu sayfanın resmi pakette yok – yazı tipiyle gösteriliyor.",
                fontSize = 11.sp, color = pal.muted, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        val frame = RoundedCornerShape(3.dp)
        Box(
            Modifier.weight(1f).fillMaxWidth().border(3.dp, pal.gold, frame).padding(5.dp).border(1.dp, pal.gold, frame)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            ArabicContent(q, page, family, pal, if (st.font == "hayrat") 1f else st.arZoom, selected, onAyah, onTap, st.thin, st.font == "husrev")
        }
    }
}

@Composable
private fun ArabicContent(q: Quran, page: Int, family: FontFamily, pal: Palette, zoom: Float, selected: Int?, onAyah: (Int) -> Unit, onTap: () -> Unit, thin: Int = 0, brackets: Boolean = false) {
    val blocks = remember(q, page) { buildBlocks(q, page) }
    val m = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val headerH = 58.dp
        val headerPx = with(density) { headerH.roundToPx() }
        val gapPx = with(density) { 4.dp.roundToPx() }
        val base = remember(q, page, family, w, h) { fitArabic(m, blocks, family, w, h, headerPx, gapPx) }
        val size = base * zoom
        val boxH = maxHeight
        val basm = remember(pal) { basmalaText(pal.red) }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = boxH),
            verticalArrangement = if (zoom <= 1.01f) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp),
        ) {
            blocks.forEach { b ->
                when (b) {
                    is Blk.Header -> SurahHeader(b.surah, pal, family, headerH * zoom.coerceIn(1f, 1.6f))
                    Blk.Basmala -> Box(Modifier.fillMaxWidth()) {
                        Text(basm, style = arabicStyle(family, size, pal.ink, TextAlign.Center), modifier = Modifier.fillMaxWidth())
                        ThinOverlay(basm, arabicStyle(family, size, pal.ink, TextAlign.Center), thin, pal)
                    }
                    is Blk.Para -> AyahParagraph(b, arabicStyle(family, size, pal.ink), selected, pal, onAyah, onTap, thin, brackets)
                }
            }
        }
    }
}

@Composable
fun BookImage(uri: Uri, zoom: Float, modifier: Modifier, fillWidth: Boolean = false) {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) { bmp = withContext(Dispatchers.IO) { Pack.loadImage(ctx, uri, 2400) } }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) {
            CircularProgressIndicator()
        } else {
            val z = zoom.coerceAtLeast(1f)
            val mw = maxWidth
            val mh = maxHeight
            val boxWider = mh.value > 0f && b.height > 0 && (mw.value / mh.value) > (b.width.toFloat() / b.height)
            if (z <= 1.01f && fillWidth && boxWider) {
                // Tek sayfa: ekranın bütün genişliğini doldurur, aşağı kaydırılarak okunur (diziliş değişmez)
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Image(b, contentDescription = "Mushaf sayfası", modifier = Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height), contentScale = ContentScale.FillWidth)
                }
            } else if (z <= 1.01f) {
                Image(b, contentDescription = "Mushaf sayfası", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    val w = if (fillWidth && boxWider) mw * z else mw * z
                    Image(b, contentDescription = "Mushaf sayfası", modifier = Modifier.width(w).aspectRatio(b.width.toFloat() / b.height), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}


/**
 * Harfleri inceltir: aynı metni kâğıt renginde ince bir çizgiyle tekrar çizer,
 * böylece harflerin kenarından biraz silinir ve içlerindeki boşluklar açılır.
 */
@Composable
fun ThinOverlay(text: AnnotatedString, style: TextStyle, thin: Int, pal: Palette, selected: Boolean = false) {
    if (thin <= 0 || selected) return
    val density = LocalDensity.current
    val px = with(density) { style.fontSize.toPx() }
    val w = px * when (thin) { 1 -> 0.022f; 2 -> 0.036f; else -> 0.052f }
    Text(
        AnnotatedString(text.text),
        style = style.copy(color = pal.paper, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = w)),
        modifier = Modifier.fillMaxWidth(),
    )
}
