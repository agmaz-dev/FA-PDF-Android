package de.fa.taximeter.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.tariff.FareEngine
import de.fa.taximeter.domain.tariff.TariffProfile
import de.fa.taximeter.domain.trip.SurchargeDef
import de.fa.taximeter.domain.trip.SurchargeSelection
import de.fa.taximeter.ui.Format
import de.fa.taximeter.ui.theme.MeterColors

@Composable
private fun MeterDialogFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(12.dp))
                .border(2.dp, MeterColors.Frame, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(title, color = MeterColors.Label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun MeterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MeterColors.Label) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .border(2.dp, color, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Tarifauswahl (nur im Zustand FREI). */
@Composable
fun TariffDialog(
    options: List<TariffProfile>,
    selectedNumber: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MeterDialogFrame("TARIF WÄHLEN", onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            options.forEach { p ->
                val sel = p.displayNumber == selectedNumber
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(2.dp, if (sel) MeterColors.Label else MeterColors.Dim, RoundedCornerShape(8.dp))
                        .clickable(role = Role.RadioButton) { onSelect(p.displayNumber) }
                        .padding(12.dp)
                ) {
                    Text(
                        p.label + if (p.isCustom) "  (BENUTZERDEFINIERT)" else "",
                        color = if (sel) MeterColors.Label else MeterColors.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(p.officialName, color = MeterColors.Inactive, fontSize = 14.sp)
                    Text(tariffSummary(p), color = MeterColors.White, fontSize = 14.sp)
                    Text(p.scope, color = MeterColors.Inactive, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            MeterButton("SCHLIESSEN", onDismiss, Modifier.align(Alignment.End), MeterColors.Inactive)
        }
    }
}

fun tariffSummary(p: TariffProfile): String {
    val bands = p.bands.joinToString(" · ") { b ->
        val from = Format.decimal(b.fromM / 1000.0, 0)
        val range = if (b.toM == null) "ab $from km" else "$from–${Format.decimal(b.toM / 1000.0, 0)} km"
        "$range: ${Money.formatEuro(b.pricePerKm)}/km"
    }
    return "Grundpreis ${Money.formatEuro(p.baseFare)} · $bands · Wartezeit ${Money.formatEuro(p.waitingPerHour)}/h " +
        "(Umschaltung ab ca. ${Format.decimal(FareEngine.switchSpeedKmh(p, 0.0), 1)} km/h)"
}

/**
 * Zuschlagsauswahl. Öffnen/Schließen beeinflusst die laufende Fahrt nicht; jeder Zuschlag
 * kann nur einmal gewählt werden (Antippen = an/aus).
 */
@Composable
fun SurchargeDialog(
    available: List<SurchargeDef>,
    selection: SurchargeSelection,
    editable: Boolean,
    onToggle: (SurchargeDef) -> Unit,
    onCreateCustom: (label: String, amount: java.math.BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Auch bereits gewählte Zuschläge zeigen, die nicht (mehr) in der Liste stehen
    val all = available + selection.items.filter { s -> available.none { it.id == s.id } }

    MeterDialogFrame("ZUSCHLÄGE", onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Summe: ${Money.formatEuro(selection.total)}",
                color = MeterColors.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            all.forEach { def ->
                val on = selection.contains(def.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(2.dp, if (on) MeterColors.Label else MeterColors.Dim, RoundedCornerShape(8.dp))
                        .clickable(enabled = editable, role = Role.Checkbox) { onToggle(def) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .border(2.dp, if (on) MeterColors.Label else MeterColors.Inactive, RoundedCornerShape(4.dp))
                            .padding(5.dp)
                            .background(if (on) MeterColors.Label else Color.Transparent)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                def.displayLabel,
                                color = if (on) MeterColors.Label else MeterColors.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                Money.formatEuro(def.amount),
                                color = if (on) MeterColors.Label else MeterColors.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(def.condition, color = MeterColors.Inactive, fontSize = 12.sp)
                        if (on) Text("AUSGEWÄHLT – erneut tippen zum Entfernen", color = MeterColors.Label, fontSize = 12.sp)
                    }
                }
            }

            if (editable) {
                Spacer(Modifier.height(10.dp))
                Text("Benutzerdefinierter Zuschlag", color = MeterColors.Warn, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Nur für Fälle außerhalb der amtlichen Zuschläge. Wird ausdrücklich als „benutzerdefiniert“ gekennzeichnet.",
                    color = MeterColors.Inactive,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Bezeichnung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(8) },
                    label = { Text("Betrag in €, z. B. 2,50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MeterColors.Label, fontSize = 13.sp) }
                Spacer(Modifier.height(6.dp))
                MeterButton("HINZUFÜGEN", onClick = {
                    val v = Money.parseDe(amount)
                    when {
                        name.isBlank() -> error = "Bitte eine Bezeichnung eingeben."
                        v == null || v.signum() <= 0 || v > java.math.BigDecimal("999.99") -> error = "Bitte einen gültigen Betrag eingeben."
                        else -> {
                            onCreateCustom(name.trim(), Money.cents(v))
                            name = ""; amount = ""; error = null
                        }
                    }
                }, color = MeterColors.Warn)
            } else {
                Text("Fahrt abgeschlossen – Zuschläge nicht mehr änderbar.", color = MeterColors.Inactive, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            MeterButton("FERTIG", onDismiss, Modifier.align(Alignment.End))
        }
    }
}

/** Einfache Ja/Nein-Abfrage im Taximeter-Stil. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MeterDialogFrame(title, onDismiss) {
        Text(text, color = MeterColors.White, fontSize = 16.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MeterButton("ABBRECHEN", onDismiss, color = MeterColors.Inactive)
            Spacer(Modifier.width(12.dp))
            MeterButton(confirm, onConfirm)
        }
    }
}
