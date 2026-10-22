package de.fapdf.ui.workspace

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import de.fapdf.ui.components.DocCard
import kotlin.math.roundToInt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fapdf.data.DocumentRepository
import de.fapdf.pdf.Box
import de.fapdf.pdf.FieldKind
import de.fapdf.ui.WorkspaceMode
import de.fapdf.ui.components.BusyDialog
import de.fapdf.ui.components.ConfirmDialog
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.InfoBanner
import de.fapdf.ui.components.MessageDialog
import de.fapdf.ui.components.PageGestures
import de.fapdf.ui.components.PageThumb
import de.fapdf.ui.components.PasswordDialog
import de.fapdf.ui.components.PdfPagesView
import de.fapdf.ui.components.PlacementLayer
import de.fapdf.ui.components.rememberExportLauncher
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.launch

private fun modeTitle(m: WorkspaceMode) = when (m) {
    WorkspaceMode.READ -> "PDF lesen"
    WorkspaceMode.EDIT -> "PDF bearbeiten"
    WorkspaceMode.ANNOTATE -> "Kommentieren"
    WorkspaceMode.FORM -> "Formular ausfüllen"
    WorkspaceMode.SIGN -> "Unterschreiben"
    WorkspaceMode.REDACT -> "Schwärzen"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    docId: Long,
    initialMode: WorkspaceMode,
    onBack: () -> Unit,
    onNewSignature: () -> Unit,
    onManageProfiles: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: WorkspaceViewModel = viewModel(key = "ws_$docId", factory = WorkspaceViewModel.Factory(app, docId, initialMode))
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var showDiscard by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) } // "ocr", "font", "more", "sign", "bookmarks", "profiles"
    var searchOpen by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var fullEditor by remember { mutableStateOf(false) }
    var newTextAlign by remember { mutableStateOf(de.fapdf.pdf.Alignment.LEFT) }
    // Unterschreiben: Unterschrift-Fenster wie in der Vorlage direkt anbieten
    LaunchedEffect(vm.mode, vm.source != null) {
        if (vm.mode == WorkspaceMode.SIGN && vm.source != null && vm.formInfo?.signatureFields.isNullOrEmpty() && vm.placement == null) sheet = "sign"
    }
    var searchText by remember { mutableStateOf("") }
    var signatureTarget by remember { mutableStateOf<Pair<Int, Box>?>(null) }
    val exporter = rememberExportLauncher()
    val currentPage = listState.firstVisibleItemIndex

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = decodeBitmap(context, uri)
            if (bmp != null) vm.startPlacement(currentPage, bmp, signature = false)
            else FileActions.toast(context, "Bild konnte nicht geladen werden")
        }
    }

    fun leave() { if (vm.dirty) showDiscard = true else onBack() }
    BackHandler { if (fullscreen) fullscreen = false else leave() }

    val doc = vm.doc
    val source = vm.source

    // Seiten im sichtbaren Bereich im Hintergrund analysieren (für Auswahl und Formatierung)
    LaunchedEffect(currentPage, vm.versionKey, vm.mode) {
        if (vm.mode == WorkspaceMode.EDIT || vm.mode == WorkspaceMode.ANNOTATE) vm.ensureAnalyzed(currentPage)
    }

    Scaffold(
        containerColor = FaColors.Background,
        topBar = {
            if (!fullscreen) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FaColors.Background),
                navigationIcon = {
                    IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") }
                },
                title = {
                    Text(
                        if (vm.mode == WorkspaceMode.READ) DocumentRepository.baseName(doc?.displayName ?: "") else modeTitle(vm.mode),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    if (vm.mode == WorkspaceMode.READ) {
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) vm.clearSearch() }) {
                            Icon(Icons.Outlined.Search, "Suchen", tint = if (searchOpen) FaColors.Accent else FaColors.Label)
                        }
                        IconButton(onClick = { sheet = "bookmarks" }) { Icon(Icons.Outlined.BookmarkBorder, "Lesezeichen") }
                        IconButton(onClick = { sheet = "more" }) { Icon(Icons.Outlined.MoreVert, "Weitere") }
                    } else if (vm.mode == WorkspaceMode.EDIT) {
                        IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Rückgängig") }
                        IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Wiederherstellen") }
                        Button(
                            onClick = { showSave = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp).height(38.dp),
                        ) { Text("Speichern", style = MaterialTheme.typography.labelLarge) }
                    } else {
                        Box {
                            IconButton(onClick = { overflow = true }) { Icon(Icons.Outlined.MoreVert, "Weitere") }
                            DropdownMenu(overflow, { overflow = false }) {
                                DropdownMenuItem(text = { Text("Rückgängig") }, enabled = vm.canUndo, onClick = { overflow = false; vm.undo() },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, null) })
                                DropdownMenuItem(text = { Text("Wiederherstellen") }, enabled = vm.canRedo, onClick = { overflow = false; vm.redo() },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Redo, null) })
                                DropdownMenuItem(text = { Text("Speichern") }, onClick = {
                                    overflow = false
                                    if (vm.mode == WorkspaceMode.FORM && vm.pendingValues.isNotEmpty()) vm.applyPendingValues { showSave = true } else showSave = true
                                }, leadingIcon = { Icon(Icons.Outlined.Save, null) })
                                DropdownMenuItem(text = { Text("Drucken, Teilen, Export …") }, onClick = { overflow = false; sheet = "more" },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!fullscreen) Surface(color = FaColors.Surface, shadowElevation = 8.dp) {
                // Leiste wandert mit der Tastatur nach oben – das Eingabefeld bleibt sichtbar
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                    if (vm.mode == WorkspaceMode.READ && source != null && source.pageCount > 0 && vm.placement == null) {
                        ThumbStrip(vm, source, currentPage) { p -> scope.launch { listState.animateScrollToItem(p) } }
                        ZoomRow(zoom, onZoom = { zoom = it }, onFullscreen = { fullscreen = true }, onGrid = { sheet = "grid" })
                    }
                    if (vm.mode == WorkspaceMode.ANNOTATE && commentsOpen && vm.placement == null) {
                        CommentsPanel(vm, onGo = { p -> scope.launch { listState.animateScrollToItem(p) } }, onClose = { commentsOpen = false })
                    }
                    if (vm.mode == WorkspaceMode.FORM && vm.placement == null) {
                        FormNavigator(vm, onGo = { p -> scope.launch { listState.animateScrollToItem(p) } })
                    }
                    if (vm.mode == WorkspaceMode.EDIT && vm.placement == null && vm.moveState == null) {
                        val sel = vm.selection
                        if (sel != null) InlineTextEditor(vm, sel, onMore = { fullEditor = true })
                        else FormatBar(vm.newTextStyle, newTextAlign, null, null, onStyle = { vm.newTextStyle = it.copy(useOriginalFont = false) }, onAlign = { newTextAlign = it })
                    }
                    BottomTools(vm, currentPage, commentsOpen = commentsOpen, onToggleComments = { commentsOpen = !commentsOpen }, onSheet = { sheet = it }, onPickImage = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, onDraw = {
                        vm.switchMode(WorkspaceMode.ANNOTATE); vm.annotTool = AnnotTool.INK
                    }, onSave = { showSave = true })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searchOpen && vm.mode == WorkspaceMode.READ && !fullscreen) {
                SearchRow(searchText, { searchText = it }, onSearch = { vm.search(searchText) }, hits = vm.searchHits.size, index = vm.searchIndex,
                    onPrev = {
                        if (vm.searchHits.isNotEmpty()) {
                            vm.searchIndex = (vm.searchIndex - 1 + vm.searchHits.size) % vm.searchHits.size
                            scope.launch { listState.animateScrollToItem(vm.searchHits[vm.searchIndex].page) }
                        }
                    },
                    onNext = {
                        if (vm.searchHits.isNotEmpty()) {
                            vm.searchIndex = (vm.searchIndex + 1) % vm.searchHits.size
                            scope.launch { listState.animateScrollToItem(vm.searchHits[vm.searchIndex].page) }
                        }
                    })
            }
            if (vm.mode != WorkspaceMode.READ && doc != null) {
                DocCard(doc, subtitle = "Seite ${currentPage + 1} von ${source?.pageCount ?: doc.pageCount}" + if (vm.dirty) " · nicht gespeichert" else "")
            }
            if (!fullscreen) ModeBanners(vm, onSignatureField = { f ->
                val w = f.widgets.first()
                signatureTarget = w.pageIndex to w.viewBox
                scope.launch { listState.animateScrollToItem(w.pageIndex) }
                sheet = "sign"
            })
            Box(Modifier.weight(1f).fillMaxWidth().background(FaColors.PageArea)) {
                if (source != null) {
                    val gestures = gesturesFor(vm)
                    PdfPagesView(
                        source = source, versionKey = vm.versionKey, cache = vm.bitmapCache, listState = listState,
                        zoom = zoom, onZoom = { zoom = it },
                        scrollEnabled = !gestures.dragEnabled && vm.placement == null,
                        gestures = gestures,
                    ) { page, pxPerPt -> PageOverlay(vm, page, pxPerPt) }
                    Text(
                        "${currentPage + 1} / ${source.pageCount}",
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    if (fullscreen) IconButton(
                        onClick = { fullscreen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(CircleShape).background(Color(0x99000000)),
                    ) { Icon(Icons.Outlined.FullscreenExit, "Vollbild beenden", tint = Color.White) }
                }
            }
        }
    }

    // ---------- Dialoge ----------
    vm.busy?.let { BusyDialog(it) }
    vm.message?.let { (t, m) -> MessageDialog(t, m) { vm.message = null } }
    vm.loadError?.let { MessageDialog("Fehler beim Öffnen", it) { onBack() } }
    if (vm.needsPassword) PasswordDialog(
        "Passwortgeschützte PDF", "Bitte geben Sie das Passwort ein, um das Dokument zu öffnen.",
        onConfirm = { vm.open(it) }, onDismiss = onBack,
    )
    if (showDiscard) ConfirmDialog(
        "Ungespeicherte Änderungen", "Möchten Sie die Änderungen verwerfen? Über „Speichern“ in der oberen Leiste können Sie sie vorher sichern.",
        "Verwerfen", destructive = true, onConfirm = { showDiscard = false; onBack() }, onDismiss = { showDiscard = false },
    )
    if (showSave) SaveSheet(
        hasSource = doc?.sourceUri != null,
        onSave = { original -> showSave = false; vm.save(original) },
        onExport = { showSave = false; vm.current?.let { exporter(it, doc?.displayName ?: "Dokument.pdf") } },
        onPrint = { showSave = false; vm.current?.let { FileActions.print(context, it, doc?.displayName ?: "Dokument.pdf") } },
        onShare = { showSave = false; vm.current?.let { FileActions.share(context, it, doc?.displayName ?: "Dokument.pdf") } },
        onDismiss = { showSave = false },
    )
    if (vm.formPrompt) ConfirmDialog(
        "Formular erkannt", "Dieses Dokument enthält ausfüllbare Felder. Möchten Sie das Formular ausfüllen?", "Ausfüllen",
        onConfirm = { vm.formPrompt = false; vm.switchMode(WorkspaceMode.FORM) }, onDismiss = { vm.formPrompt = false },
    )
    if (vm.profilePrompt) ConfirmDialog(
        "Profildaten verfügbar", "Gespeicherte Profildaten gefunden. Möchten Sie diese automatisch einfügen? Sie können alle Werte vor dem Übernehmen prüfen.",
        "Profil wählen", onConfirm = { vm.profilePrompt = false; sheet = "profiles" }, onDismiss = { vm.profilePrompt = false },
    )
    vm.scanPromptPage?.let { p ->
        ConfirmDialog(
            "Gescannte Seite", "Diese Seite besteht aus einem Bild. Soll die Texterkennung (OCR) gestartet werden, damit Sie den Text bearbeiten können?",
            "Texterkennung starten", onConfirm = { vm.runOcr(p) }, onDismiss = { vm.scanPromptPage = null },
        )
    }
    vm.selection?.let { sel -> if (fullEditor) EditTextSheet(vm, sel) { fullEditor = false } }
    LaunchedEffect(vm.selection) { if (vm.selection == null) fullEditor = false }
    vm.objectMenu?.let { obj ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.objectMenu = null },
            title = { Text(if (obj.signature) "Unterschrift" else "Bild") },
            text = { Text("Sie können das eingefügte Element verschieben, in der Größe ändern, drehen oder entfernen.") },
            confirmButton = { TextButton(onClick = { vm.editObject(obj) }) { Text("Bearbeiten") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deleteObject(obj) }) { Text("Entfernen", color = FaColors.Destructive) }
                    TextButton(onClick = { vm.objectMenu = null }) { Text("Abbrechen") }
                }
            },
        )
    }
    // Neuer Text wird direkt auf der Seite eingegeben (kein Dialog)
    LaunchedEffect(vm.addTextAt) { vm.addTextAt?.let { vm.inlineNewText = it; vm.addTextAt = null } }
    vm.noteAt?.let { (p, u, v) -> NoteDialog(onDismiss = { vm.noteAt = null }) { vm.addNote(p, u, v, it) } }
    vm.editingField?.let { f -> FieldDialog(f, vm.displayValue(f), onDismiss = { vm.editingField = null }) { value -> vm.setPending(f.id, value); vm.editingField = null } }
    vm.autofillPreview?.let { (profile, list) -> AutofillPreviewDialog(profile.label, list, onDismiss = { vm.autofillPreview = null }) { vm.acceptAutofill(it) } }
    vm.labelSuggestions?.let { list -> LabelSuggestionDialog(list, onDismiss = { vm.labelSuggestions = null }) { vm.applyLabelSuggestions(it) } }
    vm.signatureFieldPrompt?.let { f ->
        ConfirmDialog(
            "Unterschriftsfeld erkannt", "Auf Seite ${f.widgets.first().pageIndex + 1} befindet sich ein Unterschriftsfeld („${f.label}“). Möchten Sie eine gespeicherte Unterschrift einfügen? Sie wird erst nach Ihrer Bestätigung eingesetzt.",
            "Unterschrift wählen", onConfirm = {
                vm.signatureFieldPrompt = null
                val w = f.widgets.first()
                signatureTarget = w.pageIndex to w.viewBox
                scope.launch { listState.animateScrollToItem(w.pageIndex) }
                sheet = "sign"
            }, onDismiss = { vm.signatureFieldPrompt = null },
        )
    }

    when (sheet) {
        "ocr" -> OcrSheet(onDismiss = { sheet = null }, onPage = { sheet = null; vm.runOcr(currentPage) }, onDocument = { sheet = null; vm.makeSearchable() })
        "editmore" -> EditMoreSheet(
            highlightActive = vm.editTool == EditTool.HIGHLIGHT,
            onDismiss = { sheet = null },
            onOcr = { sheet = "ocr" },
            onFont = { sheet = "font" },
            onHighlight = { sheet = null; vm.editTool = if (vm.editTool == EditTool.HIGHLIGHT) EditTool.TEXT else EditTool.HIGHLIGHT },
            onSign = { sheet = "sign" },
            onMore = { sheet = "more" },
        )
        "font" -> StyleDialog(vm.newTextStyle, onDismiss = { sheet = null }) { vm.newTextStyle = it; sheet = null }
        "more" -> MoreSheet(vm, currentPage, onDismiss = { sheet = null }, onPrint = {
            vm.current?.let { FileActions.print(context, it, doc?.displayName ?: "Dokument.pdf") }
        }, onShare = { vm.current?.let { FileActions.share(context, it, doc?.displayName ?: "Dokument.pdf") } }, onExport = {
            vm.current?.let { exporter(it, doc?.displayName ?: "Dokument.pdf") }
        })
        "sign" -> SignatureSheet(vm, onDismiss = { sheet = null; signatureTarget = null }, onNew = { sheet = null; onNewSignature() }) { bmp ->
            sheet = null
            val target = signatureTarget
            signatureTarget = null
            vm.startPlacement(target?.first ?: currentPage, bmp, signature = true, target = target?.second)
        }
        "bookmarks" -> BookmarksSheet(vm, currentPage, onDismiss = { sheet = null }) { p -> sheet = null; scope.launch { listState.animateScrollToItem(p) } }
        "profiles" -> ProfilePickerDialog(vm, onDismiss = { sheet = null }, onManage = { sheet = null; onManageProfiles() }) { profile ->
            sheet = null
            if (vm.formInfo?.hasFields == true) vm.prepareAutofill(profile) else vm.detectFlatFormLabels(profile)
        }
        "jump" -> PageJumpDialog(source?.pageCount ?: 1, onDismiss = { sheet = null }) { p -> sheet = null; scope.launch { listState.scrollToItem(p) } }
        "redact" -> RedactOptionsDialog(vm.redactRects.size, onDismiss = { sheet = null }) { raster, meta -> sheet = null; vm.applyRedaction(raster, meta) { showSave = true } }
        "grid" -> source?.let { src -> PageGridSheet(src, currentPage, onDismiss = { sheet = null }) { p -> sheet = null; scope.launch { listState.scrollToItem(p) } } }
        "redactsearch" -> TextInputDialog("Begriff schwärzen", "Alle Fundstellen des Begriffs im Dokument werden zum Schwärzen markiert.", "Suchbegriff", onDismiss = { sheet = null }) { sheet = null; vm.redactSearch(it) }
    }
}

