package de.fapdf.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
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
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(appVm: AppViewModel, onBack: (() -> Unit)?, onProfiles: () -> Unit = {}, onSignatures: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = appVm.container.settings
    val s by repo.settings.collectAsState(initial = AppSettings())
    var provider by remember { mutableStateOf(AiProvider.ANTHROPIC) }
    var model by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val cur = repo.current()
        provider = cur.aiProvider; model = cur.aiModel
    }
    ScreenScaffold("Einstellungen", onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("Persönliche Daten")
            GroupCard {
                GroupRow("Familienprofile", "Daten für das automatische Ausfüllen von Formularen", Icons.Outlined.People, onClick = onProfiles)
                GroupRow("Unterschriften", "Gespeicherte Unterschriften verwalten", Icons.Outlined.Draw, showDivider = false, onClick = onSignatures)
            }
            SectionTitle("Darstellung")
            GroupCard {
                de.fapdf.data.ThemeMode.entries.forEachIndexed { i, m ->
                    GroupRow(
                        m.title,
                        when (m) {
                            de.fapdf.data.ThemeMode.SYSTEM -> "Folgt der Einstellung des Geräts"
                            de.fapdf.data.ThemeMode.LIGHT -> "Helles Design"
                            de.fapdf.data.ThemeMode.DARK -> "Dunkles Design – schont die Augen bei wenig Licht"
                        },
                        showDivider = i < de.fapdf.data.ThemeMode.entries.size - 1,
                        trailing = { androidx.compose.material3.RadioButton(selected = s.themeMode == m, onClick = { scope.launch { repo.setTheme(m) } }) },
                    ) { scope.launch { repo.setTheme(m) } }
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
                    "immer erst nach Ihrer ausdrücklichen Zustimmung. Dafür ist ein eigener, kostenpflichtiger API-Schlüssel des Anbieters nötig; die Abrechnung erfolgt direkt beim Anbieter. " +
                    "Der Schlüssel wird verschlüsselt auf diesem Gerät gespeichert. Die Offline-Übersetzung und alle übrigen Werkzeuge funktionieren ohne KI.",
                Modifier.padding(horizontal = 20.dp), color = FaColors.SecondaryLabel,
            )
            GroupCard(Modifier.padding(top = 10.dp)) {
                AiProvider.entries.forEach { p ->
                    GroupRow(p.title, p.endpointHost, trailing = {
                        androidx.compose.material3.RadioButton(selected = provider == p, onClick = { provider = p; model = p.defaultModel })
                    }) { provider = p; model = p.defaultModel }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Modellname") },
                    supportingText = { Text(if (provider == AiProvider.OPENAI) "Modellname laut OpenAI-Dokumentation eintragen" else "Vorgabe: ${provider.defaultModel}") })
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (s.hasAiKey) "API-Schlüssel (gespeichert – zum Ändern neu eingeben)" else "API-Schlüssel") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.pricingUrl))) }) { Text("Preise von ${provider.title} ansehen") }
            }
            PrimaryButton("KI-Einstellungen speichern", Modifier.padding(horizontal = 16.dp)) {
                scope.launch {
                    repo.setAi(provider, model)
                    if (key.isNotBlank()) repo.setApiKey(key)
                    key = ""
                    FileActions.toast(context, "Gespeichert")
                }
            }
            if (s.hasAiKey) TextButton(onClick = { scope.launch { repo.setApiKey(null) } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("API-Schlüssel löschen", color = FaColors.Destructive)
            }
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
