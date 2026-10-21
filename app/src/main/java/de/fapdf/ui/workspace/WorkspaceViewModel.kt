package de.fapdf.ui.workspace

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import de.fapdf.FaPdfApp
import de.fapdf.data.DocumentEntity
import de.fapdf.data.PersonProfile
import de.fapdf.data.SignatureEntity
import de.fapdf.pdf.Alignment
import de.fapdf.pdf.Annotator
import de.fapdf.pdf.AutofillSuggestion
import de.fapdf.pdf.Box
import de.fapdf.pdf.DetectedStyle
import de.fapdf.pdf.DocFontCache
import de.fapdf.pdf.FieldKind
import de.fapdf.pdf.FieldMatcher
import de.fapdf.pdf.FieldValue
import de.fapdf.pdf.FontFamily
import de.fapdf.pdf.FormEngine
import de.fapdf.pdf.FormFieldInfo
import de.fapdf.pdf.FormInfo
import de.fapdf.pdf.MarkupType
import de.fapdf.pdf.OcrEngine
import de.fapdf.pdf.OcrLayer
import de.fapdf.pdf.OcrPage
import de.fapdf.pdf.PageAnalyzer
import de.fapdf.pdf.PageRaster
import de.fapdf.pdf.PageText
import de.fapdf.pdf.PasswordRequiredException
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.PdfOps
import de.fapdf.pdf.PdfPageSource
import de.fapdf.pdf.PdfUserException
import de.fapdf.pdf.PreviewFactory
import de.fapdf.pdf.RedactionReport
import de.fapdf.pdf.Redactor
import de.fapdf.pdf.ScanEditEngine
import de.fapdf.pdf.TextEditEngine
import de.fapdf.pdf.TextStyle
import de.fapdf.ui.WorkspaceMode
import de.fapdf.ui.components.PageBitmapCache
import de.fapdf.ui.components.Placement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Auswahl einer Textzeile (digital oder per OCR erkannt). */
data class TextSelection(
    val page: Int,
    val lineIds: List<Int>,
    val blockLineIds: List<Int>,
    val text: String,
    val blockText: String,
    val viewBox: Box,
    val blockViewBox: Box,
    val detected: DetectedStyle,
    val fontId: Int,
    val isScan: Boolean,
    val ocrLineIndex: Int = -1,
    val canUseOriginalFont: Boolean = true,
)

data class SearchHit(val page: Int, val viewBox: Box)
data class DragRect(val page: Int, val box: Box)
enum class EditTool { TEXT, HIGHLIGHT }
enum class AnnotTool { SCROLL, HIGHLIGHT, UNDERLINE, STRIKE, NOTE, INK }
data class LabelSuggestion(val page: Int, val u: Float, val v: Float, val label: String, val value: String, val size: Float)

class WorkspaceViewModel(app: Application, val docId: Long, initialMode: WorkspaceMode) : AndroidViewModel(app) {
    private val c = (app as FaPdfApp).container
    private val editEngine = TextEditEngine(c.fonts)
    private val scanEngine = ScanEditEngine(c.fonts)
    private val ocrEngine = OcrEngine()
    private val workDir = File(app.cacheDir, "work/$docId").apply { deleteRecursively(); mkdirs() }

    // ---------- Zustand ----------
    var doc by mutableStateOf<DocumentEntity?>(null); private set
    var mode by mutableStateOf(initialMode)
    var source by mutableStateOf<PdfPageSource?>(null); private set
    var versionKey by mutableIntStateOf(0); private set
    val bitmapCache = PageBitmapCache()
    var busy by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<Pair<String, String>?>(null)
    var needsPassword by mutableStateOf(false); private set
    var wasEncrypted by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set

    private val versions = ArrayList<File>()
    private var index = -1
    private var savedIndex = 0
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var dirty by mutableStateOf(false); private set

    val pageTexts = mutableStateMapOf<Int, PageText>()
    val ocrPages = mutableStateMapOf<Int, OcrPage>()
    private var analysisDoc: PDDocument? = null

    // Bearbeiten
    var editTool by mutableStateOf(EditTool.TEXT)
    var selection by mutableStateOf<TextSelection?>(null)
    var addTextAt by mutableStateOf<Triple<Int, Float, Float>?>(null)
    var newTextStyle by mutableStateOf(TextStyle(useOriginalFont = false, size = 11f))
    var moveState by mutableStateOf<Pair<TextSelection, Pair<Float, Float>>?>(null)
    var scanPromptPage by mutableStateOf<Int?>(null)

