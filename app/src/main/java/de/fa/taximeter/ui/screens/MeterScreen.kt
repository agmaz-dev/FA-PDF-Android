package de.fa.taximeter.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.gps.GpsQuality
import de.fa.taximeter.domain.tariff.TariffProfile
import de.fa.taximeter.domain.trip.Phase
import de.fa.taximeter.ui.components.SevenSegment
import de.fa.taximeter.ui.theme.MeterColors
import java.math.BigDecimal

/** Alle Werte, die die Hauptanzeige braucht (reine Darstellung, keine Logik). */
data class MeterUi(
    val phase: Phase,
    val tariff: TariffProfile,
    /** Betrag der großen Anzeige (Fahrpreis oder – nach STOPP – Summe inkl. Zuschläge). */
    val amount: BigDecimal,
    val showsTotal: Boolean,
    val surchargeTotal: BigDecimal,
    val gpsQuality: GpsQuality,
    val estimating: Boolean,
    val simulation: Boolean,
    val providerEnabled: Boolean,
    val fineLocation: Boolean,
    val anyLocation: Boolean,
)

class MeterActions(
    val onTarif: () -> Unit,
    val onZuschlag: () -> Unit,
    val onZuschlagLong: () -> Unit,
    val onFrei: () -> Unit,
    val onBesetzt: () -> Unit,
    val onStopp: () -> Unit,
    val onHistory: () -> Unit,
    val onSettings: () -> Unit,
    val onRequestPermission: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeterScreen(ui: MeterUi, actions: MeterActions) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MeterColors.Background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        val landscape = maxWidth > maxHeight * 1.1f
        val scale = if (landscape) {
            minOf(maxWidth.value / 700f, maxHeight.value / 340f)
        } else {
            minOf(maxWidth.value / 380f, maxHeight.value / 700f)
        }.coerceIn(0.7f, 2.4f)
        val labelSize = (22f * scale).sp
        val barSize = (24f * scale).sp
        val smallSize = (12f * scale.coerceAtMost(1.4f)).sp

        Column(Modifier.fillMaxSize()) {
            // Nur FAHRTEN und EINSTELLUNGEN, dezent
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                HeaderButton("FAHRTEN", smallSize, actions.onHistory)
                HeaderButton("EINSTELLUNGEN", smallSize, actions.onSettings)
            }
            Banners(ui, actions, smallSize)

            // ---------------- TARIF | ZUSCHLAG
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(if (landscape) 0.30f else 0.20f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(2.4f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClickLabel = "Tarif wählen", onClick = actions.onTarif),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("TARIF", color = MeterColors.Label, fontSize = labelSize, fontWeight = FontWeight.Bold, maxLines = 1)
                    SevenSegment(
                        text = ui.tariff.displayNumber.toString(),
                        digits = 2,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        alignment = Alignment.Center,
                        description = "Tarif ${ui.tariff.label}",
                    )
                    if (ui.tariff.isCustom) {
                        Text("BENUTZERDEFINIERT", color = MeterColors.Warn, fontSize = smallSize, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(0.4f))
                Column(
                    Modifier
                        .weight(6f)
                        .fillMaxHeight()
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = "Zuschlag plus 0,50 Euro",
                            onLongClickLabel = "Zuschlag zurücksetzen",
                            onLongClick = actions.onZuschlagLong,
                            onClick = actions.onZuschlag,
                        ),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("ZUSCHLAG", color = MeterColors.Label, fontSize = labelSize, fontWeight = FontWeight.Bold, maxLines = 1)
                    val zText = when {
                        ui.showsTotal -> "" // in STOPP steckt der Zuschlag unten in der Summe
                        ui.surchargeTotal.signum() > 0 -> Money.formatDe(ui.surchargeTotal)
                        ui.phase == Phase.FREI -> ""
                        else -> "0,00"
                    }
                    SevenSegment(
                        text = zText,
                        digits = 6,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        description = "Zuschlag ${Money.formatEuro(ui.surchargeTotal)}",
                    )
                }
            }

            Spacer(Modifier.height(if (landscape) 6.dp else 10.dp))

            // ---------------- FREI | BESETZT | STOPP
            Row(
                Modifier
                    .fillMaxWidth(if (landscape) 0.82f else 1f)
                    .align(Alignment.CenterHorizontally)
                    .weight(if (landscape) 0.16f else 0.12f)
                    .heightIn(min = 56.dp)
                    .border(2.5.dp, MeterColors.Frame, RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusCell("FREI", ui.phase == Phase.FREI, barSize, actions.onFrei)
                StatusCell("BESETZT", ui.phase == Phase.BESETZT, barSize, actions.onBesetzt)
                StatusCell("STOPP", ui.phase == Phase.STOPP, barSize, actions.onStopp)
            }

            Spacer(Modifier.height(if (landscape) 6.dp else 10.dp))

            // ---------------- € Betrag
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(if (landscape) 0.40f else 0.24f),
            ) {
                Text(
                    "€",
                    color = MeterColors.White,
                    fontSize = (labelSize.value * 1.4f).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                )
                val text = if (ui.phase == Phase.FREI) "" else Money.formatDe(ui.amount)
                SevenSegment(
                    text = text,
                    digits = 7,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    description = (if (ui.showsTotal) "Kasse " else "Fahrpreis ") + Money.formatEuro(ui.amount),
                )
            }

            // ---------------- FAHRPREIS | KASSE   + GPS-Punkte
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                val running = ui.phase == Phase.BESETZT || ui.phase == Phase.STOPP
                Text(
                    "FAHRPREIS",
                    color = if (running && !ui.showsTotal) MeterColors.Label else MeterColors.Inactive,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.width((28f * scale).dp))
                Text(
                    "KASSE",
                    color = if (running && ui.showsTotal) MeterColors.Label else MeterColors.Inactive,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                GpsDots(ui, smallSize)
            }
        }
    }
}

