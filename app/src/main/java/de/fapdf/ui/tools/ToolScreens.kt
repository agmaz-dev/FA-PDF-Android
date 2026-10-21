package de.fapdf.ui.tools

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.fapdf.data.DocumentEntity
import de.fapdf.pdf.DocFontCache
import de.fapdf.pdf.NumberFormat
import de.fapdf.pdf.NumberPosition
import de.fapdf.pdf.OcrEngine
import de.fapdf.pdf.OcrLayer
import de.fapdf.pdf.PageAnalyzer
import de.fapdf.pdf.PageRangeParser
import de.fapdf.pdf.PageRaster
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.PdfOps
import de.fapdf.pdf.PdfUserException
import de.fapdf.pdf.WatermarkPosition
import de.fapdf.pdf.WatermarkSpec
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.Tool
import de.fapdf.ui.components.DocChooserSheet
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.GroupCard
import de.fapdf.ui.components.GroupRow
import de.fapdf.ui.components.InfoBanner
import de.fapdf.ui.components.PrimaryButton
import de.fapdf.ui.components.SectionTitle
import de.fapdf.ui.components.file
import de.fapdf.ui.components.findActivity
import de.fapdf.ui.components.rememberExportLauncher
import de.fapdf.ui.theme.FaColors
import de.fapdf.ui.workspace.decodeBitmap

/** Einstieg für alle Werkzeuge ohne Seitenansicht. */
@Composable
fun ToolScreen(tool: Tool, docId: Long?, appVm: AppViewModel, onBack: () -> Unit, onOpenDoc: (Long) -> Unit) {
    val docs by appVm.documents.collectAsState()
    var currentId by remember { mutableStateOf(docId) }
    var choose by remember { mutableStateOf(false) }
    val doc = docs.firstOrNull { it.id == currentId }
    val runner = rememberToolRunner(appVm)

    if (tool.needsDocument && doc == null) {
        ToolScaffold(tool, runner, onBack, onOpenDoc) {
            InfoBanner("Bitte wählen Sie zuerst ein Dokument.")
            PrimaryButton("Dokument auswählen", Modifier.padding(16.dp)) { choose = true }
        }
    } else {
        val replaceTarget = if (tool in setOf(Tool.MERGE, Tool.SPLIT, Tool.EXTRACT_PAGES, Tool.SCANNER, Tool.SHARE, Tool.REQUEST_SIGNATURES)) null else doc
        ToolScaffold(tool, runner, onBack, onOpenDoc, replaceTarget) {
            val change = { choose = true }
            when (tool) {
                Tool.MERGE -> MergeTool(appVm, runner, docs, docId)
                Tool.SPLIT -> SplitTool(doc!!, runner, change)
                Tool.ROTATE -> RotateTool(doc!!, runner, change)
                Tool.DELETE_PAGES -> PageSelectTool(doc!!, runner, change, delete = true)
                Tool.EXTRACT_PAGES -> PageSelectTool(doc!!, runner, change, delete = false)
                Tool.ORGANIZE -> OrganizeTool(doc!!, runner, change)
                Tool.PAGE_NUMBERS -> PageNumberTool(doc!!, runner, change)
                Tool.CROP -> CropTool(doc!!, runner, change)
                Tool.WATERMARK -> WatermarkTool(doc!!, runner, change)
                Tool.SHARE -> ShareTool(doc!!, change)
                Tool.REQUEST_SIGNATURES -> RequestSignaturesTool(doc!!, change)
                Tool.REMOVE_PASSWORD -> RemovePasswordTool(doc!!, runner, change)
                Tool.ENCRYPT -> EncryptTool(doc!!, runner, change)
                Tool.FLATTEN -> FlattenTool(doc!!, runner, change)
                Tool.SCANNER -> ScannerTool(appVm, runner)
                else -> Text("Dieses Werkzeug wird in der Seitenansicht geöffnet.", Modifier.padding(16.dp))
            }
        }
    }
    if (choose) DocChooserSheet(
        documents = docs, onImport = { uri -> choose = false; appVm.import(uri, persist = true) { currentId = it; runner.results = emptyList() } },
        onDismiss = { choose = false },
    ) { d -> choose = false; currentId = d.id; runner.results = emptyList() }
}