    // Platzieren (Bild/Unterschrift)
    var placement by mutableStateOf<Placement?>(null)
    var placementIsSignature by mutableStateOf(false)

    // Kommentieren / Schwärzen
    var annotTool by mutableStateOf(AnnotTool.HIGHLIGHT)
    var annotColor by mutableStateOf(0xFFFFD60A.toInt())
    var dragRect by mutableStateOf<DragRect?>(null)
    private var dragStart: Pair<Float, Float>? = null
    val inkStrokes = mutableStateListOf<Pair<Int, List<Pair<Float, Float>>>>()
    var currentStroke by mutableStateOf<Pair<Int, List<Pair<Float, Float>>>?>(null)
    var noteAt by mutableStateOf<Triple<Int, Float, Float>?>(null)
    val redactRects = mutableStateListOf<DragRect>()
    var redactScroll by mutableStateOf(false)

    // Formulare
    var formInfo by mutableStateOf<FormInfo?>(null); private set
    val pendingValues = mutableStateMapOf<String, FieldValue>()
    var editingField by mutableStateOf<FormFieldInfo?>(null)
    var formPrompt by mutableStateOf(false)
    var profilePrompt by mutableStateOf(false)
    var autofillPreview by mutableStateOf<Pair<PersonProfile, List<AutofillSuggestion>>?>(null)
    var labelSuggestions by mutableStateOf<List<LabelSuggestion>?>(null)
    var signatureFieldPrompt by mutableStateOf<FormFieldInfo?>(null)
    private var formPromptShown = false

    // Lesen
    var searchHits by mutableStateOf<List<SearchHit>>(emptyList()); private set
    var searchIndex by mutableIntStateOf(0)
    var outline by mutableStateOf<List<Pair<String, Int>>>(emptyList()); private set

    val current: File? get() = versions.getOrNull(index)

    init { open(null) }

    fun open(password: String?) {
        viewModelScope.launch {
            busy = "Dokument wird geöffnet …"
            try {
                val d = c.documents.get(docId) ?: throw PdfUserException("Dokument nicht gefunden.")
                doc = d
                c.documents.touchOpened(docId)
                val v0 = File(workDir, "v0.pdf")
                val info = withContext(PdfIo.pdfDispatcher) {
                    PdfIo.loadDecrypted(File(d.filePath), password).use { pd ->
                        val enc = pd.isEncrypted
                        PdfIo.save(pd, v0)
                        enc
                    }
                }
                wasEncrypted = info
                needsPassword = false
                versions.clear(); versions.add(v0); index = 0; savedIndex = 0
                reloadPreview()
                loadOutline()
                afterOpenChecks()
            } catch (e: PasswordRequiredException) {
                needsPassword = true
            } catch (e: Exception) {
                loadError = e.message ?: "Das Dokument konnte nicht geöffnet werden."
            } finally {
                busy = null
            }
        }
    }

    private suspend fun reloadPreview() {
        val file = current ?: return
        val old = source
        val (newSource, fi) = withContext(PdfIo.pdfDispatcher) {
            analysisDoc?.close(); analysisDoc = null
            val preview = PreviewFactory.prepare(file, File(workDir, "preview_$index.pdf"))
            val form = runCatching { PdfIo.load(file).use { FormEngine.read(it) } }.getOrNull()
            PdfPageSource.open(preview) to form
        }
        formInfo = fi
        pageTexts.clear()
        bitmapCache.clear()
        source = newSource
        versionKey++
        withContext(Dispatchers.IO) { old?.close() }
        canUndo = index > 0
        canRedo = index < versions.size - 1
        dirty = index != savedIndex
    }

    private suspend fun afterOpenChecks() {
        val fi = formInfo
        if (fi != null && fi.hasFields && !formPromptShown) {
            formPromptShown = true
            if (mode == WorkspaceMode.FORM) maybeOfferProfiles() else if (mode != WorkspaceMode.SIGN) formPrompt = true
        }
        if (mode == WorkspaceMode.SIGN) fi?.signatureFields?.firstOrNull()?.let { signatureFieldPrompt = it }
        if (fi?.xfaOnly == true) message = "Hinweis" to "Dieses Formular nutzt das XFA-Format, das nur von Adobe-Programmen vollständig unterstützt wird. Die Felder können hier nicht ausgefüllt werden."
    }

