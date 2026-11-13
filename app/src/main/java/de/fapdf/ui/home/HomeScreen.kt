package de.fapdf.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import de.fapdf.data.DocumentRepository
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import de.fapdf.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fapdf.data.DocumentEntity
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.Tool
import de.fapdf.ui.ToolCategory
import de.fapdf.ui.WorkspaceMode
import de.fapdf.ui.components.ConfirmDialog
import de.fapdf.ui.components.DocChooserSheet
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.Format
import de.fapdf.ui.components.Thumbnail
import de.fapdf.ui.components.ToolIconBox
import de.fapdf.ui.components.file
import de.fapdf.ui.theme.FaColors
import de.fapdf.ui.workspace.TextInputDialog
import kotlinx.coroutines.launch

interface HomeNav {
    fun workspace(docId: Long, mode: WorkspaceMode)
    fun tool(tool: Tool, docId: Long?)
    fun profiles()
    fun signatures()
    fun settings()
}

/**
 * Startseite nach Designvorlage: drei Bereiche (Dokumente | Werkzeuge | Einstellungen)
 * über die untere Leiste – oder per Wischen (nach rechts → Dokumente).
 */
@Composable
fun HomeScreen(vm: AppViewModel, nav: HomeNav) {
    val pager = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()
    val docs by vm.documents.collectAsState()
    var chooserFor by remember { mutableStateOf<Tool?>(null) }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri, persist = true)
    }

    Scaffold(
        containerColor = FaColors.Background,
        bottomBar = {
            // Schmale Leiste: kleine Symbole, kleine Schrift
            Row(
                Modifier.fillMaxWidth().background(FaColors.Surface).navigationBarsPadding().height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    Triple("Dokumente", Icons.Outlined.Description, Icons.Filled.Description),
                    Triple("Werkzeuge", Icons.Outlined.GridView, Icons.Filled.GridView),
                    Triple("Einstellungen", Icons.Outlined.Settings, Icons.Filled.Settings),
                ).forEachIndexed { i, (label, icon, iconSel) ->
                    val sel = pager.currentPage == i
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable { scope.launch { pager.animateScrollToPage(i) } },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (sel) iconSel else icon, label, tint = if (sel) FaColors.Accent else FaColors.SecondaryLabel, modifier = Modifier.size(22.dp))
                        Text(label, fontSize = 11.sp, color = if (sel) FaColors.Accent else FaColors.SecondaryLabel, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.padding(padding).fillMaxSize(), beyondViewportPageCount = 1) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        // Schmale Kopfzeile – der Platz gehört den Dokumenten
                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Dateien", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Text("${docs.size}", fontSize = 14.sp, color = FaColors.SecondaryLabel)
                        }
                        RecentDocuments(vm, docs, nav)
                    }
                    // Schwebender Plus-Knopf: PDF öffnen
                    Box(
                        Modifier.align(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(16.dp).size(56.dp).clip(RoundedCornerShape(18.dp))
                            .background(FaColors.Accent).clickable { opener.launch(arrayOf("application/pdf")) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, "PDF öffnen", tint = FaColors.OnAccent, modifier = Modifier.size(30.dp)) }
                }
                1 -> ToolsHome(
                    onOpen = { opener.launch(arrayOf("application/pdf")) },
                    onScan = { nav.tool(Tool.SCANNER, null) },
                    onProfile = nav::profiles,
                ) { tool -> if (!tool.needsDocument) nav.tool(tool, null) else chooserFor = tool }
                else -> SettingsScreen(vm, onBack = null, onProfiles = nav::profiles, onSignatures = nav::signatures)
            }
        }
    }

    chooserFor?.let { tool ->
        DocChooserSheet(
            documents = docs, title = tool.title,
            onImport = { uri -> chooserFor = null; vm.import(uri, persist = true) { id -> openTool(nav, tool, id) } },
            onDismiss = { chooserFor = null },
        ) { d -> chooserFor = null; openTool(nav, tool, d.id) }
    }

    vm.actionDocId?.let { id ->
        val d = docs.firstOrNull { it.id == id }
        if (d != null) ActionSheet(vm, d, nav) { vm.actionDocId = null }
    }
}

private fun openTool(nav: HomeNav, tool: Tool, docId: Long) {
    val m = tool.workspaceMode
    if (m != null) nav.workspace(docId, m) else nav.tool(tool, docId)
}

