package de.fapdf.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import de.fapdf.data.DocumentEntity
import de.fapdf.data.DocumentRepository
import de.fapdf.pdf.PageRef
import de.fapdf.pdf.PdfOps
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.components.DocChooserSheet
import de.fapdf.ui.components.LargePagePreview
import de.fapdf.ui.components.PageThumb
import de.fapdf.ui.components.rememberDocSource
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.launch

private data class OrgItem(val uid: Int, val ref: PageRef)

/**
 * „Seiten“: große Seitenvorschau im Raster. Antippen markiert, zweimal tippen vergrößert,
 * gedrückt halten und ziehen sortiert. Unten: Drehen, Verdoppeln, Herauslösen, Einfügen, Löschen.
 */
@Composable
fun PagesTool(appVm: AppViewModel, doc: DocumentEntity, runner: ToolRunner, docs: List<DocumentEntity>, register: (() -> Unit) -> Unit) {
    var uid by remember(doc.id, doc.modifiedAt) { mutableIntStateOf(doc.pageCount) }
    val items = remember(doc.id, doc.modifiedAt) { mutableStateListOf<OrgItem>().apply { addAll((0 until doc.pageCount).map { OrgItem(it, PageRef(doc.id, it)) }) } }
    val selected = remember(doc.id, doc.modifiedAt) { mutableStateListOf<Int>() }
    val extraDocs = remember(doc.id, doc.modifiedAt) { mutableStateListOf<Long>() }
    var choose by remember { mutableStateOf(false) }
    var insertMenu by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<OrgItem?>(null) }
    val mainSrc = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var dragKey by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun sources() = (listOf(doc) + extraDocs.mapNotNull { id -> docs.firstOrNull { it.id == id } }).associate { it.id to it.file() }
    fun insertIndex(): Int = items.indexOfLast { it.uid in selected }.let { if (it < 0) items.size else it + 1 }

    // „Speichern“ oben rechts
    LaunchedEffect(doc.id, doc.modifiedAt) {
        register {
            val refs = items.map { it.ref }
            val srcs = sources(); val pw = runner.password
            runner.runInPlace("Seiten werden gespeichert …", doc, "Seiten gespeichert") { out -> PdfOps.compose(srcs, refs, out, mapOf(doc.id to pw)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                DocumentRepository.baseName(doc.displayName) + " · ${items.size} Seiten",
                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected.isNotEmpty()) Text("${selected.size} ausgewählt", color = FaColors.Accent, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { if (selected.size == items.size) selected.clear() else { selected.clear(); selected.addAll(items.map { it.uid }) } }) {
                Text(if (selected.size == items.size && items.isNotEmpty()) "Keine" else "Alle")
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp), state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().pointerInput(doc.id, doc.modifiedAt) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { off ->
                            gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                off.x >= it.offset.x && off.x <= it.offset.x + it.size.width && off.y >= it.offset.y && off.y <= it.offset.y + it.size.height
                            }?.let { dragKey = it.key as Int; dragOffset = Offset.Zero }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val key = dragKey ?: return@detectDragGesturesAfterLongPress
                            dragOffset += amount
                            val info = gridState.layoutInfo.visibleItemsInfo
                            val cur = info.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                            val cx = cur.offset.x + cur.size.width / 2f + dragOffset.x
                            val cy = cur.offset.y + cur.size.height / 2f + dragOffset.y
                            val target = info.firstOrNull {
                                it.key != key && cx >= it.offset.x && cx <= it.offset.x + it.size.width && cy >= it.offset.y && cy <= it.offset.y + it.size.height
                            }
                            if (target != null) {
                                val from = items.indexOfFirst { it.uid == key }
                                val to = items.indexOfFirst { it.uid == target.key }
                                if (from >= 0 && to >= 0) {
                                    items.add(to, items.removeAt(from))
                                    dragOffset += Offset((cur.offset.x - target.offset.x).toFloat(), (cur.offset.y - target.offset.y).toFloat())
                                }
                            }
                            // Am Rand automatisch weiterblättern
                            val end = gridState.layoutInfo.viewportEndOffset
                            if (cy > end - 120) scope.launch { gridState.scrollBy(24f) }
                            else if (cy < 120) scope.launch { gridState.scrollBy(-24f) }
                        },
                        onDragEnd = { dragKey = null; dragOffset = Offset.Zero },
                        onDragCancel = { dragKey = null; dragOffset = Offset.Zero },
                    )
                },
            ) {
                itemsIndexed(items, key = { _, it -> it.uid }) { index, item ->
                    val dragging = item.uid == dragKey
                    val isSel = item.uid in selected
                    val srcDoc = if (item.ref.source == doc.id) doc else docs.firstOrNull { it.id == item.ref.source }
                    val src = when {
                        item.ref.source == PdfOps.BLANK -> null
                        item.ref.source == doc.id -> mainSrc
                        else -> rememberDocSource(srcDoc, null)
                    }
                    Column(
                        (if (dragging) Modifier.zIndex(1f).graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.06f; scaleY = 1.06f }
                        else Modifier.animateItem())
                            .pointerInput(item.uid) {
                                detectTapGestures(
                                    onTap = { if (isSel) selected.remove(item.uid) else selected.add(item.uid) },
                                    onDoubleTap = { preview = item },
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            if (src != null) PageThumb(src, item.ref.page, Modifier.fillMaxWidth(), extraRotation = item.ref.extraRotation, selected = isSel, renderWidth = 360)
                            else Box(
                                Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(6.dp)).background(Color.White)
                                    .border(if (isSel) 3.dp else 1.dp, if (isSel) FaColors.Accent else FaColors.Separator, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (src == null && item.ref.source != PdfOps.BLANK) "…" else "Leere Seite", color = Color(0xFF8A8A93), fontSize = 13.sp) }
                            // Auswahlkreis oben rechts (auch ohne Auswahl sichtbar)
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                                    .background(if (isSel) FaColors.Accent else Color(0x8C0B0B0F))
                                    .border(2.dp, if (isSel) Color(0xFF0B0B0F) else Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (isSel) Icon(Icons.Outlined.Check, null, tint = Color(0xFF0B0B0F), modifier = Modifier.size(15.dp)) }
                        }
                        Text(
                            "${index + 1}" + if (item.ref.source != doc.id && item.ref.source != PdfOps.BLANK) " · ${srcDoc?.displayName?.let { DocumentRepository.baseName(it) } ?: ""}" else "",
                            color = if (isSel) FaColors.Accent else FaColors.SecondaryLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            // Aktionsleiste unten
            val has = selected.isNotEmpty()
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).widthIn(max = 520.dp).fillMaxWidth().height(70.dp)
                    .clip(RoundedCornerShape(20.dp)).background(FaColors.Card).border(1.dp, FaColors.CardBorder, RoundedCornerShape(20.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                BarAction(Icons.Outlined.RotateRight, "Drehen", has) {
                    for (i in items.indices) if (items[i].uid in selected) items[i] = items[i].copy(ref = items[i].ref.copy(extraRotation = (items[i].ref.extraRotation + 90) % 360))
                }
                BarAction(Icons.Outlined.ContentCopy, "Verdoppeln", has) {
                    val copies = ArrayList<OrgItem>()
                    var i = 0
                    while (i < items.size) {
                        val it = items[i]
                        if (it.uid in selected) { val c = OrgItem(uid++, it.ref); items.add(i + 1, c); copies.add(c); i++ }
                        i++
                    }
                }
                BarAction(Icons.Outlined.CallSplit, "Herauslösen", has) {
                    val refs = items.filter { it.uid in selected }.map { it.ref }
                    val srcs = sources(); val pw = runner.password
                    runner.run("Seiten werden herausgelöst …") {
                        val out = runner.tmp("auszug")
                        PdfOps.compose(srcs, refs, out, mapOf(doc.id to pw))
                        listOf(out to DocumentRepository.ensurePdfName(DocumentRepository.baseName(doc.displayName) + " – Seiten " + refs.size))
                    }
                }
                Box {
                    BarAction(Icons.Outlined.Add, "Einfügen", true) { insertMenu = true }
                    DropdownMenu(insertMenu, { insertMenu = false }) {
                        DropdownMenuItem(text = { Text("Leere Seite") }, onClick = {
                            insertMenu = false
                            items.add(insertIndex(), OrgItem(uid++, PageRef(PdfOps.BLANK, 0)))
                        })
                        DropdownMenuItem(text = { Text("Seiten aus anderer PDF …") }, onClick = { insertMenu = false; choose = true })
                    }
                }
                BarAction(Icons.Outlined.DeleteOutline, "Löschen", has && selected.size < items.size, FaColors.Destructive) {
                    items.removeAll { it.uid in selected }; selected.clear()
                }
            }
        }
    }

    preview?.let { item ->
        val src = if (item.ref.source == doc.id) mainSrc else null
        Dialog(onDismissRequest = { preview = null }) {
            Box(Modifier.fillMaxWidth().clickable { preview = null }) {
                if (src != null) LargePagePreview(src, item.ref.page, extraRotation = item.ref.extraRotation, maxHeight = 640.dp)
                else Text("Vorschau nicht verfügbar", color = Color.White, modifier = Modifier.padding(24.dp))
            }
        }
    }

    if (choose) DocChooserSheet(
        documents = docs, title = "Seiten einfügen aus …",
        onImport = { uri ->
            choose = false
            appVm.import(uri, persist = false) { id ->
                val d = appVm.documents.value.firstOrNull { it.id == id }
                if (id !in extraDocs && id != doc.id) extraDocs.add(id)
                val at = insertIndex()
                repeat(d?.pageCount ?: 0) { p -> items.add(at + p, OrgItem(uid++, PageRef(id, p))) }
            }
        },
        onDismiss = { choose = false },
    ) { d ->
        choose = false
        if (d.id != doc.id && d.id !in extraDocs) extraDocs.add(d.id)
        val at = insertIndex()
        repeat(d.pageCount) { p -> items.add(at + p, OrgItem(uid++, PageRef(d.id, p))) }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, tint: Color = FaColors.Accent, onClick: () -> Unit) {
    val c = if (enabled) tint else FaColors.SecondaryLabel.copy(alpha = 0.5f)
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = c, modifier = Modifier.size(24.dp))
        Text(label, color = if (enabled) FaColors.Label else c, fontSize = 11.sp)
    }
}
