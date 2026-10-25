package de.fa.taximeter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.trip.TripRecord
import de.fa.taximeter.ui.Format
import de.fa.taximeter.ui.components.SevenSegment
import de.fa.taximeter.ui.theme.MeterColors

/**
 * Bildschirmfüllende Abschlussanzeige (Fahrgastansicht). Alle Beträge stammen aus genau einem
 * gespeicherten [TripRecord]: Gesamtbetrag = Fahrpreis + Summe der Zuschläge.
 * Die Anzeige bleibt, bis bewusst [primaryLabel] gedrückt wird.
 */
@Composable
fun KasseScreen(record: TripRecord, primaryLabel: String, onPrimary: () -> Unit, onSecondary: (() -> Unit)? = null, secondaryLabel: String = "") {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MeterColors.Background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val landscape = maxWidth > maxHeight * 1.1f
        val scale = if (landscape) minOf(maxWidth.value / 700f, maxHeight.value / 340f).coerceIn(0.7f, 2.2f)
        else minOf(maxWidth.value / 380f, maxHeight.value / 700f).coerceIn(0.7f, 2.2f)
        val label = (20f * scale).sp
        val small = (13f * scale.coerceAtMost(1.5f)).sp
        val lineHeight = (46f * scale).dp

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Heading(record, small)
                    LineItems(record, label, lineHeight)
                    Details(record, small)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    TotalBlock(record, label, Modifier.weight(1f))
                    Buttons(primaryLabel, onPrimary, onSecondary, secondaryLabel)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Heading(record, small)
                LineItems(record, label, lineHeight)
                TotalBlock(record, label, Modifier.weight(1f))
                Column(
                    Modifier
                        .weight(0.6f)
                        .verticalScroll(rememberScrollState())
                ) { Details(record, small) }
                Buttons(primaryLabel, onPrimary, onSecondary, secondaryLabel)
            }
        }
    }
}

@Composable
private fun Heading(r: TripRecord, small: TextUnit) {
    Text("FAHRTABSCHLUSS · ${r.profile.label}", color = MeterColors.White, fontSize = small, fontWeight = FontWeight.Bold)
    Text("GPS-Fahrpreisschätzung – nicht geeicht, kein amtlicher Taxameterbeleg", color = MeterColors.Inactive, fontSize = small)
    if (r.simulation) Text("SIMULATION – TESTMODUS", color = MeterColors.Warn, fontSize = small, fontWeight = FontWeight.Bold)
    if (r.profile.isCustom) Text("BENUTZERDEFINIERTER TARIF", color = MeterColors.Warn, fontSize = small, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun AmountLine(text: String, amount: String, label: TextUnit, height: Dp, labelColor: androidx.compose.ui.graphics.Color = MeterColors.Label) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = labelColor,
            fontSize = label,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SevenSegment(amount, 6, Modifier.weight(1.1f).fillMaxHeight(), description = "$text $amount Euro")
        Text(" €", color = MeterColors.White, fontSize = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LineItems(r: TripRecord, label: TextUnit, height: Dp) {
    AmountLine("FAHRPREIS", Money.formatDe(r.fare), label, height)
    r.surcharges.forEach { s ->
        AmountLine(s.displayLabel.uppercase(), Money.formatDe(s.amount), label, height * 0.85f)
    }
    if (r.surcharges.isEmpty()) {
        AmountLine("ZUSCHLÄGE", "0,00", label, height * 0.7f, MeterColors.Inactive)
    }
    HorizontalDivider(color = MeterColors.Frame, thickness = 2.dp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun TotalBlock(r: TripRecord, label: TextUnit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("GESAMTBETRAG", color = MeterColors.Label, fontSize = (label.value * 1.3f).sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Text("€", color = MeterColors.White, fontSize = (label.value * 1.6f).sp, fontWeight = FontWeight.Bold)
            SevenSegment(
                Money.formatDe(r.total), 6,
                Modifier.weight(1f).fillMaxHeight(),
                description = "Gesamtbetrag ${Money.formatEuro(r.total)}",
            )
        }
    }
}

@Composable
private fun Details(r: TripRecord, small: TextUnit) {
    val lines = buildList {
        add("Tarif: ${r.profile.label} (${r.profile.officialName}), Version ${r.profile.version}")
        add("Start ${Format.dateTime(r.startWallMs)} · Ende ${Format.time(r.endWallMs)}")
        add("Strecke ${Format.km(r.distanceM)} · Dauer ${Format.duration(r.durationMs)} · berechnete Wartezeit ${Format.duration(r.waitingMs)}")
        add("Zuschläge gesamt ${Money.formatEuro(r.surchargeTotal)}")
        if (r.tip.signum() > 0) add("Trinkgeld ${Money.formatEuro(r.tip)} · bezahlt ${Money.formatEuro(r.paid)}")
        add("Bezahlt: ${r.payment.label}" + (r.paymentRef?.let { " · SumUp-Transaktion $it" } ?: ""))
        if (r.gaps.isEmpty()) {
            add("Messlücken: keine")
        } else {
            val bridged = r.gaps.sumOf { it.bridgedM }
            add(
                "Messlücken: ${r.gaps.size}, zusammen ${Format.duration(r.gapMs)} – Strecke in Lücken nicht gemessen" +
                    if (bridged > 0) " (davon ${Format.meters(bridged)} geprüft per Luftlinie überbrückt)" else ""
            )
            r.gaps.forEach { g ->
                add("  · ${Format.time(g.startWallMs)} ${g.reason.label}, ${Format.duration(g.durationMs)}")
            }
        }
        if (r.restoredAfterProcessDeath) add("Fahrt wurde nach App-Neustart fortgesetzt (Messlücke vermerkt).")
        add("Berechnung: ${r.profile.calculationMode.label}")
    }
    lines.forEach { Text(it, color = MeterColors.Inactive, fontSize = small) }
}

@Composable
private fun Buttons(primary: String, onPrimary: () -> Unit, onSecondary: (() -> Unit)?, secondaryLabel: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        if (onSecondary != null) {
            MeterButton(secondaryLabel, onSecondary, color = MeterColors.Inactive)
            Spacer(Modifier.width(12.dp))
        }
        MeterButton(primary, onPrimary, Modifier.width(220.dp))
    }
}
