package de.fapdf.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.fapdf.pdf.PdfPageSource
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Bitmap-Cache pro geöffneter Dokumentversion. */
class PageBitmapCache(maxBytes: Int = (Runtime.getRuntime().maxMemory() / 6).toInt()) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(k: String): Bitmap? = cache.get(k)
    fun put(k: String, b: Bitmap) { cache.put(k, b) }
    fun clear() = cache.evictAll()
}

/** Gesten auf einer Seite – Koordinaten immer in Ansichtspunkten der PDF-Seite. */
class PageGestures(
    val onTap: ((page: Int, u: Float, v: Float) -> Unit)? = null,
    val dragEnabled: Boolean = false,
    val onDragStart: (page: Int, u: Float, v: Float) -> Unit = { _, _, _ -> },
    val onDrag: (page: Int, u: Float, v: Float) -> Unit = { _, _, _ -> },
    val onDragEnd: (page: Int) -> Unit = {},
)

/**
 * Scrollbare, zoombare Seitenansicht. Zwei-Finger-Geste zoomt (1×–4×) um den Punkt zwischen den Fingern und
 * verschiebt die Seite gleichzeitig; bei Zoom kann einfach waagerecht gezogen werden. Im Lesemodus zoomt
 * Doppeltippen auf die getippte Stelle. [overlay] zeichnet Auswahl, Felder usw. über jede Seite.
 */
