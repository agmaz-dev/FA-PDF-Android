package de.fapdf.ui.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.fapdf.ai.AiBatchTranslator
import de.fapdf.ai.AiClient
import de.fapdf.ai.ChatMessage
import de.fapdf.ai.DocumentText
import de.fapdf.ai.Lang
import de.fapdf.ai.OfflineTranslator
import de.fapdf.ai.Prompts
import de.fapdf.data.AppSettings
import de.fapdf.data.DocumentEntity
import de.fapdf.data.DocumentRepository
import de.fapdf.pdf.LayoutTranslator
import de.fapdf.pdf.OcrEngine
import de.fapdf.pdf.PageRaster
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.TransUnit
import de.fapdf.pdf.TextPdfWriter
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.Tool
import de.fapdf.ui.components.BusyDialog
import de.fapdf.ui.components.DocChooserSheet
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.InfoBanner
import de.fapdf.ui.components.MessageDialog
import de.fapdf.ui.components.PrimaryButton
import de.fapdf.ui.components.ScreenScaffold
import de.fapdf.ui.components.SectionTitle
import de.fapdf.ui.components.file
import de.fapdf.ui.theme.FaColors
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.mutableStateMapOf
import de.fapdf.ai.LanguageDetector
import de.fapdf.ai.QuizParser
import de.fapdf.ai.QuizQuestion
import de.fapdf.ui.components.ChoiceBoxes
import de.fapdf.ui.components.DocCard
import de.fapdf.ui.components.FaButton
import de.fapdf.ui.components.FaDropdown
import de.fapdf.ui.components.FaSoftButton
import de.fapdf.ui.components.InfoCard
import de.fapdf.ui.components.PageNavigator
import de.fapdf.ui.components.PageThumb
import de.fapdf.ui.components.SectionLabel
import de.fapdf.ui.components.Stepper
import de.fapdf.ui.components.ToggleRow
import de.fapdf.ui.components.rememberDocSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AiScreen(tool: Tool, docId: Long?, appVm: AppViewModel, onBack: () -> Unit, onSettings: () -> Unit, onOpenDoc: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by appVm.documents.collectAsState()
    var currentId by remember { mutableStateOf(docId) }
    val doc = docs.firstOrNull { it.id == currentId }
    var choose by remember { mutableStateOf(doc == null) }
    var mode by remember { mutableStateOf(tool) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var extracted by remember { mutableStateOf<DocumentText.Result?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var consentFor by remember { mutableStateOf<(() -> Unit)?>(null) }
    var consentGiven by remember { mutableStateOf(false) }
    val settings by appVm.container.settings.settings.collectAsState(initial = AppSettings())
    val chat = remember { mutableStateListOf<ChatMessage>() }
    var from by remember { mutableStateOf<Lang?>(null) } // null = automatisch erkennen
    var keepLayout by remember { mutableStateOf(true) }
    var summaryKind by remember { mutableStateOf(Prompts.SummaryKind.DETAILED) }
    var summaryLang by remember { mutableStateOf(Lang.DE) }
    var qCount by remember { mutableStateOf(10) }
    var qMulti by remember { mutableStateOf(true) }
    var qDiff by remember { mutableStateOf(Prompts.Difficulty.MEDIUM) }
    var quiz by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var chatPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var to by remember { mutableStateOf(Lang.TR) }
    var offline by remember { mutableStateOf(true) }

    val needsCloud = mode != Tool.TRANSLATE || !offline

    fun extract(after: () -> Unit) {
        val d = doc ?: return
        if (extracted != null) { after(); return }
        scope.launch {
            busy = "Dokument wird auf dem Gerät gelesen …"
            try {
                val tmp = File(context.cacheDir, "ai").apply { mkdirs() }
                val r = DocumentText.extract(d.file(), tmp) { p, n -> busy = "Seite $p von $n wird gelesen …" }
                if (r.text.replace(Regex("\\[Seite \\d+]"), "").isBlank()) throw IllegalStateException("Im Dokument wurde kein Text gefunden.")
                extracted = r
                after()
            } catch (e: de.fapdf.pdf.PasswordRequiredException) {
                error = "Das Dokument ist passwortgeschützt. Entfernen Sie den Schutz zuerst mit „PDF-Passwort entfernen“."
            } catch (e: Exception) {
                error = e.message ?: "Fehler beim Lesen"
            } finally {
                busy = null
            }
        }
    }

    fun cloud(system: String, messages: List<ChatMessage>, onText: (String) -> Unit) {
        scope.launch {
            busy = "Anfrage an ${settings.aiProvider.title} …"
            try {
                val key = appVm.container.settings.apiKey() ?: throw IllegalStateException("Kein API-Schlüssel hinterlegt.")
                val s = appVm.container.settings.current()
                onText(AiClient(s.aiProvider, key, s.aiModel).complete(system, messages, maxTokens = 3000))
            } catch (e: Exception) {
                error = e.message ?: "Fehler bei der KI-Anfrage"
            } finally {
                busy = null
            }
        }
    }

    fun withConsent(action: () -> Unit) { if (consentGiven) action() else consentFor = action }

    var translatedId by remember { mutableStateOf<Long?>(null) }

    /** Übersetzt Absatz für Absatz und erzeugt eine PDF im Original-Layout. */
    suspend fun translateUnits(d: DocumentEntity, units: List<TransUnit>) {
        busy = "Übersetzung …"
        try {
            val texts = units.map { it.text }
            val src = from ?: run {
                busy = "Sprache wird erkannt …"
                LanguageDetector.detect(texts.joinToString(" ")) ?: throw IllegalStateException("Die Sprache des Dokuments konnte nicht erkannt werden. Bitte wählen Sie die Ausgangssprache aus.")
            }
            if (src == to) throw IllegalStateException("Das Dokument ist bereits auf ${to.title} (erkannt: ${src.title}).")
            busy = "Übersetzung …"
            val translated = if (offline) {
                OfflineTranslator(context).translateAll(texts, src, to) { p, n -> busy = "Übersetze Absatz $p von $n …" }
            } else {
                val key = appVm.container.settings.apiKey() ?: throw IllegalStateException("Kein API-Schlüssel hinterlegt.")
                val s = appVm.container.settings.current()
                AiBatchTranslator.translateAll(AiClient(s.aiProvider, key, s.aiModel), texts, to) { p, n -> busy = "KI-Übersetzung Teil $p von $n …" }
            }
            busy = "Übersetzte PDF wird erstellt …"
            val tmp = File(context.cacheDir, "ai").apply { mkdirs() }
            val out = File(context.cacheDir, "translated_${System.nanoTime()}.pdf")
            withContext(PdfIo.pdfDispatcher) {
                if (keepLayout) PdfIo.transform(d.file(), out) { pd -> LayoutTranslator.apply(pd, units.zip(translated), appVm.container.fonts, d.file(), tmp) }
                else TextPdfWriter.write("${DocumentRepository.baseName(d.displayName)} (${to.title})", translated.joinToString("\n\n"), appVm.container.fonts, out)
            }
            val id = appVm.container.documents.importFile(out, "${DocumentRepository.baseName(d.displayName)} (${to.title}).pdf")
            out.delete()
            translatedId = id
            result = translated.joinToString("\n\n")
        } catch (e: Exception) {
            error = "Übersetzung fehlgeschlagen: ${e.message}" + if (offline && e !is IllegalStateException) "\nBeim ersten Mal muss das Sprachmodell (ca. 30 MB) einmalig aus dem Internet geladen werden." else ""
        } finally {
            busy = null
        }
    }

    fun translateLayout() {
        val d = doc ?: return
        translatedId = null; result = null
        scope.launch {
            busy = "Dokument wird analysiert …"
            try {
                val file = d.file()
                val tmp = File(context.cacheDir, "ai").apply { mkdirs() }
                val (digital, scannedPages) = withContext(PdfIo.pdfDispatcher) { PdfIo.load(file).use { LayoutTranslator.collect(it) } }
                val units = ArrayList(digital)
                if (scannedPages.isNotEmpty()) {
                    val ocr = OcrEngine()
                    try {
                        scannedPages.forEachIndexed { k, p ->
                            busy = "Texterkennung Seite ${p + 1} (${k + 1}/${scannedPages.size}) …"
                            val bmp = withContext(Dispatchers.IO) { PageRaster.render(file, p, OcrEngine.OCR_SCALE, tmp) }
                            val res = ocr.recognize(p, bmp, OcrEngine.OCR_SCALE)
                            units.addAll(LayoutTranslator.ocrUnits(p, res, bmp, OcrEngine.OCR_SCALE))
                            bmp.recycle()
                        }
                    } finally {
                        ocr.close()
                    }
                }
                if (units.isEmpty()) throw IllegalStateException("Im Dokument wurde kein übersetzbarer Text gefunden.")
                extracted = DocumentText.Result(units.joinToString("\n\n") { it.text }, d.pageCount, scannedPages.size, false)
                busy = null
                val proceed: () -> Unit = { scope.launch { translateUnits(d, units) } }
                if (offline) proceed() else withConsent(proceed)
            } catch (e: de.fapdf.pdf.PasswordRequiredException) {
                busy = null
                error = "Das Dokument ist passwortgeschützt. Entfernen Sie den Schutz zuerst mit „PDF-Passwort entfernen“."
            } catch (e: Exception) {
                busy = null
                error = e.message ?: "Fehler beim Lesen des Dokuments"
            }
        }
    }

    fun runTask() {
        if (mode == Tool.TRANSLATE) { translateLayout(); return }
        extract {
            val text = extracted?.text ?: return@extract
            when (mode) {
                Tool.AI_SUMMARY -> withConsent {
                    cloud(Prompts.withDocument(Prompts.summary(summaryKind, summaryLang), text), listOf(ChatMessage("user", "Bitte fasse das Dokument zusammen."))) { result = it }
                }
                Tool.AI_QUESTIONS -> withConsent {
                    cloud(Prompts.withDocument(Prompts.questions(qCount, qMulti, qDiff), text), listOf(ChatMessage("user", "Bitte erstelle die Fragen als JSON."))) { raw ->
                        val parsed = QuizParser.parse(raw)
                        quiz = parsed
                        result = if (parsed.isEmpty()) raw else parsed.mapIndexed { i, q ->
                            "Frage ${i + 1}: ${q.question}\n" + q.options.mapIndexed { k, o -> "  ${'A' + k}) $o" }.joinToString("\n").let { if (it.isEmpty()) "" else it + "\n" } +
                                "Antwort: ${q.answer}" + (q.page?.let { " (Seite $it)" } ?: "")
                        }.joinToString("\n\n")
                    }
                }
                else -> {}
            }
        }
    }

    fun ask(q: String) {
        extract {
            val text = extracted?.text ?: return@extract
            withConsent {
                chat.add(ChatMessage("user", q))
                val scoped = LanguageDetector.pages(text, chatPages).ifBlank { text }
                cloud(Prompts.withDocument(Prompts.CHAT, scoped), chat.toList()) { chat.add(ChatMessage("assistant", it)) }
            }
        }
    }

    ScreenScaffold(mode.title, onBack) { mod ->
        Column(mod.fillMaxSize().imePadding()) {
            if (doc != null) DocCard(doc, subtitle = docSubtitle(doc), onChange = { choose = true })
            else FaSoftButton("Dokument auswählen") { choose = true }
            if (needsCloud && !settings.hasAiKey) {
                InfoCard("Für diese Funktion ist ein eigener API-Schlüssel eines KI-Anbieters nötig (kostenpflichtig, Abrechnung direkt beim Anbieter). Tippen Sie hier, um ihn einzurichten.",
                    Modifier.clickable(onClick = onSettings), color = FaColors.Ai)
            }
            extracted?.let { r ->
                Text(
                    "${r.pages} Seiten gelesen" + (if (r.ocrPages > 0) ", davon ${r.ocrPages} per Texterkennung" else "") +
                        " · ${r.text.length} Zeichen" + (if (r.truncated) " · Dokument gekürzt (zu lang)" else ""),
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
                )
            }
            if (mode == Tool.AI_CHAT) {
                if (doc != null && doc.pageCount > 1) PageChips(doc.pageCount, chatPages) { chatPages = it }
                ChatView(chat, Modifier.weight(1f), enabled = doc != null && settings.hasAiKey) { ask(it) }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (mode) {
                        Tool.TRANSLATE -> {
                            SectionLabel("Ausgangssprache")
                            FaDropdown(from, listOf<Lang?>(null) + Lang.entries, { it?.title ?: "Automatisch (erkennen)" }, Modifier.padding(horizontal = 16.dp)) { from = it }
                            SectionLabel("Zielsprache")
                            FaDropdown(to, Lang.entries.toList(), { it.title }, Modifier.padding(horizontal = 16.dp)) { to = it }
                            Spacer(Modifier.height(8.dp))
                            ToggleRow("Layout beibehalten", "Übersetzung an derselben Stelle wie das Original", checked = keepLayout) { keepLayout = it }
                            SectionLabel("Verfahren")
                            ChoiceBoxes(listOf("Auf dem Gerät", "KI (Cloud)"), if (offline) 0 else 1, subtitles = listOf("Kostenlos, Dokument bleibt auf dem Gerät", "Eigener API-Schlüssel nötig")) { offline = it == 0 }
                        }
                        Tool.AI_SUMMARY -> {
                            SectionLabel("Art der Zusammenfassung")
                            ChoiceBoxes(Prompts.SummaryKind.entries.map { it.title }, summaryKind.ordinal) { summaryKind = Prompts.SummaryKind.entries[it] }
                            SectionLabel("Sprache")
                            FaDropdown(summaryLang, Lang.entries.toList(), { it.title }, Modifier.padding(horizontal = 16.dp)) { summaryLang = it }
                        }
                        else -> {
                            SectionLabel("Anzahl der Fragen", trailing = { Stepper(qCount, 3..20) { qCount = it } })
                            SectionLabel("Fragetyp")
                            ChoiceBoxes(listOf("Multiple Choice", "Offen"), if (qMulti) 0 else 1) { qMulti = it == 0 }
                            SectionLabel("Schwierigkeit")
                            FaDropdown(qDiff, Prompts.Difficulty.entries.toList(), { it.title }, Modifier.padding(horizontal = 16.dp)) { qDiff = it }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FaButton(
                        when (mode) { Tool.TRANSLATE -> "Übersetzen"; Tool.AI_SUMMARY -> "Zusammenfassen"; else -> "Fragen erstellen" },
                        icon = if (mode == Tool.TRANSLATE) Icons.Outlined.Translate else Icons.Outlined.AutoAwesome,
                        enabled = doc != null && (!needsCloud || settings.hasAiKey) && (mode != Tool.TRANSLATE || from != to),
                    ) { quiz = emptyList(); runTask() }
                    if (mode == Tool.TRANSLATE && offline) Text(
                        "Jeder Absatz wird übersetzt und an derselben Stelle eingesetzt – Layout, Schriftfarbe und Ausrichtung bleiben erhalten. Für rechtsverbindliche Übersetzungen nicht geeignet.",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
                    )
                    val tid = translatedId
                    val tdoc = docs.firstOrNull { it.id == tid }
                    if (mode == Tool.TRANSLATE && doc != null && tdoc != null) {
                        SectionLabel("Vorschau")
                        SideBySide(doc, tdoc)
                        FaButton("Übersetzte PDF öffnen") { onOpenDoc(tdoc.id) }
                    }
                    if (mode == Tool.AI_QUESTIONS && quiz.isNotEmpty()) {
                        SectionLabel("Fragen")
                        QuizCards(quiz)
                    }
                    result?.let { r ->
                        if (mode == Tool.AI_SUMMARY || (mode == Tool.AI_QUESTIONS && quiz.isEmpty())) {
                            SectionLabel("Ergebnis")
                            SelectionContainer {
                                Text(
                                    r, Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .background(FaColors.Card).border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp)).padding(14.dp),
                                )
                            }
                        }
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                            FaSoftButton("Text teilen", Modifier.weight(1f)) { FileActions.shareText(context, r, mode.title) }
                            if (mode != Tool.TRANSLATE) FaSoftButton("Als PDF speichern", Modifier.weight(1f)) {
                                val d = doc ?: return@FaSoftButton
                                scope.launch {
                                    busy = "PDF wird erstellt …"
                                    try {
                                        val out = File(context.cacheDir, "ai_${System.nanoTime()}.pdf")
                                        val title = if (mode == Tool.AI_SUMMARY) "Zusammenfassung: ${d.displayName}" else "Fragen zu: ${d.displayName}"
                                        withContext(PdfIo.pdfDispatcher) { TextPdfWriter.write(title, r, appVm.container.fonts, out) }
                                        val suffix = if (mode == Tool.AI_SUMMARY) "Zusammenfassung" else "Fragen"
                                        val id = appVm.container.documents.importFile(out, "${DocumentRepository.baseName(d.displayName)} ($suffix).pdf")
                                        out.delete()
                                        onOpenDoc(id)
                                    } catch (e: Exception) { error = e.message } finally { busy = null }
                                }
                            }
                        }
                        if (mode == Tool.AI_SUMMARY) FaSoftButton("Fragen zum Dokument stellen") { mode = Tool.AI_CHAT }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    busy?.let { BusyDialog(it) }
    error?.let { MessageDialog("Hinweis", it) { error = null } }
    consentFor?.let { action ->
        val chars = extracted?.text?.length ?: 0
        AlertDialog(
            onDismissRequest = { consentFor = null },
            title = { Text("Daten an KI-Dienst senden?") },
            text = {
                Column {
                    Text("Für diese Funktion wird der Text des Dokuments an ${settings.aiProvider.title} (${settings.aiProvider.endpointHost}) übertragen und dort verarbeitet.")
                    Spacer(Modifier.height(8.dp))
                    Text("Umfang: ca. $chars Zeichen ≈ ${AiClient.estimateTokens(chars)} Tokens Eingabe pro Anfrage (beim Chat bei jeder Frage erneut), Modell „${settings.aiModel}“.")
                    Spacer(Modifier.height(8.dp))
                    Text("Kosten: Es gelten die aktuellen Preise des Anbieters für Ihren API-Schlüssel. Bitte senden Sie keine Dokumente, deren Weitergabe nicht erlaubt ist.", color = FaColors.SecondaryLabel)
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.aiProvider.pricingUrl))) }) { Text("Preise ansehen") }
                }
            },
            confirmButton = { TextButton(onClick = { consentGiven = true; consentFor = null; action() }) { Text("Zustimmen und senden") } },
            dismissButton = { TextButton(onClick = { consentFor = null }) { Text("Abbrechen") } },
        )
    }
    if (choose) DocChooserSheet(
        documents = docs, onImport = { uri -> choose = false; appVm.import(uri, persist = false) { currentId = it; extracted = null; result = null; chat.clear(); consentGiven = false } },
        onDismiss = { choose = false },
    ) { d: DocumentEntity -> choose = false; currentId = d.id; extracted = null; result = null; chat.clear(); consentGiven = false }
}

