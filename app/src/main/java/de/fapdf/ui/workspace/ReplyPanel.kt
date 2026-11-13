package de.fapdf.ui.workspace

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fapdf.FaPdfApp
import de.fapdf.ai.AiRouter
import de.fapdf.ai.ChatMessage
import de.fapdf.ai.DocumentText
import de.fapdf.ai.Prompts
import de.fapdf.data.DocumentRepository
import de.fapdf.data.PersonProfile
import de.fapdf.data.SignatureEntity
import de.fapdf.pdf.LetterPdfWriter
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** KI-Antwortschreiben zu einem geöffneten Dokument. Überlebt das Auf-/Zuklappen (ViewModel). */
class ReplyViewModel(app: Application, private val docId: Long) : AndroidViewModel(app) {
    private val c = (app as FaPdfApp).container

    var busy by mutableStateOf<String?>(null)
    // Fortschritt für die bunte Anzeige: Schritt X von Y, Teilfortschritt innerhalb des Schritts
    var step by mutableStateOf(1)
    var steps by mutableStateOf(1)
    var sub by mutableStateOf<Float?>(null)
    var stepTitle by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var note by mutableStateOf<String?>(null)
    var docText: String? = null
    var docName by mutableStateOf("")

    // Ergebnis der Analyse
    var summary by mutableStateOf<String?>(null)
    var deadlines by mutableStateOf("")
    var amounts by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var email by mutableStateOf("")

    // Brief
    var stage by mutableStateOf(0) // 0 = Übersicht, 1 = Brief
    var goal by mutableStateOf("")
    var notes by mutableStateOf("")
    var sender by mutableStateOf("")
    var signerName by mutableStateOf("")
    var recipient by mutableStateOf("")
    var placeDate by mutableStateOf("")
    var subject by mutableStateOf("")
    var body by mutableStateOf("")
    var profiles by mutableStateOf<List<PersonProfile>>(emptyList())
    var signatures by mutableStateOf<List<SignatureEntity>>(emptyList())
    var signatureId by mutableStateOf<Long?>(null)
    var createdDocId by mutableStateOf<Long?>(null)
    var createdFile: File? = null
    private var bezug = ""
    private var contact = ""

    fun start(file: File) {
        if (summary != null || busy != null) return
        viewModelScope.launch {
            error = null
            try {
                docName = c.documents.get(docId)?.displayName?.let { DocumentRepository.baseName(it) } ?: ""
                profiles = runCatching { c.profiles.loadAll() }.getOrDefault(emptyList())
                profiles.firstOrNull()?.let { useProfile(it) }
                steps = 2; step = 1; sub = 0f; stepTitle = "Schreiben wird gelesen …"
                busy = "Text wird erkannt"
                val tmp = File(getApplication<Application>().cacheDir, "reply").apply { mkdirs() }
                val text = DocumentText.extract(file, tmp, maxChars = 40_000) { p, n -> busy = "Seite $p von $n wird gelesen"; sub = (p - 1f) / n }.text
                docText = text
                if (text.replace(Regex("\\[Seite \\d+]"), "").isBlank()) throw IllegalStateException("Im Dokument wurde kein Text gefunden.")
                step = 2; sub = null; stepTitle = "KI liest das Schreiben …"
                busy = "Fristen, Beträge und Antworten suchen"
                val ans = AiRouter.complete(c.settings, ANALYZE, listOf(ChatMessage("user", Prompts.withDocument("Analysiere dieses Schreiben.", text))), 1200)
                if (ans.fallback) note = "Groq nicht erreichbar – Gemini Nano hat geantwortet"
                parseAnalysis(ans.text)
            } catch (e: Exception) {
                error = e.message ?: "Fehler beim Lesen"
            } finally {
                busy = null
            }
        }
    }