@Composable
private fun ToolsHome(onOpen: () -> Unit, onScan: () -> Unit, onProfile: () -> Unit, onTool: (Tool) -> Unit) {
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<ToolGroup?>(null) }
    openGroup?.let { g -> GroupSheet(g, onDismiss = { openGroup = null }) { t -> openGroup = null; onTool(t) } }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Auf Tablets bleibt der Inhalt lesbar breit und mittig
        val contentWidth = if (maxWidth > 900.dp) 900.dp else maxWidth
        val cols = ((contentWidth - 32.dp) / 118.dp).toInt().coerceIn(3, 6)
        Column(
            Modifier.width(contentWidth).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Werkzeuge", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(FaColors.AccentLight).clickable(onClick = onProfile),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Person, "Profile", tint = FaColors.Accent, modifier = Modifier.size(22.dp)) }
            }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Werkzeug suchen") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = FaColors.Card, focusedContainerColor = FaColors.Surface,
                    unfocusedBorderColor = FaColors.CardBorder,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onOpen, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent),
                ) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(10.dp)); Text("PDF öffnen", style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = onScan, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FaColors.AccentLight, contentColor = FaColors.Accent),
                ) { Icon(Icons.Outlined.CropFree, null); Spacer(Modifier.width(10.dp)); Text("Scannen", style = MaterialTheme.typography.labelLarge) }
            }
            val q = query.trim()
            if (q.isNotEmpty()) {
                val hits = Tool.entries.filter { it != Tool.REQUEST_SIGNATURES }.filter { it.title.contains(q, ignoreCase = true) || it.category.title.contains(q, ignoreCase = true) }
                Spacer(Modifier.height(18.dp))
                if (hits.isEmpty()) Text("Kein Werkzeug gefunden.", color = FaColors.SecondaryLabel)
                TileGrid(hits, cols, onTool)
            } else {
                // Werkzeuge in wenigen Gruppen; Antippen öffnet die Gruppe von unten
                val gcols = if (contentWidth > 600.dp) 3 else 2
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    toolGroups.chunked(gcols).forEach { row ->
                        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { g ->
                                GroupTile(g, Modifier.weight(1f).fillMaxHeight()) { if (g.tools.size == 1) onTool(g.tools[0].first) else openGroup = g }
                            }
                            repeat(gcols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(FaColors.Card)
                            .clickable { onTool(Tool.SHARE) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Share, null, tint = FaColors.Accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Teilen & Drucken", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("›", color = FaColors.SecondaryLabel, fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Kachelraster: farbiger Hintergrund, Symbol in Kategoriefarbe, Text mittig. */
@Composable
private fun TileGrid(tools: List<Tool>, cols: Int, onTool: (Tool) -> Unit, horizontal: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tool -> ToolTile(tool, Modifier.weight(1f), horizontal) { onTool(tool) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToolTile(tool: Tool, modifier: Modifier, horizontal: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val box = modifier.heightIn(min = if (horizontal) 64.dp else 104.dp).clip(shape).background(FaColors.Card).clickable(onClick = onClick)
    val icon: @Composable () -> Unit = {
        val t = tool.iconText
        if (t != null) Text(t, color = FaColors.Accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        else Icon(tool.icon, null, tint = FaColors.Accent, modifier = Modifier.size(30.dp))
    }
    if (horizontal) {
        Row(box.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon(); Spacer(Modifier.width(12.dp))
            Text(tool.title, style = MaterialTheme.typography.bodyMedium, color = FaColors.Label)
        }
    } else {
        Column(box.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(
                tool.title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = FaColors.Label, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class SortMode(val title: String) { OPENED("Zuletzt geöffnet"), MODIFIED("Zuletzt bearbeitet"), IMPORTED("Importdatum"), NAME("Name"), SIZE("Größe") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentDocuments(vm: AppViewModel, docs: List<DocumentEntity>, nav: HomeNav) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortMode.OPENED) }
    var favOnly by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var renameDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var deadlinesOnly by remember { mutableStateOf(false) }
    var previewId by remember { mutableStateOf<Long?>(null) }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val index = vm.container.index
    @Suppress("UNUSED_VARIABLE") val indexVersion = index.version // neu zeichnen, wenn Text/Kategorien dazukommen
    val presentCats = docs.mapNotNull { index.category(it.id) }.distinct().sortedBy { de.fapdf.data.DocIndex.CATEGORIES.indexOf(it) }
    val now = System.currentTimeMillis()
    val withDeadline = docs.mapNotNull { d -> index.deadline(d.id)?.takeIf { it.date != null }?.let { d to it } }
    val soon = withDeadline.count { (_, dl) -> dl.date!! < now + 14L * 24 * 3600_000 && dl.date > now - 30L * 24 * 3600_000 }
    val list0 = docs.filter { (!favOnly || it.favorite) && (category == null || index.category(it.id) == category) && (query.isBlank() || index.matches(it, query)) && (!deadlinesOnly || index.deadline(it.id)?.date != null) }
    val list = if (deadlinesOnly) list0.sortedBy { index.deadline(it.id)?.date ?: Long.MAX_VALUE } else list0
        .let { l ->
            when (sort) {
                SortMode.OPENED -> l.sortedByDescending { it.lastOpenedAt }
                SortMode.MODIFIED -> l.sortedByDescending { it.modifiedAt }
                SortMode.IMPORTED -> l.sortedByDescending { it.importedAt }
                SortMode.NAME -> l.sortedBy { it.displayName.lowercase() }
                SortMode.SIZE -> l.sortedByDescending { it.sizeBytes }
            }
        }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, Modifier.weight(1f), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Suchen") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = FaColors.Card, focusedContainerColor = FaColors.Card,
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = FaColors.Accent,
                ),
            )
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "Sortieren") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.entries.forEach { m ->
                        DropdownMenuItem(text = { Text((if (m == sort) "✓ " else "") + m.title) }, onClick = { sort = m; sortMenu = false })
                    }
                }
            }
        }
        // Kategorien (von der KI bzw. automatisch erkannt) und Favoriten
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            item { FilterChip(selected = category == null && !favOnly && !deadlinesOnly, onClick = { category = null; favOnly = false; deadlinesOnly = false }, label = { Text("Alle") }) }
            if (withDeadline.isNotEmpty()) item {
                FilterChip(selected = deadlinesOnly, onClick = { deadlinesOnly = !deadlinesOnly }, label = { Text("Fristen (${withDeadline.size})") })
            }
            item {
                FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text("Favoriten") },
                    leadingIcon = { Icon(if (favOnly) Icons.Filled.Star else Icons.Outlined.StarOutline, null, modifier = Modifier.size(18.dp)) })
            }
            items(presentCats.size) { i ->
                val c = presentCats[i]
                FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
            }
        }
        if (soon > 0 && !deadlinesOnly) Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2A1416)).clickable { deadlinesOnly = true }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (soon == 1) "1 Frist in den nächsten 14 Tagen" else "$soon Fristen in den nächsten 14 Tagen",
                color = Color(0xFFFFD6D6), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("Anzeigen ›", color = Color(0xFFFFB3B3), fontSize = 13.sp)
        }
        Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1.15f).fillMaxHeight()) {
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (docs.isEmpty()) "Noch keine Dokumente.\nÖffnen Sie eine PDF über das Ordnersymbol oder über „Öffnen mit“ in einer anderen App."
                    else "Keine Treffer.",
                    color = FaColors.SecondaryLabel, style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list.size, key = { list[it].id }) { i ->
                val d = list[i]
                var menu by remember { mutableStateOf(false) }
                // Dokument als große Vorschaukarte
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FaColors.Card)
                        .let { if (wide && previewId == d.id) it.border(2.dp, FaColors.Accent, RoundedCornerShape(18.dp)) else it }
                        .combinedClickable(onClick = { if (wide) previewId = d.id else vm.actionDocId = d.id }, onLongClick = { menu = true })
                        .padding(8.dp),
                ) {
                    Box {
                        BigThumb(d.thumbnailPath, d.modifiedAt)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(34.dp).clip(CircleShape).background(Color(0x990B0B0F))
                                .clickable { vm.setFavorite(d.id, !d.favorite) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (d.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline, "Favorit", tint = if (d.favorite) Color(0xFFFFB300) else Color.White, modifier = Modifier.size(20.dp)) }
                        if (vm.namingBusy == d.id) Text(
                            "KI benennt …", color = Color(0xFF0B0B0F), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(FaColors.Accent).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        DocumentRepository.baseName(d.displayName), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, color = FaColors.Label,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
                    )
                    Row(Modifier.padding(top = 4.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        index.category(d.id)?.let { c ->
                            Text(c, color = FaColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(FaColors.AccentLight).padding(horizontal = 6.dp, vertical = 2.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.GERMANY).format(java.util.Date(d.modifiedAt)),
                            color = FaColors.SecondaryLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    index.deadline(d.id)?.date?.let { t ->
                        Text("Frist " + java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(t)),
                            color = if (t < now) Color(0xFFFF6B6B) else Color(0xFFFFB3B3), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A1416)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (query.isNotBlank() && !d.displayName.contains(query, ignoreCase = true)) index.snippet(d.id, query)?.let { sn ->
                        Text(sn, color = FaColors.SecondaryLabel, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
                    }
                    Box {
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Öffnen") }, onClick = { menu = false; vm.actionDocId = d.id })
                            DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; renameDoc = d })
                            DropdownMenuItem(text = { Text("Aus Verlauf entfernen") }, leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, null) }, onClick = { menu = false; vm.removeFromHistory(d.id) })
                            DropdownMenuItem(text = { Text("Löschen", color = FaColors.Destructive) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = FaColors.Destructive) }, onClick = { menu = false; deleteDoc = d })
                        }
                    }
                }
            }
        }
        }
        if (wide) {
            val pd = docs.firstOrNull { it.id == previewId } ?: list.firstOrNull()
            if (pd != null) DocPreviewPane(vm, pd, nav, Modifier.weight(1f).fillMaxHeight())
        }
        }
    }
    renameDoc?.let { d ->
        TextInputDialog("Umbenennen", null, "Dateiname", initial = d.displayName.removeSuffix(".pdf"), onDismiss = { renameDoc = null }) {
            vm.rename(d.id, it); renameDoc = null
        }
    }
    deleteDoc?.let { d ->
        ConfirmDialog(
            "Dokument löschen?",
            "„${d.displayName}“ wird aus FA PDF gelöscht." + if (d.sourceUri != null) " Die Originaldatei wird ebenfalls gelöscht." else "",
            "Löschen", destructive = true,
            onConfirm = {
                deleteDoc = null
                vm.deleteCompletely(d.id) { orig -> if (orig) FileActions.toast(context, "Dokument und Originaldatei gelöscht") }
            },
            onDismiss = { deleteDoc = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(vm: AppViewModel, d: DocumentEntity, nav: HomeNav, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var scanEdit by remember { mutableStateOf(false) }
    if (scanEdit) de.fapdf.ui.tools.ScanEditDialog(d.id) { scanEdit = false }
    var expanded by remember { mutableStateOf<String?>(null) }
    var askConsent by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun nameIt() {
        vm.nameNow(d.id) { msg -> FileActions.toast(context, msg) }
    }
    fun go(mode: WorkspaceMode) { onDismiss(); nav.workspace(d.id, mode) }
    fun tool(t: Tool) { onDismiss(); nav.tool(t, d.id) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FaColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(d.thumbnailPath, 44.dp, 58.dp, d.modifiedAt)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(DocumentRepository.baseName(d.displayName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${d.pageCount} Seiten · ${Format.size(d.sizeBytes)}" + (vm.container.index.category(d.id)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                    Text(
                        if (vm.namingBusy == d.id) "KI benennt …" else "Mit KI benennen",
                        color = FaColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = vm.namingBusy == null) {
                            scope.launch { if (vm.aiAllowed()) nameIt() else askConsent = true }
                        }.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // Zwei große Hauptaktionen
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigAction(Icons.Outlined.MenuBook, "Öffnen", true, Modifier.weight(1f)) { go(WorkspaceMode.READ) }
                BigAction(Icons.Outlined.Gesture, "Unterschreiben", false, Modifier.weight(1f)) { go(WorkspaceMode.SIGN) }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FaColors.Card)) {
                MenuRow(Icons.Outlined.Edit, "Bearbeiten", "Text · Kommentieren · Ausfüllen", expanded == "edit") { expanded = if (expanded == "edit") null else "edit" }
                if (expanded == "edit") SubChips(listOf("Text" to { go(WorkspaceMode.EDIT) }, "Kommentieren" to { go(WorkspaceMode.ANNOTATE) }, "Ausfüllen" to { go(WorkspaceMode.FORM) }))
                HorizontalDivider(color = FaColors.Separator)
                MenuRow(Icons.Outlined.GridView, "Seiten", "Sortieren · Drehen · Herauslösen · Löschen", false) { tool(Tool.ORGANIZE) }
                if (remember(d.id) { de.fapdf.ui.tools.ScanStore.has(context, d.id) }) {
                    HorizontalDivider(color = FaColors.Separator)
                    MenuRow(Icons.Outlined.AutoFixHigh, "Scan nachbearbeiten", "Original · Verbessert · Kräftig · Schwarzweiß", false) { scanEdit = true }
                }
                HorizontalDivider(color = FaColors.Separator)
                MenuRow(Icons.Outlined.Share, "Teilen & Export", "Weitergeben · Drucken · Verschlüsseln", expanded == "share") { expanded = if (expanded == "share") null else "share" }
                if (expanded == "share") SubChips(listOf(
                    "Weitergeben" to { onDismiss(); FileActions.share(context, d.file(), d.displayName) },
                    "Drucken" to { onDismiss(); FileActions.print(context, d.file(), d.displayName) },
                    "Verschlüsseln" to { tool(Tool.ENCRYPT) },
                ))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FaColors.Card)) {
                MenuRow(Icons.Outlined.Delete, "Löschen", null, false, FaColors.Destructive) { confirmDelete = true }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (askConsent) ConfirmDialog(
        "Text an KI senden?", "Zum Benennen wird der Text dieses Dokuments an den gewählten KI-Dienst übertragen.", "Zustimmen – nicht mehr fragen",
        onConfirm = { askConsent = false; vm.consentGiven(); nameIt() }, onDismiss = { askConsent = false },
    )
    if (confirmDelete) ConfirmDialog(
        "Dokument löschen?", "„${d.displayName}“ wird endgültig gelöscht.", "Löschen", destructive = true,
        onConfirm = { confirmDelete = false; onDismiss(); vm.deleteCompletely(d.id) {} },
        onDismiss = { confirmDelete = false },
    )
}


@Composable
private fun BigAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(18.dp)).background(if (primary) FaColors.Accent else FaColors.Card)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = if (primary) FaColors.OnAccent else FaColors.Accent, modifier = Modifier.size(24.dp))
        Text(label, color = if (primary) FaColors.OnAccent else FaColors.Label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, open: Boolean,
    tint: Color = FaColors.Accent, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (tint == FaColors.Destructive) FaColors.DestructiveLight else FaColors.AccentLight), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (tint == FaColors.Destructive) tint else FaColors.Label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = FaColors.SecondaryLabel, fontSize = 12.sp)
        }
        if (subtitle != null && title != "Seiten") Text(if (open) "▴" else "▾", color = FaColors.SecondaryLabel)
    }
}

