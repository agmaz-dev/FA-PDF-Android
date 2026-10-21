package de.fapdf.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.fapdf.data.PersonProfile
import de.fapdf.data.SignatureEntity
import de.fapdf.pdf.Alignment as TextAlignment
import de.fapdf.pdf.AutofillSuggestion
import de.fapdf.pdf.FieldKind
import de.fapdf.pdf.FieldValue
import de.fapdf.pdf.FontFamily
import de.fapdf.pdf.FormFieldInfo
import de.fapdf.pdf.TextStyle
import de.fapdf.ui.WorkspaceMode
import de.fapdf.ui.components.Biometric
import de.fapdf.ui.components.PrimaryButton
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val textColors = listOf(0xFF000000.toInt(), 0xFF3A3A3C.toInt(), 0xFF0A3D91.toInt(), 0xFFC62828.toInt(), 0xFF1B5E20.toInt())

private fun colorName(c: Int): String = when (c and 0xFFFFFF) {
    0x000000 -> "schwarz"
    0xFFFFFF -> "weiß"
    else -> String.format("#%06X", c and 0xFFFFFF)
}

/** Formatierung: Originalschrift, Familie, Fett/Kursiv, Größe, Farbe. */
@Composable
fun StyleEditor(style: TextStyle, allowOriginal: Boolean, detectedColor: Int?, onChange: (TextStyle) -> Unit) {
    Column {
        if (allowOriginal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Originalschrift verwenden", style = MaterialTheme.typography.bodyMedium)
                    Text("Eingebettete Schrift des Dokuments, sonst passende Ersatzschrift", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                }
                Switch(checked = style.useOriginalFont, onCheckedChange = { onChange(style.copy(useOriginalFont = it)) })
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FontFamily.entries.forEach { f ->
                FilterChip(
                    selected = style.family == f, onClick = { onChange(style.copy(family = f, useOriginalFont = false)) },
                    label = { Text(f.title) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = style.bold, onClick = { onChange(style.copy(bold = !style.bold, useOriginalFont = false)) }, label = { Text("Fett", fontWeight = FontWeight.Bold) })
            FilterChip(selected = style.italic, onClick = { onChange(style.copy(italic = !style.italic, useOriginalFont = false)) }, label = { Text("Kursiv") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onChange(style.copy(size = (style.size - 0.5f).coerceAtLeast(4f))) }) { Icon(Icons.Outlined.Remove, "Kleiner") }
            Text("${style.size} pt", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onChange(style.copy(size = (style.size + 0.5f).coerceAtMost(96f))) }) { Icon(Icons.Outlined.Add, "Größer") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Farbe", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
            val colors = (listOfNotNull(detectedColor) + textColors).distinct()
            colors.forEach { c ->
                Box(
                    Modifier.padding(4.dp).size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (style.colorArgb == c) 3.dp else 1.dp, if (style.colorArgb == c) FaColors.Accent else FaColors.Separator, CircleShape)
                        .clickable { onChange(style.copy(colorArgb = c)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTextSheet(vm: WorkspaceViewModel, sel: TextSelection) {
    var asBlock by remember(sel) { mutableStateOf(false) }
    var text by remember(sel, asBlock) { mutableStateOf(if (asBlock) sel.blockText else sel.text) }
    var style by remember(sel) { mutableStateOf(sel.detected.toTextStyle().copy(useOriginalFont = sel.canUseOriginalFont)) }
    var align by remember(sel) { mutableStateOf(sel.detected.alignment) }
    val d = sel.detected
    ModalBottomSheet(onDismissRequest = { vm.selection = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Text bearbeiten", style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append("Erkannt: ${d.fontName} · ${d.size} pt")
                    if (d.bold) append(" · fett"); if (d.italic) append(" · kursiv")
                    append(" · ${colorName(d.colorArgb)}")
                    if (!sel.isScan) append(if (d.embedded) " · eingebettet" else " · nicht eingebettet")
                },
                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
            )
            if (sel.isScan) Text(
                "Gescannte Seite: Die Pixel der Zeile werden entfernt und der neue Text mit einer Ersatzschrift gesetzt.",
                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (!sel.isScan && sel.blockLineIds.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !asBlock, onClick = { asBlock = false }, label = { Text("Nur diese Zeile") })
                    FilterChip(selected = asBlock, onClick = { asBlock = true }, label = { Text("Ganzer Absatz (${sel.blockLineIds.size} Zeilen)") })
                }
            }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = if (asBlock) 4 else 2, label = { Text("Text") })
            Spacer(Modifier.height(8.dp))
            StyleEditor(style, allowOriginal = sel.canUseOriginalFont, detectedColor = d.colorArgb) { style = it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TextAlignment.LEFT to "Links", TextAlignment.CENTER to "Mitte", TextAlignment.RIGHT to "Rechts").forEach { (a, t) ->
                    FilterChip(selected = align == a, onClick = { align = a }, label = { Text(t) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.deleteSelection(sel, asBlock) }) { Text("Löschen", color = FaColors.Destructive) }
                if (!sel.isScan && !asBlock) OutlinedButton(onClick = { vm.startMove(sel) }) { Text("Verschieben") }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Übernehmen", enabled = text.isNotBlank()) { vm.applyTextEdit(sel, text, style, align, asBlock) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AddTextDialog(initial: TextStyle, onDismiss: () -> Unit, onConfirm: (String, TextStyle) -> Unit) {
    var text by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(initial.copy(useOriginalFont = false)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text hinzufügen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Text") })
                Spacer(Modifier.height(8.dp))
                StyleEditor(style, allowOriginal = false, detectedColor = null) { style = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text, style) }, enabled = text.isNotBlank()) { Text("Einfügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun StyleDialog(initial: TextStyle, onDismiss: () -> Unit, onConfirm: (TextStyle) -> Unit) {
    var style by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schrift für neuen Text") },
        text = { StyleEditor(style, allowOriginal = false, detectedColor = null) { style = it } },
        confirmButton = { TextButton(onClick = { onConfirm(style) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun TextInputDialog(title: String, message: String?, label: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) { Text(message, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)) }
                OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), label = { Text(label) })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(t) }, enabled = t.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) =
    TextInputDialog("Notiz hinzufügen", null, "Notiz", onDismiss = onDismiss, onConfirm = onConfirm)

@Composable
fun PageJumpDialog(pageCount: Int, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    var t by remember { mutableStateOf("") }
    val n = t.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu Seite springen") },
        text = {
            OutlinedTextField(
                t, { t = it.filter(Char::isDigit) }, label = { Text("Seite (1–$pageCount)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onJump(n!! - 1) }, enabled = n != null && n in 1..pageCount) { Text("Springen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun FieldDialog(field: FormFieldInfo, current: String, onDismiss: () -> Unit, onConfirm: (FieldValue) -> Unit) {
    var text by remember { mutableStateOf(current.ifEmpty { field.value }) }
    var checked by remember { mutableStateOf(if (current.isNotEmpty()) current == "✓" else field.checked) }
    var choice by remember { mutableStateOf(current.ifEmpty { field.value }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.label.ifBlank { "Feld" }) },
        text = {
            Column {
                when (field.kind) {
                    FieldKind.CHECKBOX -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { checked = it }); Text("Angekreuzt")
                    }
                    FieldKind.RADIO, FieldKind.CHOICE -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(field.options.indices.toList()) { i ->
                            Row(Modifier.fillMaxWidth().clickable { choice = field.options[i] }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = choice == field.options[i], onClick = { choice = field.options[i] })
                                Text(field.optionLabels.getOrElse(i) { field.options[i] })
                            }
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            text, { if (field.maxLength <= 0 || it.length <= field.maxLength) text = it }, Modifier.fillMaxWidth(),
                            minLines = if (field.kind == FieldKind.MULTILINE) 3 else 1,
                            placeholder = { if (field.kind == FieldKind.DATE) Text("TT.MM.JJJJ") },
                            keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == FieldKind.DATE) KeyboardType.Number else KeyboardType.Text),
                        )
                        if (field.kind == FieldKind.DATE) TextButton(onClick = { text = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date()) }) { Text("Heute einsetzen") }
                        if (field.maxLength > 0) Text("Max. ${field.maxLength} Zeichen", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    when (field.kind) {
                        FieldKind.CHECKBOX -> FieldValue.Bool(checked)
                        FieldKind.RADIO, FieldKind.CHOICE -> FieldValue.Choice(choice)
                        else -> FieldValue.Text(text)
                    }
                )
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun AutofillPreviewDialog(profileLabel: String, list: List<AutofillSuggestion>, onDismiss: () -> Unit, onAccept: (List<AutofillSuggestion>) -> Unit) {
    val checked = remember { mutableStateListOf(*list.indices.map { true }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Werte aus „$profileLabel“ prüfen") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list.indices.toList()) { i ->
                    val s = list[i]
                    Row(Modifier.fillMaxWidth().clickable { checked[i] = !checked[i] }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                        Column {
                            Text(s.fieldLabel, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                            Text(s.value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(color = FaColors.Separator)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAccept(list.filterIndexed { i, _ -> checked[i] }) }) { Text("Einfügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun LabelSuggestionDialog(list: List<LabelSuggestion>, onDismiss: () -> Unit, onAccept: (List<LabelSuggestion>) -> Unit) {
    val checked = remember { mutableStateListOf(*list.indices.map { true }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gefundene Beschriftungen") },
        text = {
            Column {
                Text("Die Werte werden rechts neben die Beschriftung geschrieben. Bitte prüfen Sie die Position danach in der Vorschau (Rückgängig ist möglich).", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list.indices.toList()) { i ->
                        val s = list[i]
                        Row(Modifier.fillMaxWidth().clickable { checked[i] = !checked[i] }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                            Column {
                                Text("${s.label} (Seite ${s.page + 1})", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                                Text(s.value)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAccept(list.filterIndexed { i, _ -> checked[i] }) }) { Text("Einfügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSheet(onDismiss: () -> Unit, onPage: () -> Unit, onDocument: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Texterkennung (OCR)", style = MaterialTheme.typography.titleMedium)
            Text("Läuft vollständig auf dem Gerät (Deutsch, Englisch, Türkisch u. a.). Es werden keine Daten übertragen.", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
            Spacer(Modifier.height(12.dp))
            SheetItem("Diese Seite erkennen und bearbeiten", "Auch für Text, der als Bild oder in eingebetteten Objekten vorliegt", onPage)
            SheetItem("Ganzes Dokument durchsuchbar machen", "Fügt eine unsichtbare Textebene hinzu – das Aussehen bleibt unverändert", onDocument)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetItem(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
    }
    HorizontalDivider(color = FaColors.Separator)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(vm: WorkspaceViewModel, currentPage: Int, onDismiss: () -> Unit, onPrint: () -> Unit, onShare: () -> Unit, onExport: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Weitere Werkzeuge", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                WorkspaceMode.READ to "Lesen", WorkspaceMode.EDIT to "Text bearbeiten", WorkspaceMode.ANNOTATE to "Kommentieren",
                WorkspaceMode.FORM to "Formular ausfüllen", WorkspaceMode.SIGN to "Unterschreiben", WorkspaceMode.REDACT to "Schwärzen",
            ).filter { it.first != vm.mode }.forEach { (m, t) -> SheetItem(t) { onDismiss(); vm.switchMode(m) } }
            SheetItem("Seite ${currentPage + 1} um 90° drehen") { onDismiss(); vm.rotatePage(currentPage) }
            SheetItem("Drucken") { onDismiss(); onPrint() }
            SheetItem("Weitergeben (E-Mail, Messenger …)") { onDismiss(); onShare() }
            SheetItem("Als neue PDF exportieren", "Speicherort selbst wählen") { onDismiss(); onExport() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(hasSource: Boolean, onSave: (Boolean) -> Unit, onExport: () -> Unit, onPrint: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Speichern", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SheetItem("In FA PDF speichern", "Aktualisiert das Dokument in „Letzte Dokumente“") { onSave(false) }
            if (hasSource) SheetItem("Speichern und Originaldatei überschreiben", "Die ursprünglich geöffnete Datei wird ersetzt") { onSave(true) }
            SheetItem("Als neue PDF exportieren", "Speicherort und Namen wählen") { onExport() }
            SheetItem("Drucken") { onPrint() }
            SheetItem("Weitergeben") { onShare() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureSheet(vm: WorkspaceViewModel, onDismiss: () -> Unit, onNew: () -> Unit, onPick: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sigs by vm.signatures().collectAsState(initial = emptyList())
    var locked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { locked = vm.settings().biometricLock }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Unterschrift wählen", style = MaterialTheme.typography.titleMedium)
            Text("Die Unterschrift wird erst eingesetzt, wenn Sie die Position bestätigen. Es handelt sich um eine einfache elektronische Unterschrift (Bild), nicht um eine qualifizierte Signatur.", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
            Spacer(Modifier.height(8.dp))
            if (sigs.isEmpty()) Text("Noch keine Unterschrift gespeichert.", modifier = Modifier.padding(vertical = 12.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(sigs, key = { it.id }) { s ->
                    SignatureRow(vm, s, showThumb = locked == false) {
                        val load: () -> Unit = { scope.launch { vm.loadSignatureBitmap(s)?.let(onPick) } }
                        if (locked == true) Biometric.authenticate(context, "Unterschrift freigeben") { ok -> if (ok) load() } else load()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Neue Unterschrift zeichnen") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SignatureRow(vm: WorkspaceViewModel, s: SignatureEntity, showThumb: Boolean, onClick: () -> Unit) {
    var thumb by remember(s.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(s.id, showThumb) { if (showThumb) thumb = vm.loadSignatureBitmap(s) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 110.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F2F7)), contentAlignment = Alignment.Center) {
            val t = thumb
            if (t != null) Image(t.asImageBitmap(), contentDescription = s.name, modifier = Modifier.padding(4.dp))
            else Text("Geschützt", color = FaColors.SecondaryLabel, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Text(s.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ProfilePickerDialog(vm: WorkspaceViewModel, onDismiss: () -> Unit, onManage: () -> Unit, onPick: (PersonProfile) -> Unit) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf<List<PersonProfile>?>(null) }
    var denied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val locked = vm.settings().biometricLock
        if (locked) {
            Biometric.authenticate(context, "Profile entsperren") { ok ->
                if (!ok) denied = true else scope.launch { profiles = vm.loadProfiles() }
            }
        } else profiles = vm.loadProfiles()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil auswählen") },
        text = {
            val list = profiles
            when {
                denied -> Text("Zugriff verweigert.")
                list == null -> Text("Profile werden geladen …")
                list.isEmpty() -> Text("Es ist noch kein Profil angelegt. Legen Sie Profile für sich und Ihre Familie an, um Formulare automatisch auszufüllen.")
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.id }) { p ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp)) {
                            Text(p.label, style = MaterialTheme.typography.bodyLarge)
                            Text(p.fullName, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                        }
                        HorizontalDivider(color = FaColors.Separator)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onManage) { Text("Profile verwalten") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(vm: WorkspaceViewModel, currentPage: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val marks by vm.bookmarks().collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lesezeichen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.addBookmark(currentPage) }) { Text("Seite ${currentPage + 1} merken") }
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (marks.isEmpty()) item { Text("Keine eigenen Lesezeichen.", color = FaColors.SecondaryLabel, modifier = Modifier.padding(vertical = 8.dp)) }
                items(marks, key = { it.id }) { b ->
                    Row(Modifier.fillMaxWidth().clickable { onGo(b.pageIndex) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.label, Modifier.weight(1f))
                        IconButton(onClick = { vm.deleteBookmark(b.id) }) { Icon(Icons.Outlined.Delete, "Löschen", tint = FaColors.Destructive) }
                    }
                }
                if (vm.outline.isNotEmpty()) {
                    item { Text("Inhaltsverzeichnis des Dokuments", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                    items(vm.outline) { (title, page) ->
                        Row(Modifier.fillMaxWidth().clickable { onGo(page) }.padding(vertical = 8.dp)) {
                            Text(title, Modifier.weight(1f)); Text("S. ${page + 1}", color = FaColors.SecondaryLabel)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun RedactOptionsDialog(count: Int, onDismiss: () -> Unit, onApply: (Boolean, Boolean) -> Unit) {
    var raster by remember { mutableStateOf(false) }
    var meta by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$count Bereich(e) schwärzen") },
        text = {
            Column {
                Text("Der Inhalt unter den Bereichen wird dauerhaft aus der Datei entfernt – nicht nur verdeckt. Dies kann nach dem Speichern nicht rückgängig gemacht werden.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(raster, { raster = it }); Text("Betroffene Seiten zusätzlich als Bild neu aufbauen (höchste Sicherheit, Text dieser Seiten ist danach nicht mehr markierbar)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(meta, { meta = it }); Text("Dokument-Metadaten (Autor, Titel …) entfernen")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(raster, meta) }) { Text("Dauerhaft schwärzen", color = FaColors.Destructive) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
