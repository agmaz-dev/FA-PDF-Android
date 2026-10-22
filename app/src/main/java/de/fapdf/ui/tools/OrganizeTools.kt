package de.fapdf.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.fapdf.data.DocumentEntity
import de.fapdf.data.DocumentRepository
import de.fapdf.pdf.PageRangeParser
import de.fapdf.pdf.PageRef
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.PdfOps
import de.fapdf.pdf.PdfUserException
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.components.DocCard
import de.fapdf.ui.components.DocChooserSheet
import de.fapdf.ui.components.FaButton
import de.fapdf.ui.components.FaSoftButton
import de.fapdf.ui.components.InfoCard
import de.fapdf.ui.components.LabeledField
import de.fapdf.ui.components.LargePagePreview
import de.fapdf.ui.components.PageNavigator
import de.fapdf.ui.components.PageThumb
import de.fapdf.ui.components.RadioRow
import de.fapdf.ui.components.ReorderableColumn
import de.fapdf.ui.components.SectionLabel
import de.fapdf.ui.components.Thumbnail
import de.fapdf.ui.components.ToggleRow
import de.fapdf.ui.components.file
import de.fapdf.ui.components.rememberDocSource
import de.fapdf.ui.theme.FaColors

// ---------------- PDFs zusammenfügen ----------------

@Composable
fun MergeToolV2(appVm: AppViewModel, runner: ToolRunner, docs: List<DocumentEntity>, initial: Long?) {
    val selected = remember { mutableStateListOf<Long>().apply { initial?.let { add(it) } } }
    var choose by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val chosen = selected.mapNotNull { id -> docs.firstOrNull { it.id == id } }
    val suggested = chosen.take(3).joinToString("_und_") { DocumentRepository.baseName(it.displayName) }.ifBlank { "Zusammengefügt" } + ".pdf"

    SectionLabel("Ausgewählte Dateien (${chosen.size})")
    if (chosen.isEmpty()) InfoCard("Fügen Sie mindestens zwei PDF-Dateien hinzu. Die Reihenfolge ändern Sie mit den Pfeilen oder durch Gedrückthalten und Ziehen.")
    ReorderableColumn(
        items = chosen, keyOf = { it.id },
        onMove = { from, to -> val id = selected.removeAt(from); selected.add(to, id) },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { d, i, dragMod, _ ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FaColors.Surface)
                .border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp)).then(dragMod).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DragIndicator, "Ziehen zum Sortieren", tint = FaColors.SecondaryLabel)
            Spacer(Modifier.width(6.dp))
            Thumbnail(d.thumbnailPath, 38.dp, 50.dp, d.modifiedAt)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${d.pageCount} Seiten · ${de.fapdf.ui.components.Format.size(d.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
            }
            IconButton(onClick = { if (i > 0) { selected.removeAt(i); selected.add(i - 1, d.id) } }, enabled = i > 0) {
                Icon(Icons.Outlined.ArrowUpward, "Nach oben", tint = FaColors.Accent)
            }
            IconButton(onClick = { if (i < selected.size - 1) { selected.removeAt(i); selected.add(i + 1, d.id) } }, enabled = i < selected.size - 1) {
                Icon(Icons.Outlined.ArrowDownward, "Nach unten", tint = FaColors.Accent)
            }
            IconButton(onClick = { selected.remove(d.id) }) { Icon(Icons.Outlined.Close, "Entfernen", tint = FaColors.SecondaryLabel) }
        }
    }
    FaSoftButton("Dateien hinzufügen", icon = Icons.Outlined.Add) { choose = true }
    SectionLabel("Ausgabedatei")
    LabeledField("Dateiname", name.ifEmpty { suggested }, { name = it })
    FaButton("Zusammenfügen", icon = Icons.Outlined.NoteAdd, enabled = chosen.size >= 2) {
        val files = chosen.map { it.file() }
        val out = DocumentRepository.ensurePdfName(name.ifBlank { suggested })
        runner.run("PDFs werden zusammengefügt …") {
            val f = runner.tmp("merge")
            PdfOps.merge(files, f)
            listOf(f to out)
        }
    }
    if (choose) DocChooserSheet(
        documents = docs.filter { it.id !in selected }, title = "Dateien hinzufügen",
        onImport = { uri -> choose = false; appVm.import(uri, persist = false) { selected.add(it) } },
        onDismiss = { choose = false },
    ) { d -> choose = false; selected.add(d.id) }
}