// ---------------- Organisieren ----------------

@Composable
private fun MergeTool(appVm: AppViewModel, runner: ToolRunner, docs: List<DocumentEntity>, initial: Long?) {
    val selected = remember { mutableStateListOf<Long>().apply { initial?.let { add(it) } } }
    var choose by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Zusammengefügt") }
    SectionTitle("Reihenfolge")
    GroupCard {
        if (selected.isEmpty()) Text("Noch keine Dateien gewählt.", Modifier.padding(16.dp), color = FaColors.SecondaryLabel)
        selected.forEachIndexed { i, id ->
            val d = docs.firstOrNull { it.id == id } ?: return@forEachIndexed
            GroupRow("${i + 1}. ${d.displayName}", "${d.pageCount} Seiten", trailing = {
                Row {
                    IconButton(onClick = { if (i > 0) { selected.removeAt(i); selected.add(i - 1, id) } }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, "Nach oben") }
                    IconButton(onClick = { if (i < selected.size - 1) { selected.removeAt(i); selected.add(i + 1, id) } }, enabled = i < selected.size - 1) { Icon(Icons.Outlined.ArrowDownward, "Nach unten") }
                    IconButton(onClick = { selected.removeAt(i) }) { Icon(Icons.Outlined.Close, "Entfernen") }
                }
            })
        }
        GroupRow("Datei hinzufügen", icon = Icons.Outlined.Add, showDivider = false) { choose = true }
    }
    SectionTitle("Name der neuen PDF")
    OutlinedTextField(name, { name = it }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), singleLine = true)
    PrimaryButton("${selected.size} Dateien zusammenfügen", Modifier.padding(16.dp), enabled = selected.size >= 2) {
        val files = selected.mapNotNull { id -> docs.firstOrNull { it.id == id }?.file() }
        runner.run("PDFs werden zusammengefügt …") {
            val out = runner.tmp("merge")
            PdfOps.merge(files, out)
            listOf(out to "${name.ifBlank { "Zusammengefügt" }}.pdf")
        }
    }
    if (choose) DocChooserSheet(
        documents = docs.filter { it.id !in selected }, title = "Datei hinzufügen",
        onImport = { uri -> choose = false; appVm.import(uri, persist = false) { selected.add(it) } },
        onDismiss = { choose = false },
    ) { d -> choose = false; selected.add(d.id) }
}

@Composable
private fun SplitTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var eachPage by remember { mutableStateOf(false) }
    var ranges by remember { mutableStateOf("1-${(doc.pageCount / 2).coerceAtLeast(1)}, ${(doc.pageCount / 2 + 1).coerceAtMost(doc.pageCount)}-${doc.pageCount}") }
    DocHeader(doc, onChange)
    SectionTitle("Aufteilen")
    GroupCard {
        GroupRow("Nach Seitenbereichen", trailing = { Checkbox(!eachPage, { eachPage = !it }) }) { eachPage = false }
        GroupRow("Jede Seite als eigene PDF", trailing = { Checkbox(eachPage, { eachPage = it }) }, showDivider = false) { eachPage = true }
    }
    if (!eachPage) {
        SectionTitle("Bereiche (jeder Bereich wird eine eigene PDF)")
        OutlinedTextField(ranges, { ranges = it }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), singleLine = true,
            supportingText = { Text("z. B. 1-3, 4, 5-${doc.pageCount}") })
    }
    PrimaryButton("PDF teilen", Modifier.padding(16.dp)) {
        runner.run("PDF wird geteilt …") {
            val pageCount = PdfIo.load(doc.file(), runner.password).use { it.numberOfPages }
            val parts = if (eachPage) (0 until pageCount).map { listOf(it) }
            else try { PageRangeParser.parse(ranges, pageCount) } catch (e: IllegalArgumentException) { throw PdfUserException(e.message ?: "Ungültige Bereiche") }
            val outs = parts.map { runner.tmp("split") }
            PdfOps.split(doc.file(), parts, outs, runner.password)
            outs.mapIndexed { i, f ->
                val p = parts[i]
                f to outName(doc, if (p.size == 1) "Seite ${p.first() + 1}" else "Seiten ${p.first() + 1}-${p.last() + 1}")
            }
        }
    }
}