    fun switchMode(m: WorkspaceMode) {
        mode = m
        selection = null; dragRect = null; placement = null; moveState = null
        if (m == WorkspaceMode.FORM) viewModelScope.launch { maybeOfferProfiles() }
        if (m == WorkspaceMode.SIGN) formInfo?.signatureFields?.firstOrNull()?.let { signatureFieldPrompt = it }
    }

    private suspend fun maybeOfferProfiles() {
        val fi = formInfo ?: return
        if (!fi.hasFields) return
        // Nur prüfen, ob Profile existieren (ohne Entschlüsselung)
        val hasProfiles = runCatching { c.profiles.observeLabels().first().isNotEmpty() }.getOrDefault(false)
        val anyMatch = fi.fields.any { FieldMatcher.match(it.label) != null || FieldMatcher.match(it.id) != null }
        if (hasProfiles && anyMatch) profilePrompt = true
    }

    // ---------- Versionen ----------
    private fun newVersionFile() = File(workDir, "v${System.nanoTime()}.pdf")

    private suspend fun pushVersion(f: File) {
        while (versions.size > index + 1) versions.removeAt(versions.size - 1).delete()
        versions.add(f); index = versions.size - 1
        if (savedIndex > index) savedIndex = -1
        reloadPreview()
    }

    fun undo() { if (index > 0) viewModelScope.launch { index--; ocrPages.clear(); reloadPreview() } }
    fun redo() { if (index < versions.size - 1) viewModelScope.launch { index++; ocrPages.clear(); reloadPreview() } }

    /** Führt eine Änderung aus: Eingabe = aktuelle Version, Ausgabe = neue Version. */
    private fun runEdit(label: String, keepOcr: Boolean = false, op: suspend (File, File) -> String?, onSuccess: () -> Unit = {}) {
        val input = current ?: return
        viewModelScope.launch {
            busy = label
            try {
                val out = newVersionFile()
                val note = withContext(PdfIo.pdfDispatcher) { op(input, out) }
                val savedOcr = if (keepOcr) HashMap(ocrPages) else null
                ocrPages.clear()
                pushVersion(out)
                savedOcr?.let { ocrPages.putAll(it) }
                onSuccess()
                if (note != null) message = "Hinweis" to note
            } catch (e: Exception) {
                message = "Nicht möglich" to (e.message ?: e.javaClass.simpleName)
            } finally {
                busy = null
            }
        }
    }

    fun save(alsoOriginal: Boolean, onDone: () -> Unit = {}) {
        val f = current ?: return
        viewModelScope.launch {
            busy = "Speichern …"
            try {
                c.documents.replaceContent(docId, f)
                var extra = ""
                if (alsoOriginal) extra = if (c.documents.writeBackToSource(docId)) "\nDie Originaldatei wurde ebenfalls aktualisiert." else "\nDie Originaldatei konnte nicht überschrieben werden (keine Schreibberechtigung)."
                savedIndex = index; dirty = false
                doc = c.documents.get(docId)
                if (wasEncrypted) extra += "\nHinweis: Das Dokument war verschlüsselt und wurde ohne Passwortschutz gespeichert. Über „PDF verschlüsseln“ können Sie es erneut schützen."
                message = "Gespeichert" to ("Die Änderungen wurden gespeichert.$extra")
                onDone()
            } catch (e: Exception) {
                message = "Fehler" to (e.message ?: "Speichern fehlgeschlagen")
            } finally {
                busy = null
            }
        }
    }

    // ---------- Analyse ----------
    suspend fun pageText(page: Int): PageText? {
        pageTexts[page]?.let { return it }
        val f = current ?: return null
        val pt = withContext(PdfIo.pdfDispatcher) {
            val d = analysisDoc ?: PdfIo.load(f).also { analysisDoc = it }
            runCatching { PageAnalyzer.analyze(d, page) }.getOrNull()
        }
        if (pt != null) pageTexts[page] = pt
        return pt
    }

    fun ensureAnalyzed(page: Int) { if (pageTexts[page] == null) viewModelScope.launch { pageText(page) } }