private fun gesturesFor(vm: WorkspaceViewModel): PageGestures = when (vm.mode) {
    WorkspaceMode.READ -> PageGestures()
    WorkspaceMode.EDIT -> when {
        vm.placement != null -> PageGestures()
        vm.moveState != null -> PageGestures(dragEnabled = true, onDragStart = vm::onDragStart, onDrag = vm::onDrag, onDragEnd = vm::onDragEnd)
        vm.editTool == EditTool.HIGHLIGHT -> PageGestures(dragEnabled = true, onDragStart = vm::onDragStart, onDrag = vm::onDrag, onDragEnd = vm::onDragEnd)
        else -> PageGestures(onTap = vm::onEditTap)
    }
    WorkspaceMode.ANNOTATE -> when (vm.annotTool) {
        AnnotTool.SCROLL -> PageGestures()
        AnnotTool.NOTE -> PageGestures(onTap = { p, u, v -> vm.noteAt = Triple(p, u, v) })
        AnnotTool.ERASER -> PageGestures(onTap = vm::eraseAt)
        else -> PageGestures(dragEnabled = true, onDragStart = vm::onDragStart, onDrag = vm::onDrag, onDragEnd = vm::onDragEnd)
    }
    WorkspaceMode.REDACT -> if (vm.redactScroll) PageGestures()
    else PageGestures(dragEnabled = true, onDragStart = vm::onDragStart, onDrag = vm::onDrag, onDragEnd = vm::onDragEnd)
    WorkspaceMode.FORM -> PageGestures(onTap = { p, u, v -> vm.tapObjectOr(p, u, v) {
        var widget: de.fapdf.pdf.WidgetInfo? = null
        val f = vm.formInfo?.fields?.firstOrNull { fi ->
            fi.widgets.firstOrNull { it.pageIndex == p && it.viewBox.inset(2f).contains(u, v) }?.also { widget = it } != null
        }
        when {
            f != null -> vm.activateField(f, widget)
            vm.inlineFieldId != null || vm.inlineNewText != null -> { vm.inlineFieldId = null; vm.inlineNewText = null }
            else -> vm.inlineNewText = Triple(p, u, v)
        }
    } })
    WorkspaceMode.SIGN -> PageGestures(onTap = { p, u, v -> vm.tapObjectOr(p, u, v) {} })
}