@Composable
private fun RotateTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    val selected = remember { mutableStateListOf<Int>() }
    var angle by remember { mutableStateOf(90) }
    var pw by remember { mutableStateOf(false) }
    DocHeader(doc, onChange)
    SectionTitle("Drehung")
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(90 to "90° rechts", 270 to "90° links", 180 to "180°").forEach { (a, t) -> FilterChip(angle == a, { angle = a }, { Text(t) }) }
    }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (selected.isEmpty()) "Keine Auswahl = alle Seiten" else "${selected.size} Seite(n) ausgewählt", Modifier.weight(1f), color = FaColors.SecondaryLabel)
        OutlinedButton(onClick = { selected.clear() }) { Text("Auswahl aufheben") }
    }
    PageThumbGrid(doc, runner.password, null, selected.toSet(), selected.associateWith { angle }, onToggle = { p ->
        if (p in selected) selected.remove(p) else selected.add(p)
    }, onNeedPassword = { pw = true; runner.askPassword = { pw = false } })
    PrimaryButton("Drehen und speichern", Modifier.padding(16.dp)) {
        runner.run("Seiten werden gedreht …") {
            val out = runner.tmp("rotate")
            PdfIo.transform(doc.file(), out, runner.password) { d ->
                PdfOps.rotate(d, if (selected.isEmpty()) (0 until d.numberOfPages).toList() else selected.toList(), angle)
            }
            listOf(out to outName(doc, "gedreht"))
        }
    }
}

@Composable
private fun PageSelectTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit, delete: Boolean) {
    val selected = remember { mutableStateListOf<Int>() }
    DocHeader(doc, onChange)
    InfoBanner(if (delete) "Tippen Sie auf die Seiten, die entfernt werden sollen." else "Tippen Sie auf die Seiten, die in eine neue PDF übernommen werden sollen.")
    PageThumbGrid(doc, runner.password, null, selected.toSet(), onToggle = { p -> if (p in selected) selected.remove(p) else selected.add(p) },
        onNeedPassword = { runner.askPassword = {} })
    PrimaryButton(
        if (delete) "${selected.size} Seite(n) löschen" else "${selected.size} Seite(n) extrahieren",
        Modifier.padding(16.dp), enabled = selected.isNotEmpty(), color = if (delete) FaColors.Destructive else FaColors.Accent,
    ) {
        runner.run(if (delete) "Seiten werden gelöscht …" else "Seiten werden extrahiert …") {
            val out = runner.tmp("pages")
            if (delete) {
                PdfIo.transform(doc.file(), out, runner.password) { d -> PdfOps.deletePages(d, selected.toList()) }
                listOf(out to outName(doc, "bearbeitet"))
            } else {
                PdfOps.extract(doc.file(), selected.sorted(), out, runner.password)
                listOf(out to outName(doc, "Auszug"))
            }
        }
    }
}

