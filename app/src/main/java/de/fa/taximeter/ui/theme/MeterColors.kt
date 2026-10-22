package de.fa.taximeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Farben nach der roten Referenzabbildung. Es gibt bewusst keine grüne Variante. */
object MeterColors {
    val Background = Color(0xFF000000)
    val Lit = Color(0xFFFF2A2A)          // leuchtende Segmente
    val Unlit = Color(0xFF2E0707)        // dunkelrote, nicht leuchtende Segmente
    val Label = Color(0xFFFF3B3B)        // aktive Beschriftung
    val Inactive = Color(0xFF8C8C8C)     // inaktive Beschriftung (grau)
    val Frame = Color(0xFFF2F2F2)        // weiße Umrandung der Zustandsleiste
    val White = Color(0xFFFFFFFF)
    val Dim = Color(0xFF5A5A5A)
    val Warn = Color(0xFFFFB020)         // Statusfarbe "unzuverlässig" (bernstein)
    val Panel = Color(0xFF120202)
}

private val Scheme = darkColorScheme(
    primary = MeterColors.Lit,
    onPrimary = Color.Black,
    secondary = MeterColors.Label,
    background = MeterColors.Background,
    onBackground = MeterColors.White,
    surface = Color(0xFF0D0D0D),
    onSurface = MeterColors.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = MeterColors.Warn,
)

@Composable
fun MeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
