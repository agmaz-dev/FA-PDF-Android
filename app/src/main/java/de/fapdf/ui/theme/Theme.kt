package de.fapdf.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fapdf.R

/** Farben nach der verbindlichen Designvorlage. */
object FaColors {
    val Background = Color(0xFFFFFFFF)
    val Card = Color(0xFFF5F6F8)
    val CardBorder = Color(0xFFE6E8EC)
    val Surface = Color.White
    val Label = Color(0xFF15171C)
    val SecondaryLabel = Color(0xFF6B7280)
    val Separator = Color(0xFFE5E7EB)
    val Accent = Color(0xFF1F5BE0)          // Blau der Hauptschaltflächen
    val AccentLight = Color(0xFFE3ECFD)     // Hellblau (z. B. „Scannen“, Info-Karten)
    val Destructive = Color(0xFFE5383B)
    val DestructiveLight = Color(0xFFFDECEC)
    val Success = Color(0xFF22A06B)
    val PdfRed = Color(0xFFE53935)

    // Kategorien: Symbolfarbe + Kachelhintergrund
    val Organize = Color(0xFF7C3AED)
    val OrganizeTile = Color(0xFFF1ECFE)
    val Edit = Color(0xFF0E8F80)
    val EditTile = Color(0xFFE3F7F2)
    val Sign = Color(0xFFE5383B)
    val SignTile = Color(0xFFFDEAE6)
    val SignRequest = Color(0xFFE5383B)
    val Encrypt = Color(0xFFE5383B)
    val EncryptTile = Color(0xFFFDE6EA)
    val Ai = Color(0xFF1F5BE0)
    val AiTile = Color(0xFFE5EEFD)
    val Scan = Color(0xFF1F5BE0)
    val ScanTile = Color(0xFFE5EEFD)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val scheme = lightColorScheme(
    primary = FaColors.Accent,
    onPrimary = Color.White,
    primaryContainer = FaColors.AccentLight,
    onPrimaryContainer = FaColors.Accent,
    secondary = FaColors.Edit,
    background = FaColors.Background,
    onBackground = FaColors.Label,
    surface = FaColors.Surface,
    onSurface = FaColors.Label,
    surfaceVariant = FaColors.Card,
    onSurfaceVariant = FaColors.SecondaryLabel,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = FaColors.Card,
    error = FaColors.Destructive,
    outline = Color(0xFFD1D5DB),
    outlineVariant = FaColors.Separator,
)

private fun t(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.3f).toInt()) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

private val typography = Typography(
    displaySmall = t(32, FontWeight.Bold, 38),
    headlineMedium = t(28, FontWeight.Bold, 34),
    headlineSmall = t(22, FontWeight.Bold, 28),
    titleLarge = t(20, FontWeight.SemiBold, 26),
    titleMedium = t(17, FontWeight.SemiBold, 22),
    titleSmall = t(15, FontWeight.SemiBold, 20),
    bodyLarge = t(16, FontWeight.Normal, 22),
    bodyMedium = t(14, FontWeight.Normal, 20),
    bodySmall = t(12, FontWeight.Normal, 16),
    labelLarge = t(15, FontWeight.SemiBold, 20),
    labelMedium = t(13, FontWeight.Medium, 18),
    labelSmall = t(11, FontWeight.Medium, 14),
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FaPdfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
