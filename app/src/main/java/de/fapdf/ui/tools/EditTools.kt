package de.fapdf.ui.tools

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import de.fapdf.data.DocumentEntity
import de.fapdf.data.DocumentRepository
import de.fapdf.pdf.Box as FBox
import de.fapdf.pdf.NumberFormat
import de.fapdf.pdf.NumberPosition
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.PdfOps
import de.fapdf.pdf.WatermarkPosition
import de.fapdf.pdf.WatermarkSpec
import de.fapdf.ui.components.ChoiceBoxes
import de.fapdf.ui.components.DocCard
import de.fapdf.ui.components.FaButton
import de.fapdf.ui.components.FaDropdown
import de.fapdf.ui.components.FaSoftButton
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.InfoCard
import de.fapdf.ui.components.LabeledField
import de.fapdf.ui.components.LargePagePreview
import de.fapdf.ui.components.PageNavigator
import de.fapdf.ui.components.SectionLabel
import de.fapdf.ui.components.ToggleRow
import de.fapdf.ui.components.UnderlineTabs
import de.fapdf.ui.components.file
import de.fapdf.ui.components.rememberDocSource
import de.fapdf.ui.components.rememberExportLauncher
import de.fapdf.ui.theme.FaColors
import de.fapdf.ui.workspace.decodeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

// ---------------- Seitenzahlen ----------------

@Composable
fun PageNumberToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit, register: (() -> Unit) -> Unit) {
    var pos by remember { mutableStateOf(NumberPosition.BOTTOM_CENTER) }
    var fmt by remember { mutableStateOf(NumberFormat.OF) }
    var start by remember { mutableStateOf("1") }
    var size by remember { mutableFloatStateOf(10f) }
    var margin by remember { mutableFloatStateOf(28f) }
    var skipFirst by remember { mutableStateOf(false) }
    var page by remember(doc.id) { mutableIntStateOf(0) }
    val src = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    val apply = {
        val p = pos; val f = fmt; val st = start.toIntOrNull() ?: 1; val sz = size; val m = margin; val sk = skipFirst
        runner.run("Seitenzahlen werden eingefügt …") {
            val out = runner.tmp("numbers")
            PdfIo.transform(doc.file(), out, runner.password) { d -> PdfOps.addPageNumbers(d, runner.fonts, p, f, st, sz, m, sk) }
            listOf(out to outName(doc, "nummeriert"))
        }
    }
    register(apply)
    DocCard(doc, subtitle = "Seite ${page + 1} von ${max(1, doc.pageCount)}", onChange = onChange)
    if (src != null) {
        val (wPt, _) = src.pageSizes[page.coerceIn(0, src.pageCount - 1)]
        val total = src.pageCount
        LargePagePreview(src, page.coerceIn(0, total - 1), maxHeight = 360.dp) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val scale = maxWidth.value / wPt // dp pro Punkt
                val n = (start.toIntOrNull() ?: 1) + page - (if (skipFirst) 1 else 0)
                val shown = !(skipFirst && page == 0)
                if (shown) {
                    val align = when (pos) {
                        NumberPosition.TOP_LEFT -> Alignment.TopStart
                        NumberPosition.TOP_CENTER -> Alignment.TopCenter
                        NumberPosition.TOP_RIGHT -> Alignment.TopEnd
                        NumberPosition.BOTTOM_LEFT -> Alignment.BottomStart
                        NumberPosition.BOTTOM_CENTER -> Alignment.BottomCenter
                        NumberPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                    }
                    Text(
                        fmt.format(n, total - (if (skipFirst) 1 else 0) + (start.toIntOrNull() ?: 1) - 1),
                        fontSize = (size * scale).coerceAtLeast(6f).sp, color = Color.Black,
                        modifier = Modifier.align(align).padding(((margin - size * 0.6f).coerceAtLeast(2f) * scale).dp)
                            .border(1.dp, FaColors.Accent, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp),
                    )
                }
            }
        }
        PageNavigator(page, total) { page = it }
    }
    SectionLabel("Position der Seitenzahl")
    PositionGrid(pos) { pos = it }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Format", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, bottom = 2.dp))
            FaDropdown(fmt, NumberFormat.entries, { it.title }) { fmt = it }
        }
        Column(Modifier.weight(0.8f)) {
            LabeledField("Starten bei", start, { start = it.filter(Char::isDigit).take(4) }, keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
        }
    }
    SectionLabel("Weitere Optionen")
    SliderLine("Schriftgröße", "${size.toInt()} pt", size, 6f..24f) { size = it }
    SliderLine("Abstand zum Rand", "${(margin / 72f * 25.4f).toInt()} mm", margin, 10f..80f) { margin = it }
    ToggleRow("Erste Seite auslassen", "z. B. für ein Deckblatt", checked = skipFirst) { skipFirst = it }
}