    // ---------- Bearbeiten: Tippen ----------
    fun onEditTap(page: Int, u: Float, v: Float) {
        if (placement != null || moveState != null) return
        viewModelScope.launch {
            val ocr = ocrPages[page]
            if (ocr != null) {
                val idx = ocr.lines.indexOfFirst { it.viewBox.inset(3f).contains(u, v) }
                if (idx >= 0) { selectOcrLine(page, idx); return@launch }
                addTextAt = Triple(page, u, v); return@launch
            }
            val pt = pageText(page) ?: return@launch
            val line = pt.lineAtView(u, v)
            if (line == null) {
                if (pt.looksScanned) { scanPromptPage = page; return@launch }
                addTextAt = Triple(page, u, v)
                return@launch
            }
            val block = pt.blockOfLine(line.id)
            val blockIds = block?.lineIds ?: listOf(line.id)
            val det = editEngine.detectStyle(pt, listOf(line.id))
            selection = TextSelection(
                page = page, lineIds = listOf(line.id), blockLineIds = blockIds,
                text = line.text, blockText = blockIds.joinToString("\n") { pt.lines[it].text },
                viewBox = pt.viewBoxOf(line),
                blockViewBox = pt.geometry.userBoxToView(block?.bbox ?: line.bbox),
                detected = det, fontId = line.fontId, isScan = false,
            )
        }
    }

    private suspend fun selectOcrLine(page: Int, idx: Int) {
        val f = current ?: return
        val ocr = ocrPages[page] ?: return
        val line = ocr.lines[idx]
        busy = "Schrift wird analysiert …"
        try {
            val bmp = withContext(Dispatchers.IO) { PageRaster.render(f, page, OcrEngine.OCR_SCALE, workDir) }
            val st = scanEngine.detect(bmp, OcrEngine.OCR_SCALE, line)
            bmp.recycle()
            selection = TextSelection(
                page = page, lineIds = emptyList(), blockLineIds = emptyList(), text = line.text, blockText = line.text,
                viewBox = line.viewBox, blockViewBox = line.viewBox,
                detected = DetectedStyle("Erkannt aus Scan", st.family, st.bold, st.italic, st.size, st.colorArgb, false, Alignment.LEFT, st.size * 1.2f),
                fontId = -1, isScan = true, ocrLineIndex = idx, canUseOriginalFont = false,
            )
        } finally {
            busy = null
        }
    }

    fun applyTextEdit(sel: TextSelection, newText: String, style: TextStyle, alignment: Alignment, asBlock: Boolean) {
        selection = null
        if (sel.isScan) {
            val page = sel.page
            val ocr = ocrPages[page] ?: return
            var updated: OcrPage? = null
            runEdit("Scan wird bearbeitet …", keepOcr = true, op = { input, out ->
                val bmp = PageRaster.render(input, page, OcrEngine.OCR_SCALE, workDir)
                PdfIo.transform(input, out) { d ->
                    updated = scanEngine.replaceLine(
                        d, page, bmp, OcrEngine.OCR_SCALE, ocr, sel.ocrLineIndex, newText,
                        ScanEditEngine.ScanStyle(style.family, style.bold, style.italic, style.size, style.colorArgb),
                    )
                }
                bmp.recycle()
                null
            }, onSuccess = { updated?.let { ocrPages[page] = it } })
            return
        }
        val ids = if (asBlock) sel.blockLineIds else sel.lineIds
        runEdit("Text wird geändert …", op = { input, out ->
            PdfIo.transform(input, out) { d -> editEngine.replaceText(d, sel.page, ids, newText, style, alignment, asBlock).note }
        })
    }

    fun deleteSelection(sel: TextSelection, asBlock: Boolean) {
        selection = null
        if (sel.isScan) { applyTextEdit(sel, "", TextStyle(false), Alignment.LEFT, false); return }
        val ids = if (asBlock) sel.blockLineIds else sel.lineIds
        runEdit("Text wird gelöscht …", op = { input, out -> PdfIo.transform(input, out) { d -> editEngine.deleteLines(d, sel.page, ids) }; null })
    }

    fun startMove(sel: TextSelection) { selection = null; moveState = sel to (0f to 0f) }
    fun cancelMove() { moveState = null }
    fun commitMove() {
        val (sel, delta) = moveState ?: return
        moveState = null
        if (sel.isScan) { message = "Hinweis" to "Gescannter Text kann nicht verschoben werden. Löschen Sie die Zeile und fügen Sie sie an der neuen Stelle ein."; return }
        val lineId = sel.lineIds.first()
        runEdit("Text wird verschoben …", op = { input, out ->
            PdfIo.transform(input, out) { d -> editEngine.moveLine(d, sel.page, lineId, delta.first, delta.second) }; null
        })
    }

