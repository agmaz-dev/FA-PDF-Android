package de.fapdf.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Scanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.fapdf.ui.theme.FaColors

enum class ToolCategory(val title: String, private val colors: () -> Pair<Color, Color>, val headerIcon: ImageVector) {
    ORGANIZE("Organisieren", { FaColors.Organize to FaColors.OrganizeTile }, Icons.Filled.Description),
    EDIT("Bearbeiten", { FaColors.Edit to FaColors.EditTile }, Icons.Filled.Edit),
    SIGN("Ausfüllen und Signieren", { FaColors.Sign to FaColors.SignTile }, Icons.Outlined.Draw),
    ENCRYPT("Verschlüsseln", { FaColors.Encrypt to FaColors.EncryptTile }, Icons.Filled.Shield),
    AI("KI PDF", { FaColors.Ai to FaColors.AiTile }, Icons.Filled.AutoAwesome),
    SCAN("Scan", { FaColors.Scan to FaColors.ScanTile }, Icons.Outlined.CropFree);

    /** Farben werden bei jedem Zugriff gelesen, damit Hell/Dunkel sofort wirkt. */
    val color: Color get() = colors().first
    val tile: Color get() = colors().second
}

/** Arbeitsbereich-Modi für Werkzeuge, die die Seitenansicht benötigen. */
enum class WorkspaceMode { READ, EDIT, ANNOTATE, FORM, SIGN, REDACT }

/**
 * Alle 25 Werkzeuge der Designvorlage. [workspaceMode] != null → Seitenansicht,
 * sonst eigener Werkzeugbildschirm. [iconText] ersetzt das Symbol (z. B. „123“).
 */
enum class Tool(
    val title: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val workspaceMode: WorkspaceMode? = null,
    val limitedNote: String? = null,
    val needsDocument: Boolean = true,
    val iconText: String? = null,
) {
    MERGE("PDFs zusammenfügen", ToolCategory.ORGANIZE, Icons.Outlined.Link, needsDocument = false),
    SPLIT("PDF teilen", ToolCategory.ORGANIZE, Icons.Outlined.Share),
    ROTATE("PDF drehen", ToolCategory.ORGANIZE, Icons.Outlined.Refresh),
    DELETE_PAGES("PDF-Seiten löschen", ToolCategory.ORGANIZE, Icons.Outlined.DeleteOutline),
    EXTRACT_PAGES("PDF-Seiten extrahieren", ToolCategory.ORGANIZE, Icons.Outlined.Description),
    ORGANIZE("PDF organisieren", ToolCategory.ORGANIZE, Icons.Outlined.Layers),

    EDIT("PDF bearbeiten", ToolCategory.EDIT, Icons.Outlined.Edit, WorkspaceMode.EDIT),
    ANNOTATE("PDF kommentieren", ToolCategory.EDIT, Icons.Outlined.ChatBubbleOutline, WorkspaceMode.ANNOTATE),
    READER("PDF-Reader", ToolCategory.EDIT, Icons.Outlined.MenuBook, WorkspaceMode.READ),
    PAGE_NUMBERS("Seitenzahlen einfügen", ToolCategory.EDIT, Icons.Outlined.Article, iconText = "123"),
    CROP("PDFs zuschneiden", ToolCategory.EDIT, Icons.Outlined.Crop),
    REDACT("PDF schwärzen", ToolCategory.EDIT, Icons.Outlined.VisibilityOff, WorkspaceMode.REDACT),
    WATERMARK("Wasserzeichen-PDF", ToolCategory.EDIT, Icons.Outlined.WaterDrop),
    FORM("PDF-Formularausfüller", ToolCategory.EDIT, Icons.Outlined.Article, WorkspaceMode.FORM),
    SHARE("PDF freigeben", ToolCategory.EDIT, Icons.Outlined.Share),

    SIGN("Unterschreibe PDF", ToolCategory.SIGN, Icons.Outlined.Gesture, WorkspaceMode.SIGN),
    REQUEST_SIGNATURES(
        "Unterschriften anfordern (Sign.com)", ToolCategory.SIGN, Icons.Outlined.ContactPage,
        limitedNote = "Versand per E-Mail – Sign.com nicht angebunden",
    ),

    REMOVE_PASSWORD("PDF Passwort entfernen", ToolCategory.ENCRYPT, Icons.Outlined.LockOpen),
    ENCRYPT("PDF verschlüsseln", ToolCategory.ENCRYPT, Icons.Outlined.Lock),
    FLATTEN("Ebenen der PDF reduzieren", ToolCategory.ENCRYPT, Icons.Outlined.Layers),

    AI_CHAT("Mit PDFs chatten", ToolCategory.AI, Icons.Outlined.ChatBubbleOutline, limitedNote = "Eigener API-Schlüssel nötig"),
    AI_SUMMARY("KI-PDF-Zusammenfassung", ToolCategory.AI, Icons.Outlined.Summarize, limitedNote = "Eigener API-Schlüssel nötig"),
    TRANSLATE("PDF übersetzen", ToolCategory.AI, Icons.Outlined.Translate),
    AI_QUESTIONS("KI-basierter Fragen-Generator", ToolCategory.AI, Icons.Outlined.Psychology, limitedNote = "Eigener API-Schlüssel nötig"),

    SCANNER("PDF-Scanner", ToolCategory.SCAN, Icons.Outlined.Scanner, needsDocument = false);

    val iconColor: Color get() = category.color
    val tileColor: Color get() = category.tile
    val iconTint: Color get() = Color.White

    companion object {
        fun byCategory(c: ToolCategory) = entries.filter { it.category == c }
    }
}