@Composable
private fun PositionGrid(selected: NumberPosition, onSelect: (NumberPosition) -> Unit) {
    val rows = listOf(
        listOf(NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER, NumberPosition.TOP_RIGHT),
        listOf(NumberPosition.BOTTOM_LEFT, NumberPosition.BOTTOM_CENTER, NumberPosition.BOTTOM_RIGHT),
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val sel = p == selected
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (sel) FaColors.AccentLight else FaColors.Surface)
                            .border(if (sel) 2.dp else 1.dp, if (sel) FaColors.Accent else FaColors.CardBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelect(p) }.padding(8.dp),
                        contentAlignment = when (p) {
                            NumberPosition.TOP_LEFT -> Alignment.TopStart
                            NumberPosition.TOP_CENTER -> Alignment.TopCenter
                            NumberPosition.TOP_RIGHT -> Alignment.TopEnd
                            NumberPosition.BOTTOM_LEFT -> Alignment.BottomStart
                            NumberPosition.BOTTOM_CENTER -> Alignment.BottomCenter
                            NumberPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                        },
                    ) {
                        Box(Modifier.size(22.dp, 6.dp).clip(RoundedCornerShape(2.dp)).background(if (sel) FaColors.Accent else Color(0xFFB0B5BF)))
                    }
                }
            }
        }
        Text(selected.title, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
    }
}

