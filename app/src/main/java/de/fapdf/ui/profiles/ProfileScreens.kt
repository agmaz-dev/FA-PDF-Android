package de.fapdf.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.fapdf.data.CustomValue
import de.fapdf.data.PersonProfile
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.components.Biometric
import de.fapdf.ui.components.ConfirmDialog
import de.fapdf.ui.components.GroupCard
import de.fapdf.ui.components.GroupRow
import de.fapdf.ui.components.InfoBanner
import de.fapdf.ui.components.PrimaryButton
import de.fapdf.ui.components.ScreenScaffold
import de.fapdf.ui.components.SectionTitle
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Gemeinsame Sperre: Wenn Biometrie aktiviert ist, erst nach erfolgreicher Prüfung anzeigen. */
@Composable
fun LockGate(appVm: AppViewModel, title: String, onDenied: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        val locked = appVm.container.settings.settings.first().biometricLock
        if (!locked) unlocked = true
        else Biometric.authenticate(context, title) { ok -> if (ok) unlocked = true else { unlocked = false; onDenied() } }
    }
    if (unlocked == true) content()
}

@Composable
fun ProfilesScreen(appVm: AppViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val labels by appVm.container.profiles.observeLabels().collectAsState(initial = emptyList())
    LockGate(appVm, "Profile entsperren", onBack) {
        ScreenScaffold("Familienprofile", onBack) { mod ->
            Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
                InfoBanner("Profile werden nur auf diesem Gerät gespeichert und mit einem Schlüssel aus dem Android-Keystore verschlüsselt. Sie werden nie automatisch übertragen.")
                SectionTitle("Profile")
                GroupCard {
                    labels.forEach { (id, label) -> GroupRow(label, icon = Icons.Outlined.Person) { onEdit(id) } }
                    GroupRow("Neues Profil", icon = Icons.Outlined.Add, iconColor = FaColors.Success, showDivider = false) { onEdit(0) }
                }
                Text(
                    "Tipp: Legen Sie z. B. „Ich“, „Ehepartner“, „Kind 1“ an. Beim Ausfüllen eines Formulars schlägt FA PDF passende Werte vor – Sie prüfen sie, bevor sie eingesetzt werden.",
                    Modifier.padding(20.dp), color = FaColors.SecondaryLabel,
                )
            }
        }
    }
}

@Composable
fun ProfileEditScreen(appVm: AppViewModel, id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == 0L) }
    var p by remember { mutableStateOf(PersonProfile()) }
    val custom = remember { mutableStateListOf<CustomValue>() }
    var confirmDelete by remember { mutableStateOf(false) }
    LockGate(appVm, "Profil entsperren", onBack) {
        // Entschlüsselung erst nach erfolgreicher Freigabe
        LaunchedEffect(id) {
            if (id != 0L) {
                appVm.container.profiles.load(id)?.let { p = it; custom.clear(); custom.addAll(it.custom) }
                loaded = true
            }
        }
        ScreenScaffold(if (id == 0L) "Neues Profil" else p.label, onBack, actions = {
            TextButton(onClick = {
                scope.launch { appVm.container.profiles.save(p.copy(custom = custom.filter { it.key.isNotBlank() })); onBack() }
            }) { Text("Sichern") }
        }) { mod ->
            if (!loaded) return@ScreenScaffold
            Column(mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Field("Bezeichnung (z. B. Ich, Ehepartner, Kind 1)", p.label) { p = p.copy(label = it) }
                SectionTitle("Person", Modifier.padding(start = 0.dp))
                Field("Vorname", p.firstName) { p = p.copy(firstName = it) }
                Field("Nachname", p.lastName) { p = p.copy(lastName = it) }
                Field("Geburtsdatum (TT.MM.JJJJ)", p.birthDate, KeyboardType.Number) { p = p.copy(birthDate = it) }
                Field("Geburtsort", p.birthPlace) { p = p.copy(birthPlace = it) }
                SectionTitle("Anschrift", Modifier.padding(start = 0.dp))
                Row {
                    Column(Modifier.weight(3f)) { Field("Straße", p.street) { p = p.copy(street = it) } }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1.3f)) { Field("Nr.", p.houseNumber) { p = p.copy(houseNumber = it) } }
                }
                Row {
                    Column(Modifier.weight(1.3f)) { Field("PLZ", p.zip, KeyboardType.Number) { p = p.copy(zip = it) } }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(3f)) { Field("Ort", p.city) { p = p.copy(city = it) } }
                }
                Field("Land", p.country) { p = p.copy(country = it) }
                SectionTitle("Kontakt", Modifier.padding(start = 0.dp))
                Field("Telefon", p.phone, KeyboardType.Phone) { p = p.copy(phone = it) }
                Field("E-Mail", p.email, KeyboardType.Email) { p = p.copy(email = it) }
                SectionTitle("Weitere Werte (z. B. Versichertennummer, Steuer-ID)", Modifier.padding(start = 0.dp))
                custom.forEachIndexed { i, cv ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Field("Bezeichnung", cv.key) { custom[i] = cv.copy(key = it) } }
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1.3f)) { Field("Wert", cv.value) { custom[i] = cv.copy(value = it) } }
                        IconButton(onClick = { custom.removeAt(i) }) { Icon(Icons.Outlined.Close, "Entfernen") }
                    }
                }
                TextButton(onClick = { custom.add(CustomValue("", "")) }) { Text("+ Wert hinzufügen") }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Profil speichern") {
                    scope.launch { appVm.container.profiles.save(p.copy(custom = custom.filter { it.key.isNotBlank() })); onBack() }
                }
                if (id != 0L) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Profil löschen", color = FaColors.Destructive)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Profil löschen?", "Alle Angaben dieses Profils werden endgültig gelöscht.", "Löschen", destructive = true,
        onConfirm = { confirmDelete = false; scope.launch { appVm.container.profiles.delete(id); onBack() } },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth().padding(vertical = 3.dp), singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}