// ---------------- PDF teilen ----------------

private data class RangeRow(val id: Int, val text: String)

@Composable
fun SplitToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var eachPage by remember { mutableStateOf(false) }
    val n = doc.pageCount.coerceAtLeast(1)
    var nextId by remember { mutableIntStateOf(3) }
    val rows = remember(doc.id) {
        mutableStateListOf<RangeRow>().apply {
            // Vorschlag: in bis zu drei gleich große Teile
            val parts = if (n >= 3) 3 else n
            val size = (n + parts - 1) / parts
            var start = 1; var id = 0
            while (start <= n) { val end = minOf(n, start + size - 1); add(RangeRow(id++, if (start == end) "$start" else "$start – $end")); start = end + 1 }
        }
    }
    val src = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    DocCard(doc, onChange = onChange)
    Column(Modifier.padding(top = 6.dp)) {
        RadioRow(eachPage, "Alle Seiten einzeln teilen", "Jede Seite wird als eigene PDF gespeichert.") { eachPage = true }
        RadioRow(!eachPage, "Seitenbereiche", "PDF in benutzerdefinierte Bereiche teilen.") { eachPage = false }
    }
    fun parse(t: String): List<Int>? = runCatching { PageRangeParser.parse(t.replace('–', '-').replace(" ", ""), n).flatten() }.getOrNull()
    if (!eachPage) {
        rows.forEachIndexed { i, r ->
            val pages = parse(r.text)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Mini-Vorschau der ersten Seiten des Bereichs
                Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (src != null && pages != null) pages.take(3).forEach { p -> PageThumb(src, p, Modifier.width(30.dp), renderWidth = 120) }
                }
                Spacer(Modifier.width(8.dp))
                Text("Seiten", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    r.text, { rows[i] = r.copy(text = it) }, Modifier.weight(1f), singleLine = true, isError = pages == null,
                    shape = RoundedCornerShape(10.dp),
                )
                IconButton(onClick = { rows.removeAt(i) }, enabled = rows.size > 1) { Icon(Icons.Outlined.Close, "Bereich entfernen") }
            }
        }
        FaSoftButton("Bereich hinzufügen", icon = Icons.Outlined.Add) { rows.add(RangeRow(nextId++, "$n")) }
    }
    val valid = eachPage || rows.all { parse(it.text) != null }
    FaButton("PDF teilen", icon = Icons.Outlined.ContentCut, enabled = valid) {
        val texts = rows.map { it.text }
        val each = eachPage
        runner.run("PDF wird geteilt …") {
            val pageCount = PdfIo.load(doc.file(), runner.password).use { it.numberOfPages }
            val parts = if (each) (0 until pageCount).map { listOf(it) }
            else texts.map { t -> runCatching { PageRangeParser.parse(t.replace('–', '-').replace(" ", ""), pageCount).flatten() }.getOrElse { throw PdfUserException("Ungültiger Bereich: $t") } }
            val outs = parts.map { runner.tmp("split") }
            PdfOps.split(doc.file(), parts, outs, runner.password)
            outs.mapIndexed { i, f ->
                val p = parts[i]
                f to outName(doc, if (p.size == 1) "Seite ${p.first() + 1}" else "Seiten ${p.first() + 1}-${p.last() + 1}")
            }
        }
    }
}

// ---------------- PDF drehen ----------------

