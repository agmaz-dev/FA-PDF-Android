package de.fapdf.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Ballot
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.fapdf.ui.theme.FaColors

enum class ToolCategory(val title: String, val color: Color) {
    ORGANIZE("Organisieren", FaColors.Organize),
    EDIT("Bearbeiten", FaColors.Edit),
    SIGN("Ausfüllen und Signieren", FaColors.Sign),
    ENCRYPT("Verschlüsseln", FaColors.Encrypt),
    AI("KI PDF", FaColors.Ai),
    SCAN("Scan", FaColors.Scan),
}

/** Arbeitsbereich-Modi für Werkzeuge, die die Seitenansicht benötigen. */
enum class WorkspaceMode { READ, EDIT, ANNOTATE, FORM, SIGN, REDACT }

/**
 * Alle 25 Werkzeuge der Referenzgrafik. [workspaceMode] != null → Öffnet die Seitenansicht,
 * sonst einen eigenen Werkzeugbildschirm. [limitedNote] kennzeichnet ehrlich Einschränkungen.
 */
enum class Tool(
    val title: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val workspaceMode: WorkspaceMode? = null,
    val iconColor: Color = category.color,
    val iconTint: Color = Color.White,
    val limitedNote: String? = null,
    val needsDocument: Boolean = true,
) {
    MERGE("PDFs zusammenfügen", ToolCategory.ORGANIZE, Icons.Outlined.LibraryAdd, needsDocument = false),
    SPLIT("PDF teilen", ToolCategory.ORGANIZE, Icons.Outlined.ContentCut),
    ROTATE("PDF drehen", ToolCategory.ORGANIZE, Icons.Outlined.RotateRight),
    DELETE_PAGES("PDF-Seiten löschen", ToolCategory.ORGANIZE, Icons.Outlined.DeleteOutline),
    EXTRACT_PAGES("PDF-Seiten extrahieren", ToolCategory.ORGANIZE, Icons.Outlined.Dashboard),
    ORGANIZE("PDF organisieren", ToolCategory.ORGANIZE, Icons.Outlined.GridView),

    EDIT("PDF bearbeiten", ToolCategory.EDIT, Icons.Outlined.Title, WorkspaceMode.EDIT),
    ANNOTATE("PDF kommentieren", ToolCategory.EDIT, Icons.Outlined.Edit, WorkspaceMode.ANNOTATE),
    READER("PDF-Reader", ToolCategory.EDIT, Icons.Outlined.Article, WorkspaceMode.READ),
    PAGE_NUMBERS("Seitenzahlen einfügen", ToolCategory.EDIT, Icons.Outlined.FormatListNumbered),
    CROP("PDFs zuschneiden", ToolCategory.EDIT, Icons.Outlined.Crop),
    REDACT("PDF schwärzen", ToolCategory.EDIT, Icons.Outlined.VisibilityOff, WorkspaceMode.REDACT),
    WATERMARK("Wasserzeichen-PDF", ToolCategory.EDIT, Icons.Outlined.Approval),
    FORM("PDF-Formularausfüller", ToolCategory.EDIT, Icons.Outlined.Ballot, WorkspaceMode.FORM),
    SHARE("PDF freigeben", ToolCategory.EDIT, Icons.Outlined.Share),

    SIGN("Unterschreibe PDF", ToolCategory.SIGN, Icons.Outlined.Draw, WorkspaceMode.SIGN),
    REQUEST_SIGNATURES(
        "Unterschriften anfordern", ToolCategory.SIGN, Icons.Outlined.HistoryEdu,
        iconColor = FaColors.SignRequest, iconTint = Color(0xFF1C1C1E),
        limitedNote = "Versand per E-Mail – kein Signaturdienst angebunden",
    ),

    REMOVE_PASSWORD("PDF-Passwort entfernen", ToolCategory.ENCRYPT, Icons.Outlined.LockOpen),
    ENCRYPT("PDF verschlüsseln", ToolCategory.ENCRYPT, Icons.Outlined.Lock),
    FLATTEN("Ebenen der PDF reduzieren", ToolCategory.ENCRYPT, Icons.Outlined.Layers),

    AI_CHAT("Mit PDFs chatten", ToolCategory.AI, Icons.Outlined.Chat, limitedNote = "Eigener API-Schlüssel nötig"),
    AI_SUMMARY("KI-PDF-Zusammenfassung", ToolCategory.AI, Icons.Outlined.FormatListBulleted, limitedNote = "Eigener API-Schlüssel nötig"),
    TRANSLATE("PDF übersetzen", ToolCategory.AI, Icons.Outlined.Translate),
    AI_QUESTIONS("KI-basierter Fragen-Generator", ToolCategory.AI, Icons.Outlined.Quiz, limitedNote = "Eigener API-Schlüssel nötig"),

    SCANNER("PDF-Scanner", ToolCategory.SCAN, Icons.Outlined.PhotoCamera, needsDocument = false);

    companion object {
        fun byCategory(c: ToolCategory) = entries.filter { it.category == c }
    }
}