    fun addText(page: Int, u: Float, v: Float, text: String, style: TextStyle) {
        addTextAt = null
        if (text.isBlank()) return
        newTextStyle = style
        runEdit("Text wird eingefügt …", op = { input, out ->
            PdfIo.transform(input, out) { d -> editEngine.addText(d, page, u, v + style.size * 0.8f, text, style).note }
        })
    }

    // ---------- OCR ----------
    fun runOcr(page: Int) {
        val f = current ?: return
        scanPromptPage = null
        viewModelScope.launch {
            busy = "Texterkennung (OCR) Seite ${page + 1} …"
            try {
                val bmp = withContext(Dispatchers.IO) { PageRaster.render(f, page, OcrEngine.OCR_SCALE, workDir) }
                val res = ocrEngine.recognize(page, bmp, OcrEngine.OCR_SCALE)
                bmp.recycle()
                ocrPages[page] = res
                message = if (res.lines.isEmpty()) "Texterkennung" to "Auf dieser Seite wurde kein Text erkannt."
                else "Texterkennung" to "${res.lines.size} Zeilen erkannt. Tippen Sie auf eine Zeile, um sie zu bearbeiten."
            } catch (e: Exception) {
                message = "Texterkennung fehlgeschlagen" to (e.message ?: "Unbekannter Fehler")
            } finally {
                busy = null
            }
        }
    }

    /** Ganzes Dokument per OCR durchsuchbar machen (unsichtbare Textebene, Aussehen bleibt gleich). */
    fun makeSearchable() {
        val input = current ?: return
        viewModelScope.launch {
            busy = "Dokument wird durchsuchbar gemacht …"
            try {
                val count = source?.pageCount ?: 0
                val results = HashMap<Int, OcrPage>()
                for (p in 0 until count) {
                    busy = "Texterkennung Seite ${p + 1} von $count …"
                    val pt = pageText(p)
                    if (pt != null && pt.visibleGlyphCount > 30) continue // hat bereits echten Text
                    if (pt != null && pt.hiddenLines.isNotEmpty()) continue // hat bereits OCR-Ebene
                    val bmp = withContext(Dispatchers.IO) { PageRaster.render(input, p, OcrEngine.OCR_SCALE, workDir) }
                    results[p] = ocrEngine.recognize(p, bmp, OcrEngine.OCR_SCALE)
                    bmp.recycle()
                }
                if (results.isEmpty()) { message = "Texterkennung" to "Alle Seiten enthalten bereits Text."; return@launch }
                runEdit("Textebene wird gespeichert …", op = { inp, out ->
                    PdfIo.transform(inp, out) { d ->
                        val cache = DocFontCache(d, c.fonts)
                        results.forEach { (p, r) -> OcrLayer.addInvisibleText(d, p, r.lines, cache) }
                    }
                    "${results.size} Seite(n) wurden durchsuchbar gemacht."
                })
            } catch (e: Exception) {
                message = "Texterkennung fehlgeschlagen" to (e.message ?: "")
            } finally {
                if (busy?.startsWith("Texterkennung") == true) busy = null
            }
        }
    }

    // ---------- Bilder & Unterschriften ----------
    fun startPlacement(page: Int, bmp: Bitmap, signature: Boolean, target: Box? = null) {
        val size = source?.pageSizes?.getOrNull(page) ?: return
        val aspect = bmp.height.toFloat() / bmp.width
        val (w, h, cx, cy) = if (target != null) {
            val tw = target.width
            val th = target.height
            val w = if (tw * aspect <= th) tw else th / aspect
            listOf(w, w * aspect, target.centerX, target.centerY)
        } else {
            val w = size.first * (if (signature) 0.35f else 0.5f)
            listOf(w, w * aspect, size.first / 2, size.second / 2)
        }
        placement = Placement(page, bmp, cx, cy, w, h)
        placementIsSignature = signature
    }

    fun commitPlacement() {
        val p = placement ?: return
        placement = null
        runEdit(if (placementIsSignature) "Unterschrift wird eingefügt …" else "Bild wird eingefügt …", op = { input, out ->
            PdfIo.transform(input, out) { d -> editEngine.insertImage(d, p.page, p.bitmap, p.cx, p.cy, p.w, p.h, p.rotation) }
            null
        })
    }