@Composable
fun RotateToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var delta by remember(doc.id, doc.modifiedAt) { mutableIntStateOf(0) }
    var allPages by remember { mutableStateOf(true) }
    val src = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    DocCard(doc, onChange = onChange)
    if (src != null) {
        val count = src.pageCount
        // Durchgehend wischbare Seitenansicht (statt Pfeilen); die Nachbarseiten ragen sichtbar herein
        val pager = rememberPagerState(pageCount = { count })
        val p = pager.currentPage.coerceIn(0, count - 1)
        HorizontalPager(
            state = pager, contentPadding = PaddingValues(horizontal = 48.dp), pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth().height(380.dp), beyondViewportPageCount = 1,
        ) { i ->
            val rot = if (allPages || i == p) delta else 0
            LargePagePreview(src, i, extraRotation = rot, maxHeight = 360.dp)
        }
        Text("Seite ${p + 1} von $count", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RotateButton("90° nach links", Icons.Outlined.RotateLeft, Modifier.weight(1f)) { delta = (delta + 270) % 360 }
            RotateButton("90° nach rechts", Icons.Outlined.RotateRight, Modifier.weight(1f)) { delta = (delta + 90) % 360 }
        }
        SectionLabel("Für welche Seiten anwenden?")
        RadioRow(allPages, "Alle Seiten ($count)") { allPages = true }
        RadioRow(!allPages, "Nur ausgewählte Seite (Seite ${p + 1})") { allPages = false }
        FaButton("Speichern", icon = Icons.Outlined.Refresh, enabled = delta != 0) {
            val d = delta; val all = allPages; val only = p
            runner.runInPlace("Seiten werden gedreht …", doc, if (all) "Alle Seiten gedreht" else "Seite ${only + 1} gedreht") { out ->
                PdfIo.transform(doc.file(), out, runner.password) { pd ->
                    PdfOps.rotate(pd, if (all) (0 until pd.numberOfPages).toList() else listOf(only), d)
                }
            }
        }
        if (delta == 0) Text("Wählen Sie zuerst eine Drehrichtung.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
    } else {
        Text("Vorschau wird geladen …", Modifier.padding(20.dp), color = FaColors.SecondaryLabel)
    }
}

@Composable
private fun RotateButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(FaColors.AccentLight).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = FaColors.Accent)
        Spacer(Modifier.height(4.dp))
        Text(text, color = FaColors.Accent, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------- Seiten löschen ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletePagesTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    val selected = remember(doc.id) { mutableStateListOf<Int>() }
    var confirm by remember { mutableStateOf(false) }
    DocCard(doc, onChange = onChange)
    SelectionHeader(selected.size, doc.pageCount, onAll = { selected.clear(); selected.addAll(0 until doc.pageCount) }, onNone = { selected.clear() })
    PageThumbGrid(
        doc, runner.password, null, selected.toSet(), selectable = true,
        onToggle = { p -> if (p in selected) selected.remove(p) else selected.add(p) },
        onNeedPassword = { runner.askPassword = {} },
        labelFor = { _, p -> "${p + 1}" },
    )
    Text(
        if (selected.size == 1) "1 Seite ausgewählt" else "${selected.size} Seiten ausgewählt",
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall,
    )
    FaSoftButton("Löschen", icon = Icons.Outlined.DeleteOutline, color = FaColors.Destructive, enabled = selected.isNotEmpty()) { confirm = true }
    if (confirm) ModalBottomSheet(onDismissRequest = { confirm = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Seiten löschen?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Möchten Sie die ${selected.size} ausgewählten Seiten wirklich aus dem PDF entfernen?",
                style = MaterialTheme.typography.bodyMedium, color = FaColors.SecondaryLabel,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { confirm = false }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Abbrechen", color = FaColors.Accent) }
                androidx.compose.material3.Button(
                    onClick = {
                        confirm = false
                        val pages = selected.toList()
                        val n = pages.size
                        runner.runInPlace("Seiten werden gelöscht …", doc, if (n == 1) "1 Seite gelöscht" else "$n Seiten gelöscht") { out ->
                            PdfIo.transform(doc.file(), out, runner.password) { pd -> PdfOps.deletePages(pd, pages) }
                        }
                        selected.clear()
                    },
                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FaColors.Destructive),
                ) { Text("Löschen") }
            }
        }
    }
}

