package de.fapdf.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fapdf.data.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fapdf.R

/**
 * Farben nach der verbindlichen Designvorlage (hell) mit passender dunkler Variante.
 * [dark] ist ein Compose-Zustand: Ein Wechsel färbt alle Bildschirme sofort um.
 */
object FaColors {
    var dark by mutableStateOf(false)
    private fun c(light: Long, dark: Long) = if (this.dark) Color(dark) else Color(light)

    val Background get() = c(0xFFFFFFFF, 0xFF121317)
    val Card get() = c(0xFFF5F6F8, 0xFF1D1F25)
    val CardBorder get() = c(0xFFE6E8EC, 0xFF2E313A)
    val Surface get() = c(0xFFFFFFFF, 0xFF17191E)
    /** Hintergrund hinter PDF-Seiten (Seiten selbst bleiben weiß wie Papier). */
    val PageArea get() = c(0xFFF1F2F5, 0xFF0C0D10)
    val Label get() = c(0xFF15171C, 0xFFECEEF2)
    val SecondaryLabel get() = c(0xFF6B7280, 0xFF9AA1AD)
    val Separator get() = c(0xFFE5E7EB, 0xFF2A2D35)
    val Accent get() = c(0xFF1F5BE0, 0xFF5B8DF6)
    val AccentLight get() = c(0xFFE3ECFD, 0xFF1D2B4B)
    val Destructive get() = c(0xFFE5383B, 0xFFFF6B6B)
    val DestructiveLight get() = c(0xFFFDECEC, 0xFF3B1F21)
    val Success get() = c(0xFF22A06B, 0xFF3CCB8C)
    val PdfRed = Color(0xFFE53935)

    // Kategorien: Symbolfarbe + Kachelhintergrund
    val Organize get() = c(0xFF7C3AED, 0xFFA98BFA)
    val OrganizeTile get() = c(0xFFF1ECFE, 0xFF272042)
    val Edit get() = c(0xFF0E8F80, 0xFF35C9B5)
    val EditTile get() = c(0xFFE3F7F2, 0xFF15302C)
    val Sign get() = c(0xFFE5383B, 0xFFFF7070)
    val SignTile get() = c(0xFFFDEAE6, 0xFF3A2220)
    val SignRequest get() = Sign
    val Encrypt get() = c(0xFFE5383B, 0xFFFF7070)
    val EncryptTile get() = c(0xFFFDE6EA, 0xFF3A1E26)
    val Ai get() = c(0xFF1F5BE0, 0xFF5B8DF6)
    val AiTile get() = c(0xFFE5EEFD, 0xFF1D2B4B)
    val Scan get() = Ai
    val ScanTile get() = AiTile
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun scheme(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
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
    surfaceContainer = FaColors.Surface,
    surfaceContainerLow = FaColors.Surface,
    surfaceContainerHigh = FaColors.Surface,
    surfaceContainerHighest = FaColors.Card,
    surfaceContainerLowest = FaColors.Surface,
    surfaceBright = FaColors.Surface,
    inverseSurface = FaColors.Label,
    inverseOnSurface = FaColors.Background,
    error = FaColors.Destructive,
    outline = if (dark) Color(0xFF454A55) else Color(0xFFD1D5DB),
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
fun FaPdfTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    if (FaColors.dark != dark) FaColors.dark = dark
    MaterialTheme(colorScheme = scheme(dark), typography = typography, shapes = shapes, content = content)
}
