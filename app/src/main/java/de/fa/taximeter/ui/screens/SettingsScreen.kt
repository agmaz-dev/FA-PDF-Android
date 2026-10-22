package de.fa.taximeter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.taximeter.data.OrientationMode
import de.fa.taximeter.data.Settings
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.tariff.DistanceBand
import de.fa.taximeter.domain.tariff.FareEngine
import de.fa.taximeter.domain.tariff.TariffCatalog
import de.fa.taximeter.domain.tariff.TariffProfile
import de.fa.taximeter.domain.trip.Phase
import de.fa.taximeter.ui.Format
import de.fa.taximeter.ui.theme.MeterColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActions(
    val setOrientation: (OrientationMode) -> Unit,
    val setKeepScreenOn: (Boolean) -> Unit,
    val setTestMode: (Boolean) -> Unit,
    val saveCustomTariff: (Int, TariffProfile) -> Unit,
    val removeCustomTariff: (Int) -> Unit,
    val setUseCustom: (Int, Boolean) -> Unit,
    val removeCustomSurcharge: (String) -> Unit,
    val message: (String) -> Unit,
    val onBack: () -> Unit,
)

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, color = MeterColors.Label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MeterColors.White else MeterColors.Dim, fontSize = 16.sp)
            subtitle?.let { Text(it, color = MeterColors.Inactive, fontSize = 12.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MeterColors.White,
                checkedTrackColor = MeterColors.Lit,
            ),
        )
    }
}

