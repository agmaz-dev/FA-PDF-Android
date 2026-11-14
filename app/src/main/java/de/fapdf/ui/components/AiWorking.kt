package de.fapdf.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fapdf.ui.theme.FaColors

/** Farben der KI-Animation: Blau, Lila, Pink, Lime. */
private val AiColors = listOf(Color(0xFF4F8CFF), Color(0xFF9B6BFF), Color(0xFFFF6BC1), Color(0xFFC6F432))

/** Weiche, schwebende Farbwolken (wie bei Gemini), unten im Bereich. */
@Composable
fun AiAurora(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c")
    val p by t.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "p")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val r = maxOf(w, h) * 0.42f
        fun blob(color: Color, x: Float, y: Float, rad: Float) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * p), color.copy(alpha = 0f)), center = Offset(x, y), radius = rad), rad, Offset(x, y))
        }
        blob(AiColors[0], w * (0.15f + 0.35f * a), h * 0.85f, r)
        blob(AiColors[2], w * (0.85f - 0.4f * b), h * 0.9f, r * 0.95f)
        blob(AiColors[3], w * (0.3f + 0.4f * c), h * 1.0f, r * 0.85f)
        blob(AiColors[1], w * (0.7f - 0.3f * a), h * 0.8f, r * 0.9f)
    }
}

/** Bunter, schimmernder Fortschrittsbalken. [fraction] = null → läuft unbestimmt. */
@Composable
fun AiProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bar")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "shift")
    val loop by t.animateFloat(0.08f, 0.92f, infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "loop")
    val value = (fraction ?: loop).coerceIn(0.03f, 1f)
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF26262E))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * value
            val span = size.width
            val brush = Brush.linearGradient(
                AiColors + AiColors.first(),
                start = Offset(-span + span * 2 * shift, 0f), end = Offset(span * 2 * shift, 0f),
                tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
            )
            drawRoundRect(brush, size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }
    }
}

/**
 * KI arbeitet: Titel, „Schritt X von Y“, bunter Balken, unten Farbleuchten.
 * [fraction] null → Balken läuft von selbst.
 */
@Composable
fun AiWorkingPanel(title: String, detail: String?, fraction: Float?, modifier: Modifier = Modifier, partial: String? = null) {
    Box(modifier) {
        // Während Gemma auf der Grafikeinheit rechnet, keine Licht-Animation (sie würde mitrechnen und ruckeln)
        if (!de.fapdf.ai.GemmaLocal.running) AiAurora(Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = FaColors.Label, lineHeight = 27.sp)
            if (!detail.isNullOrBlank()) Text(detail, fontSize = 14.sp, color = FaColors.SecondaryLabel, modifier = Modifier.padding(top = 6.dp))
            AiProgressBar(fraction, Modifier.padding(top = 18.dp))
            // Wort für Wort: die letzten Zeilen des entstehenden Textes
            if (!partial.isNullOrBlank()) Text(
                de.fapdf.ai.AiText.clean(partial).takeLast(420), fontSize = 14.sp, lineHeight = 19.sp, color = FaColors.Label,
                maxLines = 7, overflow = androidx.compose.ui.text.style.TextOverflow.Clip, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** Fortschritt aus Texten wie „Seite 2 von 5“ oder „Teil 1 von 3“ herauslesen. */
fun progressFromText(text: String): Float? {
    val m = Regex("(\\d+)\\s*(?:von|/)\\s*(\\d+)").find(text) ?: return null
    val a = m.groupValues[1].toFloatOrNull() ?: return null
    val b = m.groupValues[2].toFloatOrNull() ?: return null
    return if (b > 0) (a / b).coerceIn(0f, 1f) else null
}

/** Ersatz für den Lade-Dialog bei KI-Aufgaben. */
@Composable
fun AiWorkingDialog(text: String, partial: String? = null) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.9f).height(if (partial.isNullOrBlank()) 260.dp else 380.dp).clip(RoundedCornerShape(26.dp)).background(FaColors.Surface), contentAlignment = Alignment.TopStart) {
            AiWorkingPanel(if (partial.isNullOrBlank()) "KI arbeitet …" else "KI schreibt …", text, progressFromText(text), Modifier.fillMaxSize(), partial)
        }
    }
}

/** Sanft wachsender Wert innerhalb eines Schritts, damit der Balken nie stillsteht. */
@Composable
fun rememberStepProgress(step: Int, steps: Int, sub: Float?): Float {
    val anim = remember(step) { Animatable(0f) }
    LaunchedEffect(step) { anim.animateTo(0.9f, tween(9000, easing = FastOutSlowInEasing)) }
    val s = steps.coerceAtLeast(1)
    return (((step - 1).coerceAtLeast(0)) + (sub ?: anim.value)) / s
}