@Composable
private fun SelectionHeader(count: Int, total: Int, onAll: () -> Unit, onNone: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Tippen Sie auf die gewünschten Seiten", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
        Text("Alle", Modifier.clickable(onClick = onAll).padding(8.dp), color = FaColors.Accent, style = MaterialTheme.typography.labelMedium)
        Text("Keine", Modifier.clickable(enabled = count > 0, onClick = onNone).padding(8.dp), color = if (count > 0) FaColors.Accent else FaColors.SecondaryLabel, style = MaterialTheme.typography.labelMedium)
    }
    if (total == 0) Text("Seitenzahl unbekannt (passwortgeschützt).", Modifier.padding(horizontal = 16.dp), color = FaColors.SecondaryLabel)
}

// ---------------- Seiten extrahieren ----------------

@Composable
fun ExtractPagesTool(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    val selected = remember(doc.id) { mutableStateListOf<Int>() }
    var field by remember(doc.id) { mutableStateOf("") }
    var singleFiles by remember { mutableStateOf(false) }
    fun fieldFromSelection() { field = compactRanges(selected.sorted()) }
    DocCard(doc, onChange = onChange)
    SelectionHeader(selected.size, doc.pageCount, onAll = { selected.clear(); selected.addAll(0 until doc.pageCount); fieldFromSelection() }, onNone = { selected.clear(); field = "" })
    PageThumbGrid(
        doc, runner.password, null, selected.toSet(), selectable = true,
        onToggle = { p -> if (p in selected) selected.remove(p) else selected.add(p); fieldFromSelection() },
        onNeedPassword = { runner.askPassword = {} },
        labelFor = { _, p -> "${p + 1}" },
    )
    val parsed = if (field.isBlank()) emptyList() else runCatching { PageRangeParser.parse(field.replace('–', '-').replace(" ", ""), doc.pageCount).flatten() }.getOrNull()
    LabeledField(
        "Zu extrahierende Seiten", field,
        { t ->
            field = t
            runCatching { PageRangeParser.parse(t.replace('–', '-').replace(" ", ""), doc.pageCount).flatten() }.getOrNull()?.let {
                selected.clear(); selected.addAll(it.distinct())
            }
        },
        hint = if (parsed == null) "Ungültige Angabe" else "Seitenzahlen durch Komma trennen (z. B. 1, 3, 5) oder Bereiche angeben (z. B. 1–4).",
        isError = parsed == null,
    )
    ToggleRow("Als einzelne PDFs speichern", "Jede ausgewählte Seite wird als eigene PDF gespeichert.", checked = singleFiles) { singleFiles = it }
    FaButton("Extrahieren", icon = Icons.Outlined.Upload, enabled = !parsed.isNullOrEmpty()) {
        val pages = parsed!!.distinct()
        val single = singleFiles
        runner.run("Seiten werden extrahiert …") {
            if (single) {
                val outs = pages.map { runner.tmp("extract") }
                PdfOps.split(doc.file(), pages.map { listOf(it) }, outs, runner.password)
                outs.mapIndexed { i, f -> f to outName(doc, "Seite ${pages[i] + 1}") }
            } else {
                val out = runner.tmp("extract")
                PdfOps.extract(doc.file(), pages, out, runner.password)
                listOf(out to outName(doc, "Auszug"))
            }
        }
    }
}

/** [0,1,2,4] → „1–3, 5“ */
fun compactRanges(pages: List<Int>): String {
    if (pages.isEmpty()) return ""
    val out = ArrayList<String>()
    var start = pages.first(); var prev = start
    for (p in pages.drop(1) + Int.MIN_VALUE) {
        if (p == prev + 1) { prev = p; continue }
        out.add(if (start == prev) "${start + 1}" else "${start + 1}–${prev + 1}")
        start = p; prev = p
    }
    return out.joinToString(", ")
}

// ---------------- PDF organisieren ----------------

