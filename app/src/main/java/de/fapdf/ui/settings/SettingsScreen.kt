package de.fapdf.ui.settings

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import de.fapdf.ui.theme.FaColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.People
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.fapdf.BuildConfig
import de.fapdf.data.AiProvider
import de.fapdf.data.AppSettings
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.components.Biometric
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.GroupCard
import de.fapdf.ui.components.GroupRow
import de.fapdf.ui.components.PrimaryButton
import de.fapdf.ui.components.ScreenScaffold
import de.fapdf.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(appVm: AppViewModel, onBack: (() -> Unit)?, onProfiles: () -> Unit = {}, onSignatures: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = appVm.container.settings
    val s by repo.settings.collectAsState(initial = AppSettings())
    var provider by remember { mutableStateOf(AiProvider.NANO) }
    var model by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(de.fapdf.data.DEFAULT_AI_BASE_URL) }
    LaunchedEffect(Unit) {
        repo.migrateLegacyKey()
        val cur = repo.current()
        provider = cur.aiProvider; model = cur.aiModel; baseUrl = cur.aiBaseUrl
    }
    // Sicherung: Passwort abfragen, dann Datei wählen
    var backupBusy by remember { mutableStateOf<String?>(null) }
    var pendingPw by remember { mutableStateOf<String?>(null) }
    var askPw by remember { mutableStateOf<String?>(null) } // "create" | "restore"
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val createLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = pendingPw; pendingPw = null
        if (uri != null && pw != null) scope.launch {
            backupBusy = "Sicherung wird erstellt …"
            try {
                val r = context.contentResolver.openOutputStream(uri)!!.use { de.fapdf.data.Backup.create(appVm.container, pw, it) }
                FileActions.toast(context, "Sicherung erstellt: ${r.documents} Dokumente, ${r.profiles} Profile, ${r.signatures} Unterschriften")
            } catch (e: Exception) { FileActions.toast(context, "Sicherung fehlgeschlagen: ${e.message}") } finally { backupBusy = null }
        }
    }
    val openLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; askPw = "restore" }
    }
    ScreenScaffold("Einstellungen", onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("Persönliche Daten")
            GroupCard {
                GroupRow("Familienprofile", "Daten für das automatische Ausfüllen von Formularen", Icons.Outlined.People, onClick = onProfiles)
                GroupRow("Unterschriften", "Gespeicherte Unterschriften verwalten", Icons.Outlined.Draw, showDivider = false, onClick = onSignatures)
            }
            SectionTitle("Sicherung")
            GroupCard {
                GroupRow("Sicherung erstellen", "Alle Dokumente, Profile und Unterschriften in einer Datei – mit Passwort, z. B. auf Google Drive speichern", Icons.Outlined.CloudUpload, onClick = { askPw = "create" })
                GroupRow("Sicherung wiederherstellen", "Auf neuem Handy oder nach Neuinstallation", Icons.Outlined.CloudDownload, showDivider = false, onClick = { openLauncher.launch(arrayOf("*/*")) })
            }
            SectionTitle("Darstellung")
            // Umschalter in einer Zeile: System · Hell · Dunkel
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(FaColors.Card).padding(6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                de.fapdf.data.ThemeMode.entries.forEach { m ->
                    val sel = s.themeMode == m
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f).height(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                            .background(if (sel) FaColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { scope.launch { repo.setTheme(m) } },
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text(
                            if (m == de.fapdf.data.ThemeMode.SYSTEM) "System" else m.title,
                            color = if (sel) FaColors.OnAccent else FaColors.SecondaryLabel,
                            fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        )
                    }
                }
            }
            SectionTitle("Datenschutz")
            GroupCard {
                GroupRow(
                    "Profile & Unterschriften sperren", "Fingerabdruck, Gesicht oder Geräte-PIN erforderlich", showDivider = false,
                    trailing = {
                        Switch(checked = s.biometricLock, onCheckedChange = { on ->
                            Biometric.authenticate(context, "Einstellung bestätigen") { ok -> if (ok) scope.launch { repo.setBiometric(on) } }
                        })
                    },
                )
            }
            SectionTitle("KI-Funktionen (optional)")
            Text(
                "Die KI-Funktionen „Mit PDFs chatten“, „Zusammenfassung“, „Fragen-Generator“ und die KI-Übersetzung senden Dokumenttext an den gewählten Anbieter – " +
                    "immer erst nach Ihrer ausdrücklichen Zustimmung. Kostenlos geht es mit „Groq“ (Schlüssel von console.groq.com) oder ganz privat mit „FA KI“ auf diesem Handy. Zwischen beiden einfach umschalten und speichern. " +
                    "Der Schlüssel wird verschlüsselt auf diesem Gerät gespeichert. Die Offline-Übersetzung und alle übrigen Werkzeuge funktionieren ohne KI.",
                Modifier.padding(horizontal = 20.dp), color = FaColors.SecondaryLabel,
            )
            GroupCard(Modifier.padding(top = 10.dp)) {
                AiProvider.entries.forEach { p ->
                    GroupRow(p.title, p.endpointHost + if (p in s.savedKeys) " · Schlüssel gespeichert ✓" else "", trailing = {
                        androidx.compose.material3.RadioButton(selected = provider == p, onClick = { provider = p; model = p.defaultModel; scope.launch { repo.setAi(p, p.defaultModel, null); FileActions.toast(context, "${p.title.substringBefore(" –")} ausgewählt") } })
                    }) {
                        // Antippen wählt den Dienst sofort aus; gespeicherte Schlüssel bleiben erhalten
                        provider = p; model = p.defaultModel
                        scope.launch { repo.setAi(p, p.defaultModel, null); FileActions.toast(context, "${p.title.substringBefore(" –")} ausgewählt") }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (provider == AiProvider.CUSTOM) OutlinedTextField(
                    baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Adresse") },
                    supportingText = { Text("Adresse eines OpenAI-kompatiblen Dienstes, endet meist auf /v1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (provider == AiProvider.PC) {
                    var pcUrl by remember(s.pcUrl) { mutableStateOf(s.pcUrl.ifBlank { "http://192.168.178.20:11434/v1" }) }
                    var pcModel by remember(s.pcModel) { mutableStateOf(s.pcModel) }
                    Text(
                        "Antworten kommen von Ihrem eigenen PC mit Ollama – privat, ohne Limit. Auf dem PC Ollama starten " +
                            "(Umgebungsvariable OLLAMA_HOST=0.0.0.0) und das Modell laden. Unterwegs: Tailscale auf PC und Handy, dann die Tailscale-Adresse eintragen. " +
                            "Ist der PC aus, springt die App automatisch auf Groq bzw. Gemini Nano.",
                        color = FaColors.SecondaryLabel, modifier = Modifier.padding(vertical = 4.dp),
                    )
                    OutlinedTextField(pcUrl, { pcUrl = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Adresse des PCs") },
                        supportingText = { Text("z. B. http://192.168.178.20:11434/v1 oder http://100.x.y.z:11434/v1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(pcModel, { pcModel = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Modell auf dem PC") },
                        supportingText = { Text("z. B. gpt-oss:20b oder gpt-oss:120b") })
                    TextButton(onClick = { scope.launch { repo.setPc(pcUrl, pcModel); FileActions.toast(context, "PC gespeichert") } }) { Text("PC-Adresse speichern") }
                } else if (provider == AiProvider.NANO) {
                    Text(
                        "Antworten kommen von Gemini Nano, der KI, die in Android auf diesem Handy eingebaut ist – " +
                            "kein Schlüssel, kein Internet, Dokumente verlassen das Handy nicht. Gut für kurze Dokumente " +
                            "(etwa 3–4 Seiten) und Zusammenfassungen.",
                        color = FaColors.SecondaryLabel, modifier = Modifier.padding(vertical = 4.dp),
                    )
                    var nanoState by remember { mutableStateOf<String?>(null) }
                    TextButton(onClick = { nanoState = "Wird geprüft …"; scope.launch { nanoState = de.fapdf.ai.GeminiNano.statusText() } }) { Text("Gemini Nano prüfen") }
                    nanoState?.let { Text(it, color = FaColors.SecondaryLabel) }
                } else if (provider == AiProvider.GEMMA) {
                    Text(
                        "Gemma 4 (Google) und Qwen 3.5 (Alibaba) sind frei verfügbare Modelle, die komplett auf diesem Handy laufen – ohne Internet, " +
                            "Dokumente verlassen das Handy nicht. Anders als Gemini Nano lesen sie auch lange Dokumente (10–12 Seiten) auf einmal. " +
                            "Einmaliger Download, am besten im WLAN. Das ausgewählte Modell (Punkt links) wird verwendet.",
                        color = FaColors.SecondaryLabel, modifier = Modifier.padding(vertical = 4.dp),
                    )
                    GemmaModelCard()
                } else if (provider == AiProvider.FAKI) Text(
                    "Antworten kommen von der App FA KI auf diesem Handy. Dort ein Modell herunterladen und einschalten – " +
                        "kein Schlüssel und kein Internet nötig. Dokumente verlassen das Handy nicht.",
                    color = FaColors.SecondaryLabel, modifier = Modifier.padding(vertical = 4.dp),
                ) else {
                    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Modellname") },
                        supportingText = { Text(when (provider) {
                            AiProvider.OPENAI -> "Modellname laut OpenAI-Dokumentation eintragen"
                            AiProvider.CUSTOM -> "Modellname laut Anbieter"
                            else -> "Vorgabe: ${provider.defaultModel}"
                        }) })
                    OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (provider in s.savedKeys) "Schlüssel für ${provider.title.substringBefore(" –")} gespeichert ✓ (zum Ändern neu eingeben)" else "API-Schlüssel für ${provider.title.substringBefore(" –")}") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (provider.pricingUrl.isNotBlank()) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.pricingUrl))) }) {
                        Text(if (provider == AiProvider.GROQ) "Kostenlosen Groq-Schlüssel holen" else "Preise von ${provider.title} ansehen")
                    }
                }
            }
            if (provider != AiProvider.GEMMA) GemmaInstalledHint()
            PrimaryButton("KI-Einstellungen speichern", Modifier.padding(horizontal = 16.dp)) {
                scope.launch {
                    repo.setAi(provider, model.ifBlank { provider.defaultModel }, if (provider == AiProvider.CUSTOM) baseUrl else null)
                    if (key.isNotBlank()) repo.setApiKey(provider, key)
                    key = ""
                    FileActions.toast(context, "Gespeichert")
                }
            }
            if (provider in s.savedKeys) TextButton(onClick = { scope.launch { repo.setApiKey(provider, null) } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Schlüssel für ${provider.title.substringBefore(" –")} löschen", color = FaColors.Destructive)
            }
            askPw?.let { mode ->
                de.fapdf.ui.components.PasswordDialog(
                    if (mode == "create") "Passwort für die Sicherung" else "Passwort der Sicherung",
                    if (mode == "create") "Ohne dieses Passwort lässt sich die Sicherung nicht öffnen. Bitte gut merken." else "Bitte das Passwort eingeben, mit dem die Sicherung erstellt wurde.",
                    onConfirm = { pw ->
                        askPw = null
                        if (mode == "create") {
                            pendingPw = pw
                            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY).format(java.util.Date())
                            createLauncher.launch("FA-PDF-Sicherung-$stamp.fapdf")
                        } else restoreUri?.let { uri ->
                            scope.launch {
                                backupBusy = "Sicherung wird wiederhergestellt …"
                                try {
                                    val r = context.contentResolver.openInputStream(uri)!!.use { de.fapdf.data.Backup.restore(appVm.container, pw, it, context.cacheDir) }
                                    FileActions.toast(context, "Wiederhergestellt: ${r.documents} Dokumente, ${r.profiles} Profile, ${r.signatures} Unterschriften")
                                } catch (e: Exception) { FileActions.toast(context, e.message ?: "Wiederherstellen fehlgeschlagen") } finally { backupBusy = null }
                            }
                        }
                    },
                    onDismiss = { askPw = null },
                )
            }
            backupBusy?.let { de.fapdf.ui.components.BusyDialog(it) }
            SectionTitle("Über FA PDF")
            GroupCard {
                GroupRow("Version", BuildConfig.VERSION_NAME)
                GroupRow("PDF-Engine", "PdfBox-Android (Apache License 2.0)")
                GroupRow("Texterkennung, Scanner, Übersetzung", "Google ML Kit (auf dem Gerät)")
                GroupRow("Ersatzschriften", "Liberation Sans/Serif/Mono (SIL Open Font License)", showDivider = false)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(30.dp))
        }
    }
}