@Composable
private fun PageOverlay(vm: WorkspaceViewModel, page: Int, s: Float) {
    val sel = vm.selection
    val move = vm.moveState
    val drag = vm.dragRect
    val ocr = vm.ocrPages[page]
    val accent = FaColors.Accent
    Canvas(Modifier.fillMaxSize()) {
        fun rect(b: Box, color: Color, fill: Boolean = false, width: Float = 2.dp.toPx()) {
            val tl = Offset(b.left * s, b.top * s)
            val sz = Size(b.width * s, b.height * s)
            if (fill) drawRect(color, tl, sz) else drawRect(color, tl, sz, style = Stroke(width))
        }
        // OCR-Zeilen dezent anzeigen
        if (vm.mode == WorkspaceMode.EDIT) ocr?.lines?.forEach { rect(it.viewBox, accent.copy(alpha = 0.35f), width = 1.dp.toPx()) }
        if (sel != null && sel.page == page) { rect(sel.viewBox.inset(1.5f), accent.copy(alpha = 0.15f), fill = true); rect(sel.viewBox.inset(1.5f), accent) }
        if (move != null && move.first.page == page) {
            val (du, dv) = move.second
            val b = move.first.viewBox
            rect(Box(b.left + du, b.top + dv, b.right + du, b.bottom + dv), accent.copy(alpha = 0.25f), fill = true)
            rect(b, accent.copy(alpha = 0.5f), width = 1.dp.toPx())
        }
        if (drag != null && drag.page == page) {
            val c = if (vm.mode == WorkspaceMode.REDACT) Color.Black.copy(alpha = 0.5f) else Color(0x66FFD60A)
            rect(drag.box, c, fill = true)
        }
        if (vm.mode == WorkspaceMode.REDACT) vm.redactRects.filter { it.page == page }.forEach { rect(it.box, Color.Black.copy(alpha = 0.75f), fill = true) }
        if (vm.mode == WorkspaceMode.ANNOTATE) {
            val strokes = vm.inkStrokes.filter { it.first == page }.map { it.second } + listOfNotNull(vm.currentStroke?.takeIf { it.first == page }?.second)
            strokes.forEach { pts -> drawStroke(pts, s, Color(vm.annotColor)) }
        }
        if (vm.mode == WorkspaceMode.READ) vm.searchHits.forEachIndexed { i, h ->
            if (h.page == page) rect(h.viewBox.inset(1f), if (i == vm.searchIndex) Color(0x88FF9500) else Color(0x55FFD60A), fill = true)
        }
        if (vm.mode == WorkspaceMode.FORM || vm.mode == WorkspaceMode.SIGN) vm.formInfo?.fields?.forEach { f ->
            f.widgets.filter { it.pageIndex == page }.forEach { w ->
                val isSig = f.kind == FieldKind.SIGNATURE
                if (vm.mode == WorkspaceMode.SIGN && !isSig) return@forEach
                rect(w.viewBox, (if (isSig) Color(0xFFE364E8) else accent).copy(alpha = 0.10f), fill = true)
                rect(w.viewBox, (if (isSig) Color(0xFFE364E8) else accent).copy(alpha = 0.55f), width = 1.dp.toPx())
                if (f.id == vm.focusedFieldId) rect(w.viewBox.inset(2f), accent, width = 2.5f.dp.toPx())
                if (f.id == vm.inlineFieldId) return@forEach // wird gerade direkt bearbeitet
                val text = when {
                    vm.pendingValues[f.id] == null -> null
                    f.kind == FieldKind.RADIO -> if (vm.currentChoice(f) == w.onValue) "●" else null
                    f.kind == FieldKind.CHECKBOX -> if (vm.currentChecked(f)) "✓" else null
                    else -> vm.displayValue(f)
                }
                if (!text.isNullOrEmpty()) drawFieldText(text, w.viewBox, s)
            }
        }
    }
    vm.placement?.takeIf { it.page == page }?.let { PlacementLayer(it, s) }
    // Direkte Eingabe auf der Seite
    if (vm.mode == WorkspaceMode.FORM) {
        val f = vm.formInfo?.fields?.firstOrNull { it.id == vm.inlineFieldId }
        val w = f?.widgets?.firstOrNull { it.pageIndex == page }
        if (f != null && w != null) InlineFieldEditor(vm, f, w, s)
    }
    vm.inlineNewText?.takeIf { it.first == page }?.let { (_, u, v) -> InlineNewTextEditor(vm, page, u, v, s) }
}

