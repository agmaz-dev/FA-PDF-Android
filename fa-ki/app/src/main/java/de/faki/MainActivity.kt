package de.faki

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0B0F)
private val Card = Color(0xFF17171D)
private val Line = Color(0xFF26262E)
private val Lime = Color(0xFFC6F432)
private val TextC = Color(0xFFF4F4F6)
private val Grey = Color(0xFFA0A0AB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val crash = Crash.takeReport(this)
        if (Settings.serverOn(this)) runCatching { KiService.start(this) }.onFailure { KiService.lastError = it.message }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Lime, background = Bg, surface = Card, onPrimary = Bg)) {
                Screen(crash)
            }
        }
    }
}

@Composable
private fun Screen(crash: String?) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // Fortschritt der Downloads und Status regelmäßig auffrischen
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("FA KI", color = TextC, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Text("Eigene KI auf diesem Handy – für alle FA-Apps", color = Grey, fontSize = 15.sp)
        Spacer(Modifier.height(18.dp))
        if (crash != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF3A1E21)).padding(16.dp)) {
                Text("Letzter Absturz", color = Color(0xFFFF6B6B), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(crash.take(1500), color = TextC, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FA KI Absturz", crash))
                    Toast.makeText(ctx, "Bericht kopiert – bitte an Claude schicken", Toast.LENGTH_LONG).show()
                }) { Text("Bericht kopieren", color = Lime) }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Server-Status
        val on = remember(tick) { KiService.running }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (on) "Läuft" else "Aus", color = if (on) Lime else Grey, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (on) "Andere Apps erreichen die KI über diese Adresse" else "Einschalten, damit andere Apps die KI nutzen können",
                        color = Grey, fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = {
                        Settings.setServerOn(ctx, it)
                        if (it) KiService.start(ctx) else KiService.stop(ctx)
                        scope.launch { delay(500); tick++ }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Lime),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bg).clickable {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("FA KI", KiServer.BASE_URL))
                    Toast.makeText(ctx, "Adresse kopiert", Toast.LENGTH_SHORT).show()
                }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(KiServer.BASE_URL, color = TextC, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("Kopieren", color = Lime, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text("Schlüssel: beliebig (z. B. „lokal“) · Modellname: lokal", color = Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            KiService.lastError?.let { Text("Fehler: $it", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
            // Samsung beendet Apps im Hintergrund sonst nach einiger Zeit – dann ist die KI für andere Apps weg
            val pm = ctx.getSystemService(android.os.PowerManager::class.java)
            val unrestricted = remember(tick) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
            if (!unrestricted) TextButton(onClick = {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + ctx.packageName)))
                }
            }) { Text("Im Hintergrund dauerhaft erlauben (Akku: nicht eingeschränkt)", color = Lime) }
        }

        Spacer(Modifier.height(22.dp))
        Text("Modelle", color = Grey, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        val active = remember(tick) { Settings.active(ctx)?.id }
        Models.all.forEach { m ->
            ModelCard(m, active == m.id, tick) { tick++ }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))
        TestBox()
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ModelCard(m: KiModel, isActive: Boolean, tick: Int, changed: () -> Unit) {
    val ctx = LocalContext.current
    val progress = remember(tick) { Models.progress(ctx, m) }
    val downloaded = remember(tick) { Models.isDownloaded(ctx, m) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (isActive) Color(0xFF222A12) else Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.title, color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${m.sizeGb} GB", color = Grey, fontSize = 13.sp)
            }
            if (downloaded) {
                Switch(
                    checked = isActive,
                    onCheckedChange = { on ->
                        // Immer nur ein Modell eingeschaltet
                        Settings.setActive(ctx, if (on) m else null)
                        Engine.unloadNow()
                        KiService.refresh(ctx)
                        changed()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Lime),
                )
            }
        }
        Text(m.description, color = Grey, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        when {
            progress != null -> {
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = Lime, trackColor = Line)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Wird heruntergeladen … ${(progress * 100).toInt()} %", color = Grey, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Models.cancelDownload(ctx, m); changed() }) { Text("Abbrechen", color = Grey) }
                }
            }
            downloaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isActive) "Eingeschaltet" else "Bereit – zum Einschalten Schalter antippen", color = if (isActive) Lime else Grey, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { Models.delete(ctx, m); changed() }) { Text("Löschen", color = Color(0xFFFF6B6B)) }
            }
            else -> Button(
                onClick = { Models.startDownload(ctx, m); changed() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Bg),
            ) { Text("Herunterladen (WLAN empfohlen)", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TestBox() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("Erkläre in zwei Sätzen, was eine Selbstbeteiligung bei der Teilkasko ist.") }
    var answer by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp)) {
        Text("Testen", color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth(), minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextC, unfocusedTextColor = TextC, focusedBorderColor = Lime, unfocusedBorderColor = Line),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !running && q.isNotBlank(),
            onClick = {
                running = true; answer = "Modell wird geladen und denkt nach …"
                scope.launch {
                    val t0 = System.currentTimeMillis()
                    answer = withContext(Dispatchers.Default) {
                        try {
                            val a = Engine.complete(ctx, listOf(Msg("user", q)), 400)
                            val s = (System.currentTimeMillis() - t0) / 1000.0
                            "$a\n\n(${"%.1f".format(s)} s · ${Settings.active(ctx)?.title})"
                        } catch (e: Exception) {
                            "Fehler: ${e.message}"
                        }
                    }
                    running = false
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Bg),
        ) { Text(if (running) "Läuft …" else "Frage stellen", fontWeight = FontWeight.Bold) }
        if (answer.isNotBlank()) Text(answer, color = TextC, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp).widthIn(max = 900.dp))
    }
}