private data class OrgItem(val uid: Int, val ref: PageRef)

@Composable
fun OrganizeToolV2(appVm: AppViewModel, doc: DocumentEntity, runner: ToolRunner, docs: List<DocumentEntity>, onChange: () -> Unit) {
    var uid by remember(doc.id, doc.modifiedAt) { mutableIntStateOf(doc.pageCount) }
    val items = remember(doc.id, doc.modifiedAt) { mutableStateListOf<OrgItem>().apply { addAll((0 until doc.pageCount).map { OrgItem(it, PageRef(doc.id, it)) }) } }
    var choose by remember { mutableStateOf(false) }
    val extraDocs = remember(doc.id, doc.modifiedAt) { mutableStateListOf<Long>() }
    DocCard(doc, onChange = onChange)
    InfoCard("Zum Sortieren gedrückt halten und Seiten verschieben.")
    val mainSrc = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    ReorderableColumn(
        items = items.toList(), keyOf = { it.uid },
        onMove = { from, to -> val it = items.removeAt(from); items.add(to, it) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) { item, index, dragMod, _ ->
        val srcDoc = if (item.ref.source == doc.id) doc else docs.firstOrNull { it.id == item.ref.source }
        val src = if (item.ref.source == doc.id) mainSrc else rememberDocSource(srcDoc, null)
        var menu by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FaColors.Surface)
                .border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp)).then(dragMod).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DragIndicator, null, tint = FaColors.SecondaryLabel)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(40.dp)) { if (src != null) PageThumb(src, item.ref.page, Modifier.width(40.dp), extraRotation = item.ref.extraRotation, renderWidth = 120) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Seite ${index + 1}", style = MaterialTheme.typography.titleSmall)
                if (item.ref.source != doc.id) Text("aus ${srcDoc?.displayName ?: "anderer PDF"}, S. ${item.ref.page + 1}", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else if (item.ref.page != index) Text("ursprünglich Seite ${item.ref.page + 1}", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mehr") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Um 90° drehen") }, onClick = {
                        menu = false
                        val i = items.indexOfFirst { it.uid == item.uid }
                        if (i >= 0) items[i] = item.copy(ref = item.ref.copy(extraRotation = (item.ref.extraRotation + 90) % 360))
                    })
                    DropdownMenuItem(text = { Text("Seite entfernen", color = FaColors.Destructive) }, enabled = items.size > 1, onClick = {
                        menu = false; items.removeAll { it.uid == item.uid }
                    })
                }
            }
            Icon(Icons.Outlined.Menu, null, tint = FaColors.SecondaryLabel)
        }
    }
    FaSoftButton("Seiten hinzufügen", icon = Icons.Outlined.Add) { choose = true }
    FaButton("Fertig", icon = Icons.Outlined.Check, enabled = items.isNotEmpty()) {
        val refs = items.map { it.ref }
        val sources = (listOf(doc) + extraDocs.mapNotNull { id -> docs.firstOrNull { it.id == id } }).associate { it.id to it.file() }
        val pw = runner.password
        runner.runInPlace("Neue Seitenfolge wird gespeichert …", doc, "Neue Seitenfolge gespeichert") { out ->
            PdfOps.compose(sources, refs, out, mapOf(doc.id to pw))
        }
    }
    if (choose) DocChooserSheet(
        documents = docs, title = "Seiten hinzufügen aus …",
        onImport = { uri ->
            choose = false
            appVm.import(uri, persist = false) { id ->
                val d = appVm.documents.value.firstOrNull { it.id == id }
                val count = d?.pageCount ?: 0
                if (id !in extraDocs && id != doc.id) extraDocs.add(id)
                repeat(count) { p -> items.add(OrgItem(uid++, PageRef(id, p))) }
            }
        },
        onDismiss = { choose = false },
    ) { d ->
        choose = false
        if (d.id != doc.id && d.id !in extraDocs) extraDocs.add(d.id)
        repeat(d.pageCount) { p -> items.add(OrgItem(uid++, PageRef(d.id, p))) }
    }
}