private fun DrawScope.drawStroke(pts: List<Pair<Float, Float>>, s: Float, color: Color) {
    if (pts.size < 2) return
    val path = Path()
    path.moveTo(pts[0].first * s, pts[0].second * s)
    for (i in 1 until pts.size) path.lineTo(pts[i].first * s, pts[i].second * s)
    drawPath(path, color, style = Stroke(width = 2f * s, cap = StrokeCap.Round))
}

private fun DrawScope.drawFieldText(text: String, b: Box, s: Float) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(10, 60, 160)
            textSize = (b.height * s * 0.6f).coerceIn(8f, 48f)
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawText(text, b.left * s + 4f, b.bottom * s - b.height * s * 0.25f, paint)
    }
}

@Composable
private fun ModeBanners(vm: WorkspaceViewModel, onSignatureField: (de.fapdf.pdf.FormFieldInfo) -> Unit) {
    when (vm.mode) {
        WorkspaceMode.EDIT -> when {
            vm.placement != null -> InfoBanner("Mit dem Finger verschieben, mit zwei Fingern drehen. Größe unten mit dem Regler, „−“/„+“ oder Klein/Mittel/Groß ändern, dann „Übernehmen“.")
            vm.moveState != null -> InfoBanner("Ziehen Sie den Text an die neue Position.")
            vm.editTool == EditTool.HIGHLIGHT -> InfoBanner("Ziehen Sie über den Text, den Sie markieren möchten.")
            else -> InfoBanner("Tippen Sie auf einen Text, um ihn zu ändern, oder auf eine freie Stelle, um Text hinzuzufügen.")
        }
        WorkspaceMode.FORM -> {
            val fi = vm.formInfo
            if (fi?.hasFields == true) InfoBanner("${fi.fields.size} Felder erkannt. Tippen Sie auf ein Feld, um es auszufüllen.")
            else InfoBanner("Keine interaktiven Felder. Tippen Sie auf eine Stelle, um Text einzufügen, oder lassen Sie Beschriftungen erkennen.")
        }
        WorkspaceMode.SIGN -> {
            val sig = vm.formInfo?.signatureFields?.firstOrNull()
            if (vm.placement != null) InfoBanner("Unterschrift verschieben und unten in der Größe anpassen, dann „Übernehmen“.")
            else if (sig != null) InfoBanner("Unterschriftsfeld erkannt.", FaColors.Sign, "Einfügen" to { onSignatureField(sig) })
            else InfoBanner("Wählen Sie unten eine Unterschrift. Eine eingefügte Unterschrift können Sie antippen, um sie zu verschieben, zu skalieren oder zu entfernen. (Einfache elektronische Unterschrift, keine kryptografische Signatur.)")
        }
        WorkspaceMode.REDACT -> if (vm.redactScroll) InfoBanner("Blättern aktiv – tippen Sie unten auf „Bereich markieren“, um Inhalte auszuwählen.")
        WorkspaceMode.ANNOTATE -> when (vm.annotTool) {
            AnnotTool.SCROLL -> InfoBanner("Blättern aktiv – wählen Sie unten ein Werkzeug.")
            AnnotTool.ERASER -> InfoBanner("Radierer: Tippen Sie auf einen Kommentar, um ihn zu entfernen.", FaColors.Destructive)
            else -> {}
        }
        WorkspaceMode.READ -> {}
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, tint: Color = FaColors.Label, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick)
            .background(if (selected) FaColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp).width(66.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (!enabled) FaColors.SecondaryLabel else if (selected) FaColors.Accent else tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, maxLines = 1, color = if (selected) FaColors.Accent else FaColors.Label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ToolRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

@Composable
private fun ConfirmBar(text: String, onCancel: () -> Unit, onConfirm: () -> Unit, confirmText: String = "Übernehmen") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel) { Icon(Icons.Outlined.Close, null); Spacer(Modifier.width(4.dp)); Text("Abbrechen") }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = FaColors.SecondaryLabel)
        TextButton(onClick = onConfirm) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(4.dp)); Text(confirmText, fontWeight = FontWeight.SemiBold) }
    }
}