private fun docSubtitle(d: DocumentEntity) =
    (if (d.pageCount > 0) "${d.pageCount} Seiten · " else "") + de.fapdf.ui.components.Format.size(d.sizeBytes)

@Composable
private fun PageChips(count: Int, selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected.isEmpty(), { onChange(emptySet()) }, { Text("Alle Seiten") }) }
        items((1..count).toList()) { p ->
            FilterChip(p in selected, { onChange(if (p in selected) selected - p else selected + p) }, { Text("Seite $p") })
        }
    }
}

@Composable
private fun SideBySide(original: DocumentEntity, translated: DocumentEntity) {
    val a = rememberDocSource(original, null)
    val b = rememberDocSource(translated, null)
    var page by remember(translated.id) { mutableStateOf(0) }
    val count = maxOf(1, minOf(a?.pageCount ?: 1, b?.pageCount ?: 1))
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Original" to a, "Übersetzung" to b).forEach { (label, src) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, FaColors.CardBorder, RoundedCornerShape(10.dp))) {
                    if (src != null) PageThumb(src, page.coerceAtMost(src.pageCount - 1), Modifier.fillMaxWidth(), renderWidth = 600)
                }
            }
        }
    }
    if (count > 1) PageNavigator(page, count) { page = it }
}

@Composable
private fun QuizCards(quiz: List<QuizQuestion>) {
    var i by remember(quiz) { mutableStateOf(0) }
    val reveal = remember(quiz) { mutableStateListOf<Int>() }
    val chosen = remember(quiz) { mutableStateMapOf<Int, Int>() }
    val q = quiz[i.coerceIn(0, quiz.size - 1)]
    val shown = i in reveal
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FaColors.Card)
            .border(1.dp, FaColors.CardBorder, RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Text("Frage ${i + 1}", style = MaterialTheme.typography.labelMedium, color = FaColors.Ai)
        Text(q.question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
        q.options.forEachIndexed { k, o ->
            val picked = chosen[i] == k
            val color = when {
                shown && k == q.correct -> Color(0xFF0E8F80)
                shown && picked -> FaColors.Destructive
                picked -> FaColors.Accent
                else -> FaColors.CardBorder
            }
            Row(
                Modifier.padding(vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .border(1.dp, color, RoundedCornerShape(10.dp)).clickable { chosen[i] = k }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${'A' + k}", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(end = 10.dp))
                Text(o, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (shown) Text(
            "Antwort: ${q.answer}" + (q.page?.let { " (Seite $it)" } ?: ""),
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FaColors.AccentLight).padding(10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (shown) reveal.remove(i) else reveal.add(i) }) { Text(if (shown) "Antwort ausblenden" else "Antwort anzeigen") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { i-- }, enabled = i > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Zurück") }
            Text("${i + 1}/${quiz.size}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { i++ }, enabled = i < quiz.size - 1) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Weiter") }
        }
    }
}

private val suggestions = listOf(
    "Worum geht es in diesem Dokument?", "Welche Fristen gibt es?", "Welche Beträge werden genannt?",
    "Wer ist der Absender?", "Was muss ich als Nächstes tun?",
)

@Composable
private fun ChatView(messages: List<ChatMessage>, modifier: Modifier, enabled: Boolean, onSend: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Stellen Sie eine Frage zum Dokument. Die Antwort beruht ausschließlich auf dem Dokumentinhalt (bzw. den gewählten Seiten).",
                        Modifier.padding(8.dp), color = FaColors.SecondaryLabel, style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Vorschläge", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                items(suggestions) { q ->
                    Text(
                        q, color = if (enabled) FaColors.Accent else FaColors.SecondaryLabel, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(FaColors.AccentLight).clickable(enabled = enabled) { onSend(q) }.padding(12.dp),
                    )
                }
            }
            items(messages) { m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    SelectionContainer {
                        Text(
                            m.content, color = if (mine) Color.White else FaColors.Label,
                            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (mine) FaColors.Accent else FaColors.Card).padding(12.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f), placeholder = { Text("Frage zum Dokument …") }, enabled = enabled, maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            IconButton(onClick = { val q = input.trim(); if (q.isNotEmpty()) { onSend(q); input = "" } }, enabled = enabled && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Senden", tint = FaColors.Accent)
            }
        }
    }
}
