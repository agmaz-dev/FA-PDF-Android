package de.fapdf.ui.workspace

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Edit
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
    /** Erkannte handschriftliche Notiz, z. B. „Bezahlt 21.10.26“. */
    var handNote by mutableStateOf("")
    /** Entstehender Text (Wort für Wort). */
    var partial by mutableStateOf<String?>(null)
    // Anlagen: Original und weitere Dateien
    var includeOriginal by mutableStateOf(true)
    val extraAttachments = androidx.compose.runtime.mutableStateListOf<Pair<File, String>>()
    var originalFile: File? = null
    var signatureBitmap by mutableStateOf<Bitmap?>(null)
    var profileId by mutableStateOf<Long?>(null)
    val sigThumbs = androidx.compose.runtime.mutableStateMapOf<Long, Bitmap>()
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
        originalFile = file
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
                if (ans.provider == de.fapdf.data.AiProvider.GEMMA) note = de.fapdf.ai.GemmaLocal.lastInfo
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
            val m = Regex("^\\s*[-*•]?\\s*(KURZ|NOTIZ|BRIEFDATUM|FRISTDATUM|FRIST(?:EN)?|BETR(?:AG|ÄGE|AEGE)|ABSENDER|EMAIL|E-MAIL|BEZUG|ANSPRECHPARTNER|VORSCHLAG)\\s*\\d*\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE).find(line)
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
        handNote = clean(fields["NOTIZ"])
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
        if (deadlineDate != null || deadlines.isNotBlank()) c.index.setDeadline(docId, deadlineDate, deadlines, amounts)
        suggestions = vors.map { it.trim().trim('"', ',') }.filter { it.isNotEmpty() }.distinct().take(5).ifEmpty { DEFAULT_SUGGESTIONS }
    }

    /** Person wechseln; passende Unterschrift dieser Person gleich mit auswählen. */
    fun switchProfile(p: PersonProfile) {
        useProfile(p); createdDocId = null
        val cur = signatures.firstOrNull { it.id == signatureId }
        if (cur?.profileId != p.id) signatures.firstOrNull { it.profileId == p.id }?.let { signatureId = it.id; refreshSignature() }
    }

    fun useProfile(p: PersonProfile) {
        profileId = p.id
        signerName = p.fullName.replace(Regex("\\s+"), " ").trim()
        sender = listOf(signerName, p.streetLine, p.zipCity, p.phone.takeIf { it.isNotBlank() }?.let { "Tel. $it" }, p.email)
            .filter { !it.isNullOrBlank() }.joinToString("\n")
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
        placeDate = listOf(p.city.trim(), today).filter { it.isNotBlank() }.joinToString(", ")
    }

    fun loadSignatures(list: List<SignatureEntity>) {
        signatures = list
        if (signatureId != null && list.none { it.id == signatureId }) signatureId = null
        if (signatureId == null) signatureId = (list.firstOrNull { it.profileId != null && it.profileId == profileId } ?: list.firstOrNull())?.id
        refreshSignature()
        viewModelScope.launch {
            list.filter { it.id !in sigThumbs }.forEach { e -> runCatching { c.signatures.loadBitmap(e) }.getOrNull()?.let { sigThumbs[e.id] = it } }
        }
    }

    fun selectSignature(id: Long?) {
        signatureId = id; createdDocId = null; refreshSignature()
        // Unterschrift gehört zu einer Person → Brief auf diese Person umstellen
        val pid = signatures.firstOrNull { it.id == id }?.profileId
        if (pid != null && pid != profileId) profiles.firstOrNull { it.id == pid }?.let { useProfile(it) }
    }

    fun assignSignature(s: SignatureEntity, pid: Long?) {
        viewModelScope.launch { c.signatures.update(s.copy(profileId = pid)) }
        if (signatureId == s.id && pid != null) profiles.firstOrNull { it.id == pid }?.let { useProfile(it) }
    }

    fun deleteSignature(s: SignatureEntity) {
        if (signatureId == s.id) { signatureId = null; signatureBitmap = null }
        sigThumbs.remove(s.id); createdDocId = null
        viewModelScope.launch { c.signatures.delete(s) }
    }

    private fun refreshSignature() {
        val e = signatures.firstOrNull { it.id == signatureId }
        viewModelScope.launch { signatureBitmap = e?.let { runCatching { c.signatures.loadBitmap(it) }.getOrNull() } }
    }

    fun bezugText() = bezug

    /** Kurzes Zeichen für den Infoblock: die Nummer aus dem Bezug (z. B. 95852881), sonst der Bezug. */
    fun referenceShort(): String = Regex("[A-Z0-9][A-Z0-9./-]*\\d{4,}[A-Z0-9./-]*").find(bezug)?.value ?: bezug.take(30)

    /** Namen der Anlagen für den Brief („Anlage: …“). */
    fun attachmentNames(): List<String> =
        (if (includeOriginal && originalFile != null) listOf("Ihr Schreiben vom ${letterDate.ifBlank { "…" }} (Kopie)") else emptyList()) +
            extraAttachments.map { it.second.removeSuffix(".pdf") }

    /** Dateien, die beim Versand mitgehen. */
    fun attachmentFiles(): List<Pair<File, String>> =
        (if (includeOriginal) originalFile?.let { listOf(it to "Anlage – ${docName.ifBlank { "Schreiben" }}.pdf") } ?: emptyList() else emptyList()) + extraAttachments

    fun addAttachment(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val ctx = getApplication<Application>()
                val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) cur.getString(0) else null
                } ?: "Anlage"
                val f = File(File(ctx.cacheDir, "anlagen").apply { mkdirs() }, "${System.nanoTime()}_$name")
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } }
                extraAttachments.add(f to name)
                createdDocId = null
            }.onFailure { error = "Anlage konnte nicht geladen werden." }
        }
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
                    "Schreibe sachlich und klar im deutschen Geschäftsbriefstil (DIN 5008). Verwende das Wort \"höflich\" nicht, wiederhole keine Sätze " +
                    "und keine Gedanken, kein Markdown (keine Sternchen, keine Überschriften). Gib GENAU dieses Format aus:\n" +
                    "Betreff: <kurzer Betreff mit Bezug, z. B. Nummern>\n\n<Anrede>,\n\n<Text in 3 bis 5 Absätzen>\n\nMit freundlichen Grüßen\n" +
                    "Der Text soll vollständig und konkret sein: zuerst Bezug auf das Schreiben (Datum, Nummern), dann auf die einzelnen Punkte " +
                    "des Schreibens eingehen, das Anliegen begründen und am Ende klar sagen, was erbeten oder erwartet wird (mit Frist, falls passend). " +
                    "Keine Adressen, kein Datum, keinen Namen unter dem Gruß, keine Erklärungen. Verwende nur Fakten aus dem Schreiben und " +
                    "den Angaben. Setze alle bekannten Angaben (Datum, Nummern, Beträge) direkt ein. Keine Platzhalter, keine eckigen Klammern – " +
                    "was unbekannt ist, lässt du einfach weg. Handschriftliche Notizen im Schreiben sind Angaben von $name."
                partial = null
                val ans = AiRouter.complete(c.settings, system, listOf(ChatMessage("user", Prompts.withDocument("Schreibe jetzt den Antwortbrief.", text))), 2000) { partial = it }
                partial = null
                if (ans.fallback) note = "Antwort von " + AiRouter.shortName(ans.provider) + " (gewählter Dienst nicht erreichbar)" + if (ans.provider == de.fapdf.data.AiProvider.NANO) " – bitte genau prüfen" else ""
                if (ans.provider == de.fapdf.data.AiProvider.GEMMA) note = de.fapdf.ai.GemmaLocal.lastInfo
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
        val lines = de.fapdf.ai.AiText.letter(raw).lines()
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
        body = completeEnding(body)
        if (raw.contains("(Hinweis: Gemini Nano")) note = "Gemini Nano hat nur den Anfang des Schreibens gelesen – für lange Dokumente in den Einstellungen „Gemma 4“ wählen."
    }

    /** Bricht der Text mitten im Satz ab (KI-Limit), bis zum letzten vollständigen Satz kürzen und den Gruß ergänzen. */
    private fun completeEnding(t: String): String {
        var b = t.trimEnd()
        if (b.isEmpty()) return b
        val hasGreeting = Regex("(?i)(mit freundlichen grüßen|freundliche grüße|hochachtungsvoll)\\s*$").containsMatchIn(b)
        if (hasGreeting) return b
        if (b.last() !in ".!?)") {
            val cut = b.lastIndexOfAny(charArrayOf('.', '!', '?'))
            if (cut > b.length / 2) b = b.substring(0, cut + 1)
        }
        return "$b\n\nMit freundlichen Grüßen"
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
                    "ausführlich" -> "ausführlicher und detaillierter schreiben: auf jeden Punkt des ursprünglichen Schreibens (siehe <dokument>) " +
                        "einzeln eingehen, Begründungen und Fakten ergänzen, klar sagen, was erbeten oder erwartet wird – etwa doppelt so lang, " +
                        "aber ohne Wiederholungen und ohne erfundene Angaben"
                    "bestimmt" -> "bestimmter und nachdrücklicher formulieren, aber sachlich bleiben"
                    else -> "deutlich kürzer fassen, nur das Wesentliche"
                }
                val system = "Überarbeite den folgenden Brieftext: $instr. Behalte alle Fakten, Nummern und Daten sowie das Format " +
                    "(Anrede … Mit freundlichen Grüßen) bei. Kein Wort \"höflich\", keine Wiederholungen, kein Markdown. Gib nur den überarbeiteten Brieftext aus, ohne Betreff und ohne Erklärungen."
                partial = null
                val input = if (kind == "ausführlich" && docText != null) Prompts.withDocument("Brieftext:\n$current", docText!!) else current
                val ans = AiRouter.complete(c.settings, system, listOf(ChatMessage("user", input)), if (kind == "ausführlich") 2500 else 1500) { partial = it }
                partial = null
                if (ans.fallback) note = "Antwort von " + AiRouter.shortName(ans.provider) + " (gewählter Dienst nicht erreichbar)" + if (ans.provider == de.fapdf.data.AiProvider.NANO) " – bitte genau prüfen" else ""
                if (ans.provider == de.fapdf.data.AiProvider.GEMMA) note = de.fapdf.ai.GemmaLocal.lastInfo
                body = completeEnding(fixPlaceholders(de.fapdf.ai.AiText.letter(ans.text)))
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
                        LetterPdfWriter.Letter(sender, recipient, placeDate, subject, body, signerName, attachmentNames(), letterDate, referenceShort()),
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
            "NOTIZ: handschriftliche Notiz auf dem Brief (z. B. „Bezahlt 21.10.26“), sonst -\n" +
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
    closeRequest: Boolean = false,
    onCloseRequestHandled: () -> Unit = {},
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

    LaunchedEffect(file) { if (file != null) { vm.originalFile = file; withConsent { vm.start(file) } } }
    LaunchedEffect(Unit) { container.signatures.observeAll().collect { vm.loadSignatures(it) } }
    // Schließen mit Rückfrage, wenn schon ein Brief geschrieben ist (er bleibt aber für dieses Dokument erhalten)
    var confirmClose by remember { mutableStateOf(false) }
    val requestClose = { if (vm.body.isNotBlank() && vm.createdDocId == null) confirmClose = true else onClose() }
    LaunchedEffect(closeRequest) { if (closeRequest) { onCloseRequestHandled(); requestClose() } }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Antwort schließen?") },
        text = { Text("Der Brief bleibt erhalten, solange dieses Dokument geöffnet ist. Als PDF gespeichert ist er noch nicht.") },
        confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("Schließen") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Weiter bearbeiten") } },
    )

    Column(modifier.background(FaColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = FaColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (vm.stage == 0) "KI-Assistent" else "Antwortschreiben", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (vm.stage == 1) TextButton(onClick = { vm.stage = 0 }) { Text("Zurück") }
            IconButton(onClick = requestClose) { Icon(Icons.Outlined.Close, "Schließen") }
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
                    Modifier.fillMaxWidth().height(if (vm.partial.isNullOrBlank()) 230.dp else 360.dp).padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(FaColors.Background),
                    partial = vm.partial,
                )
            }
            if (vm.stage == 0) Overview(vm) { withConsent { vm.write() } }
            else LetterEditor(vm, onManageProfiles, onRestyle = { k -> withConsent { vm.restyle(k) } },
                onOpen = { vm.createdDocId?.let(onOpenDocument) },
                onShare = { vm.createdFile?.let { FileActions.shareMany(context, listOf(it to "Antwort – ${vm.docName}.pdf") + vm.attachmentFiles()) } },
                onMail = {
                    vm.createdFile?.let {
                        FileActions.emailMany(context, listOf(it to "Antwort – ${vm.docName}.pdf") + vm.attachmentFiles(), listOfNotNull(vm.email.ifBlank { null }), vm.subject,
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
    val context = LocalContext.current
    // Kopfkarte: Absender-Kürzel, Titel, Datum, kurze Erklärung
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val initials = vm.recipient.lines().firstOrNull { it.isNotBlank() }.orEmpty()
                .split(Regex("[\\s–-]+")).firstOrNull { it.length >= 2 }?.take(4)?.uppercase() ?: "PDF"
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Text(initials, color = Color(0xFF1D4E89), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.recipient.lines().firstOrNull { it.isNotBlank() } ?: vm.docName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FaColors.Label, maxLines = 1)
                Text(listOf(vm.letterDate, vm.bezugText()).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = FaColors.SecondaryLabel, maxLines = 1)
            }
        }
        Text(summary, color = FaColors.Label, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
    // Frist (rot) und Betrag (grün)
    if (vm.deadlines.isNotBlank() || vm.deadlineDate != null || vm.amounts.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (vm.deadlines.isNotBlank() || vm.deadlineDate != null) Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A1416)).border(1.dp, Color(0xFF5A2A2E), RoundedCornerShape(16.dp))
                .clickable { addToCalendar(context, vm) }.padding(12.dp),
        ) {
            Text("FRIST", fontSize = 11.sp, color = Color(0xFFFFB3B3), fontWeight = FontWeight.Bold)
            Text(vm.deadlineDate?.let { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(it)) } ?: vm.deadlines,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD6D6), maxLines = 2)
            Text("In Kalender eintragen", fontSize = 11.sp, color = Color(0xFFFFB3B3), modifier = Modifier.padding(top = 4.dp))
        }
        if (vm.amounts.isNotBlank()) Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(FaColors.AccentLight).border(1.dp, FaColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(12.dp),
        ) {
            Text("BETRAG", fontSize = 11.sp, color = FaColors.Accent, fontWeight = FontWeight.Bold)
            Text(vm.amounts, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FaColors.Accent, maxLines = 2)
        }
    }
    if (vm.handNote.isNotBlank()) Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(FaColors.Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Edit, null, tint = FaColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Notiz im Brief erkannt: „${vm.handNote}“", fontSize = 13.sp, color = FaColors.Label)
    }
    Text("Antworten mit …", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FaColors.Label, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    // Eigene Stichpunkte – tippen oder diktieren
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) vm.notes = listOf(vm.notes, t).filter { it.isNotBlank() }.joinToString(" ")
    }
    OutlinedTextField(
        vm.notes, { vm.notes = it }, Modifier.fillMaxWidth().heightIn(min = 90.dp),
        label = { Text("Eigene Stichpunkte (optional)") },
        placeholder = { Text("z. B. Betrag am 21.10. überwiesen, Beleg liegt bei") },
        trailingIcon = {
            IconButton(onClick = {
                val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Stichpunkte für die Antwort")
                runCatching { speech.launch(i) }.onFailure { FileActions.toast(context, "Spracheingabe nicht verfügbar") }
            }) { Icon(Icons.Outlined.Mic, "Diktieren", tint = FaColors.Accent) }
        },
    )
    Button(
        onClick = onWrite, enabled = vm.busy == null && (vm.goal.isNotBlank() || vm.notes.isNotBlank()),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent, contentColor = FaColors.OnAccent),
    ) { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Brief schreiben", fontWeight = FontWeight.SemiBold) }
    if (vm.body.isNotBlank()) TextButton(onClick = { vm.stage = 1 }) { Text("Zum letzten Entwurf") }
}