private val annotColors = listOf(0xFFFFD60A.toInt(), 0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFFF375F.toInt(), 0xFF1C1C1E.toInt())

@Composable
private fun BottomTools(
    vm: WorkspaceViewModel, currentPage: Int, commentsOpen: Boolean, onToggleComments: () -> Unit,
    onSheet: (String) -> Unit, onPickImage: () -> Unit, onDraw: () -> Unit, onSave: () -> Unit,
) {
    val p = vm.placement
    if (p != null) {
        Column {
            // Größe: Regler in Prozent der Seitenbreite, dazu feine Schritte und Stufen
            val pageW = vm.source?.pageSizes?.getOrNull(p.page)?.first ?: 595f
            val percent = (p.w / pageW * 100f)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Größe", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                IconButton(onClick = { p.resizeTo(p.w * 0.9f) }) { Text("−", fontSize = 20.sp) }
                Slider(
                    value = percent.coerceIn(5f, 100f), onValueChange = { p.resizeTo(pageW * it / 100f) },
                    valueRange = 5f..100f, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { p.resizeTo(p.w * 1.1f) }) { Text("+", fontSize = 20.sp) }
                Text("${percent.toInt()} %", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = if (vm.placementIsSignature) listOf("Klein" to 18f, "Mittel" to 28f, "Groß" to 40f) else listOf("Klein" to 25f, "Mittel" to 45f, "Groß" to 70f)
                presets.forEach { (label, pct) ->
                    FilterChip(selected = kotlin.math.abs(percent - pct) < 1f, onClick = { p.resizeTo(pageW * pct / 100f) }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Drehung", style = MaterialTheme.typography.bodySmall)
                Slider(value = p.rotation, onValueChange = { p.rotation = it }, valueRange = -180f..180f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("${p.rotation.toInt()}°", style = MaterialTheme.typography.bodySmall)
            }
            ConfirmBar(if (vm.placementIsSignature) "Unterschrift einsetzen?" else "Bild einsetzen?", onCancel = vm::cancelPlacement, onConfirm = vm::commitPlacement)
        }
        return
    }
    if (vm.moveState != null) { ConfirmBar("Text hier ablegen?", onCancel = vm::cancelMove, onConfirm = vm::commitMove, confirmText = "Ablegen"); return }
    when (vm.mode) {
        WorkspaceMode.EDIT -> ToolRow {
            ToolButton(Icons.Outlined.TextFields, "Text", selected = vm.editTool == EditTool.TEXT) { vm.editTool = EditTool.TEXT }
            ToolButton(Icons.Outlined.Image, "Bild", onClick = onPickImage)
            ToolButton(Icons.Outlined.Gesture, "Zeichnen", onClick = onDraw)
            ToolButton(Icons.Outlined.MoreHoriz, "Mehr") { onSheet("editmore") }
        }
        WorkspaceMode.ANNOTATE -> Column {
            if (vm.inkStrokes.isNotEmpty()) ConfirmBar("${vm.inkStrokes.size} Strich(e) gezeichnet", onCancel = { vm.inkStrokes.clear() }, onConfirm = vm::commitInk)
            val markup = vm.annotTool in setOf(AnnotTool.HIGHLIGHT, AnnotTool.UNDERLINE, AnnotTool.STRIKE)
            if (markup || vm.annotTool == AnnotTool.INK) Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (markup) {
                    MiniChip("Markieren", vm.annotTool == AnnotTool.HIGHLIGHT) { vm.annotTool = AnnotTool.HIGHLIGHT }
                    MiniChip("Unterstr.", vm.annotTool == AnnotTool.UNDERLINE) { vm.annotTool = AnnotTool.UNDERLINE }
                    MiniChip("Durchstr.", vm.annotTool == AnnotTool.STRIKE) { vm.annotTool = AnnotTool.STRIKE }
                }
                Spacer(Modifier.weight(1f))
                annotColors.forEach { c ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (vm.annotColor == c) 26.dp else 20.dp).clip(CircleShape).background(Color(c))
                            .clickable { vm.annotColor = c }
                    )
                }
            }
            ToolRow {
                val t = vm.annotTool
                fun pick(x: AnnotTool) { vm.annotTool = if (vm.annotTool == x) AnnotTool.SCROLL else x }
                ToolButton(Icons.Outlined.BorderColor, "Hervorheben", t == AnnotTool.HIGHLIGHT || t == AnnotTool.UNDERLINE || t == AnnotTool.STRIKE) { pick(AnnotTool.HIGHLIGHT) }
                ToolButton(Icons.Outlined.ChatBubbleOutline, "Notiz", t == AnnotTool.NOTE) { pick(AnnotTool.NOTE) }
                ToolButton(Icons.Outlined.Gesture, "Zeichnen", t == AnnotTool.INK) { pick(AnnotTool.INK) }
                ToolButton(Icons.Outlined.AutoFixNormal, "Radierer", t == AnnotTool.ERASER) { pick(AnnotTool.ERASER) }
                ToolButton(Icons.Outlined.Forum, "Liste (${vm.comments.size})", commentsOpen, onClick = onToggleComments)
            }
        }
        WorkspaceMode.REDACT -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.Outlined.CropFree, "Bereich markieren", selected = !vm.redactScroll) { vm.redactScroll = !vm.redactScroll }
                ToolButton(Icons.Outlined.Search, "Text suchen") { onSheet("redactsearch") }
                if (vm.redactRects.isNotEmpty()) ToolButton(Icons.AutoMirrored.Outlined.Undo, "Letzte entfernen") { vm.redactRects.removeAt(vm.redactRects.size - 1) }
            }
            Row(
                Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FaColors.AccentLight).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, null, tint = FaColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Markierte Inhalte werden dauerhaft entfernt.", style = MaterialTheme.typography.bodySmall, color = FaColors.Label)
            }
            Button(
                onClick = { onSheet("redact") }, enabled = vm.redactRects.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent),
            ) { Text(if (vm.redactRects.isEmpty()) "Schwärzen" else "Schwärzen (${vm.redactRects.size})") }
        }
        WorkspaceMode.FORM -> Column {
            ToolRow {
                ToolButton(Icons.Outlined.People, "Profil") { onSheet("profiles") }
                ToolButton(Icons.Outlined.Gesture, "Unterschrift") { onSheet("sign") }
                ToolButton(Icons.Outlined.TextFields, "Freitext") { vm.addTextAt = Triple(currentPage, 60f, 100f) }
            }
            val n = vm.pendingValues.size
            Button(
                onClick = { vm.applyPendingValues { onSave() } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent),
            ) { Text(if (n > 0) "Speichern ($n Werte)" else "Speichern") }
        }
        WorkspaceMode.SIGN -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Button(
                onClick = { onSheet("sign") }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent),
            ) { Icon(Icons.Outlined.Gesture, null); Spacer(Modifier.width(8.dp)); Text("Unterschrift hinzufügen") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { onSheet("more") }) { Text("Weitere Optionen") }
            }
        }
        WorkspaceMode.READ -> {}
    }
}

