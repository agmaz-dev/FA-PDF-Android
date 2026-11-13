package de.fapdf.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fapdf.FaPdfApp
import de.fapdf.pdf.ScanEnhancer
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Bewahrt die Original-Fotos eines Scans auf, damit man ihn später neu aufbereiten kann. */
object ScanStore {
    val MODES = listOf("Original", "Verbessert", "Kräftig", "Schwarzweiß")

    fun dir(ctx: Context, id: Long) = File(ctx.filesDir, "scan_raw/$id")
    fun pending(ctx: Context) = File(ctx.filesDir, "scan_raw/pending")
    fun has(ctx: Context, id: Long) = pages(dir(ctx, id)).isNotEmpty()
    fun pages(d: File): List<File> = d.listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()

    /** Gewählten Modus und Drehungen merken. */
    fun saveMeta(d: File, mode: Int, rot: List<Int>) = File(d, "meta.txt").writeText("$mode\n" + rot.joinToString(","))
    fun loadMeta(d: File, n: Int): Pair<Int, List<Int>> {
        val l = runCatching { File(d, "meta.txt").readLines() }.getOrDefault(emptyList())
        val mode = l.getOrNull(0)?.toIntOrNull() ?: 1
        val rot = l.getOrNull(1)?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
        return mode to List(n) { rot.getOrElse(it) { 0 } }
    }

    /** Nach dem Import: vorgemerkte Fotos dem neuen Dokument zuordnen. */
    fun claimPending(ctx: Context, id: Long, mode: Int) {
        val p = pending(ctx)
        if (!p.exists()) return
        val d = dir(ctx, id); d.deleteRecursively(); d.parentFile?.mkdirs()
        if (!p.renameTo(d)) { p.copyRecursively(d, true); p.deleteRecursively() }
        saveMeta(d, mode, List(pages(d).size) { 0 })
    }

    fun loadPage(f: File, maxSide: Int, rotation: Int): Bitmap? {
        val raw = f.inputStream().use { ScanEnhancer.decode(it, maxSide) } ?: return null
        if (rotation % 360 == 0) return raw
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }

    /** Alle Seiten neu aufbereiten und als PDF schreiben. */
    fun build(d: File, mode: Int, rot: List<Int>, out: File) {
        val pages = pages(d).mapIndexedNotNull { i, f ->
            loadPage(f, 2400, rot.getOrElse(i) { 0 })?.let { b -> ScanEnhancer.process(b, mode).also { if (it !== b) b.recycle() } }
        }
        require(pages.isNotEmpty()) { "Keine Scan-Seiten gefunden" }
        ScanEnhancer.buildPdf(pages, out)
        pages.forEach { it.recycle() }
    }
}

/** Vollbild: Scan nachträglich anders aufbereiten (Filter, Drehen) und die PDF ersetzen. */
@Composable
fun ScanEditDialog(docId: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as FaPdfApp).container
    val scope = rememberCoroutineScope()
    val dir = remember { ScanStore.dir(context, docId) }
    val files = remember { ScanStore.pages(dir) }
    val meta = remember { ScanStore.loadMeta(dir, files.size) }
    var mode by remember { mutableIntStateOf(meta.first) }
    val rot = remember { mutableStateListOf<Int>().apply { addAll(meta.second) } }
    var page by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    val preview by produceState<Bitmap?>(null, page, mode, rot.getOrNull(page)) {
        value = withContext(Dispatchers.Default) {
            files.getOrNull(page)?.let { f -> ScanStore.loadPage(f, 1400, rot[page])?.let { b -> ScanEnhancer.process(b, mode).also { if (it !== b) b.recycle() } } }
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(FaColors.Background).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scan nachbearbeiten", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (files.size > 1) {
                    IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vorherige Seite") }
                    Text("${page + 1} / ${files.size}", fontSize = 14.sp, color = FaColors.SecondaryLabel)
                    IconButton(onClick = { if (page < files.size - 1) page++ }, enabled = page < files.size - 1) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Nächste Seite") }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                val b = preview
                if (b == null) CircularProgressIndicator(color = FaColors.Accent)
                else Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanStore.MODES.forEachIndexed { i, t ->
                    val sel = mode == i
                    Box(
                        Modifier.weight(1f).height(42.dp).background(if (sel) FaColors.Accent else FaColors.Card, RoundedCornerShape(12.dp)).clickable { mode = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(t, fontSize = 13.sp, maxLines = 1, color = if (sel) FaColors.OnAccent else FaColors.Label, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlimButton("Seite drehen", Modifier.weight(1f), icon = true) { rot[page] = (rot[page] + 90) % 360 }
                SlimButton("Abbrechen", Modifier.weight(1f)) { if (!saving) onDismiss() }
                SlimButton(if (saving) "Speichert …" else "Übernehmen", Modifier.weight(1f), primary = true) {
                    if (saving) return@SlimButton
                    saving = true
                    scope.launch {
                        val ok = runCatching {
                            withContext(Dispatchers.Default) {
                                val out = File(context.cacheDir, "scan_edit_$docId.pdf")
                                ScanStore.build(dir, mode, rot.toList(), out)
                                ScanStore.saveMeta(dir, mode, rot.toList())
                                container.documents.replaceContent(docId, out)
                                runCatching { container.documents.refreshThumbnail(docId) }
                                out.delete()
                            }
                        }.isSuccess
                        saving = false
                        de.fapdf.ui.components.FileActions.toast(context, if (ok) "Scan aktualisiert" else "Scan konnte nicht gespeichert werden")
                        if (ok) onDismiss()
                    }
                }
            }
        }
    }
}

/** Gleich große, einzeilige Knöpfe für Aktionsreihen. */
@Composable
fun SlimButton(text: String, modifier: Modifier, primary: Boolean = false, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).background(if (primary) FaColors.Accent else FaColors.Card, RoundedCornerShape(12.dp))
            .border(1.dp, if (primary) Color.Transparent else FaColors.CardBorder, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Icon(Icons.Outlined.RotateRight, null, tint = if (primary) FaColors.OnAccent else FaColors.Accent, modifier = Modifier.size(18.dp).padding(end = 2.dp))
        Text(text, fontSize = 13.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            color = if (primary) FaColors.OnAccent else FaColors.Accent, fontWeight = FontWeight.SemiBold)
    }
}

/** Erste Seite einer PDF groß anzeigen (aktualisiert sich, wenn sich die Datei ändert). */
@Composable
fun PdfFirstPage(file: File, stamp: Long, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, file.absolutePath, stamp) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { r ->
                        r.openPage(0).use { p ->
                            val w = 900; val h = (w.toFloat() * p.height / p.width).toInt()
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                                b.eraseColor(android.graphics.Color.WHITE)
                                p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: CircularProgressIndicator(color = FaColors.Accent)
    }
}