private fun addToCalendar(context: android.content.Context, vm: ReplyViewModel) {
    val begin = vm.deadlineDate ?: (System.currentTimeMillis() + 3L * 24 * 3600_000)
    val i = android.content.Intent(android.content.Intent.ACTION_INSERT)
        .setData(android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .putExtra(android.provider.CalendarContract.Events.TITLE, (if (vm.deadlineDate == null) "Frist prüfen: " else "Frist: ") + vm.docName)
        .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, listOf(vm.deadlines, vm.amounts, vm.summary.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"))
    runCatching { context.startActivity(i) }.onFailure { FileActions.toast(context, "Keine Kalender-App gefunden") }
}

/** Der Brief so, wie er gedruckt aussieht. Antippen öffnet die Felder zum Ändern. */
@Composable
private fun LetterPaper(vm: ReplyViewModel, onEdit: () -> Unit) {
    val ink = Color(0xFF1A1A1F)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        // Wie im PDF: links Anschriftfeld, rechts daneben der Infoblock
        val (name, postal, contact) = LetterPdfWriter.splitSender(vm.sender.replace(Regex("[ \\t]+"), " "))
        Row(Modifier.fillMaxWidth().padding(top = 26.dp)) {
            Column(Modifier.weight(1.15f)) {
                Text((listOf(name) + postal).filter { it.isNotBlank() }.joinToString(" · "), color = Color(0xFF777777), fontSize = 6.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                LetterPdfWriter.splitAddress(vm.recipient.replace(Regex("[ \\t]+"), " ")).forEach { Text(it, color = ink, fontSize = 10.sp, lineHeight = 13.sp) }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                if (name.isNotBlank()) Text(name, color = ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                postal.forEach { Text(it, color = ink, fontSize = 8.sp, lineHeight = 11.sp) }
                Spacer(Modifier.height(5.dp))
                val infos = contact + listOfNotNull(
                    vm.letterDate.takeIf { it.isNotBlank() }?.let { LetterPdfWriter.Info("Ihr Schreiben", it) },
                    vm.referenceShort().takeIf { it.isNotBlank() }?.let { LetterPdfWriter.Info("Ihr Zeichen", it) },
                    LetterPdfWriter.Info("Datum", LetterPdfWriter.dateOnly(vm.placeDate)),
                )
                infos.forEach { inf ->
                    Row {
                        Text(inf.label, color = Color(0xFF666666), fontSize = 7.sp, modifier = Modifier.width(52.dp))
                        Text(inf.value, color = ink, fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
        }
        Text(vm.subject, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(vm.body, color = ink, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp))
        vm.signatureBitmap?.let { b ->
            androidx.compose.foundation.Image(b.asImageBitmap(), "Unterschrift", Modifier.padding(top = 4.dp).height(44.dp))
        }
        if (vm.signerName.isNotBlank()) Text(vm.signerName, color = ink, fontSize = 11.sp, modifier = Modifier.padding(top = if (vm.signatureBitmap == null) 22.dp else 0.dp))
        val atts = vm.attachmentNames()
        if (atts.isNotEmpty()) {
            Text(if (atts.size == 1) "Anlage" else "Anlagen", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            atts.forEach { Text(it, color = ink, fontSize = 11.sp) }
        }
    }
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
    var editFields by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addAttachment(uri)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ausführlich" to "Ausführlicher", "bestimmt" to "Bestimmter", "kurz" to "Kürzer").forEach { (k, t) ->
            EqualButton(t, Modifier.weight(1f), enabled = vm.busy == null) { onRestyle(k) }
        }
    }
    if (vm.profiles.size > 1) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Absender", color = FaColors.SecondaryLabel, fontSize = 13.sp)
            vm.profiles.forEach { p ->
                val sel = vm.profileId == p.id
                Text(p.label.ifBlank { p.fullName }, color = if (sel) FaColors.OnAccent else FaColors.Label, fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) FaColors.Accent else FaColors.Card)
                        .clickable { vm.switchProfile(p) }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    } else if (vm.profiles.isEmpty()) {
        TextButton(onClick = onManageProfiles) { Text("Absender einmal als Profil speichern") }
    }
    if (!editFields) {
        LetterPaper(vm) { editFields = true }
        Text("Antippen zum Ändern", color = FaColors.SecondaryLabel, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    } else {
        OutlinedTextField(vm.sender, { vm.sender = it }, Modifier.fillMaxWidth(), label = { Text("Absender") }, minLines = 2)
        OutlinedTextField(vm.recipient, { vm.recipient = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Empfänger") }, minLines = 3)
        OutlinedTextField(vm.placeDate, { vm.placeDate = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Ort, Datum") }, singleLine = true)
        OutlinedTextField(vm.subject, { vm.subject = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Betreff") })
        OutlinedTextField(vm.body, { vm.body = it }, Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 260.dp), label = { Text("Brieftext") })
        OutlinedTextField(vm.signerName, { vm.signerName = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Name unter dem Brief") }, singleLine = true)
        TextButton(onClick = { editFields = false; vm.createdDocId = null }) { Text("Fertig – als Papier ansehen") }
    }
    TextButton(onClick = { vm.write() }, enabled = vm.busy == null) { Text("Neu schreiben") }

    if (vm.signatures.isNotEmpty()) SignatureStrip(vm)
    // Anlagen
    Text("Anlagen", color = FaColors.SecondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.fillMaxWidth().clickable { vm.includeOriginal = !vm.includeOriginal; vm.createdDocId = null }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(vm.includeOriginal, { vm.includeOriginal = it; vm.createdDocId = null })
        Text("Original-Schreiben als Kopie anhängen", color = FaColors.Label, fontSize = 14.sp)
    }
    vm.extraAttachments.toList().forEach { (f, n) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(n, color = FaColors.Label, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
            IconButton(onClick = { vm.extraAttachments.remove(f to n); vm.createdDocId = null }) { Icon(Icons.Outlined.Close, "Entfernen", tint = FaColors.SecondaryLabel) }
        }
    }
    TextButton(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }) { Text("+ Weitere Anlage (z. B. Zahlungsbeleg)") }

    Text("Bitte prüfen Sie den Entwurf vor dem Versand – die KI kann sich irren und ersetzt keine Rechtsberatung.",
        color = FaColors.SecondaryLabel, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Button(
        onClick = { vm.createPdf() }, enabled = vm.busy == null && vm.body.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent, contentColor = FaColors.OnAccent),
    ) { Text(if (vm.createdDocId == null) "Als PDF speichern" else "PDF neu erstellen", fontWeight = FontWeight.SemiBold) }
    if (vm.createdDocId != null) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqualButton("Öffnen", Modifier.weight(1f), onClick = onOpen)
            EqualButton("E-Mail", Modifier.weight(1f), onClick = onMail)
            EqualButton("Teilen", Modifier.weight(1f), onClick = onShare)
        }
        if (vm.email.isNotBlank()) Text("E-Mail geht an: ${vm.email}" + if (vm.attachmentFiles().isNotEmpty()) " · mit ${vm.attachmentFiles().size} Anlage(n)" else "", color = FaColors.SecondaryLabel, fontSize = 12.sp)
    }
}

@Composable
private fun EqualButton(text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp), shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) { Text(text, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
}


/** Schmale Reihe mit Unterschriften: Tippen = auswählen (stellt auch die Person um), lange drücken = zuordnen/löschen. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SignatureStrip(vm: ReplyViewModel) {
    var menuFor by remember { mutableStateOf<SignatureEntity?>(null) }
    var deleteFor by remember { mutableStateOf<SignatureEntity?>(null) }
    Text("Unterschrift  ·  lange drücken zum Zuordnen oder Löschen", color = FaColors.SecondaryLabel, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val sel = vm.signatureId == null
            Box(
                Modifier.size(96.dp, 64.dp).clip(RoundedCornerShape(12.dp)).background(FaColors.Card)
                    .border(2.dp, if (sel) FaColors.Accent else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { vm.selectSignature(null) },
                contentAlignment = Alignment.Center,
            ) { Text("Ohne", color = if (sel) FaColors.Accent else FaColors.SecondaryLabel, fontSize = 13.sp) }
        }
        items(vm.signatures.size) { i ->
            val s = vm.signatures[i]
            val sel = vm.signatureId == s.id
            val owner = vm.profiles.firstOrNull { it.id == s.profileId }
            Box {
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(12.dp))
                        .border(2.dp, if (sel) FaColors.Accent else Color.Transparent, RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { vm.selectSignature(s.id) }, onLongClick = { menuFor = s }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(44.dp).background(Color.White), contentAlignment = Alignment.Center) {
                        vm.sigThumbs[s.id]?.let { b ->
                            androidx.compose.foundation.Image(b.asImageBitmap(), s.name, Modifier.fillMaxSize().padding(4.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                        }
                    }
                    Text((owner?.let { it.label.ifBlank { it.fullName } } ?: s.name), fontSize = 11.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = if (sel) FaColors.Accent else FaColors.SecondaryLabel,
                        modifier = Modifier.fillMaxWidth().background(FaColors.Card).padding(horizontal = 6.dp, vertical = 3.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                androidx.compose.material3.DropdownMenu(expanded = menuFor?.id == s.id, onDismissRequest = { menuFor = null }) {
                    vm.profiles.forEach { p ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text((if (s.profileId == p.id) "✓ " else "") + "Gehört zu " + p.label.ifBlank { p.fullName }) },
                            onClick = { menuFor = null; vm.assignSignature(s, p.id) },
                        )
                    }
                    if (s.profileId != null) androidx.compose.material3.DropdownMenuItem(text = { Text("Zuordnung entfernen") }, onClick = { menuFor = null; vm.assignSignature(s, null) })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Löschen", color = Color(0xFFFF5A5A)) }, onClick = { menuFor = null; deleteFor = s })
                }
            }
        }
    }
    deleteFor?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Unterschrift löschen?") },
            text = { Text("„${s.name}“ wird endgültig entfernt.") },
            confirmButton = { TextButton(onClick = { vm.deleteSignature(s); deleteFor = null }) { Text("Löschen", color = Color(0xFFFF5A5A)) } },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Abbrechen") } },
        )
    }
}