@Composable
private fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = if (selected) FaColors.Accent else FaColors.SecondaryLabel,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) FaColors.AccentLight else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun SoftAction(icon: ImageVector, text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) FaColors.AccentLight else FaColors.Card)
            .border(1.dp, if (selected) FaColors.Accent else FaColors.CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) FaColors.Accent else FaColors.Label, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) FaColors.Accent else FaColors.Label, maxLines = 1)
    }
}

@Composable
private fun ThumbStrip(vm: WorkspaceViewModel, source: de.fapdf.pdf.PdfPageSource, currentPage: Int, onGo: (Int) -> Unit) {
    val stripState = rememberLazyListState()
    LaunchedEffect(currentPage) { runCatching { stripState.animateScrollToItem((currentPage - 2).coerceAtLeast(0)) } }
    LazyRow(
        state = stripState,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        items(source.pageCount, key = { "${vm.versionKey}-$it" }) { p ->
            PageThumb(
                source, p, Modifier.width(48.dp), selected = p == currentPage, label = "${p + 1}", renderWidth = 160,
                onClick = { onGo(p) },
            )
        }
    }
}

@Composable
private fun ZoomRow(zoom: Float, onZoom: (Float) -> Unit, onFullscreen: () -> Unit, onGrid: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onGrid) { Icon(Icons.Outlined.GridView, "Seitenübersicht") }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(FaColors.Card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onZoom((zoom - 0.25f).coerceIn(1f, 4f)) }, enabled = zoom > 1.01f) { Icon(Icons.Outlined.Remove, "Verkleinern") }
            Text("${(zoom * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { onZoom((zoom + 0.25f).coerceIn(1f, 4f)) }, enabled = zoom < 3.99f) { Icon(Icons.Outlined.Add, "Vergrößern") }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onFullscreen) { Icon(Icons.Outlined.Fullscreen, "Vollbild") }
    }
}