    suspend fun loadSignatureBitmap(s: SignatureEntity): Bitmap? = c.signatures.loadBitmap(s)

    // ---------- Kommentieren & Schwärzen: Ziehen ----------
    fun onDragStart(page: Int, u: Float, v: Float) {
        if (mode == WorkspaceMode.EDIT && moveState != null) { dragStart = u to v; return }
        if (mode == WorkspaceMode.ANNOTATE && annotTool == AnnotTool.INK) { currentStroke = page to listOf(u to v); return }
        dragStart = u to v
        dragRect = DragRect(page, Box(u, v, u, v))
    }

    fun onDrag(page: Int, u: Float, v: Float) {
        val ms = moveState
        if (mode == WorkspaceMode.EDIT && ms != null) {
            val s = dragStart ?: return
            moveState = ms.first to ((u - s.first) to (v - s.second)); return
        }
        val cs = currentStroke
        if (cs != null) { if (cs.first == page) currentStroke = page to (cs.second + (u to v)); return }
        val s = dragStart ?: return
        dragRect = DragRect(page, Box.of(s.first, s.second, u, v))
    }

    fun onDragEnd(@Suppress("UNUSED_PARAMETER") page: Int) {
        dragStart = null
        currentStroke?.let { inkStrokes.add(it); currentStroke = null; return }
        if (mode == WorkspaceMode.EDIT && moveState != null) return
        val r = dragRect ?: return
        dragRect = null
        if (r.box.width < 4f || r.box.height < 4f) return
        when (mode) {
            WorkspaceMode.REDACT -> redactRects.add(r)
            WorkspaceMode.ANNOTATE, WorkspaceMode.EDIT -> {
                val type = when {
                    mode == WorkspaceMode.EDIT -> MarkupType.HIGHLIGHT
                    annotTool == AnnotTool.UNDERLINE -> MarkupType.UNDERLINE
                    annotTool == AnnotTool.STRIKE -> MarkupType.STRIKEOUT
                    else -> MarkupType.HIGHLIGHT
                }
                val color = if (mode == WorkspaceMode.EDIT) 0xFFFFD60A.toInt() else annotColor
                runEdit("${type.title} …", op = { input, out ->
                    PdfIo.transform(input, out) { d -> Annotator.addMarkup(d, r.page, r.box, type, color) }; null
                })
            }
            else -> {}
        }
    }

    fun commitInk() {
        if (inkStrokes.isEmpty()) return
        val byPage = inkStrokes.groupBy({ it.first }, { it.second })
        inkStrokes.clear()
        val color = annotColor
        runEdit("Zeichnung wird gespeichert …", op = { input, out ->
            PdfIo.transform(input, out) { d -> byPage.forEach { (p, strokes) -> Annotator.addInk(d, p, strokes, color, 2f) } }; null
        })
    }

    fun addNote(page: Int, u: Float, v: Float, text: String) {
        noteAt = null
        if (text.isBlank()) return
        runEdit("Notiz wird eingefügt …", op = { input, out ->
            PdfIo.transform(input, out) { d -> Annotator.addNote(d, page, u, v, text, 0xFFFFD60A.toInt()) }; null
        })
    }

    fun redactSearch(term: String) {
        viewModelScope.launch {
            val hits = findAll(term)
            hits.forEach { redactRects.add(DragRect(it.page, it.viewBox.inset(1f))) }
            message = "Schwärzen" to if (hits.isEmpty()) "„$term“ wurde nicht gefunden." else "${hits.size} Fundstelle(n) für „$term“ markiert. Tippen Sie auf „Anwenden“."
        }
    }

    fun applyRedaction(forceRaster: Boolean, removeMetadata: Boolean) {
        if (redactRects.isEmpty()) return
        val byPage = redactRects.groupBy({ it.page }, { it.box })
        var report: RedactionReport? = null
        runEdit("Inhalte werden dauerhaft entfernt …", op = { input, out ->
            PdfIo.transform(input, out) { d -> report = Redactor(workDir).apply(d, byPage, forceRaster, removeMetadata, scanEngine) }
            null
        }, onSuccess = {
            redactRects.clear()
            report?.let { r ->
                message = "Geschwärzt" to buildString {
                    append("${r.removedGlyphs} Zeichen wurden aus dem Dokument entfernt.")
                    if (r.rasterizedPages.isNotEmpty()) append("\nSeite(n) ${r.rasterizedPages.joinToString { "${it + 1}" }} wurden zusätzlich als Bild neu aufgebaut, da dort Bilder oder eingebettete Objekte betroffen waren.")
                    append("\nSpeichern Sie das Dokument, um die Schwärzung zu übernehmen.")
                }
            }
        })
    }