@Composable
private fun OrganizeTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    val order = remember(doc.id) { mutableStateListOf<Int>().apply { addAll(0 until doc.pageCount) } }
    val rotations = remember(doc.id) { mutableStateMapOf<Int, Int>() }
    var sel by remember { mutableStateOf<Int?>(null) }
    DocHeader(doc, onChange)
    InfoBanner("Seite antippen, dann verschieben, drehen oder entfernen.")
    val s = sel
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        OutlinedButton(enabled = s != null && order.indexOf(s) > 0, onClick = { val i = order.indexOf(s!!); order.removeAt(i); order.add(i - 1, s) }) { Text("◀ Vor") }
        OutlinedButton(enabled = s != null && order.indexOf(s) in 0 until order.size - 1, onClick = { val i = order.indexOf(s!!); order.removeAt(i); order.add(i + 1, s) }) { Text("Zurück ▶") }
        OutlinedButton(enabled = s != null, onClick = { rotations[s!!] = ((rotations[s] ?: 0) + 90) % 360 }) { Text("↻") }
        OutlinedButton(enabled = s != null && order.size > 1, onClick = { order.remove(s!!); sel = null }) { Text("✕") }
    }
    PageThumbGrid(doc, runner.password, order.toList(), setOfNotNull(sel), rotations.toMap(), onToggle = { p -> sel = if (sel == p) null else p },
        onNeedPassword = { runner.askPassword = {} })
    PrimaryButton("Neue Reihenfolge speichern", Modifier.padding(16.dp)) {
        val o = order.toList(); val r = rotations.toMap()
        runner.run("Seiten werden neu angeordnet …") {
            val out = runner.tmp("organize")
            PdfIo.transform(doc.file(), out, runner.password) { d -> PdfOps.reorganize(d, o, r) }
            listOf(out to outName(doc, "organisiert"))
        }
    }
}

// ---------------- Bearbeiten ----------------

@Composable
private fun PageNumberTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var pos by remember { mutableStateOf(NumberPosition.BOTTOM_CENTER) }
    var fmt by remember { mutableStateOf(NumberFormat.PAGE_OF) }
    var start by remember { mutableStateOf("1") }
    var size by remember { mutableStateOf(10f) }
    var margin by remember { mutableStateOf(28f) }
    var skipFirst by remember { mutableStateOf(false) }
    DocHeader(doc, onChange)
    SectionTitle("Position")
    ChipRows(NumberPosition.entries, pos, { it.title }) { pos = it }
    SectionTitle("Format")
    ChipRows(NumberFormat.entries, fmt, { it.title }) { fmt = it }
    SectionTitle("Einstellungen")
    GroupCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Beginnen mit", Modifier.weight(1f))
            OutlinedTextField(start, { start = it.filter(Char::isDigit).take(4) }, Modifier.width(90.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        SliderRow("Schriftgröße", size, 6f..24f, "${size.toInt()} pt") { size = it }
        SliderRow("Abstand zum Rand", margin, 10f..80f, "${(margin / 72f * 25.4f).toInt()} mm") { margin = it }
        GroupRow("Erste Seite auslassen (Deckblatt)", trailing = { Switch(skipFirst, { skipFirst = it }) }, showDivider = false)
    }
    PrimaryButton("Seitenzahlen einfügen", Modifier.padding(16.dp)) {
        runner.run("Seitenzahlen werden eingefügt …") {
            val out = runner.tmp("numbers")
            PdfIo.transform(doc.file(), out, runner.password) { d ->
                PdfOps.addPageNumbers(d, runner.fonts, pos, fmt, start.toIntOrNull() ?: 1, size, margin, skipFirst)
            }
            listOf(out to outName(doc, "nummeriert"))
        }
    }
}

@Composable
private fun CropTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var top by remember { mutableStateOf(10f) }
    var bottom by remember { mutableStateOf(10f) }
    var left by remember { mutableStateOf(10f) }
    var right by remember { mutableStateOf(10f) }
    DocHeader(doc, onChange)
    InfoBanner("Die Ränder werden auf allen Seiten ausgeblendet (CropBox). Inhalte außerhalb bleiben in der Datei erhalten – für vertrauliche Inhalte bitte „PDF schwärzen“ verwenden.")
    SectionTitle("Ränder in Millimetern")
    GroupCard {
        SliderRow("Oben", top, 0f..80f, "${top.toInt()} mm") { top = it }
        SliderRow("Unten", bottom, 0f..80f, "${bottom.toInt()} mm") { bottom = it }
        SliderRow("Links", left, 0f..80f, "${left.toInt()} mm") { left = it }
        SliderRow("Rechts", right, 0f..80f, "${right.toInt()} mm") { right = it }
    }
    PrimaryButton("Zuschneiden", Modifier.padding(16.dp)) {
        val mm = 72f / 25.4f
        runner.run("Seiten werden zugeschnitten …") {
            val out = runner.tmp("crop")
            PdfIo.transform(doc.file(), out, runner.password) { d ->
                PdfOps.crop(d, (0 until d.numberOfPages).toList(), top * mm, right * mm, bottom * mm, left * mm)
            }
            listOf(out to outName(doc, "zugeschnitten"))
        }
    }
}