@Composable
private fun SubChips(items: List<Pair<String, () -> Unit>>) {
    Row(Modifier.fillMaxWidth().padding(start = 66.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, action) ->
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(FaColors.AccentLight).clickable(onClick = action)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) { Text(label, color = FaColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}


/** Werkzeug-Gruppe auf der Startseite: wenige große Kacheln statt vieler kleiner. */
private class ToolGroup(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tools: List<Pair<Tool, String>>)

private val toolGroups = listOf(
    ToolGroup("Bearbeiten", "Text · Kommentieren · Schwärzen · Stempel", Icons.Outlined.Edit, listOf(
        Tool.EDIT to "Antippen und tippen – Schrift wird erkannt",
        Tool.ANNOTATE to "Markieren, Notizen, Zeichnen",
        Tool.REDACT to "Stellen dauerhaft unkenntlich machen",
        Tool.PAGE_NUMBERS to "Unten oder oben einfügen",
        Tool.WATERMARK to "Text oder Bild über alle Seiten",
        Tool.CROP to "Ränder entfernen",
    )),
    ToolGroup("Ausfüllen & Unterschreiben", "Formulare · Unterschrift", Icons.Outlined.Gesture, listOf(
        Tool.FORM to "Felder ausfüllen, Profil einsetzen",
        Tool.SIGN to "Gespeicherte Unterschrift einsetzen",
    )),
    ToolGroup("Seiten", "Sortieren · Drehen · Aufteilen · Löschen", Icons.Outlined.GridView, listOf(
        Tool.ORGANIZE to "Große Vorschau, sortieren, drehen, löschen",
        Tool.SPLIT to "In mehrere PDFs aufteilen",
    )),
    ToolGroup("Zusammenfügen", "Mehrere PDFs zu einer", Icons.Outlined.Link, listOf(Tool.MERGE to "")),
    ToolGroup("KI-Assistent", "Erklären · Chat · Übersetzen · Fragen", Icons.Outlined.AutoAwesome, listOf(
        Tool.AI_SUMMARY to "Worum geht es, Fristen, Beträge",
        Tool.AI_CHAT to "Fragen zum Dokument stellen",
        Tool.TRANSLATE to "In andere Sprache übersetzen",
        Tool.AI_QUESTIONS to "Lernfragen aus dem Dokument",
    )),
    ToolGroup("Schutz", "Passwort · entsperren · reduzieren", Icons.Outlined.Lock, listOf(
        Tool.ENCRYPT to "Mit Passwort schützen",
        Tool.REMOVE_PASSWORD to "Passwort entfernen",
        Tool.FLATTEN to "Felder und Kommentare fest einbrennen",
    )),
)

@Composable
private fun GroupTile(g: ToolGroup, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 110.dp).clip(RoundedCornerShape(20.dp)).background(FaColors.Card).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Icon(g.icon, null, tint = FaColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Text(g.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = FaColors.Label)
        Text(g.subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = FaColors.SecondaryLabel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(g: ToolGroup, onDismiss: () -> Unit, onPick: (Tool) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = FaColors.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(g.icon, null, tint = FaColors.Accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(g.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FaColors.Card)) {
                g.tools.forEachIndexed { i, (t, desc) ->
                    if (i > 0) HorizontalDivider(color = FaColors.Separator)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onPick(t) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(FaColors.AccentLight), contentAlignment = Alignment.Center) {
                            val txt = t.iconText
                            if (txt != null) Text(txt, color = FaColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            else Icon(t.icon, null, tint = FaColors.Accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = FaColors.Label)
                            if (desc.isNotBlank()) Text(desc, fontSize = 12.sp, color = FaColors.SecondaryLabel)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}


/** Große Vorschau der ersten Seite für die Dokument-Karten. */
@Composable
private fun BigThumb(path: String?, key: Any?) {
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path, key) {
        value = path?.let { p ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { android.graphics.BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        val i = img
        if (i != null) androidx.compose.foundation.Image(i, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter)
        else Text("PDF", color = Color(0xFFB0B0B8), fontWeight = FontWeight.Bold)
    }
}


/** Fold aufgeklappt: rechts die Vorschau des gewählten Dokuments mit den wichtigsten Aktionen. */
@Composable
private fun DocPreviewPane(vm: AppViewModel, d: DocumentEntity, nav: HomeNav, modifier: Modifier) {
    val src = de.fapdf.ui.components.rememberDocSource(d, null)
    val index = vm.container.index
    Column(modifier.padding(end = 16.dp, bottom = 16.dp).clip(RoundedCornerShape(22.dp)).background(FaColors.Card)) {
        Column(Modifier.padding(16.dp)) {
            Text(DocumentRepository.baseName(d.displayName), fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(index.category(d.id), "${d.pageCount} Seiten", Format.date(d.modifiedAt)).joinToString(" · "), color = FaColors.SecondaryLabel, fontSize = 13.sp)
            index.deadline(d.id)?.let { dl ->
                Text("Frist: " + (dl.date?.let { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(it)) } ?: dl.text) +
                    (if (dl.amount.isNotBlank()) " · ${dl.amount}" else ""), color = Color(0xFFFFB3B3), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Öffnen" to { nav.workspace(d.id, WorkspaceMode.READ) },
                    "Unterschreiben" to { nav.workspace(d.id, WorkspaceMode.SIGN) },
                    "Mehr" to { vm.actionDocId = d.id },
                ).forEachIndexed { i, (t, a) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (i == 0) FaColors.Accent else FaColors.Background).clickable(onClick = a),
                        contentAlignment = Alignment.Center,
                    ) { Text(t, color = if (i == 0) FaColors.OnAccent else FaColors.Label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
                }
            }
        }
        if (src != null) androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(src.pageCount) { p -> de.fapdf.ui.components.LargePagePreview(src, p, maxHeight = 900.dp) }
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Vorschau wird geladen …", color = FaColors.SecondaryLabel) }
    }
}
