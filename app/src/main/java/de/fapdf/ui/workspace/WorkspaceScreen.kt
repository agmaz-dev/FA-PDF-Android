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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import de.fapdf.ui.components.PasswordDialog
import de.fapdf.ui.components.PdfPagesView
import de.fapdf.ui.components.PlacementLayer
import de.fapdf.ui.components.rememberExportLauncher
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.launch

private fun modeTitle(m: WorkspaceMode) = when (m) {
    WorkspaceMode.READ -> "Lesen"
    WorkspaceMode.EDIT -> "Bearbeiten"
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
    BackHandler { leave() }

    val doc = vm.doc
    val source = vm.source

    // Seiten im sichtbaren Bereich im Hintergrund analysieren (für Auswahl und Formatierung)
    LaunchedEffect(currentPage, vm.versionKey, vm.mode) {
        if (vm.mode == WorkspaceMode.EDIT || vm.mode == WorkspaceMode.ANNOTATE) vm.ensureAnalyzed(currentPage)
    }

    Scaffold(
        containerColor = FaColors.Background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = FaColors.Background),
                navigationIcon = {
                    IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = FaColors.Accent) }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            DocumentRepository.baseName(doc?.displayName ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(modeTitle(vm.mode) + if (vm.dirty) " • nicht gespeichert" else "", style = MaterialTheme.typography.labelSmall, color = FaColors.SecondaryLabel)
                    }
                },
                actions = {
                    if (vm.mode == WorkspaceMode.READ) {
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) vm.clearSearch() }) { Icon(Icons.Outlined.Search, "Suchen") }
                    }
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Rückgängig") }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Wiederherstellen") }
                    IconButton(onClick = { showSave = true }) { Icon(Icons.Outlined.Save, "Speichern", tint = if (vm.dirty) FaColors.Accent else FaColors.SecondaryLabel) }
                },
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    BottomTools(vm, currentPage, onSheet = { sheet = it }, onPickImage = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, onPrint = {
                        vm.current?.let { FileActions.print(context, it, doc?.displayName ?: "Dokument.pdf") }
                    }, onShare = {
                        vm.current?.let { FileActions.share(context, it, doc?.displayName ?: "Dokument.pdf") }
                    })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searchOpen && vm.mode == WorkspaceMode.READ) {
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
            ModeBanners(vm, onSignatureField = { f ->
                val w = f.widgets.first()
                signatureTarget = w.pageIndex to w.viewBox
                scope.launch { listState.animateScrollToItem(w.pageIndex) }
                sheet = "sign"
            })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (source != null) {
                    val gestures = gesturesFor(vm)
                    PdfPagesView(
                        source = source, versionKey = vm.versionKey, cache = vm.bitmapCache, listState = listState,
                        zoom = zoom, onZoom = { zoom = it },
                        scrollEnabled = !gestures.dragEnabled && vm.placement == null,
                        gestures = gestures,
                    ) { page, pxPerPt -> PageOverlay(vm, page, pxPerPt) }
                    // Seitenanzeige
                    Text(
                        "${currentPage + 1} / ${source.pageCount}",
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
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
    vm.selection?.let { sel -> EditTextSheet(vm, sel) }
    vm.addTextAt?.let { (p, u, v) -> AddTextDialog(vm.newTextStyle, onDismiss = { vm.addTextAt = null }) { text, style -> vm.addText(p, u, v, text, style) } }
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
        "redact" -> RedactOptionsDialog(vm.redactRects.size, onDismiss = { sheet = null }) { raster, meta -> sheet = null; vm.applyRedaction(raster, meta) }
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
        else -> PageGestures(dragEnabled = true, onDragStart = vm::onDragStart, onDrag = vm::onDrag, onDragEnd = vm::onDragEnd)
    }
    WorkspaceMode.REDACT -> if (vm.redactScroll) PageGestures()
    else PageGestures(dragEnabled = true, onDragStart = vm::onDragStart, onDrag = vm::onDrag, onDragEnd = vm::onDragEnd)
    WorkspaceMode.FORM -> PageGestures(onTap = { p, u, v ->
        val f = vm.formInfo?.fields?.firstOrNull { fi -> fi.widgets.any { it.pageIndex == p && it.viewBox.inset(2f).contains(u, v) } }
        when {
            f != null && f.kind == FieldKind.SIGNATURE -> vm.signatureFieldPrompt = f
            f != null && !f.readOnly -> vm.editingField = f
            f == null -> vm.addTextAt = Triple(p, u, v)
        }
    })
    WorkspaceMode.SIGN -> PageGestures()
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
                val text = vm.pendingValues[f.id]?.let { vm.displayValue(f) }
                if (!text.isNullOrEmpty()) drawFieldText(text, w.viewBox, s)
            }
        }
    }
    vm.placement?.takeIf { it.page == page }?.let { PlacementLayer(it, s) }
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
            vm.placement != null -> InfoBanner("Mit zwei Fingern verschieben, vergrößern und drehen. Danach unten „Übernehmen“ tippen.")
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
            if (vm.placement != null) InfoBanner("Unterschrift mit zwei Fingern positionieren, dann „Übernehmen“.")
            else if (sig != null) InfoBanner("Unterschriftsfeld erkannt.", FaColors.Sign, "Einfügen" to { onSignatureField(sig) })
            else InfoBanner("Wählen Sie unten eine Unterschrift. Hinweis: Dies ist eine einfache elektronische Unterschrift (Bild), keine kryptografische Signatur.")
        }
        WorkspaceMode.REDACT -> InfoBanner(
            if (vm.redactScroll) "Blättern aktiv – zum Auswählen unten „Bereich“ wählen."
            else "Ziehen Sie Rechtecke über vertrauliche Inhalte. Sie werden beim Anwenden dauerhaft entfernt.",
            Color.Black,
        )
        WorkspaceMode.ANNOTATE -> if (vm.annotTool == AnnotTool.SCROLL) InfoBanner("Blättern aktiv – wählen Sie unten ein Werkzeug.")
        WorkspaceMode.READ -> {}
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, tint: Color = FaColors.Label, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick)
            .background(if (selected) FaColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 6.dp).width(48.dp),
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
    vm: WorkspaceViewModel, currentPage: Int, onSheet: (String) -> Unit, onPickImage: () -> Unit, onPrint: () -> Unit, onShare: () -> Unit,
) {
    val p = vm.placement
    if (p != null) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Drehung", style = MaterialTheme.typography.bodySmall)
                Slider(value = p.rotation, onValueChange = { p.rotation = it }, valueRange = -180f..180f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("${p.rotation.toInt()}°", style = MaterialTheme.typography.bodySmall)
            }
            ConfirmBar(if (vm.placementIsSignature) "Unterschrift einsetzen?" else "Bild einsetzen?", onCancel = { vm.placement = null }, onConfirm = vm::commitPlacement)
        }
        return
    }
    if (vm.moveState != null) { ConfirmBar("Text hier ablegen?", onCancel = vm::cancelMove, onConfirm = vm::commitMove, confirmText = "Ablegen"); return }
    when (vm.mode) {
        WorkspaceMode.EDIT -> ToolRow {
            ToolButton(Icons.Outlined.TextFields, "Text", selected = vm.editTool == EditTool.TEXT) { vm.editTool = EditTool.TEXT }
            ToolButton(Icons.Outlined.DocumentScanner, "OCR") { onSheet("ocr") }
            ToolButton(Icons.Outlined.FontDownload, "Schrift") { onSheet("font") }
            ToolButton(Icons.Outlined.Image, "Bilder", onClick = onPickImage)
            ToolButton(Icons.Outlined.BorderColor, "Markieren", selected = vm.editTool == EditTool.HIGHLIGHT) {
                vm.editTool = if (vm.editTool == EditTool.HIGHLIGHT) EditTool.TEXT else EditTool.HIGHLIGHT
            }
            ToolButton(Icons.Outlined.Draw, "Unterschr.") { onSheet("sign") }
            ToolButton(Icons.Outlined.MoreHoriz, "Weitere") { onSheet("more") }
        }
        WorkspaceMode.ANNOTATE -> Column {
            if (vm.inkStrokes.isNotEmpty()) ConfirmBar("${vm.inkStrokes.size} Strich(e) gezeichnet", onCancel = { vm.inkStrokes.clear() }, onConfirm = vm::commitInk)
            ToolRow {
                ToolButton(Icons.Outlined.PanTool, "Blättern", vm.annotTool == AnnotTool.SCROLL) { vm.annotTool = AnnotTool.SCROLL }
                ToolButton(Icons.Outlined.BorderColor, "Markieren", vm.annotTool == AnnotTool.HIGHLIGHT) { vm.annotTool = AnnotTool.HIGHLIGHT }
                ToolButton(Icons.Outlined.FormatUnderlined, "Unterstr.", vm.annotTool == AnnotTool.UNDERLINE) { vm.annotTool = AnnotTool.UNDERLINE }
                ToolButton(Icons.Outlined.FormatStrikethrough, "Durchstr.", vm.annotTool == AnnotTool.STRIKE) { vm.annotTool = AnnotTool.STRIKE }
                ToolButton(Icons.Outlined.StickyNote2, "Notiz", vm.annotTool == AnnotTool.NOTE) { vm.annotTool = AnnotTool.NOTE }
                ToolButton(Icons.Outlined.Brush, "Zeichnen", vm.annotTool == AnnotTool.INK) { vm.annotTool = AnnotTool.INK }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                annotColors.forEach { c ->
                    Box(
                        Modifier.padding(horizontal = 8.dp).size(if (vm.annotColor == c) 30.dp else 24.dp).clip(CircleShape).background(Color(c))
                            .clickable { vm.annotColor = c }
                    )
                }
            }
        }
        WorkspaceMode.REDACT -> ToolRow {
            ToolButton(Icons.Outlined.PanTool, "Blättern", vm.redactScroll) { vm.redactScroll = true }
            ToolButton(Icons.Outlined.CropFree, "Bereich", !vm.redactScroll) { vm.redactScroll = false }
            ToolButton(Icons.Outlined.ManageSearch, "Begriff") { onSheet("redactsearch") }
            ToolButton(Icons.Outlined.Delete, "Entfernen", enabled = vm.redactRects.isNotEmpty()) { vm.redactRects.removeAt(vm.redactRects.size - 1) }
            ToolButton(Icons.Outlined.Check, "Anwenden (${vm.redactRects.size})", enabled = vm.redactRects.isNotEmpty(), tint = FaColors.Destructive) { onSheet("redact") }
        }
        WorkspaceMode.FORM -> Column {
            val n = vm.pendingValues.size
            if (n > 0) ConfirmBar("$n Wert(e) geprüft?", onCancel = { vm.pendingValues.clear() }, onConfirm = vm::applyPendingValues)
            ToolRow {
                ToolButton(Icons.Outlined.People, "Profil") { onSheet("profiles") }
                ToolButton(Icons.Outlined.Draw, "Unterschr.") { onSheet("sign") }
                ToolButton(Icons.Outlined.TextFields, "Freitext") { vm.addTextAt = Triple(currentPage, 60f, 100f) }
                ToolButton(Icons.Outlined.Share, "Teilen", onClick = onShare)
            }
        }
        WorkspaceMode.SIGN -> ToolRow {
            ToolButton(Icons.Outlined.Draw, "Unterschrift") { onSheet("sign") }
            ToolButton(Icons.Outlined.Print, "Drucken", onClick = onPrint)
            ToolButton(Icons.Outlined.Share, "Teilen", onClick = onShare)
        }
        WorkspaceMode.READ -> ToolRow {
            ToolButton(Icons.Outlined.Description, "Seite …") { onSheet("jump") }
            ToolButton(Icons.Outlined.Bookmarks, "Lesezeichen") { onSheet("bookmarks") }
            ToolButton(Icons.Outlined.Print, "Drucken", onClick = onPrint)
            ToolButton(Icons.Outlined.Share, "Teilen", onClick = onShare)
            ToolButton(Icons.Outlined.MoreHoriz, "Weitere") { onSheet("more") }
        }
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
