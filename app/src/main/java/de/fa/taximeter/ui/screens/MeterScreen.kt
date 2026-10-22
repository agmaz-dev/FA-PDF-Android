package de.fa.taximeter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.gps.GpsQuality
import de.fa.taximeter.domain.tariff.TariffProfile
import de.fa.taximeter.domain.trip.Phase
import de.fa.taximeter.ui.Format
import de.fa.taximeter.ui.components.SevenSegment
import de.fa.taximeter.ui.theme.MeterColors
import java.math.BigDecimal

/** Alle Werte, die die Hauptanzeige braucht (reine Darstellung, keine Logik). */
data class MeterUi(
    val phase: Phase,
    val tariff: TariffProfile,
    val fare: BigDecimal,
    val surchargeTotal: BigDecimal,
    val distanceM: Double,
    val durationMs: Long,
    val waitingMs: Long,
    val speedMps: Double?,
    val gpsQuality: GpsQuality,
    val gpsAccuracyM: Double?,
    val simulation: Boolean,
    val providerEnabled: Boolean,
    val fineLocation: Boolean,
    val anyLocation: Boolean,
    val restoreNotice: String?,
    val gapCount: Int,
)

class MeterActions(
    val onTarif: () -> Unit,
    val onZuschlag: () -> Unit,
    val onFrei: () -> Unit,
    val onBesetzt: () -> Unit,
    val onStopp: () -> Unit,
    val onKasse: () -> Unit,
    val onHistory: () -> Unit,
    val onSettings: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onRequestPermission: () -> Unit,
)

@Composable
fun MeterScreen(ui: MeterUi, actions: MeterActions) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MeterColors.Background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        val landscape = maxWidth > maxHeight * 1.1f
        // Skalierung der Beschriftungen nach verfügbarer Fläche (Smartphone, Querformat, Fold innen/außen)
        val scale = if (landscape) {
            minOf(maxWidth.value / 700f, maxHeight.value / 340f)
        } else {
            minOf(maxWidth.value / 380f, maxHeight.value / 700f)
        }.coerceIn(0.7f, 2.4f)
        val labelSize = (22f * scale).sp
        val barSize = (24f * scale).sp
        val smallSize = (12.5f * scale.coerceAtMost(1.5f)).sp
        val infoSize = (15f * scale.coerceAtMost(1.7f)).sp

        Column(Modifier.fillMaxSize()) {
            Header(ui, actions, smallSize)
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
                        .clickable(role = Role.Button, onClickLabel = "Zuschläge wählen", onClick = actions.onZuschlag),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("ZUSCHLAG", color = MeterColors.Label, fontSize = labelSize, fontWeight = FontWeight.Bold, maxLines = 1)
                    val zText = when {
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
            StatusBar(
                phase = ui.phase,
                fontSize = barSize,
                modifier = Modifier
                    .fillMaxWidth(if (landscape) 0.82f else 1f)
                    .align(Alignment.CenterHorizontally)
                    .weight(if (landscape) 0.15f else 0.12f)
                    .heightIn(min = 56.dp),
                actions = actions,
            )

            Spacer(Modifier.height(if (landscape) 6.dp else 10.dp))

            // ---------------- € Fahrpreis
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(if (landscape) 0.36f else 0.22f),
            ) {
                Text(
                    "€",
                    color = MeterColors.White,
                    fontSize = (labelSize.value * 1.4f).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                )
                val fareText = if (ui.phase == Phase.FREI) "" else Money.formatDe(ui.fare)
                SevenSegment(
                    text = fareText,
                    digits = 7,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    description = "Fahrpreis ${Money.formatEuro(ui.fare)}",
                )
            }

            // ---------------- FAHRPREIS | KASSE   + GPS
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    "FAHRPREIS",
                    color = if (ui.phase == Phase.BESETZT || ui.phase == Phase.STOPP) MeterColors.Label else MeterColors.Inactive,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.width((28f * scale).dp))
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 96.dp)
                        .clickable(role = Role.Button, onClickLabel = "Kasse", onClick = actions.onKasse),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "KASSE",
                        color = if (ui.phase == Phase.STOPP) MeterColors.Label else MeterColors.Inactive,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                GpsIndicator(ui, smallSize, (34f * scale.coerceAtMost(1.6f)).dp)
            }

            InfoRow(ui, infoSize, landscape)
        }
    }
}