    private fun parseAnalysis(raw: String) {
        val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
        val o = if (s >= 0 && e > s) runCatching { JSONObject(raw.substring(s, e + 1)) }.getOrNull() else null
        if (o == null) {
            summary = raw.trim().take(800)
            suggestions = DEFAULT_SUGGESTIONS
            return
        }
        summary = o.optString("kurzfassung").trim().ifBlank { "–" }
        deadlines = o.optString("fristen").trim().takeUnless { it.equals("leer", true) || it == "-" }.orEmpty()
        amounts = o.optString("betraege").trim().takeUnless { it.equals("leer", true) || it == "-" }.orEmpty()
        email = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+").find(o.optString("email"))?.value.orEmpty()
        bezug = o.optString("bezug").trim()
        contact = o.optString("ansprechpartner").trim()
        val abs = o.optString("absender").replace("\\n", "\n").trim()
        if (recipient.isBlank()) recipient = abs
        val arr = o.optJSONArray("vorschlaege")
        suggestions = (0 until (arr?.length() ?: 0)).map { arr!!.optString(it).trim() }.filter { it.isNotEmpty() }.take(5)
            .ifEmpty { DEFAULT_SUGGESTIONS }
    }

    fun useProfile(p: PersonProfile) {
        signerName = p.fullName
        sender = listOf(p.fullName, p.streetLine, p.zipCity, p.phone.takeIf { it.isNotBlank() }?.let { "Tel. $it" }, p.email)
            .filter { !it.isNullOrBlank() }.joinToString("\n")
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
        placeDate = listOf(p.city, today).filter { it.isNotBlank() }.joinToString(", ")
    }

    fun loadSignatures(list: List<SignatureEntity>) {
        signatures = list
        if (signatureId == null) signatureId = list.firstOrNull()?.id
    }

    fun write() {
        val text = docText ?: return
        viewModelScope.launch {
            error = null; note = null
            steps = 1; step = 1; sub = null; stepTitle = "KI schreibt den Brief …"
            busy = "Anrede, Text und Gruß"
            try {
                if (placeDate.isBlank()) placeDate = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
                val name = signerName.ifBlank { "dem Absender" }
                val system = "Du schreibst im Namen von $name einen Antwortbrief auf das Schreiben im <dokument>. " +
                    "Ziel des Briefes: ${goal.ifBlank { "passend antworten" }}. " +
                    (if (notes.isNotBlank()) "Zusätzliche Angaben von $name: $notes. " else "") +
                    (if (bezug.isNotBlank()) "Bezug (Nummern, Datum): $bezug. " else "") +
                    (if (contact.isNotBlank()) "Ansprechpartner: $contact. " else "") +
                    "Schreibe sachlich, höflich und klar im deutschen Geschäftsbriefstil (DIN 5008). Gib GENAU dieses Format aus:\n" +
                    "Betreff: <kurzer Betreff mit Bezug, z. B. Nummern>\n\n<Anrede>,\n\n<Text in kurzen Absätzen>\n\nMit freundlichen Grüßen\n" +
                    "Keine Adressen, kein Datum, keinen Namen unter dem Gruß, keine Erklärungen. Verwende nur Fakten aus dem Schreiben und " +
                    "den Angaben; fehlende Angaben in [eckigen Klammern] kennzeichnen. Handschriftliche Notizen im Schreiben sind Angaben von $name."
                val ans = AiRouter.complete(c.settings, system, listOf(ChatMessage("user", Prompts.withDocument("Schreibe jetzt den Antwortbrief.", text))), 1500)
                if (ans.fallback) note = "Groq nicht erreichbar – Entwurf von Gemini Nano, bitte genau prüfen"
                applyLetter(ans.text)
                stage = 1
                createdDocId = null
            } catch (e: Exception) {
                error = e.message ?: "Fehler beim Schreiben"
            } finally {
                busy = null
            }
        }
    }

    private fun applyLetter(raw: String) {
        val lines = raw.trim().removePrefix("```").removeSuffix("```").trim().lines()
        val subjIdx = lines.indexOfFirst { it.trim().startsWith("Betreff", true) }
        if (subjIdx >= 0) {
            subject = lines[subjIdx].substringAfter(":").trim().trim('*')
            body = lines.drop(subjIdx + 1).joinToString("\n").trim()
        } else {
            subject = "Ihr Schreiben" + if (bezug.isNotBlank()) " – $bezug" else ""
            body = raw.trim()
        }
        // Name unter dem Gruß setzt die App selbst
        if (signerName.isNotBlank()) body = body.lines().dropLastWhile { it.isBlank() || it.trim() == signerName.trim() }.joinToString("\n")
    }