    // ---------- Formulare ----------
    fun setPending(id: String, v: FieldValue) { pendingValues[id] = v }

    fun applyPendingValues() {
        if (pendingValues.isEmpty()) return
        val values = HashMap(pendingValues)
        var errors: List<String> = emptyList()
        runEdit("Formular wird ausgefüllt …", op = { input, out ->
            PdfIo.transform(input, out) { d -> errors = FormEngine.apply(d, values, c.fonts) }
            if (errors.isEmpty()) null else "Einige Felder konnten nicht gesetzt werden:\n" + errors.joinToString("\n")
        }, onSuccess = { pendingValues.clear() })
    }

    fun displayValue(f: FormFieldInfo): String = when (val p = pendingValues[f.id]) {
        is FieldValue.Text -> p.value
        is FieldValue.Choice -> p.value
        is FieldValue.Bool -> if (p.checked) "✓" else ""
        null -> when (f.kind) {
            FieldKind.CHECKBOX -> if (f.checked) "✓" else ""
            else -> ""
        }
    }

    suspend fun loadProfiles(): List<PersonProfile> = c.profiles.loadAll()

    fun prepareAutofill(profile: PersonProfile) {
        val fi = formInfo ?: return
        val fields = fi.fields.filter { !it.readOnly && it.kind in setOf(FieldKind.TEXT, FieldKind.MULTILINE, FieldKind.DATE) }
        val byLabel = FieldMatcher.plan(fields.map { it.id to it.label }, profile)
        val byName = FieldMatcher.plan(fields.map { it.id to it.id }, profile)
        val merged = LinkedHashMap<String, AutofillSuggestion>()
        (byLabel + byName).forEach { s -> merged.putIfAbsent(s.fieldId, s) }
        val list = merged.values.map { s -> s.copy(fieldLabel = fields.firstOrNull { it.id == s.fieldId }?.label ?: s.fieldLabel) }
        if (list.isEmpty()) message = "Automatisch ausfüllen" to "Für dieses Formular wurden keine passenden Profilangaben gefunden."
        else autofillPreview = profile to list
    }

    fun acceptAutofill(selected: List<AutofillSuggestion>) {
        autofillPreview = null
        selected.forEach { pendingValues[it.fieldId] = FieldValue.Text(it.value) }
        if (selected.isNotEmpty()) message = "Bitte prüfen" to "${selected.size} Werte wurden eingetragen. Prüfen Sie die Angaben und tippen Sie auf „Übernehmen“, um sie in das PDF zu schreiben."
    }

    /** Für Formulare ohne Felder: Beschriftungen (digital oder per OCR) erkennen. */
    fun detectFlatFormLabels(profile: PersonProfile) {
        viewModelScope.launch {
            busy = "Beschriftungen werden gesucht …"
            try {
                val count = source?.pageCount ?: 0
                val out = ArrayList<LabelSuggestion>()
                for (p in 0 until count) {
                    val pt = pageText(p)
                    val lines: List<Pair<String, Box>> = if (pt != null && pt.lines.isNotEmpty()) {
                        pt.lines.map { it.text to pt.viewBoxOf(it) }
                    } else {
                        val ocr = ocrPages[p] ?: run {
                            val f = current ?: return@launch
                            val bmp = withContext(Dispatchers.IO) { PageRaster.render(f, p, OcrEngine.OCR_SCALE, workDir) }
                            ocrEngine.recognize(p, bmp, OcrEngine.OCR_SCALE).also { bmp.recycle(); ocrPages[p] = it }
                        }
                        ocr.lines.map { it.text to it.viewBox }
                    }
                    FieldMatcher.findLabels(lines.map { it.first }).forEach { hit ->
                        val box = lines[hit.lineIndex].second
                        val value = FieldMatcher.valueFor(profile, hit.key)
                        if (value.isNotBlank()) {
                            val u = if (hit.startFraction >= 1f) box.right + 6f else box.left + box.width * hit.startFraction + 4f
                            out.add(LabelSuggestion(p, u, box.top, hit.label, value, (box.height * 0.75f).coerceIn(8f, 14f)))
                        }
                    }
                }
                if (out.isEmpty()) message = "Keine Beschriftungen" to "Es wurden keine passenden Beschriftungen gefunden. Tippen Sie auf eine Stelle im Dokument, um Text manuell einzufügen."
                else labelSuggestions = out
            } catch (e: Exception) {
                message = "Fehler" to (e.message ?: "")
            } finally {
                busy = null
            }
        }
    }

