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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.trip.MeterMachine
import de.fa.taximeter.ui.theme.MeterColors
import java.math.BigDecimal

/**
 * KASSE-Fenster vor der Kartenzahlung: Der Fahrer tippt den Betrag, den der Kunde nennt
 * („machen Sie 65“). Das Trinkgeld = Betrag − Summe wird automatisch berechnet.
 */
@Composable
fun TipDialog(
    due: BigDecimal,
    onPay: (tip: BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }   // eingetippte Ziffern, z. B. "7500" = 75,00
    val typed: BigDecimal? = input.takeIf { it.isNotEmpty() }?.let { BigDecimal(it).movePointLeft(2) }
    val typedTip = typed?.subtract(due)
    val typedOk = typed != null && typedTip!!.signum() >= 0 && typedTip <= MeterMachine.MAX_TIP

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(12.dp))
                .border(2.dp, MeterColors.Frame, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KASSE", color = MeterColors.Label, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Money.formatEuro(due), color = MeterColors.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Text("Betrag, den der Kunde nennt:", color = MeterColors.Inactive, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            // Runde Beträge
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeterMachine.roundSuggestions(due).forEach { target ->
                    BigButton(
                        title = Money.formatDe(target).removeSuffix(",00") + " €",
                        sub = "+" + Money.formatEuro(target.subtract(due)),
                        modifier = Modifier.weight(1f),
                    ) { onPay(target.subtract(due)) }
                }
            }

            Spacer(Modifier.height(12.dp))
            // Anderer Betrag mit eigenem Ziffernblock
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MeterColors.Dim, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Anderer Betrag", color = MeterColors.Inactive, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    if (typed == null) "– €" else Money.formatEuro(typed),
                    color = MeterColors.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                when {
                    typed == null -> " "
                    typedTip!!.signum() < 0 -> "Betrag ist kleiner als die Summe"
                    !typedOk -> "Trinkgeld zu hoch"
                    else -> "Trinkgeld: " + Money.formatEuro(typedTip)
                },
                color = if (typed != null && !typedOk) MeterColors.Warn else MeterColors.Inactive,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "00")
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Key(k, Modifier.weight(1f)) {
                            input = when (k) {
                                "⌫" -> input.dropLast(1)
                                else -> (input + k).trimStart('0').take(6)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            BigButton(
                title = if (typedOk) "MIT KARTE: " + Money.formatEuro(typed!!) else "BETRAG EINTIPPEN",
                sub = null,
                enabled = typedOk,
                modifier = Modifier.fillMaxWidth(),
            ) { if (typedOk) onPay(typedTip!!) }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("OHNE TRINKGELD", Money.formatEuro(due), modifier = Modifier.weight(1f), color = MeterColors.White) {
                    onPay(BigDecimal.ZERO)
                }
                BigButton("ABBRECHEN", null, modifier = Modifier.weight(1f), color = MeterColors.Inactive, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun BigButton(
    title: String,
    sub: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MeterColors.Label,
    onClick: () -> Unit,
) {
    val c = if (enabled) color else MeterColors.Dim
    Column(
        modifier
            .heightIn(min = 64.dp)
            .border(2.dp, c, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = c, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, color = MeterColors.Inactive, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .border(1.dp, MeterColors.Dim, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MeterColors.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