@Composable
fun SliderLine(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text("$title: $valueText", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

// ---------------- Zuschneiden ----------------

@Composable
fun CropToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit, register: (() -> Unit) -> Unit) {
    var frame by remember { mutableStateOf(FBox(0.06f, 0.05f, 0.94f, 0.95f)) }
    var page by remember(doc.id) { mutableIntStateOf(0) }
    var auto by remember { mutableStateOf(false) }
    var allPages by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val src = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    val apply = apply@{
        val s = src ?: return@apply
        val f = frame; val all = allPages; val only = page; val isAuto = auto
        runner.run("Seiten werden zugeschnitten …") {
            val out = runner.tmp("crop")
            PdfIo.transform(doc.file(), out, runner.password) { d ->
                val pages = if (all) (0 until d.numberOfPages).toList() else listOf(only)
                if (isAuto) {
                    // Automatisch: für jede Seite den tatsächlichen Inhaltsbereich ermitteln
                    pages.forEach { p -> PdfOps.cropFractions(d, listOf(p), AutoCrop.contentFrame(s, p) ?: f) }
                } else {
                    PdfOps.cropFractions(d, pages, f)
                }
            }
            listOf(out to outName(doc, "zugeschnitten"))
        }
    }
    register(apply)
    DocCard(doc, subtitle = "Seite ${page + 1} von ${max(1, doc.pageCount)}", onChange = onChange)
    if (src == null) {
        Text("Seite wird geladen …", Modifier.padding(20.dp), color = FaColors.SecondaryLabel)
        return
    }
    val p = page.coerceIn(0, src.pageCount - 1)
    Box(Modifier.fillMaxWidth().background(FaColors.Card)) { CropSelector(src, p, frame) { frame = it; auto = false } }
    PageNavigator(p, src.pageCount) { page = it }
    SectionLabel("Zuschnittbereich")
    ChoiceBoxes(listOf("Automatisch", "Manuell"), if (auto) 0 else 1) { i ->
        if (i == 0) {
            auto = true
            scope.launch {
                val fr = withContext(Dispatchers.IO) { AutoCrop.contentFrame(src, p) }
                if (fr != null) frame = fr else runner.info = "Auf dieser Seite wurde kein Inhalt erkannt."
            }
        } else auto = false
    }
    if (auto) Text(
        "Automatisch: Leere Ränder werden auf jeder Seite einzeln erkannt und entfernt.",
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
    )
    ToggleRow("Auf alle Seiten anwenden", checked = allPages) { allPages = it }
    InfoCard("Zuschneiden blendet Ränder aus. Für vertrauliche Inhalte bitte „PDF schwärzen“ verwenden.")
}

/** Erkennt den bedruckten Bereich einer Seite (Anteile 0…1) anhand der Pixel. */
object AutoCrop {
    fun contentFrame(src: de.fapdf.pdf.PdfPageSource, page: Int): FBox? {
        val bmp = runCatching { src.renderBlocking(page, 500) }.getOrNull() ?: return null
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val l = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            if (l < 235) { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
        }
        bmp.recycle()
        if (maxX < 0) return null
        val pad = 0.015f
        return FBox(
            max(0f, minX.toFloat() / w - pad), max(0f, minY.toFloat() / h - pad),
            min(1f, (maxX + 1).toFloat() / w + pad), min(1f, (maxY + 1).toFloat() / h + pad),
        )
    }
}

// ---------------- Wasserzeichen ----------------

@Composable
fun WatermarkToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit, register: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("ENTWURF") }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var opacity by remember { mutableFloatStateOf(0.4f) }
    var angle by remember { mutableFloatStateOf(45f) }
    var size by remember { mutableFloatStateOf(54f) }
    var color by remember { mutableIntStateOf(0xFF1F5BE0.toInt()) }
    var position by remember { mutableStateOf(WatermarkPosition.CENTER) }
    var scale by remember { mutableFloatStateOf(40f) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) image = decodeBitmap(context, uri) }
    val src = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    val apply = {
        val spec = WatermarkSpec(
            text = if (tab == 0) text else null, image = if (tab == 1) image else null, fontSize = size, colorArgb = color,
            opacity = opacity, rotationDeg = angle, position = position, imageScalePercent = scale,
        )
        runner.run("Wasserzeichen wird eingefügt …") {
            val out = runner.tmp("watermark")
            PdfIo.transform(doc.file(), out, runner.password) { d -> PdfOps.watermark(d, runner.fonts, spec) }
            listOf(out to outName(doc, "Wasserzeichen"))
        }
    }
    register(apply)
    DocCard(doc, onChange = onChange)
    if (src != null) {
        val (wPt, _) = src.pageSizes[0]
        LargePagePreview(src, 0, maxHeight = 340.dp) {
            // Live-Vorschau des Wasserzeichens
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val k = maxWidth.value / wPt
                val align = when (position) {
                    WatermarkPosition.TOP -> Alignment.TopCenter
                    WatermarkPosition.BOTTOM -> Alignment.BottomCenter
                    else -> Alignment.Center
                }
                val img = image
                if (tab == 0 && text.isNotBlank()) {
                    Text(
                        text, color = Color(color), fontWeight = FontWeight.Bold, fontSize = (size * k).sp,
                        modifier = Modifier.align(align).padding(vertical = 12.dp).rotate(-angle).alpha(opacity),
                    )
                } else if (tab == 1 && img != null) {
                    Image(
                        img.asImageBitmap(), null,
                        Modifier.align(align).width(maxWidth * (scale / 100f)).rotate(-angle).alpha(opacity),
                    )
                }
            }
        }
    }
    UnderlineTabs(listOf("Text", "Bild"), tab) { tab = it }
    if (tab == 0) {
        LabeledField("Wasserzeichen-Text", text, { text = it })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0xFF1F5BE0.toInt(), 0xFFE53935.toInt(), 0xFF8E8E93.toInt(), 0xFF111111.toInt()).forEach { c ->
                Box(
                    Modifier.size(if (c == color) 32.dp else 26.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 0.dp, Color.White, CircleShape).clickable { color = c }
                )
            }
        }
        SliderLine("Schriftgröße", "${size.toInt()} pt", size, 12f..120f) { size = it }
    } else {
        FaSoftButton(if (image == null) "Bild auswählen" else "Anderes Bild wählen") {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        SliderLine("Breite", "${scale.toInt()} %", scale, 10f..100f) { scale = it }
    }
    SliderLine("Deckkraft", "${(opacity * 100).toInt()} %", opacity, 0.05f..1f) { opacity = it }
    SliderLine("Winkel", "${angle.toInt()}°", angle, -90f..90f) { angle = it }
    SectionLabel("Position")
    ChoiceBoxes(WatermarkPosition.entries.map { it.title }, WatermarkPosition.entries.indexOf(position)) { position = WatermarkPosition.entries[it] }
    FaButton("Anwenden", enabled = if (tab == 1) image != null else text.isNotBlank(), onClick = apply)
}