    fun restyle(kind: String) {
        val current = body
        if (current.isBlank()) return
        viewModelScope.launch {
            error = null
            steps = 1; step = 1; sub = null; stepTitle = "Brief wird überarbeitet …"
            busy = "Ton und Länge anpassen"
            try {
                val instr = when (kind) {
                    "höflich" -> "freundlicher und höflicher formulieren"
                    "bestimmt" -> "bestimmter und nachdrücklicher formulieren, aber sachlich bleiben"
                    else -> "deutlich kürzer fassen, nur das Wesentliche"
                }
                val system = "Überarbeite den folgenden Brieftext: $instr. Behalte alle Fakten, Nummern und Daten sowie das Format " +
                    "(Anrede … Mit freundlichen Grüßen) bei. Gib nur den überarbeiteten Brieftext aus, ohne Betreff und ohne Erklärungen."
                val ans = AiRouter.complete(c.settings, system, listOf(ChatMessage("user", current)), 1500)
                if (ans.fallback) note = "Groq nicht erreichbar – überarbeitet von Gemini Nano"
                body = ans.text.trim().removePrefix("```").removeSuffix("```").trim()
                createdDocId = null
            } catch (e: Exception) {
                error = e.message ?: "Fehler beim Überarbeiten"
            } finally {
                busy = null
            }
        }
    }

    fun createPdf() {
        viewModelScope.launch {
            error = null
            steps = 1; step = 1; sub = null; stepTitle = "PDF wird erstellt …"
            busy = "Briefbogen, Text und Unterschrift"
            try {
                val sig: Bitmap? = signatures.firstOrNull { it.id == signatureId }?.let { c.signatures.loadBitmap(it) }
                val name = "Antwort – ${docName.ifBlank { "Schreiben" }}.pdf"
                val out = File(getApplication<Application>().cacheDir, "antwort_${System.nanoTime()}.pdf")
                withContext(Dispatchers.IO) {
                    LetterPdfWriter.write(
                        LetterPdfWriter.Letter(sender, recipient, placeDate, subject, body, signerName),
                        sig, c.fonts, out,
                    )
                }
                val id = c.documents.importFile(out, DocumentRepository.sanitize(name))
                createdDocId = id
                createdFile = c.documents.get(id)?.let { c.documents.fileOf(it) } ?: out
                note = "Gespeichert als „$name“"
            } catch (e: Exception) {
                error = e.message ?: "PDF konnte nicht erstellt werden"
            } finally {
                busy = null
            }
        }
    }

    class Factory(private val app: Application, private val docId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReplyViewModel(app, docId) as T
    }