/** Herunterladen / Fortschritt / Löschen / Auswahl der lokalen Gemma-Modelle (E2B und E4B). */
@Composable
private fun GemmaModelCard(onlyInstalled: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var askDelete by remember { mutableStateOf<de.fapdf.ai.GemmaLocal.Variant?>(null) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); tick++ } }
    val sel = remember(tick) { de.fapdf.ai.GemmaLocal.selected(context) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        de.fapdf.ai.GemmaLocal.Variant.entries.forEach { v ->
            val installed = remember(tick) { de.fapdf.ai.GemmaLocal.installed(context, v) }
            val st = remember(tick) { de.fapdf.ai.GemmaLocal.downloadState(context, v) }
            if (onlyInstalled && !installed && st.fraction == null) return@forEach
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (installed) androidx.compose.material3.RadioButton(selected = sel == v, onClick = { de.fapdf.ai.GemmaLocal.select(context, v); tick++ })
                    Column(Modifier.weight(1f)) {
                        Text(v.title + " · " + v.sizeText, fontWeight = FontWeight.SemiBold, color = FaColors.Label)
                        Text(v.note, color = FaColors.SecondaryLabel, fontSize = 12.sp)
                    }
                }
                when {
                    installed -> {
                        Text("✓ Geladen" + (if (sel == v) " · wird verwendet" else "") +
                            de.fapdf.ai.GemmaLocal.backendName.takeIf { it.isNotBlank() && sel == v }?.let { " · läuft auf: $it" }.orEmpty(),
                            color = FaColors.Success, fontSize = 13.sp)
                        TextButton(onClick = { askDelete = v }) { Text("Löschen und ${v.sizeText} freigeben", color = FaColors.Destructive) }
                    }
                    st.fraction != null -> {
                        Text("Wird geladen … ${(st.fraction * 100).toInt()} % von ${v.sizeText}", color = FaColors.Label, fontSize = 13.sp)
                        androidx.compose.material3.LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = FaColors.Accent)
                        TextButton(onClick = { de.fapdf.ai.GemmaLocal.cancelDownload(context, v); tick++ }) { Text("Download abbrechen", color = FaColors.Destructive) }
                    }
                    else -> {
                        st.error?.let { Text(it, color = FaColors.Destructive, fontSize = 13.sp) }
                        TextButton(onClick = { de.fapdf.ai.GemmaLocal.startDownload(context, v); de.fapdf.ai.GemmaLocal.select(context, v); tick++ }) {
                            Text("${v.title} herunterladen (${v.sizeText})")
                        }
                    }
                }
            }
        }
        Text("Downloads laufen im Hintergrund weiter, auch wenn Sie die App verlassen.", color = FaColors.SecondaryLabel, fontSize = 12.sp)
    }
    askDelete?.let { v ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askDelete = null },
            title = { Text("${v.title} löschen?") },
            text = { Text("Das Modell wird vom Handy entfernt (${v.sizeText} frei). Sie können es jederzeit wieder herunterladen.") },
            confirmButton = { TextButton(onClick = { askDelete = null; scope.launch { de.fapdf.ai.GemmaLocal.delete(context, v); tick++; FileActions.toast(context, "${v.title} gelöscht") } }) { Text("Löschen", color = FaColors.Destructive) } },
            dismissButton = { TextButton(onClick = { askDelete = null }) { Text("Abbrechen") } },
        )
    }
}

/** Gemma geladen, aber gerade ein anderer Dienst gewählt: trotzdem Löschen anbieten. */
@Composable
private fun GemmaInstalledHint() {
    val context = LocalContext.current
    if (!remember { de.fapdf.ai.GemmaLocal.anyInstalled(context) }) return
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Geladene KI-Modelle auf diesem Handy", fontWeight = FontWeight.SemiBold, color = FaColors.Label, modifier = Modifier.padding(top = 8.dp))
        GemmaModelCard(onlyInstalled = true)
    }
}