@Composable
fun SettingsScreen(settings: Settings, phase: Phase, actions: SettingsActions) {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MeterColors.Background)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EINSTELLUNGEN", color = MeterColors.Label, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            MeterButton("ZURÜCK", actions.onBack, color = MeterColors.Inactive)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {

            SectionTitle("Anzeige")
            Text("Ausrichtung", color = MeterColors.White, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                OrientationMode.entries.forEach { o ->
                    val sel = settings.orientation == o
                    MeterButton(
                        o.label,
                        { actions.setOrientation(o) },
                        Modifier.weight(1f).padding(end = 6.dp),
                        if (sel) MeterColors.Label else MeterColors.Inactive,
                    )
                }
            }
            SwitchRow(
                "Bildschirm während aktiver Fahrt anlassen",
                "Gilt für BESETZT, STOPP und die Abschlussanzeige, solange die App sichtbar ist.",
                settings.keepScreenOn,
                onChange = actions.setKeepScreenOn,
            )
            Text(
                "Farbe: rot (feste Gestaltung nach Referenz).",
                color = MeterColors.Inactive,
                fontSize = 12.sp,
            )

            SectionTitle("Testmodus")
            SwitchRow(
                "Simulation statt GPS",
                if (phase == Phase.FREI) "Erzeugt eine synthetische Stadtfahrt. Wird dauerhaft als SIMULATION angezeigt und in der Historie gekennzeichnet."
                else "Nur im Zustand FREI änderbar.",
                settings.testMode,
                enabled = phase == Phase.FREI,
                onChange = actions.setTestMode,
            )

            SectionTitle("Tarife")
            Text(
                "Tarifänderungen gelten nur für neue Fahrten. Laufende und gespeicherte Fahrten behalten ihre Tarifversion. " +
                    "Amtliche Ausgangsprofile werden nie überschrieben.",
                color = MeterColors.Inactive,
                fontSize = 12.sp,
            )
            TariffCatalog.OFFICIAL.forEach { official ->
                TariffCard(official, settings, actions, onOpenUrl = { runCatching { uri.openUri(it) } })
            }

            SectionTitle("Zuschläge (amtlich, § 5 Abs. 2)")
            TariffCatalog.SURCHARGES.forEach { s ->
                Text("${s.label} – ${Money.formatEuro(s.amount)}", color = MeterColors.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(s.condition, color = MeterColors.Inactive, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "Kein Zuschlag für gewöhnliche Kartenzahlung. Der BER-Zuschlag wird nie automatisch gebucht.",
                color = MeterColors.Inactive,
                fontSize = 12.sp,
            )
            if (settings.customSurcharges.isNotEmpty()) {
                SectionTitle("Benutzerdefinierte Zuschläge")
                settings.customSurcharges.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            "${s.displayLabel} – ${Money.formatEuro(s.amount)}",
                            color = MeterColors.Warn,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        MeterButton("ENTFERNEN", { actions.removeCustomSurcharge(s.id) }, color = MeterColors.Inactive)
                    }
                }
            }

            SectionTitle("Hinweise")
            Text(
                "FA Taximeter ist ein GPS-Fahrpreisrechner und kein geeichter, abrechnungsfähiger Taxameter. " +
                    "Alle Beträge sind Schätzungen. Standortdaten bleiben ausschließlich auf diesem Gerät; " +
                    "die App benötigt kein Internet, kein Konto und keinen Server.",
                color = MeterColors.Inactive,
                fontSize = 13.sp,
            )
            Text(
                "Nach erzwungenem Beenden der App oder einem Geräteneustart läuft keine Messung weiter. " +
                    "Eine gesicherte Fahrt wird beim nächsten Öffnen im Zustand STOPP angeboten; die nicht gemessene Zeit wird als Messlücke vermerkt.",
                color = MeterColors.Inactive,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun TariffCard(
    official: TariffProfile,
    settings: Settings,
    actions: SettingsActions,
    onOpenUrl: (String) -> Unit,
) {
    val n = official.displayNumber
    val custom = settings.customTariffs[n]
    var editing by remember(n) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MeterColors.Dim, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(official.label, color = MeterColors.Label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("${official.officialName} · interne Kennung ${official.id} · Version ${official.version}", color = MeterColors.Inactive, fontSize = 12.sp)
        Text(official.scope, color = MeterColors.Inactive, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(tariffSummary(official), color = MeterColors.White, fontSize = 14.sp)
        official.bands.forEach { b ->
            Text(
                "Teilstrecke (0,20 €) bei ${Money.formatEuro(b.pricePerKm)}/km: ${Format.decimal(FareEngine.stepLengthM(official, b), 2)} m · " +
                    "Umschaltgeschwindigkeit ${Format.decimal(official.waitingPerHour.toDouble() / b.pricePerKm.toDouble(), 2)} km/h",
                color = MeterColors.Inactive,
                fontSize = 12.sp,
            )
        }
        Text(
            "Zeit-Einheit (0,20 €): ${Format.decimal(FareEngine.stepSeconds(official), 2)} s · Karenz je Halt: ${official.waitingGraceMs / 1000} s",
            color = MeterColors.Inactive,
            fontSize = 12.sp,
        )
        Text("Berechnungsmodus: ${official.calculationMode.label}", color = MeterColors.Warn, fontSize = 12.sp)
        Text("Rechtsgrundlage: ${official.legalBasis}", color = MeterColors.Inactive, fontSize = 12.sp)
        Text("Geprüft am: ${official.verifiedOn}", color = MeterColors.White, fontSize = 12.sp)
        official.notes.forEach { Text("• $it", color = MeterColors.Inactive, fontSize = 12.sp) }
        Spacer(Modifier.height(4.dp))
        Text("Quellen:", color = MeterColors.White, fontSize = 12.sp)
        official.sources.forEach { s ->
            Text(
                s.title,
                color = MeterColors.Label,
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onOpenUrl(s.url) }
                    .padding(vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Benutzerdefinierte Kopie", color = MeterColors.Warn, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (custom != null) {
            Text(tariffSummary(custom), color = MeterColors.Warn, fontSize = 13.sp)
            Text("Stand: ${custom.verifiedOn}", color = MeterColors.Inactive, fontSize = 12.sp)
            SwitchRow(
                "Benutzerdefinierte Kopie für neue Fahrten verwenden",
                "Wird auf der Anzeige als BENUTZERDEFINIERT markiert.",
                n in settings.useCustom,
                onChange = { actions.setUseCustom(n, it) },
            )
        } else {
            Text("Keine Kopie vorhanden – es gilt das amtliche Ausgangsprofil.", color = MeterColors.Inactive, fontSize = 12.sp)
        }
        Row(Modifier.padding(top = 6.dp)) {
            MeterButton(if (custom == null) "KOPIE ANLEGEN" else "KOPIE BEARBEITEN", { editing = !editing }, color = MeterColors.Warn)
            if (custom != null) {
                Spacer(Modifier.width(8.dp))
                MeterButton("KOPIE LÖSCHEN", { actions.removeCustomTariff(n) }, color = MeterColors.Inactive)
            }
        }
        if (editing) {
            CustomTariffEditor(
                official = official,
                current = custom ?: official,
                onSave = {
                    actions.saveCustomTariff(n, it)
                    editing = false
                },
                onError = actions.message,
            )
        }
    }
}

@Composable
private fun CustomTariffEditor(
    official: TariffProfile,
    current: TariffProfile,
    onSave: (TariffProfile) -> Unit,
    onError: (String) -> Unit,
) {
    var base by remember { mutableStateOf(Money.formatDe(current.baseFare)) }
    var waiting by remember { mutableStateOf(Money.formatDe(current.waitingPerHour)) }
    val bandPrices = remember { current.bands.map { mutableStateOf(Money.formatDe(it.pricePerKm)) } }

    Column(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = base, onValueChange = { base = it.take(8) }, singleLine = true,
            label = { Text("Grundpreis €") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        official.bands.forEachIndexed { i, b ->
            val range = if (b.toM == null) "ab ${b.fromM / 1000} km" else "${b.fromM / 1000}–${b.toM / 1000} km"
            OutlinedTextField(
                value = bandPrices[i].value, onValueChange = { bandPrices[i].value = it.take(8) }, singleLine = true,
                label = { Text("€/km $range") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = waiting, onValueChange = { waiting = it.take(8) }, singleLine = true,
            label = { Text("Wartezeit €/h") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        MeterButton("KOPIE SPEICHERN", {
            val b = Money.parseDe(base)
            val w = Money.parseDe(waiting)
            val prices = bandPrices.map { Money.parseDe(it.value) }
            if (b == null || w == null || prices.any { it == null || it.signum() <= 0 } || b < official.stepAmount) {
                onError("Bitte gültige Beträge eingeben (Grundpreis mindestens 0,20 €, Kilometerpreise größer 0).")
            } else {
                val bands = official.bands.mapIndexed { i, band -> DistanceBand(band.fromM, band.toM, prices[i]!!) }
                val today = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
                onSave(TariffCatalog.customCopy(official, b, bands, w, today))
            }
        }, color = MeterColors.Warn)
    }
}
