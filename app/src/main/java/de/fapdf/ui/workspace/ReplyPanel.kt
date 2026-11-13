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
    var deadlineDate by mutableStateOf<Long?>(null)
    var profileId: Long? = null
    private val senderPrefs = app.getSharedPreferences("reply_sender", android.content.Context.MODE_PRIVATE)
    private fun recipientKey(r: String) = r.lines().firstOrNull { it.isNotBlank() }?.lowercase()?.replace(Regex("[^a-zäöüß0-9]"), "")?.take(40)
    private fun rememberedFor(r: String): Pair<Long, Long?>? {
        val k = recipientKey(r) ?: return null
        val v = senderPrefs.getString(k, null) ?: return null
        val parts = v.split("|")
        return (parts.getOrNull(0)?.toLongOrNull() ?: return null) to parts.getOrNull(1)?.toLongOrNull()
    }
    private fun remember(r: String) {
        val k = recipientKey(r) ?: return
        val pid = profileId ?: return
        senderPrefs.edit().putString(k, "$pid|${signatureId ?: -1}").apply()
    }
    private fun parseDate(s: String?): Long? {
        val m = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})").find(s ?: return null) ?: return null
        val (d, mo, y0) = m.destructured
        val y = y0.toInt().let { if (it < 100) 2000 + it else it }
        return runCatching {
            java.util.Calendar.getInstance().apply { clear(); set(y, mo.toInt() - 1, d.toInt(), 9, 0) }.timeInMillis
        }.getOrNull()
    }

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
    /** Datum des erhaltenen Schreibens, z. B. „19.10.2026“. */
    var letterDate by mutableStateOf("")

    /** Datum des Schreibens direkt aus dem Text: zuerst neben „Datum“, sonst das erste Datum im Brief. */
    private fun findLetterDate(text: String): String {
        val d = "(\\d{1,2}\\.\\s?\\d{1,2}\\.\\s?\\d{2,4})"
        val near = Regex("(?i)datum[^\\d]{0,40}$d").find(text)?.groupValues?.get(1)
        val any = Regex(d).find(text)?.groupValues?.get(1)
        return (near ?: any).orEmpty().replace(" ", "")
    }
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
                letterDate = findLetterDate(text)
                if (text.replace(Regex("\\[Seite \\d+]"), "").isBlank()) throw IllegalStateException("Im Dokument wurde kein Text gefunden.")
                step = 2; sub = null; stepTitle = "KI liest das Schreiben …"
                busy = "Fristen, Beträge und Antworten suchen"
                val ans = AiRouter.complete(c.settings, ANALYZE, listOf(ChatMessage("user", Prompts.withDocument("Analysiere dieses Schreiben.", text))), 600)
                if (ans.fallback) note = "Antwort von " + AiRouter.shortName(ans.provider) + " (gewählter Dienst nicht erreichbar)" + if (ans.provider == de.fapdf.data.AiProvider.NANO) " – bitte genau prüfen" else ""
                parseAnalysis(ans.text)
            } catch (e: Exception) {
                error = e.message ?: "Fehler beim Lesen"
            } finally {
                busy = null
            }
        }
    }

    /** Liest die Analyse: Zeilenformat (KURZ: …), sonst JSON, sonst einzelne Felder aus kaputtem JSON. */
    private fun parseAnalysis(rawIn: String) {
        val raw = de.fapdf.ai.AiText.clean(rawIn)
        val fields = HashMap<String, String>()
        val vors = ArrayList<String>()
        // 1) Zeilenformat
        raw.lines().forEach { line ->
            val m = Regex("^\\s*[-*•]?\\s*(KURZ|BRIEFDATUM|FRISTDATUM|FRIST(?:EN)?|BETR(?:AG|ÄGE|AEGE)|ABSENDER|EMAIL|E-MAIL|BEZUG|ANSPRECHPARTNER|VORSCHLAG)\\s*\\d*\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE).find(line)
            if (m != null) {
                val key = m.groupValues[1].uppercase().let { k -> when {
                    k == "FRISTDATUM" -> "FRISTDATUM"; k == "BRIEFDATUM" -> "BRIEFDATUM"; k.startsWith("FRIST") -> "FRIST"; k.startsWith("BETR") -> "BETRAG"; k.contains("MAIL") -> "EMAIL"; else -> k } }
                val v = m.groupValues[2].trim().trim('"')
                if (key == "VORSCHLAG") { if (v.isNotBlank()) vors += v } else if (key !in fields && v.isNotBlank()) fields[key] = v
            }
        }
        // 2) JSON (auch abgeschnitten): Felder einzeln herausholen
        fun jsonField(name: String) = Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(raw)?.groupValues?.get(1)?.replace("\\n", "\n")?.trim()
        if (fields.isEmpty()) {
            jsonField("kurzfassung")?.let { fields["KURZ"] = it }
            jsonField("fristen")?.let { fields["FRIST"] = it }
            jsonField("betraege")?.let { fields["BETRAG"] = it }
            jsonField("absender")?.let { fields["ABSENDER"] = it }
            jsonField("email")?.let { fields["EMAIL"] = it }
            jsonField("bezug")?.let { fields["BEZUG"] = it }
            jsonField("ansprechpartner")?.let { fields["ANSPRECHPARTNER"] = it }
            Regex("\"vorschlaege\"\\s*:\\s*\\[(.*?)(]|$)", RegexOption.DOT_MATCHES_ALL).find(raw)?.groupValues?.get(1)?.let { arr ->
                Regex("\"((?:[^\"\\\\]|\\\\.)+)\"").findAll(arr).forEach { vors += it.groupValues[1] }
            }
        }
        fun clean(v: String?) = v?.trim()?.takeUnless { it.isBlank() || it.equals("leer", true) || it == "-" || it.startsWith("keine", true) }.orEmpty()
        summary = clean(fields["KURZ"]).ifBlank {
            // Nichts erkannt: lesbaren Text zeigen, nie Rohformat
            raw.lines().filterNot { it.trim().startsWith("{") || it.trim().startsWith("}") || it.contains("\":") }.joinToString(" ").trim().take(600).ifBlank { "–" }
        }
        Regex("\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}").find(fields["BRIEFDATUM"].orEmpty())?.value?.let { letterDate = it }
        deadlines = clean(fields["FRIST"])
        deadlineDate = parseDate(fields["FRISTDATUM"]) ?: parseDate(deadlines)
        amounts = clean(fields["BETRAG"])
        email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+").find(fields["EMAIL"].orEmpty().replace(" de", ".de").replace(" com", ".com"))?.value.orEmpty()
        bezug = clean(fields["BEZUG"])
        contact = clean(fields["ANSPRECHPARTNER"])
        val abs = fields["ABSENDER"].orEmpty().replace("\\n", "\n").replace(" | ", "\n").replace(";", "\n").trim()
        if (recipient.isBlank() && abs.isNotBlank()) recipient = abs
        // Für diesen Empfänger zuletzt benutzten Absender und Unterschrift wieder nehmen
        rememberedFor(recipient)?.let { (pid, sid) ->
            profiles.firstOrNull { it.id == pid }?.let { useProfile(it) }
            if (sid != null) signatureId = sid.takeIf { it >= 0 }
        }
        suggestions = vors.map { it.trim().trim('"', ',') }.filter { it.isNotEmpty() }.distinct().take(5).ifEmpty { DEFAULT_SUGGESTIONS }
    }

    fun useProfile(p: PersonProfile) {
        profileId = p.id
        signerName = p.fullName
        sender = listOf(p.fullName, p.streetLine, p.zipCity, p.phone.takeIf { it.isNotBlank() }?.let { "Tel. $it" }, p.email)
            .filter { !it.isNullOrBlank() }.joinToString("\n")
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
        placeDate = listOf(p.city.trim(), today).filter { it.isNotBlank() }.joinToString(", ")
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
                    (if (letterDate.isNotBlank()) "Datum des erhaltenen Schreibens: $letterDate. " else "") +
                    (if (bezug.isNotBlank()) "Bezug (Nummern): $bezug. " else "") +
                    (if (contact.isNotBlank()) "Ansprechpartner: $contact. " else "") +
                    "Schreibe sachlich, höflich und klar im deutschen Geschäftsbriefstil (DIN 5008). Gib GENAU dieses Format aus:\n" +
                    "Betreff: <kurzer Betreff mit Bezug, z. B. Nummern>\n\n<Anrede>,\n\n<Text in kurzen Absätzen>\n\nMit freundlichen Grüßen\n" +
                    "Keine Adressen, kein Datum, keinen Namen unter dem Gruß, keine Erklärungen. Verwende nur Fakten aus dem Schreiben und " +
                    "den Angaben. Setze alle bekannten Angaben (Datum, Nummern, Beträge) direkt ein. Keine Platzhalter, keine eckigen Klammern – " +
                    "was unbekannt ist, lässt du einfach weg. Handschriftliche Notizen im Schreiben sind Angaben von $name."
                val ans = AiRouter.complete(c.settings, system, listOf(ChatMessage("user", Prompts.withDocument("Schreibe jetzt den Antwortbrief.", text))), 1500)
                if (ans.fallback) note = "Antwort von " + AiRouter.shortName(ans.provider) + " (gewählter Dienst nicht erreichbar)" + if (ans.provider == de.fapdf.data.AiProvider.NANO) " – bitte genau prüfen" else ""
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
        val lines = de.fapdf.ai.AiText.clean(raw).lines()
        val subjIdx = lines.indexOfFirst { it.trim().startsWith("Betreff", true) }
        if (subjIdx >= 0) {
            subject = lines[subjIdx].substringAfter(":").trim().trim('*')
            body = lines.drop(subjIdx + 1).joinToString("\n").trim()
        } else {
            subject = "Ihr Schreiben" + if (bezug.isNotBlank()) " – $bezug" else ""
            body = raw.trim()
        }
        body = fixPlaceholders(body); subject = fixPlaceholders(subject)
        // Name unter dem Gruß setzt die App selbst
        if (signerName.isNotBlank()) body = body.lines().dropLastWhile { it.isBlank() || it.trim() == signerName.trim() }.joinToString("\n")
    }

    /** Übrig gebliebene Platzhalter wie „[Datum des Schreibens einfügen …]“ füllen oder entfernen. */
    private fun fixPlaceholders(t: String): String = t.replace(Regex("\\s?\\[([^\\]]{2,120})]")) { m ->
        val inner = m.groupValues[1]
        when {
            inner.contains("datum", true) && letterDate.isNotBlank() -> " $letterDate"
            inner.contains("betrag", true) && amounts.isNotBlank() -> " " + amounts
            Regex("(?i)einfügen|falls|sonst|weglassen|bitte|angeben|unbekannt|xx").containsMatchIn(inner) -> ""
            else -> m.value
        }
    }.replace(Regex(" {2,}"), " ").replace(Regex(" ([,.])"), "$1")

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
                if (ans.fallback) note = "Antwort von " + AiRouter.shortName(ans.provider) + " (gewählter Dienst nicht erreichbar)" + if (ans.provider == de.fapdf.data.AiProvider.NANO) " – bitte genau prüfen" else ""
                body = fixPlaceholders(de.fapdf.ai.AiText.clean(ans.text))
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
                remember(recipient)
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

        const val ANALYZE = "Du liest ein Schreiben, das der Nutzer erhalten hat. Antworte auf Deutsch, OHNE Einleitung, " +
            "genau in diesen Zeilen (jede Angabe in einer Zeile, nichts erfinden, fehlt etwas: \"-\"):\n" +
            "KURZ: 2 Sätze – wer schreibt, worum geht es, was wird verlangt\n" +
            "BRIEFDATUM: Datum des Schreibens als TT.MM.JJJJ\n" +
            "FRIST: Frist oder Termin (in Worten)\n" +
            "FRISTDATUM: letzter Tag der Frist als TT.MM.JJJJ, sonst -\n" +
            "BETRAG: wichtigster Betrag\n" +
            "ABSENDER: Postanschrift des Absenders, Teile mit | getrennt (Firma | Straße Nr. | PLZ Ort)\n" +
            "EMAIL: E-Mail des Ansprechpartners\n" +
            "BEZUG: Aktenzeichen/Kunden-/Betriebsnummer und Datum des Schreibens\n" +
            "ANSPRECHPARTNER: Name\n" +
            "VORSCHLAG: kurze Antwortmöglichkeit aus Sicht des Empfängers (höchstens 10 Wörter)\n" +
            "VORSCHLAG: zweite Möglichkeit\n" +
            "VORSCHLAG: dritte Möglichkeit\n" +
            "Handschriftliche Notizen im Schreiben (z. B. „Bezahlt am …“) sind Wissen des Empfängers und gehören in die Vorschläge."
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
                            // Gruß und Name hängt das E-Mail-Programm meist selbst als Signatur an
                            "Sehr geehrte Damen und Herren,\n\nanbei erhalten Sie mein Schreiben als PDF.\n")
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
        if (vm.deadlines.isNotBlank() || vm.deadlineDate != null) {
            val context = LocalContext.current
            TextButton(onClick = {
                val begin = vm.deadlineDate ?: (System.currentTimeMillis() + 3L * 24 * 3600_000)
                val i = android.content.Intent(android.content.Intent.ACTION_INSERT)
                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    .putExtra(android.provider.CalendarContract.Events.TITLE, (if (vm.deadlineDate == null) "Frist prüfen: " else "Frist: ") + vm.docName)
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, listOf(vm.deadlines, vm.amounts, vm.summary.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"))
                runCatching { context.startActivity(i) }.onFailure { FileActions.toast(context, "Keine Kalender-App gefunden") }
            }) {
                Text(if (vm.deadlineDate != null) "Erinnerung in Kalender eintragen (" + java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(vm.deadlineDate!!)) + ")" else "Erinnerung in Kalender eintragen",
                    color = FaColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        }
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
            EqualButton(t, Modifier.weight(1f), enabled = vm.busy == null) { onRestyle(k) }
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
            EqualButton("Öffnen", Modifier.weight(1f), onClick = onOpen)
            EqualButton("E-Mail", Modifier.weight(1f), onClick = onMail)
            EqualButton("Teilen", Modifier.weight(1f), onClick = onShare)
        }
        if (vm.email.isNotBlank()) Text("E-Mail geht an: ${vm.email}", color = FaColors.SecondaryLabel, fontSize = 12.sp)
    }
}


/** Gleich hohe, einzeilige Knöpfe – nichts bricht um. */
@Composable
private fun EqualButton(text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp), shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) { Text(text, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
}