// ---------------- Freigeben ----------------

private data class ShareTarget(val label: String, val pkg: String, val cls: String, val icon: ImageBitmap?)

@Composable
fun ShareToolV2(doc: DocumentEntity, onChange: () -> Unit) {
    val context = LocalContext.current
    val exporter = rememberExportLauncher()
    var name by remember(doc.id) { mutableStateOf(doc.displayName) }
    val shareName = DocumentRepository.ensurePdfName(name.ifBlank { doc.displayName })
    val targets by produceState<List<ShareTarget>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_SEND).setType("application/pdf")
            val preferred = listOf("com.google.android.gm", "com.google.android.apps.docs", "com.google.android.apps.messaging", "com.android.bluetooth", "com.whatsapp")
            val all = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            all.sortedBy { ri -> preferred.indexOf(ri.activityInfo.packageName).let { if (it < 0) 99 else it } }
                .distinctBy { it.activityInfo.packageName }.take(4).map { ri ->
                    ShareTarget(
                        ri.loadLabel(pm).toString(), ri.activityInfo.packageName, ri.activityInfo.name,
                        runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }
        }
    }
    DocCard(doc, onChange = onChange)
    LabeledField("Dateiname", name, { name = it })
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp))
    ) {
        ShareRow(Icons.Outlined.Share, "Als PDF teilen", "Über andere Apps teilen") { FileActions.share(context, doc.file(), shareName) }
        HorizontalDivider(color = FaColors.Separator)
        ShareRow(Icons.Outlined.Link, "Link erstellen", "Freigabelink über Ihre Cloud-App (z. B. Google Drive)") {
            FileActions.shareTo(context, doc.file(), shareName, "com.google.android.apps.docs", null)
        }
        HorizontalDivider(color = FaColors.Separator)
        ShareRow(Icons.Outlined.ContentCopy, "Kopie speichern", "Kopie in anderem Speicherort sichern") { exporter(doc.file(), shareName) }
    }
    SectionLabel("Teilen mit")
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        targets.forEach { t ->
            Column(Modifier.width(64.dp).clickable { FileActions.shareTo(context, doc.file(), shareName, t.pkg, t.cls) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(FaColors.Card), contentAlignment = Alignment.Center) {
                    t.icon?.let { Image(it, t.label, Modifier.size(34.dp)) }
                }
                Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.width(64.dp).clickable { FileActions.share(context, doc.file(), shareName) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(FaColors.Card), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MoreHoriz, null) }
            Text("Mehr", style = MaterialTheme.typography.labelSmall)
        }
    }
    FaButton("Teilen") { FileActions.share(context, doc.file(), shareName) }
}

@Composable
private fun ShareRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = FaColors.Accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = FaColors.SecondaryLabel)
    }
}

// ---------------- Unterschriften anfordern ----------------

private data class Recipient(val id: Int, val name: String, val email: String)