    companion object {
        val DEFAULT_SUGGESTIONS = listOf("Zustimmen und bestätigen", "Widersprechen", "Um Fristverlängerung bitten", "Rückfrage stellen")

        const val ANALYZE = "Du liest ein Schreiben, das der Nutzer erhalten hat. Antworte AUSSCHLIESSLICH mit JSON ohne weiteren Text:\n" +
            "{\"kurzfassung\":\"2–3 Sätze: wer schreibt, worum geht es, was wird verlangt\"," +
            "\"fristen\":\"Fristen und Termine, sonst leer\",\"betraege\":\"Beträge, sonst leer\"," +
            "\"absender\":\"vollständige Postanschrift des Absenders dieses Schreibens, Zeilen mit \\\\n getrennt (Firma/Abteilung, Straße Nr., PLZ Ort)\"," +
            "\"email\":\"E-Mail-Adresse des Ansprechpartners, sonst leer\"," +
            "\"bezug\":\"Aktenzeichen, Kunden-, Vertrags-, Schaden- oder Betriebsnummer und Datum des Schreibens, kurz\"," +
            "\"ansprechpartner\":\"Name, sonst leer\"," +
            "\"vorschlaege\":[\"3 bis 5 kurze, konkrete Antwortmöglichkeiten aus Sicht des Empfängers, je höchstens 12 Wörter\"]}\n" +
            "Handschriftliche Notizen im Schreiben (z. B. „Bezahlt am …“) sind Wissen des Empfängers und sollen in die Vorschläge einfließen. " +
            "Antworte auf Deutsch. Erfinde nichts."
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyPanel(
    docId: Long,
    file: File?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onManageProfiles: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: ReplyViewModel = viewModel(key = "reply_$docId", factory = ReplyViewModel.Factory(app, docId))
    val container = (app as FaPdfApp).container
    val consentPrefs = remember { context.getSharedPreferences("ai_consent", android.content.Context.MODE_PRIVATE) }
    var consentFor by remember { mutableStateOf<(() -> Unit)?>(null) }
    var providerTitle by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Einmalige Zustimmung pro Internet-Dienst – wie im KI-Bereich
    fun withConsent(action: () -> Unit) {
        scope.launch {
            val p = container.settings.current().aiProvider
            providerTitle = p.title
            if (p.isLocal || consentPrefs.getBoolean(p.name, false)) action() else consentFor = action
        }
    }

    LaunchedEffect(file) { if (file != null) withConsent { vm.start(file) } }
    LaunchedEffect(Unit) { container.signatures.observeAll().collect { vm.loadSignatures(it) } }

    Column(modifier.background(FaColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = FaColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (vm.stage == 0) "KI-Assistent" else "Antwortschreiben", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (vm.stage == 1) TextButton(onClick = { vm.stage = 0 }) { Text("Zurück") }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Schließen") }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            vm.note?.let { Text(it, color = FaColors.SecondaryLabel, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }
            vm.error?.let {
                Card { Text(it, color = FaColors.Destructive) }
                if (vm.summary == null) TextButton(onClick = { if (file != null) withConsent { vm.start(file) } }) { Text("Erneut versuchen") }
            }
            vm.busy?.let {
                val frac = de.fapdf.ui.components.rememberStepProgress(vm.step, vm.steps, vm.sub)
                de.fapdf.ui.components.AiWorkingPanel(
                    vm.stepTitle.ifBlank { "KI arbeitet …" },
                    (if (vm.steps > 1) "Schritt ${vm.step} von ${vm.steps} · " else "") + it,
                    frac,
                    Modifier.fillMaxWidth().height(230.dp).padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(FaColors.Background),
                )
            }
            if (vm.stage == 0) Overview(vm) { withConsent { vm.write() } }
            else LetterEditor(vm, onManageProfiles, onRestyle = { k -> withConsent { vm.restyle(k) } },
                onOpen = { vm.createdDocId?.let(onOpenDocument) },
                onShare = { vm.createdFile?.let { FileActions.share(context, it, "Antwort – ${vm.docName}.pdf") } },
                onMail = {
                    vm.createdFile?.let {
                        FileActions.email(context, it, "Antwort – ${vm.docName}.pdf", listOfNotNull(vm.email.ifBlank { null }), vm.subject,
                            "Sehr geehrte Damen und Herren,\n\nanbei erhalten Sie mein Schreiben als PDF.\n\nMit freundlichen Grüßen\n${vm.signerName}")
                    }
                })
            Spacer(Modifier.height(24.dp))
        }
    }

    consentFor?.let { action ->
        AlertDialog(
            onDismissRequest = { consentFor = null },
            title = { Text("Dokument an KI senden?") },
            text = { Text("Der Text dieses Dokuments wird an $providerTitle übertragen, damit die KI ihn lesen und eine Antwort schreiben kann.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        consentPrefs.edit().putBoolean(container.settings.current().aiProvider.name, true).apply()
                    }
                    consentFor = null; action()
                }) { Text("Zustimmen – nicht mehr fragen") }
            },
            dismissButton = { TextButton(onClick = { consentFor = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(FaColors.Card)
            .border(1.dp, FaColors.CardBorder, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(vm: ReplyViewModel, onWrite: () -> Unit) {
    val summary = vm.summary ?: return
    Card {
        Text("Worum geht es", fontWeight = FontWeight.SemiBold, color = FaColors.Label)
        Text(summary, color = FaColors.Label)
        if (vm.deadlines.isNotBlank()) Text("Fristen: ${vm.deadlines}", color = FaColors.Label)
        if (vm.amounts.isNotBlank()) Text("Beträge: ${vm.amounts}", color = FaColors.Label)
    }
    Text("Antwort schreiben", fontWeight = FontWeight.SemiBold, color = FaColors.Label, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    Text("Was soll die Antwort sagen? Vorschlag antippen oder eigene Stichpunkte eingeben.", color = FaColors.SecondaryLabel, fontSize = 13.sp)
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.suggestions.forEach { s ->
            val sel = vm.goal == s
            Box(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (sel) FaColors.AccentLight else FaColors.Card)
                    .border(1.dp, if (sel) FaColors.Accent else FaColors.CardBorder, RoundedCornerShape(18.dp))
                    .clickable { vm.goal = if (sel) "" else s }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(s, color = if (sel) FaColors.Accent else FaColors.Label, fontSize = 14.sp) }
        }
    }
    OutlinedTextField(
        vm.notes, { vm.notes = it }, Modifier.fillMaxWidth().heightIn(min = 90.dp),
        label = { Text("Eigene Stichpunkte (optional)") },
        placeholder = { Text("z. B. Betrag am 21.10. überwiesen, Beleg liegt bei") },
    )
    Button(
        onClick = onWrite, enabled = vm.busy == null && (vm.goal.isNotBlank() || vm.notes.isNotBlank()),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent, contentColor = FaColors.OnAccent),
    ) { Text("Brief schreiben", fontWeight = FontWeight.SemiBold) }
    if (vm.body.isNotBlank()) TextButton(onClick = { vm.stage = 1 }) { Text("Zum letzten Entwurf") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterEditor(
    vm: ReplyViewModel,
    onManageProfiles: () -> Unit,
    onRestyle: (String) -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onMail: () -> Unit,
) {
    if (vm.profiles.size > 1) {
        Text("Absender", color = FaColors.SecondaryLabel, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.profiles.forEach { p -> TextButton(onClick = { vm.useProfile(p) }) { Text(p.label.ifBlank { p.fullName }) } }
        }
    } else if (vm.profiles.isEmpty()) {
        TextButton(onClick = onManageProfiles) { Text("Absender einmal als Profil speichern") }
    }
    OutlinedTextField(vm.sender, { vm.sender = it }, Modifier.fillMaxWidth(), label = { Text("Absender") }, minLines = 2)
    OutlinedTextField(vm.recipient, { vm.recipient = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Empfänger") }, minLines = 3)
    OutlinedTextField(vm.placeDate, { vm.placeDate = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Ort, Datum") }, singleLine = true)
    OutlinedTextField(vm.subject, { vm.subject = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Betreff") })
    OutlinedTextField(vm.body, { vm.body = it }, Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 260.dp), label = { Text("Brieftext") })
    OutlinedTextField(vm.signerName, { vm.signerName = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Name unter dem Brief") }, singleLine = true)

    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("höflich" to "Höflicher", "bestimmt" to "Bestimmter", "kurz" to "Kürzer").forEach { (k, t) ->
            OutlinedButton(onClick = { onRestyle(k) }, enabled = vm.busy == null, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(t, fontSize = 13.sp) }
        }
    }
    TextButton(onClick = { vm.write() }, enabled = vm.busy == null) { Text("Neu schreiben") }

    if (vm.signatures.isNotEmpty()) {
        Text("Unterschrift", color = FaColors.SecondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (vm.signatures.map { it.id to it.name } + (null to "Ohne")).forEach { (id, name) ->
                val sel = vm.signatureId == id
                TextButton(onClick = { vm.signatureId = id; vm.createdDocId = null }) {
                    Text(name, color = if (sel) FaColors.Accent else FaColors.SecondaryLabel, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
    Text("Bitte prüfen Sie den Entwurf vor dem Versand – die KI kann sich irren und ersetzt keine Rechtsberatung.",
        color = FaColors.SecondaryLabel, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Button(
        onClick = { vm.createPdf() }, enabled = vm.busy == null && vm.body.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent, contentColor = FaColors.OnAccent),
    ) { Text(if (vm.createdDocId == null) "Als PDF speichern" else "PDF neu erstellen", fontWeight = FontWeight.SemiBold) }
    if (vm.createdDocId != null) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Öffnen") }
            OutlinedButton(onClick = onMail, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Per E-Mail") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Teilen") }
        }
        if (vm.email.isNotBlank()) Text("E-Mail geht an: ${vm.email}", color = FaColors.SecondaryLabel, fontSize = 12.sp)
    }
}