@Composable
private fun WatermarkTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    val context = LocalContext.current
    var useImage by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("VERTRAULICH") }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var size by remember { mutableStateOf(54f) }
    var opacity by remember { mutableStateOf(0.25f) }
    var rotation by remember { mutableStateOf(45f) }
    var position by remember { mutableStateOf(WatermarkPosition.CENTER) }
    var color by remember { mutableStateOf(0xFFE53935.toInt()) }
    var scale by remember { mutableStateOf(40f) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) image = decodeBitmap(context, uri)
    }
    DocHeader(doc, onChange)
    SectionTitle("Art")
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!useImage, { useImage = false }, { Text("Text") })
        FilterChip(useImage, { useImage = true }, { Text("Bild") })
    }
    if (!useImage) {
        OutlinedTextField(text, { text = it }, Modifier.padding(16.dp).fillMaxWidth(), singleLine = true, label = { Text("Wasserzeichen-Text") })
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0xFFE53935.toInt() to "Rot", 0xFF8E8E93.toInt() to "Grau", 0xFF0A5BD6.toInt() to "Blau", 0xFF000000.toInt() to "Schwarz").forEach { (c, t) ->
                FilterChip(color == c, { color = c }, { Text(t) })
            }
        }
    } else {
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.padding(16.dp)) {
            Text(if (image == null) "Bild auswählen" else "Anderes Bild wählen")
        }
    }
    SectionTitle("Position")
    ChipRows(WatermarkPosition.entries, position, { it.title }) { position = it }
    GroupCard(Modifier.padding(top = 8.dp)) {
        if (!useImage) SliderRow("Schriftgröße", size, 12f..120f, "${size.toInt()} pt") { size = it }
        else SliderRow("Breite", scale, 10f..100f, "${scale.toInt()} %") { scale = it }
        SliderRow("Deckkraft", opacity, 0.05f..1f, "${(opacity * 100).toInt()} %") { opacity = it }
        SliderRow("Drehung", rotation, -90f..90f, "${rotation.toInt()}°") { rotation = it }
    }
    PrimaryButton("Wasserzeichen einfügen", Modifier.padding(16.dp), enabled = if (useImage) image != null else text.isNotBlank()) {
        val spec = WatermarkSpec(
            text = if (useImage) null else text, image = if (useImage) image else null, fontSize = size, colorArgb = color,
            opacity = opacity, rotationDeg = rotation, position = position, imageScalePercent = scale,
        )
        runner.run("Wasserzeichen wird eingefügt …") {
            val out = runner.tmp("watermark")
            PdfIo.transform(doc.file(), out, runner.password) { d -> PdfOps.watermark(d, runner.fonts, spec) }
            listOf(out to outName(doc, "Wasserzeichen"))
        }
    }
}

@Composable
private fun ShareTool(doc: DocumentEntity, onChange: () -> Unit) {
    val context = LocalContext.current
    val exporter = rememberExportLauncher()
    DocHeader(doc, onChange)
    SectionTitle("Weitergeben")
    GroupCard {
        GroupRow("Teilen …", "WhatsApp, Messenger, Cloud usw.", Icons.Outlined.Share) { FileActions.share(context, doc.file(), doc.displayName) }
        GroupRow("Per E-Mail senden", null, Icons.Outlined.Email) { FileActions.email(context, doc.file(), doc.displayName, emptyList(), doc.displayName, "") }
        GroupRow("Drucken", null, Icons.Outlined.Print) { FileActions.print(context, doc.file(), doc.displayName) }
        GroupRow("In Dateien speichern", "Speicherort selbst wählen", Icons.Outlined.SaveAlt, showDivider = false) { exporter(doc.file(), doc.displayName) }
    }
}

// ---------------- Ausfüllen & Signieren ----------------