@Composable
private fun Header(ui: MeterUi, actions: MeterActions, size: TextUnit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "FA Taximeter · ${ui.tariff.label}",
                color = MeterColors.White,
                fontSize = size,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "GPS-Fahrpreisrechner – nicht geeicht",
                color = MeterColors.Inactive,
                fontSize = (size.value * 0.9f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderButton("FAHRTEN", size, actions.onHistory)
        HeaderButton("EINSTELLUNGEN", size, actions.onSettings)
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
        Text(text, color = MeterColors.Inactive, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)
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

@Composable
private fun Banners(ui: MeterUi, actions: MeterActions, size: TextUnit) {
    if (ui.simulation) {
        Banner("SIMULATION – TESTMODUS: keine echten GPS-Werte", MeterColors.Warn, size)
    }
    if (!ui.anyLocation) {
        Banner("Standortfreigabe fehlt – ohne GPS keine Fahrt möglich", MeterColors.Label, size, actions.onRequestPermission, "FREIGEBEN")
    } else if (!ui.fineLocation && !ui.simulation) {
        Banner("Nur ungefährer Standort freigegeben – Strecke kann nicht gemessen werden", MeterColors.Warn, size, actions.onRequestPermission, "GENAU ERLAUBEN")
    }
    if (!ui.providerEnabled && !ui.simulation) {
        Banner("Standort/GPS ist im Gerät ausgeschaltet", MeterColors.Label, size)
    }
    if (ui.phase == Phase.BESETZT) {
        when (ui.gpsQuality) {
            GpsQuality.LOST -> Banner("GPS VERLOREN – es wird keine Strecke berechnet (Messlücke)", MeterColors.Label, size)
            GpsQuality.UNRELIABLE -> Banner("GPS UNZUVERLÄSSIG – Messungen werden verworfen", MeterColors.Warn, size)
            else -> Unit
        }
    }
    ui.restoreNotice?.let { Banner(it, MeterColors.Warn, size, actions.onDismissNotice, "OK") }
}

@Composable
private fun StatusBar(phase: Phase, fontSize: TextUnit, modifier: Modifier, actions: MeterActions) {
    Row(
        modifier.border(2.5.dp, MeterColors.Frame, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusCell("FREI", phase == Phase.FREI, fontSize, actions.onFrei)
        StatusCell("BESETZT", phase == Phase.BESETZT, fontSize, actions.onBesetzt)
        StatusCell("STOPP", phase == Phase.STOPP, fontSize, actions.onStopp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatusCell(
    text: String,
    active: Boolean,
    fontSize: TextUnit,
    onClick: () -> Unit,
) {
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

@Composable
private fun GpsIndicator(ui: MeterUi, size: TextUnit, barHeight: Dp) {
    val (bars, color) = when (ui.gpsQuality) {
        GpsQuality.GOOD -> 4 to MeterColors.Lit
        GpsQuality.FAIR -> 3 to MeterColors.Warn
        GpsQuality.UNRELIABLE -> 1 to MeterColors.Warn
        GpsQuality.LOST -> 0 to MeterColors.Dim
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (ui.gpsQuality) {
                    GpsQuality.LOST -> "GPS verloren"
                    GpsQuality.UNRELIABLE -> "GPS unzuverlässig"
                    else -> "GPS"
                },
                color = if (ui.gpsQuality == GpsQuality.LOST) MeterColors.Label else color,
                fontSize = size,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                ui.gpsAccuracyM?.let { "±" + Format.decimal(it, 0) + " m" } ?: "± – m",
                color = MeterColors.Inactive,
                fontSize = size,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.height(barHeight), verticalAlignment = Alignment.Bottom) {
            for (i in 1..4) {
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .width(5.dp)
                        .fillMaxHeight(i / 4f)
                        .background(if (i <= bars) color else MeterColors.Unlit)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(ui: MeterUi, size: TextUnit, landscape: Boolean) {
    val items = buildList {
        add(Format.km(ui.distanceM))
        add("Dauer " + Format.duration(ui.durationMs))
        add("Warten " + Format.duration(ui.waitingMs))
        add(Format.speedKmh(ui.speedMps))
        if (ui.phase != Phase.FREI && ui.surchargeTotal.signum() > 0) {
            add("Gesamt " + Money.formatEuro(Money.cents(ui.fare.add(ui.surchargeTotal))))
        }
        if (ui.gapCount > 0) add("Messlücken: ${ui.gapCount}")
    }
    val color = if (ui.phase == Phase.FREI) MeterColors.Dim else MeterColors.Inactive
    if (landscape) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach {
                Text(it, color = color, fontSize = size, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    } else {
        // Hochformat: zwei Spalten, damit nichts abgeschnitten wird
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            items.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach {
                        Text(
                            it,
                            color = color,
                            fontSize = size,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
