package de.fapdf.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.fapdf.data.DocumentEntity
import de.fapdf.pdf.PasswordRequiredException
import de.fapdf.pdf.PdfPageSource
import de.fapdf.pdf.PreviewFactory
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Zentrale Vorschauquelle je Dokumentversion. Alle Werkzeuge teilen sich dieselbe geöffnete
 * Datei, damit Vorschauen schnell sind und sich nicht gegenseitig beim Erzeugen stören.
 */
object PreviewSources {
    private val mutex = Mutex()
    private val sources = HashMap<Long, Pair<String, PdfPageSource>>()
    val thumbs = PageBitmapCache((Runtime.getRuntime().maxMemory() / 8).toInt())

    private fun keyOf(doc: DocumentEntity, password: String?) = "${doc.modifiedAt}/${doc.sizeBytes}/${password ?: ""}"

    suspend fun get(context: Context, doc: DocumentEntity, password: String?): PdfPageSource = mutex.withLock {
        val key = keyOf(doc, password)
        sources[doc.id]?.let { (k, s) -> if (k == key) return@withLock s else runCatching { s.close() } }
        val src = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "previews").apply { mkdirs() }
            val preview = PreviewFactory.prepare(File(doc.filePath), File(dir, "doc_${doc.id}_${key.hashCode()}.pdf"), password)
            PdfPageSource.open(preview)
        }
        sources[doc.id] = key to src
        src
    }
}

/** Öffnet die Vorschau eines Dokuments; bei Passwortschutz wird [onNeedPassword] aufgerufen. */
@Composable
fun rememberDocSource(doc: DocumentEntity?, password: String?, onNeedPassword: () -> Unit = {}): PdfPageSource? {
    val context = LocalContext.current
    val needPw by rememberUpdatedState(onNeedPassword)
    val state by produceState<PdfPageSource?>(null, doc?.id, doc?.modifiedAt, password) {
        value = null
        if (doc == null) return@produceState
        value = try {
            PreviewSources.get(context, doc, password)
        } catch (e: PasswordRequiredException) {
            needPw(); null
        } catch (e: Exception) {
            null
        }
    }
    return state
}

/**
 * Eine Seitenminiatur mit echter Ausrichtung. [extraRotation] zeigt eine geplante Drehung
 * (z. B. im Werkzeug „Drehen“) bereits in der Vorschau an.
 */
@Composable
fun PageThumb(
    src: PdfPageSource,
    page: Int,
    modifier: Modifier = Modifier,
    extraRotation: Int = 0,
    selected: Boolean = false,
    selectable: Boolean = false,
    accent: Color? = null,
    label: String? = null,
    renderWidth: Int = 300,
    onClick: (() -> Unit)? = null,
) {
    val key = "${src.file.absolutePath}/$page/$renderWidth"
    val img by produceState<ImageBitmap?>(PreviewSources.thumbs.get(key)?.asImageBitmap(), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { src.render(page, renderWidth) }.getOrNull()?.also { PreviewSources.thumbs.put(key, it) }?.asImageBitmap()
            }
        }
    }
    val border = when {
        selected -> FaColors.Accent
        accent != null -> accent
        else -> FaColors.Separator
    }
    Column(modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(6.dp))
                .background(if (selected) FaColors.AccentLight else Color(0xFFEDEDF0))
                .border(if (selected || accent != null) 3.dp else 1.dp, border, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val i = img
            if (i != null) {
                Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                    val odd = ((extraRotation % 180) + 180) % 180 == 90
                    val bw = if (odd) i.height.toFloat() else i.width.toFloat()
                    val bh = if (odd) i.width.toFloat() else i.height.toFloat()
                    val s = min(size.width / bw, size.height / bh)
                    val w = i.width * s
                    val h = i.height * s
                    rotate(extraRotation.toFloat(), pivot = Offset(size.width / 2, size.height / 2)) {
                        drawRect(Color.White, topLeft = Offset((size.width - w) / 2, (size.height - h) / 2), size = androidx.compose.ui.geometry.Size(w, h))
                        drawImage(
                            i, srcOffset = IntOffset.Zero, srcSize = IntSize(i.width, i.height),
                            dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
                            dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                        )
                    }
                }
            }
            if (extraRotation % 360 != 0) {
                Text(
                    "↻ ${((extraRotation % 360) + 360) % 360}°", style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp).clip(RoundedCornerShape(6.dp))
                        .background(FaColors.Accent).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (selectable) CheckMark(selected, Modifier.align(Alignment.TopEnd).padding(5.dp))
            else if (selected) CheckMark(true, Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = accent ?: FaColors.Label, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Waagerechte Seitenleiste – einheitliche Vorschau für alle Werkzeuge. */
@Composable
fun PagePreviewStrip(
    doc: DocumentEntity,
    password: String? = null,
    onNeedPassword: () -> Unit = {},
    thumbWidth: Dp = 78.dp,
    accentFor: (Int) -> Color? = { null },
    labelFor: (Int) -> String = { "${it + 1}" },
    onClick: ((Int) -> Unit)? = null,
) {
    val src = rememberDocSource(doc, password, onNeedPassword)
    if (src == null) {
        Text("Vorschau wird geladen …", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = FaColors.SecondaryLabel)
        return
    }
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items((0 until src.pageCount).toList()) { p ->
            PageThumb(
                src, p, Modifier.width(thumbWidth), accent = accentFor(p), label = labelFor(p), renderWidth = 220,
                onClick = onClick?.let { { it(p) } },
            )
        }
    }
}