@Composable
private fun HeaderButton(text: String, size: TextUnit, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MeterColors.Dim, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Banner(text: String, color: Color, size: TextUnit, onClick: (() -> Unit)? = null, action: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = MeterColors.White, fontSize = size, fontWeight = FontWeight.Bold)
    }
}

/** Nur Hinweise, ohne die keine Messung möglich ist. */
@Composable
private fun Banners(ui: MeterUi, actions: MeterActions, size: TextUnit) {
    if (!ui.anyLocation) {
        Banner("Standortfreigabe fehlt – ohne GPS keine Fahrt möglich", MeterColors.Label, size, actions.onRequestPermission, "FREIGEBEN")
    } else if (!ui.fineLocation && !ui.simulation) {
        Banner("Nur ungefährer Standort freigegeben", MeterColors.Warn, size, actions.onRequestPermission, "GENAU ERLAUBEN")
    }
    if (!ui.providerEnabled && !ui.simulation) {
        Banner("Standort/GPS ist im Gerät ausgeschaltet", MeterColors.Label, size)
    }
}

@Composable
private fun RowScope.StatusCell(text: String, active: Boolean, fontSize: TextUnit, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClickLabel = text, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (active) MeterColors.Label else MeterColors.Inactive,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Dezente Empfangsanzeige: „G“ und vier kleine Punkte. Im Tunnel (Schätzung) sind alle Punkte aus. */
@Composable
private fun GpsDots(ui: MeterUi, size: TextUnit) {
    val dots = when {
        ui.estimating -> 0
        else -> when (ui.gpsQuality) {
            GpsQuality.GOOD -> 4
            GpsQuality.FAIR -> 3
            GpsQuality.UNRELIABLE -> 1
            GpsQuality.LOST -> 0
        }
    }
    val on = MeterColors.Lit.copy(alpha = 0.55f)
    val off = MeterColors.Unlit
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(min = 56.dp)) {
        if (ui.simulation) {
            Text("SIM ", color = MeterColors.Warn.copy(alpha = 0.7f), fontSize = size, fontWeight = FontWeight.Bold)
        }
        Text("G", color = MeterColors.Dim, fontSize = size, fontWeight = FontWeight.Bold)
        for (i in 1..4) {
            Box(
                Modifier
                    .padding(start = 3.dp)
                    .size(5.dp)
                    .background(if (i <= dots) on else off, CircleShape)
            )
        }
    }
}