@Composable
fun PdfPagesView(
    source: PdfPageSource,
    versionKey: Any,
    cache: PageBitmapCache,
    listState: LazyListState,
    zoom: Float,
    onZoom: (Float) -> Unit,
    scrollEnabled: Boolean,
    gestures: PageGestures,
    modifier: Modifier = Modifier,
    overlay: @Composable (page: Int, pxPerPt: Float) -> Unit = { _, _ -> },
) {
    val zoomCallback by rememberUpdatedState(onZoom)
    val currentGestures by rememberUpdatedState(gestures)
    var hScroll by remember { mutableFloatStateOf(0f) }
    var appliedZoom by remember { mutableFloatStateOf(zoom) }
    BoxWithConstraints(modifier.fillMaxSize().background(FaColors.PageArea).clipToBounds()) {
        val density = LocalDensity.current
        val padPx = with(density) { 12.dp.toPx() }
        val gapPx = with(density) { 12.dp.toPx() }
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        val pageSizes = source.pageSizes
        // Breites Fenster (aufgeklapptes Fold): bei Zoom 1 ist die ganze Seite sichtbar, nicht nur die Breite gefüllt
        val wide = viewportW >= with(density) { 600.dp.toPx() }
        val firstAspect = pageSizes.firstOrNull()?.let { it.first / it.second } ?: 0f
        // Höhe für „ganze Seite sichtbar“ kommt vom Bildschirm, nicht vom Fenster: öffnet sich die Tastatur, bleibt die Seite gleich groß
        val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * density.density
        val baseW = ZoomMath.basePageWidth(viewportW, maxOf(viewportH, screenH * 0.72f), padPx, gapPx, firstAspect, wide)
        val pageWidthPx = baseW * zoom
        val contentW = ZoomMath.contentWidth(pageWidthPx, viewportW, padPx)
        val maxScroll = (contentW - viewportW).coerceAtLeast(0f)
        val x = hScroll.coerceIn(0f, maxScroll)
        val baseRef by rememberUpdatedState(baseW)
        val sizesRef by rememberUpdatedState(pageSizes)
        val viewportRef by rememberUpdatedState(Size(viewportW, viewportH))
        val maxScrollRef by rememberUpdatedState(maxScroll)

        /** Wendet Zoomänderung und Fingerbewegung an; der Fokuspunkt bleibt unter den Fingern. */
        fun applyTransform(focal: Offset, pan: Offset, zoomChange: Float, notify: Boolean = true) {
            val oldZoom = appliedZoom
            val newZoom = ZoomMath.clampZoom(oldZoom * zoomChange)
            val ratio = newZoom / oldZoom
            val vp = viewportRef
            val oldPageW = baseRef * oldZoom
            val newPageW = baseRef * newZoom
            val oldHeights = ZoomMath.itemHeights(sizesRef, oldPageW, gapPx)
            val newHeights = ZoomMath.itemHeights(sizesRef, newPageW, gapPx)
            val oldAbs = ZoomMath.absoluteScroll(oldHeights, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            val newAbs = ZoomMath.newVerticalScroll(oldAbs, focal.y, pan.y, ratio, newHeights, vp.height)
            val (idx, off) = ZoomMath.indexAndOffset(newHeights, newAbs)
            hScroll = ZoomMath.newScroll(
                hScroll.coerceIn(0f, maxScrollRef), focal.x, pan.x, ratio,
                ZoomMath.margin(oldPageW, vp.width, padPx), ZoomMath.margin(newPageW, vp.width, padPx),
                ZoomMath.contentWidth(newPageW, vp.width, padPx), vp.width,
            )
            appliedZoom = newZoom
            if (notify && newZoom != oldZoom) zoomCallback(newZoom)
            listState.requestScrollToItem(idx, off)
        }

        // Zoom von außen (+/−-Tasten): um die Bildschirmmitte statt um die linke obere Ecke
        LaunchedEffect(zoom) {
            if (zoom != appliedZoom) {
                val ratio = zoom / appliedZoom
                applyTransform(Offset(viewportRef.width / 2f, viewportRef.height / 2f), Offset.Zero, ratio, notify = false)
            }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var lastTapTime = 0L
                var lastTapPos = Offset.Zero
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var multi = false
                    var moved = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            multi = true
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (z != 1f || pan != Offset.Zero) {
                                applyTransform(event.calculateCentroid(useCurrent = true), pan, z)
                                event.changes.forEach { it.consume() }
                            }
                        } else if ((event.changes.firstOrNull()?.position ?: first.position).let { (it - first.position).getDistance() } > viewConfiguration.touchSlop) {
                            moved = true
                        }
                    } while (event.changes.any { it.pressed })
                    // Doppeltippen (nur im Lesemodus, damit Tippen zum Bearbeiten nicht gestört wird)
                    val g = currentGestures
                    if (!multi && !moved && g.onTap == null && !g.dragEnabled) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300 && (first.position - lastTapPos).getDistance() < 60.dp.toPx()) {
                            val target = if (appliedZoom > 1.3f) ZoomMath.MIN_ZOOM else ZoomMath.DOUBLE_TAP_ZOOM
                            applyTransform(first.position, Offset.Zero, target / appliedZoom)
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now; lastTapPos = first.position
                        }
                    }
                }
            }.scrollable(
                orientation = Orientation.Horizontal,
                enabled = zoom > 1.01f && scrollEnabled,
                state = rememberScrollableState { delta ->
                    val old = hScroll.coerceIn(0f, maxScrollRef)
                    val new = (old - delta).coerceIn(0f, maxScrollRef)
                    hScroll = new
                    old - new
                },
            )
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = scrollEnabled,
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(with(density) { contentW.toDp() })
                    .fillMaxHeight()
                    .offset { IntOffset(-x.roundToInt(), 0) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(count = source.pageCount, key = { "$versionKey-$it" }) { page ->
                    val (wPt, hPt) = pageSizes[page]
                    val pxPerPt = pageWidthPx / wPt
                    PageItem(source, versionKey, cache, page, pageWidthPx, wPt / hPt, pxPerPt, gestures,
                        Modifier.width(with(density) { pageWidthPx.toDp() })) {
                        overlay(page, pxPerPt)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageItem(
    source: PdfPageSource, versionKey: Any, cache: PageBitmapCache, page: Int, pageWidthPx: Float,
    aspect: Float, pxPerPt: Float, gestures: PageGestures, modifier: Modifier, overlay: @Composable () -> Unit,
) {
    // Renderbreite in Stufen, damit beim Zoomen nicht ständig neu gerendert wird
    val renderW = ((pageWidthPx / 400f).roundToInt().coerceAtLeast(1) * 400).coerceAtMost(2800)
    var bitmap by remember(versionKey, page) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(versionKey, page, renderW) {
        val key = "$versionKey/$page/$renderW"
        val b = cache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching { source.render(page, renderW) }.getOrNull()
        }?.also { cache.put(key, it) }
        if (b != null) bitmap = b.asImageBitmap()
    }
    val scale by rememberUpdatedState(pxPerPt)
    val g by rememberUpdatedState(gestures)
    Box(
        modifier.padding(vertical = 6.dp).aspectRatio(aspect).shadow(3.dp).background(Color.White)
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = "Seite ${page + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
        } else {
            Text("Seite ${page + 1} wird geladen …", color = FaColors.SecondaryLabel, modifier = Modifier.align(Alignment.Center))
        }
        Box(
            Modifier.matchParentSize().pointerInput(gestures.dragEnabled, gestures.onTap != null) {
                if (g.dragEnabled) {
                    detectDragGestures(
                        onDragStart = { o -> g.onDragStart(page, o.x / scale, o.y / scale) },
                        onDrag = { change, _ -> change.consume(); g.onDrag(page, change.position.x / scale, change.position.y / scale) },
                        onDragEnd = { g.onDragEnd(page) },
                        onDragCancel = { g.onDragEnd(page) },
                    )
                } else if (g.onTap != null) {
                    detectTapGestures(onTap = { o -> g.onTap?.invoke(page, o.x / scale, o.y / scale) })
                }
            }
        )
        overlay()
    }
}

/** Frei positionierbares Bild (Unterschrift, Foto) – verschieben, skalieren, drehen. */
class Placement(val page: Int, val bitmap: Bitmap, cx: Float, cy: Float, w: Float, h: Float, rotation: Float = 0f) {
    var cx by mutableFloatStateOf(cx)
    var cy by mutableFloatStateOf(cy)
    var w by mutableFloatStateOf(w)
    var h by mutableFloatStateOf(h)
    var rotation by mutableFloatStateOf(rotation)
    val image: ImageBitmap = bitmap.asImageBitmap()
    /** Seitenverhältnis bleibt beim Skalieren immer erhalten. */
    val aspect: Float = if (w > 0f) h / w else 1f

    /** Neue Breite in Punkten (Höhe folgt automatisch), Mittelpunkt bleibt. */
    fun resizeTo(newWidth: Float) {
        w = newWidth.coerceIn(8f, 2000f)
        h = w * aspect
    }
}

@Composable
fun PlacementLayer(p: Placement, pxPerPt: Float) {
    val s by rememberUpdatedState(pxPerPt)
    Box(
        Modifier.fillMaxSize().pointerInput(p) {
            detectTransformGestures { _, pan, zoomChange, rotationChange ->
                p.cx += pan.x / s
                p.cy += pan.y / s
                p.resizeTo(p.w * zoomChange)
                p.rotation += rotationChange
            }
        }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val cx = p.cx * s
            val cy = p.cy * s
            val w = p.w * s
            val h = p.h * s
            rotate(p.rotation, pivot = Offset(cx, cy)) {
                drawImage(
                    p.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(p.image.width, p.image.height),
                    dstOffset = IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()),
                    dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                )
                drawRect(
                    FaColors.Accent, topLeft = Offset(cx - w / 2, cy - h / 2), size = Size(w, h),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                )
            }
        }
    }
}
