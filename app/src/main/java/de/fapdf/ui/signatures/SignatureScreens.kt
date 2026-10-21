package de.fapdf.ui.signatures

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.fapdf.data.SignatureEntity
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.components.ConfirmDialog
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.GroupCard
import de.fapdf.ui.components.GroupRow
import de.fapdf.ui.components.InfoBanner
import de.fapdf.ui.components.PrimaryButton
import de.fapdf.ui.components.ScreenScaffold
import de.fapdf.ui.components.SectionTitle
import de.fapdf.ui.profiles.LockGate
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
fun SignaturesScreen(appVm: AppViewModel, onBack: () -> Unit, onNew: () -> Unit) {
    val sigs by appVm.container.signatures.observeAll().collectAsState(initial = emptyList())
    val labels by appVm.container.profiles.observeLabels().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<SignatureEntity?>(null) }
    LockGate(appVm, "Unterschriften entsperren", onBack) {
        ScreenScaffold("Unterschriften", onBack) { mod ->
            Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
                InfoBanner("Unterschriften werden verschlüsselt auf dem Gerät gespeichert und nur nach Ihrer Bestätigung in Dokumente eingesetzt. Es handelt sich um einfache elektronische Unterschriften (Bild), nicht um qualifizierte Signaturen.")
                SectionTitle("Gespeichert")
                GroupCard {
                    sigs.forEach { s ->
                        var bmp by remember(s.id) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(s.id) { bmp = appVm.container.signatures.loadBitmap(s) }
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(120.dp, 52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF7F7F9)), contentAlignment = Alignment.Center) {
                                bmp?.let { Image(it.asImageBitmap(), s.name, Modifier.padding(4.dp)) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name)
                                val owner = labels.firstOrNull { it.first == s.profileId }?.second
                                if (owner != null) Text("Profil: $owner", color = FaColors.SecondaryLabel)
                            }
                            IconButton(onClick = { toDelete = s }) { Icon(Icons.Outlined.Delete, "Löschen", tint = FaColors.Destructive) }
                        }
                    }
                    GroupRow("Neue Unterschrift zeichnen", icon = Icons.Outlined.Add, iconColor = FaColors.Sign, showDivider = false) { onNew() }
                }
            }
        }
    }
    toDelete?.let { s ->
        ConfirmDialog("Unterschrift löschen?", "„${s.name}“ wird endgültig gelöscht.", "Löschen", destructive = true,
            onConfirm = { toDelete = null; scope.launch { appVm.container.signatures.delete(s) } }, onDismiss = { toDelete = null })
    }
}

private data class Stroke2(val points: List<Offset>)

@Composable
fun SignaturePadScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val strokes = remember { mutableStateListOf<Stroke2>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(0xFF111111.toInt()) }
    var width by remember { mutableStateOf(3.5f) }
    var name by remember { mutableStateOf("Meine Unterschrift") }
    var profileId by remember { mutableStateOf<Long?>(null) }
    val labels by appVm.container.profiles.observeLabels().collectAsState(initial = emptyList())

    ScreenScaffold("Unterschrift zeichnen", onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Unterschreiben Sie mit dem Finger im Feld.", color = FaColors.SecondaryLabel)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .border(1.dp, FaColors.Separator, RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> change.consume(); current = current + change.position },
                            onDragEnd = { if (current.size > 1) strokes.add(Stroke2(current)); current = emptyList() },
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // Hilfslinie
                    drawLine(FaColors.Separator, Offset(24f, size.height * 0.75f), Offset(size.width - 24f, size.height * 0.75f), strokeWidth = 2f)
                    (strokes.map { it.points } + listOf(current)).forEach { pts ->
                        if (pts.size > 1) {
                            val path = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) {
                                    val mid = Offset((pts[i - 1].x + pts[i].x) / 2, (pts[i - 1].y + pts[i].y) / 2)
                                    quadraticBezierTo(pts[i - 1].x, pts[i - 1].y, mid.x, mid.y)
                                }
                                lineTo(pts.last().x, pts.last().y)
                            }
                            drawPath(path, Color(color), style = Stroke(width = width * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                if (strokes.isEmpty() && current.isEmpty()) Text("Hier unterschreiben", Modifier.align(Alignment.Center), color = FaColors.SecondaryLabel)
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1) }) { Text("Rückgängig") }
                OutlinedButton(onClick = { strokes.clear() }) { Text("Löschen") }
                Spacer(Modifier.weight(1f))
                listOf(0xFF111111.toInt(), 0xFF0A3D91.toInt()).forEach { c ->
                    Box(Modifier.size(if (color == c) 30.dp else 24.dp).clip(CircleShape).background(Color(c)).clickable { color = c })
                }
            }
            Text("Strichstärke")
            Slider(width, { width = it }, valueRange = 1.5f..7f)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name der Unterschrift") })
            if (labels.isNotEmpty()) {
                SectionTitle("Profil zuordnen (optional)", Modifier.padding(start = 0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(profileId == null, { profileId = null }, { Text("Keins") })
                    labels.take(4).forEach { (id, l) -> FilterChip(profileId == id, { profileId = id }, { Text(l) }) }
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Unterschrift speichern", enabled = strokes.isNotEmpty()) {
                val bmp = renderSignature(strokes.map { it.points }, color, width * context.resources.displayMetrics.density)
                if (bmp == null) { FileActions.toast(context, "Bitte zuerst unterschreiben"); return@PrimaryButton }
                scope.launch { appVm.container.signatures.save(bmp, name, profileId); onBack() }
            }
        }
    }
}

/** Erzeugt ein zugeschnittenes PNG-fähiges Bitmap mit transparentem Hintergrund. */
private fun renderSignature(strokes: List<List<Offset>>, color: Int, widthPx: Float): Bitmap? {
    val all = strokes.flatten()
    if (all.size < 2) return null
    val pad = widthPx * 2
    val minX = all.minOf { it.x } - pad
    val minY = all.minOf { it.y } - pad
    val maxX = all.maxOf { it.x } + pad
    val maxY = all.maxOf { it.y } + pad
    val scale = 2f // doppelte Auflösung für scharfen Druck
    val w = max(1, ((maxX - minX) * scale).toInt())
    val h = max(1, ((maxY - minY) * scale).toInt())
    val bmp = Bitmap.createBitmap(min(w, 3000), min(h, 1500), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.scale(scale, scale)
    canvas.translate(-minX, -minY)
    val paint = Paint().apply {
        this.color = color; strokeWidth = widthPx; isAntiAlias = true
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { pts ->
        val path = android.graphics.Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val mx = (pts[i - 1].x + pts[i].x) / 2
            val my = (pts[i - 1].y + pts[i].y) / 2
            path.quadTo(pts[i - 1].x, pts[i - 1].y, mx, my)
        }
        path.lineTo(pts.last().x, pts.last().y)
        canvas.drawPath(path, paint)
    }
    return bmp
}
