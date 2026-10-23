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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.trip.TripRecord
import de.fa.taximeter.ui.Format
import de.fa.taximeter.ui.theme.MeterColors

@Composable
fun HistoryScreen(
    trips: List<TripRecord>,
    onOpen: (TripRecord) -> Unit,
    onDelete: (TripRecord) -> Unit,
    onBack: () -> Unit,
) {
    var toDelete by remember { mutableStateOf<TripRecord?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MeterColors.Background)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FAHRTEN", color = MeterColors.Label, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            MeterButton("ZURÜCK", onBack, color = MeterColors.Inactive)
        }
        Text(
            "Lokal gespeichert, nur auf diesem Gerät. Beträge sind GPS-Schätzungen – nicht geeicht.",
            color = MeterColors.Inactive,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (trips.isEmpty()) {
            Text("Noch keine abgeschlossenen Fahrten.", color = MeterColors.White, fontSize = 16.sp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(trips, key = { it.id }) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, MeterColors.Dim, RoundedCornerShape(8.dp))
                        .clickable { onOpen(r) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Format.dateTime(r.startWallMs) + " · " + r.profile.label,
                            color = MeterColors.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${Format.km(r.distanceM)} · ${Format.duration(r.durationMs)} · Zuschläge ${Money.formatEuro(r.surchargeTotal)} · ${r.payment.label}",
                            color = MeterColors.Inactive,
                            fontSize = 13.sp,
                        )
                        val flags = buildList {
                            if (r.simulation) add("SIMULATION")
                            if (r.hasGaps) add("MESSLÜCKEN")
                            if (r.profile.isCustom) add("BENUTZERDEF. TARIF")
                        }
                        if (flags.isNotEmpty()) Text(flags.joinToString(" · "), color = MeterColors.Warn, fontSize = 12.sp)
                    }
                    Text(
                        Money.formatEuro(r.total),
                        color = MeterColors.Label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    MeterButton("LÖSCHEN", { toDelete = r }, color = MeterColors.Inactive)
                }
            }
        }
    }

    toDelete?.let { r ->
        ConfirmDialog(
            title = "FAHRT LÖSCHEN?",
            text = "${Format.dateTime(r.startWallMs)} – ${Money.formatEuro(r.total)} wird endgültig aus der Historie entfernt.",
            confirm = "LÖSCHEN",
            onConfirm = {
                onDelete(r)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}
