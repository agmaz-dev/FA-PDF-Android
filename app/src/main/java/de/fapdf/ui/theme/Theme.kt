package de.fapdf.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Farben angelehnt an die Referenzgrafik und an iOS-Systemfarben. */
object FaColors {
    val Background = Color(0xFFF2F2F7)
    val Surface = Color.White
    val Label = Color(0xFF1C1C1E)
    val SecondaryLabel = Color(0xFF8A8A8E)
    val Separator = Color(0xFFE5E5EA)
    val Accent = Color(0xFF0A5BD6)
    val Destructive = Color(0xFFFF3B30)
    val Success = Color(0xFF34C759)

    val Organize = Color(0xFF7465F2)     // Violett
    val Edit = Color(0xFF1FBFB2)         // Türkis
    val Sign = Color(0xFFE364E8)         // Pink
    val SignRequest = Color(0xFFF7E03C)  // Gelb
    val Encrypt = Color(0xFFF4566B)      // Korallrot
    val Ai = Color(0xFF0D52D9)           // Blau
    val Scan = Color(0xFF0D52D9)         // Blau
}

private val scheme = lightColorScheme(
    primary = FaColors.Accent,
    onPrimary = Color.White,
    secondary = FaColors.Edit,
    background = FaColors.Background,
    onBackground = FaColors.Label,
    surface = FaColors.Surface,
    onSurface = FaColors.Label,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = FaColors.SecondaryLabel,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF2F2F7),
    error = FaColors.Destructive,
    outline = Color(0xFFD1D1D6),
    outlineVariant = FaColors.Separator,
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun FaPdfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