@Composable
fun RequestSignaturesToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nextId by remember { mutableIntStateOf(1) }
    val recipients = remember { mutableStateListOf(Recipient(0, "", "")) }
    var page by remember(doc.id) { mutableIntStateOf(max(0, doc.pageCount - 1)) }
    var fieldCenter by remember(doc.id) { mutableStateOf<Pair<Float, Float>?>(0.7f to 0.82f) } // Anteile der Seite
    var message by remember { mutableStateOf("Guten Tag,\n\nbitte unterschreiben Sie das angehängte Dokument im markierten Unterschriftsfeld und senden Sie es an mich zurück.\n\nVielen Dank!") }
    val src = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    DocCard(doc, onChange = onChange)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (FaColors.dark) Color(0xFF3A2C18) else Color(0xFFFFF3E0)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WarningAmber, null, tint = Color(0xFFEF6C00))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Sign.com nicht verbunden", style = MaterialTheme.typography.titleSmall)
            Text(
                "Für Sign.com ist ein Vertrag mit API-Zugang des Anbieters nötig. FA PDF fügt stattdessen ein echtes Unterschriftsfeld ein und sendet das Dokument per E-Mail.",
                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
            )
        }
    }
    SectionLabel("Empfänger")
    recipients.forEachIndexed { i, r ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LabeledPairField("Name", r.name) { recipients[i] = r.copy(name = it) }
                LabeledPairField("E-Mail", r.email) { recipients[i] = r.copy(email = it) }
            }
            IconButton(onClick = { recipients.removeAt(i) }, enabled = recipients.size > 1) { Icon(Icons.Outlined.DeleteOutline, "Entfernen") }
        }
    }
    FaSoftButton("Empfänger hinzufügen", icon = Icons.Outlined.Add) { recipients.add(Recipient(nextId++, "", "")) }
    SectionLabel("Unterschriftenfeld platzieren")
    if (src != null) {
        val p = page.coerceIn(0, src.pageCount - 1)
        LargePagePreview(src, p, maxHeight = 320.dp) {
            BoxWithConstraints(Modifier.fillMaxSize().pointerInput(p) {
                detectTapGestures { o -> fieldCenter = (o.x / size.width) to (o.y / size.height) }
            }) {
                fieldCenter?.let { (cx, cy) ->
                    val bw = maxWidth * 0.36f; val bh = maxHeight * 0.07f
                    Row(
                        Modifier.offset(x = maxWidth * cx - bw / 2, y = maxHeight * cy - bh / 2).size(bw, bh)
                            .background(FaColors.AccentLight.copy(alpha = 0.7f)).border(1.5.dp, FaColors.Accent),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Edit, null, tint = FaColors.Accent, modifier = Modifier.size(14.dp))
                        Text(" Unterschrift", fontSize = 10.sp, color = FaColors.Accent)
                    }
                }
            }
        }
        PageNavigator(p, src.pageCount) { page = it }
        Text("Tippen Sie auf die Stelle, an der unterschrieben werden soll.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
    }
    LabeledField("Nachricht", message, { message = it }, singleLine = false, minLines = 4)
    val emails = recipients.map { it.email.trim() }.filter { it.contains('@') }
    FaButton("Anfrage per E-Mail senden", enabled = emails.isNotEmpty()) {
        val center = fieldCenter
        val pIdx = page
        scope.launch {
            runner.busy = "Dokument wird vorbereitet …"
            try {
                val out = File(File(context.cacheDir, "share").apply { mkdirs() }, "${DocumentRepository.baseName(doc.displayName)} (zur Unterschrift).pdf")
                withContext(PdfIo.pdfDispatcher) {
                    PdfIo.transform(doc.file(), out, runner.password) { d ->
                        if (center != null) {
                            val g = PdfIo.geometry(d.getPage(pIdx))
                            val bw = g.viewWidth * 0.36f; val bh = g.viewHeight * 0.07f
                            val cx = g.viewWidth * center.first; val cy = g.viewHeight * center.second
                            recipients.filter { it.email.contains('@') }.forEachIndexed { k, r ->
                                val dy = k * (bh + 6f)
                                PdfOps.addSignatureField(d, pIdx, FBox(cx - bw / 2, cy - bh / 2 + dy, cx + bw / 2, cy + bh / 2 + dy), "Unterschrift ${r.name.ifBlank { r.email }}")
                            }
                        }
                    }
                }
                FileActions.email(context, out, out.name, emails, "Bitte unterschreiben: ${doc.displayName}", message)
            } catch (e: Exception) {
                runner.error = e.message ?: "Fehler"
            } finally {
                runner.busy = null
            }
        }
    }
}

@Composable
private fun LabeledPairField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.width(60.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
        OutlinedTextField(value, onChange, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp), textStyle = MaterialTheme.typography.bodyMedium)
    }
}