@Composable
private fun RequestSignaturesTool(doc: DocumentEntity, onChange: () -> Unit) {
    val context = LocalContext.current
    var recipients by remember { mutableStateOf("") }
    var message by remember {
        mutableStateOf("Guten Tag,\n\nbitte unterschreiben Sie das angehängte Dokument und senden Sie es an mich zurück.\n\nVielen Dank!")
    }
    DocHeader(doc, onChange)
    InfoBanner(
        "Eingeschränkt: Es ist kein Signaturdienst (z. B. Sign.com) angebunden, da dafür ein Vertrag und ein API-Zugang des Anbieters nötig sind. " +
            "FA PDF versendet das Dokument mit einer Unterschriftsbitte per E-Mail. Empfänger können es z. B. mit FA PDF unterschreiben und zurücksenden.",
        FaColors.SignRequest,
    )
    OutlinedTextField(recipients, { recipients = it }, Modifier.padding(16.dp).fillMaxWidth(), label = { Text("E-Mail-Empfänger (durch Komma getrennt)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    OutlinedTextField(message, { message = it }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), minLines = 5, label = { Text("Nachricht") })
    PrimaryButton("E-Mail mit Unterschriftsbitte erstellen", Modifier.padding(16.dp)) {
        val to = recipients.split(',', ';', ' ').map { it.trim() }.filter { it.contains('@') }
        FileActions.email(context, doc.file(), doc.displayName, to, "Bitte unterschreiben: ${doc.displayName}", message)
    }
}

// ---------------- Verschlüsseln ----------------

@Composable
private fun RemovePasswordTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    DocHeader(doc, onChange)
    InfoBanner("Der Schutz kann nur mit dem richtigen Passwort entfernt werden. Bei PDFs, die nur Bearbeitungsbeschränkungen haben, genügt ein leeres Feld.")
    OutlinedTextField(pw, { pw = it }, Modifier.padding(16.dp).fillMaxWidth(), singleLine = true, label = { Text("Vorhandenes Passwort") },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    PrimaryButton("Passwortschutz entfernen", Modifier.padding(16.dp)) {
        runner.run("Passwort wird entfernt …") {
            val out = runner.tmp("decrypt")
            val wasEncrypted = try {
                PdfIo.load(doc.file(), pw.ifEmpty { null }).use { d ->
                    val enc = d.isEncrypted
                    d.isAllSecurityToBeRemoved = true
                    PdfIo.save(d, out)
                    enc
                }
            } catch (e: de.fapdf.pdf.PasswordRequiredException) {
                throw PdfUserException("Das Passwort ist falsch.")
            }
            if (!wasEncrypted) { out.delete(); throw PdfUserException("Diese PDF ist nicht verschlüsselt.") }
            listOf(out to outName(doc, "ohne Passwort"))
        }
    }
}

@Composable
private fun EncryptTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(true) }
    var copy by remember { mutableStateOf(false) }
    var modify by remember { mutableStateOf(false) }
    DocHeader(doc, onChange)
    InfoBanner("Verschlüsselung mit AES-256. Bewahren Sie das Passwort sicher auf – ohne Passwort lässt sich die Datei nicht mehr öffnen.")
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Passwort zum Öffnen") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(pw2, { pw2 = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Passwort wiederholen") },
            visualTransformation = PasswordVisualTransformation(), isError = pw2.isNotEmpty() && pw != pw2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(owner, { owner = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Berechtigungspasswort (optional)") },
            visualTransformation = PasswordVisualTransformation())
    }
    SectionTitle("Erlaubt nach dem Öffnen")
    GroupCard {
        GroupRow("Drucken", trailing = { Switch(print, { print = it }) })
        GroupRow("Text kopieren", trailing = { Switch(copy, { copy = it }) })
        GroupRow("Bearbeiten / Formulare", trailing = { Switch(modify, { modify = it }) }, showDivider = false)
    }
    PrimaryButton("Verschlüsseln", Modifier.padding(16.dp), enabled = pw.length >= 4 && pw == pw2) {
        runner.run("PDF wird verschlüsselt …") {
            val out = runner.tmp("encrypt")
            PdfIo.load(doc.file(), runner.password).use { d ->
                if (d.isEncrypted) d.isAllSecurityToBeRemoved = true
                PdfOps.encrypt(d, pw, owner, print, copy, modify)
                PdfIo.save(d, out)
            }
            listOf(out to outName(doc, "geschützt"))
        }
    }
}