    fun applyLabelSuggestions(list: List<LabelSuggestion>) {
        labelSuggestions = null
        if (list.isEmpty()) return
        runEdit("Werte werden eingefügt …", op = { input, out ->
            PdfIo.transform(input, out) { d ->
                list.forEach { s ->
                    editEngine.addText(d, s.page, s.u, s.v + s.size * 0.95f, s.value, TextStyle(false, FontFamily.SANS, size = s.size))
                }
            }
            null
        })
    }

    // ---------- Lesen ----------
    private suspend fun findAll(term: String): List<SearchHit> {
        if (term.isBlank()) return emptyList()
        val count = source?.pageCount ?: return emptyList()
        val hits = ArrayList<SearchHit>()
        for (p in 0 until count) {
            val pt = pageText(p)
            if (pt != null) {
                for (line in pt.lines + pt.hiddenLines) {
                    var from = 0
                    while (true) {
                        val i = line.text.indexOf(term, from, ignoreCase = true)
                        if (i < 0) break
                        val glyphIdx = (i until i + term.length).mapNotNull { line.charToGlyph.getOrNull(it)?.takeIf { g -> g >= 0 } }
                        if (glyphIdx.isNotEmpty()) {
                            var b = pt.glyphs[glyphIdx.first()].bbox
                            glyphIdx.forEach { b = b.union(pt.glyphs[it].bbox) }
                            hits.add(SearchHit(p, pt.geometry.userBoxToView(b)))
                        }
                        from = i + term.length
                    }
                }
            }
            ocrPages[p]?.lines?.forEach { l ->
                l.words.filter { it.text.contains(term, ignoreCase = true) }.forEach { w -> hits.add(SearchHit(p, w.viewBox)) }
            }
        }
        return hits
    }

    fun search(term: String) {
        viewModelScope.launch {
            busy = "Suche …"
            try {
                searchHits = findAll(term)
                searchIndex = 0
                if (searchHits.isEmpty()) message = "Suche" to "„$term“ wurde nicht gefunden."
            } finally {
                busy = null
            }
        }
    }

    fun clearSearch() { searchHits = emptyList() }

    private suspend fun loadOutline() {
        val f = current ?: return
        outline = withContext(PdfIo.pdfDispatcher) {
            runCatching {
                PdfIo.load(f).use { d ->
                    val result = ArrayList<Pair<String, Int>>()
                    var item = d.documentCatalog.documentOutline?.firstChild
                    var guard = 0
                    while (item != null && guard++ < 2000) {
                        val cur = item
                        val page = runCatching { cur.findDestinationPage(d)?.let { d.pages.indexOf(it) } }.getOrNull() ?: -1
                        if (page >= 0) result.add((cur.title ?: "Abschnitt") to page)
                        item = cur.nextSibling
                    }
                    result
                }
            }.getOrDefault(emptyList())
        }
    }

    fun bookmarks() = c.bookmarks.observe(docId)
    fun addBookmark(page: Int) = viewModelScope.launch { c.bookmarks.add(docId, page, "Seite ${page + 1}") }
    fun deleteBookmark(id: Long) = viewModelScope.launch { c.bookmarks.delete(id) }

    // ---------- Sonstiges ----------
    fun rotatePage(page: Int) = runEdit("Seite wird gedreht …", op = { input, out ->
        PdfIo.transform(input, out) { d -> PdfOps.rotate(d, listOf(page), 90) }; null
    })

    fun signatures() = c.signatures.observeAll()
    suspend fun settings() = c.settings.current()

    override fun onCleared() {
        super.onCleared()
        runCatching { source?.close() }
        runCatching { analysisDoc?.close() }
        runCatching { ocrEngine.close() }
    }

    class Factory(private val app: Application, private val docId: Long, private val mode: WorkspaceMode) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkspaceViewModel(app, docId, mode) as T
    }
}
