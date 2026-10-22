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
import de.fapdf.ui.tools.DocHeader
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
    var from by remember { mutableStateOf(Lang.DE) }
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
            val translated = if (offline) {
                OfflineTranslator(context).translateAll(texts, from, to) { p, n -> busy = "Übersetze Absatz $p von $n …" }
            } else {
                val key = appVm.container.settings.apiKey() ?: throw IllegalStateException("Kein API-Schlüssel hinterlegt.")
                val s = appVm.container.settings.current()
                AiBatchTranslator.translateAll(AiClient(s.aiProvider, key, s.aiModel), texts, to) { p, n -> busy = "KI-Übersetzung Teil $p von $n …" }
            }
            busy = "Übersetzte PDF wird erstellt …"
            val tmp = File(context.cacheDir, "ai").apply { mkdirs() }
            val out = File(context.cacheDir, "translated_${System.nanoTime()}.pdf")
            withContext(PdfIo.pdfDispatcher) {
                PdfIo.transform(d.file(), out) { pd -> LayoutTranslator.apply(pd, units.zip(translated), appVm.container.fonts, d.file(), tmp) }
            }
            val id = appVm.container.documents.importFile(out, "${DocumentRepository.baseName(d.displayName)} (${to.title}).pdf")
            out.delete()
            translatedId = id
            result = translated.joinToString("\n\n")
        } catch (e: Exception) {
            error = "Übersetzung fehlgeschlagen: ${e.message}" + if (offline) "\nBeim ersten Mal muss das Sprachmodell (ca. 30 MB) einmalig aus dem Internet geladen werden." else ""
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
                Tool.AI_SUMMARY -> withConsent { cloud(Prompts.withDocument(Prompts.SUMMARY, text), listOf(ChatMessage("user", "Bitte fasse das Dokument zusammen."))) { result = it } }
                Tool.AI_QUESTIONS -> withConsent { cloud(Prompts.withDocument(Prompts.QUESTIONS, text), listOf(ChatMessage("user", "Bitte erstelle die Fragen."))) { result = it } }
                else -> {}
            }
        }
    }

    fun ask(q: String) {
        extract {
            val text = extracted?.text ?: return@extract
            withConsent {
                chat.add(ChatMessage("user", q))
                cloud(Prompts.withDocument(Prompts.CHAT, text), chat.toList()) { chat.add(ChatMessage("assistant", it)) }
            }
        }
    }

    ScreenScaffold(mode.title, onBack) { mod ->
        Column(mod.fillMaxSize().imePadding()) {
            DocHeader(doc) { choose = true }
            if (needsCloud && !settings.hasAiKey) {
                InfoBanner("Für diese Funktion ist ein eigener API-Schlüssel eines KI-Anbieters nötig (kostenpflichtig, Abrechnung direkt beim Anbieter).", FaColors.Ai, "Einrichten" to onSettings)
            }
            extracted?.let { r ->
                Text(
                    "${r.pages} Seiten gelesen" + (if (r.ocrPages > 0) ", davon ${r.ocrPages} per Texterkennung" else "") +
                        " · ${r.text.length} Zeichen" + (if (r.truncated) " · Dokument gekürzt (zu lang)" else ""),
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
                )
            }
            if (mode == Tool.AI_CHAT) {
                ChatView(chat, Modifier.weight(1f), enabled = doc != null && settings.hasAiKey) { ask(it) }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (mode == Tool.TRANSLATE) {
                        SectionTitle("Von")
                        LangRow(from) { from = it }
                        SectionTitle("Nach")
                        LangRow(to) { to = it }
                        SectionTitle("Verfahren")
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(offline, { offline = true }, { Text("Auf dem Gerät (kostenlos)") })
                            FilterChip(!offline, { offline = false }, { Text("KI (Cloud)") })
                        }
                        if (offline) Text(
                            "Jeder Absatz wird übersetzt und an derselben Stelle der Seite eingesetzt – Layout, Schriftfarbe und Ausrichtung bleiben erhalten; längere Übersetzungen werden etwas kleiner gesetzt. Das Dokument verlässt das Gerät nicht. Für rechtsverbindliche Übersetzungen nicht geeignet.",
                            Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
                        )
                    }
                    PrimaryButton(
                        when (mode) { Tool.TRANSLATE -> "Übersetzen"; Tool.AI_SUMMARY -> "Zusammenfassen"; else -> "Fragen erstellen" },
                        Modifier.padding(16.dp), enabled = doc != null && (!needsCloud || settings.hasAiKey) && (mode != Tool.TRANSLATE || from != to),
                    ) { runTask() }
                    result?.let { r ->
                        SectionTitle("Ergebnis")
                        SelectionContainer {
                            Text(r, Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp))
                        }
                        val tid = translatedId
                        if (mode == Tool.TRANSLATE && tid != null) {
                            PrimaryButton("Übersetzte PDF öffnen (Layout erhalten)", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { onOpenDoc(tid) }
                        }
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { FileActions.shareText(context, r, mode.title) }) { Text("Text teilen") }
                            if (mode != Tool.TRANSLATE) OutlinedButton(onClick = {
                                val d = doc ?: return@OutlinedButton
                                scope.launch {
                                    busy = "PDF wird erstellt …"
                                    try {
                                        val out = File(context.cacheDir, "ai_${System.nanoTime()}.pdf")
                                        val title = when (mode) {
                                            Tool.TRANSLATE -> "Übersetzung (${to.title}): ${d.displayName}"
                                            Tool.AI_SUMMARY -> "Zusammenfassung: ${d.displayName}"
                                            else -> "Fragen zu: ${d.displayName}"
                                        }
                                        withContext(PdfIo.pdfDispatcher) { TextPdfWriter.write(title, r, appVm.container.fonts, out) }
                                        val suffix = when (mode) { Tool.TRANSLATE -> to.title; Tool.AI_SUMMARY -> "Zusammenfassung"; else -> "Fragen" }
                                        val id = appVm.container.documents.importFile(out, "${DocumentRepository.baseName(d.displayName)} ($suffix).pdf")
                                        out.delete()
                                        onOpenDoc(id)
                                    } catch (e: Exception) { error = e.message } finally { busy = null }
                                }
                            }) { Text("Als PDF speichern") }
                            if (mode == Tool.AI_SUMMARY) OutlinedButton(onClick = { mode = Tool.AI_CHAT }) { Text("Fragen stellen") }
                        }
                    }
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

@Composable
private fun LangRow(selected: Lang, onSelect: (Lang) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.entries.forEach { l -> FilterChip(selected == l, { onSelect(l) }, { Text(l.title) }) }
    }
}

@Composable
private fun ChatView(messages: List<ChatMessage>, modifier: Modifier, enabled: Boolean, onSend: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState) {
            if (messages.isEmpty()) item {
                Text(
                    "Stellen Sie eine Frage zum Dokument, z. B. „Welche Frist steht in diesem Schreiben?“ oder „Welcher Betrag ist zu zahlen?“. " +
                        "Die Antwort beruht ausschließlich auf dem Dokumentinhalt.",
                    Modifier.padding(8.dp), color = FaColors.SecondaryLabel,
                )
            }
            items(messages) { m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    SelectionContainer {
                        Text(
                            m.content, color = if (mine) Color.White else FaColors.Label,
                            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (mine) FaColors.Accent else Color.White).padding(12.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Frage zum Dokument …") }, enabled = enabled, maxLines = 4)
            IconButton(onClick = { val q = input.trim(); if (q.isNotEmpty()) { onSend(q); input = "" } }, enabled = enabled && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Senden", tint = FaColors.Accent)
            }
        }
    }
}