private val timeFmt = java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale.GERMANY)

@Composable
private fun CommentsPanel(vm: WorkspaceViewModel, onGo: (Int) -> Unit, onClose: () -> Unit) {
    var openKey by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kommentare (${vm.comments.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Schließen") }
        }
        if (vm.comments.isEmpty()) {
            Text("Noch keine Kommentare. Wählen Sie unten „Hervorheben“, „Notiz“ oder „Zeichnen“.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.comments, key = { "${it.page}/${it.index}/${vm.versionKey}" }) { c ->
                val key = "${c.page}/${c.index}"
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FaColors.Card)
                        .border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp))
                        .clickable { onGo(c.page); openKey = if (openKey == key) null else key; reply = "" }
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(FaColors.AccentLight), contentAlignment = Alignment.Center) {
                            Text(c.author.firstOrNull()?.uppercase() ?: "?", color = FaColors.Accent, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.author.ifBlank { "Unbekannt" }, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Text(
                                c.typeTitle + " · Seite ${c.page + 1}" + (c.modified?.let { " · " + timeFmt.format(java.util.Date(it)) } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel, maxLines = 1,
                            )
                        }
                        IconButton(onClick = { vm.deleteComment(c) }) { Icon(Icons.Outlined.DeleteOutline, "Löschen", tint = FaColors.SecondaryLabel) }
                    }
                    if (c.text.isNotBlank()) Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    c.replies.forEach { r ->
                        Column(Modifier.padding(start = 16.dp, top = 6.dp)) {
                            Text(
                                r.author.ifBlank { "Unbekannt" } + (r.modified?.let { " · " + timeFmt.format(java.util.Date(it)) } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = FaColors.SecondaryLabel,
                            )
                            Text(r.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (openKey == key) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            reply, { reply = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Antworten …") },
                            shape = RoundedCornerShape(10.dp),
                        )
                        IconButton(onClick = { vm.replyTo(c, reply); reply = ""; openKey = null }, enabled = reply.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Outlined.Send, "Senden", tint = FaColors.Accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormNavigator(vm: WorkspaceViewModel, onGo: (Int) -> Unit) {
    val fields = vm.formInfo?.fields.orEmpty().filter { !it.readOnly && it.widgets.isNotEmpty() }
    if (fields.isEmpty()) return
    var i by remember(vm.formInfo) { mutableStateOf(0) }
    val focusedIdx = fields.indexOfFirst { it.id == vm.focusedFieldId }
    val idx = if (focusedIdx >= 0) focusedIdx else i.coerceIn(0, fields.size - 1)
    val f = fields[idx]
    fun go(n: Int) {
        i = n.coerceIn(0, fields.size - 1)
        val t = fields[i]
        onGo(t.widgets.first().pageIndex)
        vm.focusedFieldId = t.id
        if (t.kind == FieldKind.TEXT || t.kind == FieldKind.MULTILINE || t.kind == FieldKind.DATE) vm.activateField(t) else vm.inlineFieldId = null
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { go(idx - 1) }, enabled = idx > 0) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vorheriges Feld") }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(FaColors.Card).clickable {
                go(idx)
                vm.activateField(f)
            }.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Feld ${idx + 1} von ${fields.size}", style = MaterialTheme.typography.labelLarge)
            Text(
                f.label.ifBlank { f.id } + vm.displayValue(f).takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { go(idx + 1) }, enabled = idx < fields.size - 1) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Nächstes Feld") }
    }
}

@Composable
private fun SearchRow(text: String, onText: (String) -> Unit, onSearch: () -> Unit, hits: Int, index: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, onText, Modifier.weight(1f), singleLine = true, placeholder = { Text("Im Dokument suchen") })
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Suchen", tint = FaColors.Accent) }
        if (hits > 0) {
            Text("${index + 1}/$hits", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onPrev) { Text("‹") }
            TextButton(onClick = onNext) { Text("›") }
        }
    }
}

/** Lädt ein Bild verkleinert (max. 2400 px) und mit korrekter Ausrichtung. */
fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val m = maxOf(info.size.width, info.size.height)
            if (m > 2400) {
                val f = 2400f / m
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }
}.getOrNull()
