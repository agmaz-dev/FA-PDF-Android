package de.fapdf.ui.home

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
            NavigationBar(containerColor = FaColors.Surface, tonalElevation = 0.dp, modifier = Modifier.shadow(8.dp)) {
                listOf(
                    Triple("Dokumente", Icons.Outlined.Description, Icons.Filled.Description),
                    Triple("Werkzeuge", Icons.Outlined.GridView, Icons.Filled.GridView),
                    Triple("Einstellungen", Icons.Outlined.Settings, Icons.Filled.Settings),
                ).forEachIndexed { i, (label, icon, iconSel) ->
                    val sel = pager.currentPage == i
                    NavigationBarItem(
                        selected = sel, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        icon = { Icon(if (sel) iconSel else icon, label) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FaColors.Accent, selectedTextColor = FaColors.Accent,
                            indicatorColor = Color.Transparent, unselectedIconColor = FaColors.SecondaryLabel,
                            unselectedTextColor = FaColors.SecondaryLabel,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.padding(padding).fillMaxSize(), beyondViewportPageCount = 1) { page ->
            when (page) {
                0 -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Text("Dokumente", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
                    RecentDocuments(vm, docs)
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
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Auf Tablets bleibt der Inhalt lesbar breit und mittig
        val contentWidth = if (maxWidth > 900.dp) 900.dp else maxWidth
        val cols = ((contentWidth - 32.dp) / 118.dp).toInt().coerceIn(3, 6)
        Column(
            Modifier.width(contentWidth).fillMaxHeight().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, null, tint = FaColors.Accent, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(8.dp))
                Text("FA PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(FaColors.AccentLight).clickable(onClick = onProfile),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Person, "Profile", tint = FaColors.Accent, modifier = Modifier.size(26.dp)) }
            }
            Text("Deine PDF-Werkzeuge", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
            Text("Alles für deine Dokumente", style = MaterialTheme.typography.bodyLarge, color = FaColors.SecondaryLabel)
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Werkzeug suchen") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = FaColors.Card, focusedContainerColor = FaColors.Surface,
                    unfocusedBorderColor = FaColors.CardBorder,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                val hits = Tool.entries.filter { it.title.contains(q, ignoreCase = true) || it.category.title.contains(q, ignoreCase = true) }
                Spacer(Modifier.height(18.dp))
                if (hits.isEmpty()) Text("Kein Werkzeug gefunden.", color = FaColors.SecondaryLabel)
                TileGrid(hits, cols, onTool)
            } else {
                // Scannen ist bereits oben als eigene Schaltfläche erreichbar – kein doppelter Scanner-Bereich
                ToolCategory.entries.filter { it != ToolCategory.SCAN }.forEach { cat ->
                    Row(Modifier.padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(cat.headerIcon, null, tint = cat.color, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(cat.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    val tools = Tool.byCategory(cat)
                    when (cat) {
                        ToolCategory.SIGN -> TileGrid(tools, ((cols + 1) / 2).coerceAtLeast(2), onTool)
                        else -> TileGrid(tools, cols, onTool)
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
    val shape = RoundedCornerShape(12.dp)
    val box = modifier.heightIn(min = if (horizontal) 64.dp else 98.dp).clip(shape).background(tool.tileColor).clickable(onClick = onClick)
    val icon: @Composable () -> Unit = {
        val t = tool.iconText
        if (t != null) Text(t, color = tool.iconColor, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        else Icon(tool.icon, null, tint = tool.iconColor, modifier = Modifier.size(30.dp))
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
private fun RecentDocuments(vm: AppViewModel, docs: List<DocumentEntity>) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortMode.OPENED) }
    var favOnly by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var renameDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    val list = docs.filter { (!favOnly || it.favorite) && (query.isBlank() || it.displayName.contains(query, ignoreCase = true)) }
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
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Dokumente suchen") },
                shape = RoundedCornerShape(12.dp),
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text("Nur Favoriten") },
                leadingIcon = { Icon(if (favOnly) Icons.Filled.Star else Icons.Outlined.StarOutline, null) })
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (docs.isEmpty()) "Noch keine Dokumente.\nÖffnen Sie eine PDF über das Ordnersymbol oder über „Öffnen mit“ in einer anderen App."
                    else "Keine Treffer.",
                    color = FaColors.SecondaryLabel, style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            items(list, key = { it.id }) { d ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { vm.actionDocId = d.id }, onLongClick = { menu = true })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumbnail(d.thumbnailPath, key = d.modifiedAt)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Bearbeitet ${Format.date(d.modifiedAt)} · Importiert ${Format.dateShort(d.importedAt)}",
                            style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
                        )
                        Text(
                            (if (d.encrypted) "🔒 " else "") + "${if (d.pageCount > 0) "${d.pageCount} Seiten" else "geschützt"} · ${Format.size(d.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
                        )
                    }
                    IconButton(onClick = { vm.setFavorite(d.id, !d.favorite) }) {
                        Icon(if (d.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline, "Favorit", tint = if (d.favorite) Color(0xFFFFB300) else FaColors.SecondaryLabel)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mehr") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Erneut öffnen") }, onClick = { menu = false; vm.actionDocId = d.id })
                            DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; renameDoc = d })
                            DropdownMenuItem(text = { Text("Aus Verlauf entfernen") }, leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, null) }, onClick = { menu = false; vm.removeFromHistory(d.id) })
                            DropdownMenuItem(text = { Text("Löschen", color = FaColors.Destructive) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = FaColors.Destructive) }, onClick = { menu = false; deleteDoc = d })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 74.dp), color = FaColors.Separator)
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