@Composable
private fun FlattenTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    DocHeader(doc, onChange)
    InfoBanner("Formularfelder, Kommentare, Markierungen und Zeichnungen werden dauerhaft in den Seiteninhalt übernommen. Sie sind danach nicht mehr veränderbar – sehen aber in jedem Programm gleich aus.")
    PrimaryButton("Ebenen reduzieren", Modifier.padding(16.dp)) {
        runner.run("Ebenen werden reduziert …") {
            val out = runner.tmp("flatten")
            PdfIo.transform(doc.file(), out, runner.password) { d -> PdfOps.flattenAll(d) }
            listOf(out to outName(doc, "reduziert"))
        }
    }
}

// ---------------- Scan ----------------

@Composable
private fun ScannerTool(appVm: AppViewModel, runner: ToolRunner) {
    val context = LocalContext.current
    var lastId by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uri = result?.pdf?.uri
            if (uri != null) appVm.import(uri, persist = false) { id -> lastId = id; runner.results = listOf(id) }
            else runner.error = "Der Scan hat keine PDF geliefert."
        }
    }
    InfoBanner("Die Kamera erkennt das Dokument, schneidet es automatisch zu und korrigiert die Perspektive (Google ML Kit, auf dem Gerät). Benötigt Google Play-Dienste.")
    PrimaryButton("Dokument scannen", Modifier.padding(16.dp)) {
        val activity = context.findActivity()
        if (activity == null) { runner.error = "Scanner kann nicht gestartet werden."; return@PrimaryButton }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(60)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> runner.error = "Scanner nicht verfügbar: ${e.message}" }
    }
    val id = lastId
    if (id != null) {
        SectionTitle("Texterkennung")
        Text(
            "Machen Sie den Scan durchsuchbar: Der erkannte Text wird unsichtbar hinterlegt (Deutsch, Englisch, Türkisch u. a.).",
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
        )
        OutlinedButton(onClick = {
            runner.run("Texterkennung läuft …") {
                val d = runner.repo.get(id) ?: throw PdfUserException("Scan nicht gefunden")
                val ocr = OcrEngine()
                try {
                    val count = PdfIo.load(d.file()).use { it.numberOfPages }
                    val pages = (0 until count).map { p ->
                        val bmp = PageRaster.render(d.file(), p, OcrEngine.OCR_SCALE, runner.tmpDir)
                        ocr.recognize(p, bmp, OcrEngine.OCR_SCALE).also { bmp.recycle() }
                    }
                    val out = runner.tmp("ocr")
                    PdfIo.transform(d.file(), out) { doc ->
                        val cache = DocFontCache(doc, runner.fonts)
                        pages.forEach { OcrLayer.addInvisibleText(doc, it.pageIndex, it.lines, cache) }
                    }
                    val found = PdfIo.load(out).use { doc -> (0 until doc.numberOfPages).sumOf { PageAnalyzer.analyze(doc, it).hiddenLines.size } }
                    runner.info = "Texterkennung abgeschlossen: $found Zeilen erkannt."
                    listOf(out to d.displayName.replace(".pdf", " (durchsuchbar).pdf"))
                } finally {
                    ocr.close()
                }
            }
        }, modifier = Modifier.padding(16.dp).fillMaxWidth()) { Text("Durchsuchbar machen (OCR)") }
    }
}

// ---------------- Hilfen ----------------

@Composable
private fun <T> ChipRows(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        values.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { v -> FilterChip(v == selected, { onSelect(v) }, { Text(label(v)) }) }
            }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row { Text(title, Modifier.weight(1f)); Text(valueText, color = FaColors.SecondaryLabel) }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
