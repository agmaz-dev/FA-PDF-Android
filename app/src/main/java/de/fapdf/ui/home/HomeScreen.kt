package de.fapdf.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun HomeScreen(vm: AppViewModel, nav: HomeNav) {
    val pager = rememberPagerState(initialPage = 1) { 2 }
    val scope = rememberCoroutineScope()
    val docs by vm.documents.collectAsState()
    var chooserFor by remember { mutableStateOf<Tool?>(null) }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri, persist = true)
    }

    Column(Modifier.fillMaxSize().background(FaColors.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("FA PDF", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { opener.launch(arrayOf("application/pdf")) }) { Icon(Icons.Outlined.FolderOpen, "PDF öffnen", tint = FaColors.Accent) }
            IconButton(onClick = nav::profiles) { Icon(Icons.Outlined.People, "Profile", tint = FaColors.Accent) }
            IconButton(onClick = nav::signatures) { Icon(Icons.Outlined.Draw, "Unterschriften", tint = FaColors.Accent) }
            IconButton(onClick = nav::settings) { Icon(Icons.Outlined.Settings, "Einstellungen", tint = FaColors.Accent) }
        }
        // Segment-Umschalter wie in iOS; Wischen nach rechts öffnet „Letzte Dokumente“
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(Color(0xFFE3E3E8)).padding(2.dp)
        ) {
            listOf("Letzte Dokumente", "Werkzeuge").forEachIndexed { i, t ->
                val selected = pager.currentPage == i
                val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "seg")
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(bg)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp) }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            if (page == 0) RecentDocuments(vm, docs) else ToolsGrid { tool ->
                when {
                    !tool.needsDocument -> nav.tool(tool, null)
                    else -> chooserFor = tool
                }
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
private fun ToolsGrid(onTool: (Tool) -> Unit) {
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp).navigationBarsPadding()) {
        Column(Modifier.weight(1f)) {
            listOf(ToolCategory.ORGANIZE, ToolCategory.EDIT).forEach { CategoryBlock(it, onTool) }
        }
        Column(Modifier.weight(1f)) {
            listOf(ToolCategory.SIGN, ToolCategory.ENCRYPT, ToolCategory.AI, ToolCategory.SCAN).forEach { CategoryBlock(it, onTool) }
        }
    }
}

@Composable
private fun CategoryBlock(cat: ToolCategory, onTool: (Tool) -> Unit) {
    Text(cat.title, color = FaColors.SecondaryLabel, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 6.dp))
    Tool.byCategory(cat).forEach { tool ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).clickable { onTool(tool) }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIconBox(tool.icon, tool.iconColor, tool.iconTint, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(tool.title, fontSize = 15.sp, color = FaColors.Label, lineHeight = 18.sp)
                if (tool.limitedNote != null) Text(tool.limitedNote, fontSize = 10.sp, color = FaColors.SecondaryLabel, lineHeight = 12.sp)
            }
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