private data class DocAction(val title: String, val tool: Tool?, val mode: WorkspaceMode?, val special: String? = null)

private val docActions = listOf(
    DocAction("PDF öffnen / Reader", Tool.READER, WorkspaceMode.READ),
    DocAction("PDF bearbeiten", Tool.EDIT, WorkspaceMode.EDIT),
    DocAction("PDF kommentieren", Tool.ANNOTATE, WorkspaceMode.ANNOTATE),
    DocAction("PDF ausfüllen", Tool.FORM, WorkspaceMode.FORM),
    DocAction("PDF unterschreiben", Tool.SIGN, WorkspaceMode.SIGN),
    DocAction("PDF teilen (Seiten aufteilen)", Tool.SPLIT, null),
    DocAction("PDF drehen", Tool.ROTATE, null),
    DocAction("PDF organisieren", Tool.ORGANIZE, null),
    DocAction("PDF verschlüsseln", Tool.ENCRYPT, null),
    DocAction("PDF mit KI analysieren", Tool.AI_SUMMARY, null),
    DocAction("PDF drucken", null, null, "print"),
    DocAction("PDF weitergeben", Tool.SHARE, null, "share"),
    DocAction("PDF löschen", null, null, "delete"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(vm: AppViewModel, d: DocumentEntity, nav: HomeNav, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(d.thumbnailPath, 40.dp, 52.dp, d.modifiedAt)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Was möchten Sie mit diesem Dokument machen?", style = MaterialTheme.typography.titleMedium)
                    Text(d.displayName, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            docActions.forEach { a ->
                val icon = a.tool
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        when {
                            a.special == "print" -> { onDismiss(); FileActions.print(context, d.file(), d.displayName) }
                            a.special == "share" -> { onDismiss(); FileActions.share(context, d.file(), d.displayName) }
                            a.special == "delete" -> confirmDelete = true
                            a.mode != null -> { onDismiss(); nav.workspace(d.id, a.mode) }
                            a.tool != null -> { onDismiss(); nav.tool(a.tool, d.id) }
                        }
                    }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) ToolIconBox(icon.icon, icon.iconColor, icon.iconTint, size = 32.dp)
                    else ToolIconBox(
                        if (a.special == "delete") Icons.Outlined.Delete else Icons.Outlined.Print,
                        if (a.special == "delete") FaColors.Destructive else Color(0xFF8E8E93), size = 32.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(a.title, color = if (a.special == "delete") FaColors.Destructive else FaColors.Label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Dokument löschen?", "„${d.displayName}“ wird endgültig gelöscht.", "Löschen", destructive = true,
        onConfirm = { confirmDelete = false; onDismiss(); vm.deleteCompletely(d.id) {} },
        onDismiss = { confirmDelete = false },
    )
}
