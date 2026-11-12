package de.fapdf.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import de.fapdf.pdf.CatalogFont

/** Compose-Schriftfamilie einer Bibliotheksschrift – damit die Auswahl jede Schrift in ihrer eigenen Gestalt zeigt. */
@Composable
fun rememberCatalogFontFamily(c: CatalogFont): FontFamily {
    val assets = LocalContext.current.assets
    return remember(c.id) {
        runCatching {
            FontFamily(
                Font("fonts/${c.assetBase}-Regular.ttf", assets, FontWeight.Normal, FontStyle.Normal),
                Font("fonts/${c.assetBase}-Bold.ttf", assets, FontWeight.Bold, FontStyle.Normal),
                Font("fonts/${c.assetBase}-Italic.ttf", assets, FontWeight.Normal, FontStyle.Italic),
                Font("fonts/${c.assetBase}-BoldItalic.ttf", assets, FontWeight.Bold, FontStyle.Italic),
            )
        }.getOrDefault(FontFamily.Default)
    }
}

/** Eigene Schriften des Benutzers: Liste (aktualisiert sich nach dem Hinzufügen) und Funktion „Schrift hinzufügen“. */
class UserFontsState(val fonts: List<de.fapdf.pdf.FontFile>, val pick: () -> Unit)

@Composable
fun rememberUserFonts(): UserFontsState {
    val context = LocalContext.current
    val library = remember { (context.applicationContext as de.fapdf.FaPdfApp).container.fonts }
    var version by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val dir = library.userFontDir ?: return@rememberLauncherForActivityResult
        var ok = 0
        var bad = 0
        for (uri in uris) {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "schrift_${System.currentTimeMillis()}.ttf"
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = java.io.File(dir, safe)
            runCatching { context.contentResolver.openInputStream(uri)?.use { i -> target.outputStream().use { o -> i.copyTo(o) } } }
            if (de.fapdf.pdf.FontIndex.parse(target) != null) ok++ else { target.delete(); bad++ }
        }
        de.fapdf.pdf.FontIndex.invalidate(dir)
        version++
        FileActions.toast(
            context,
            when {
                ok > 0 && bad == 0 -> "$ok Schrift(en) hinzugefügt"
                ok > 0 -> "$ok hinzugefügt, $bad nicht lesbar (nur TrueType .ttf)"
                bad > 0 -> "Schrift nicht lesbar. Bitte eine .ttf-Datei wählen."
                else -> "Keine Schrift gewählt"
            },
        )
    }
    val fonts = remember(version) { library.userFonts() }
    return UserFontsState(fonts) { launcher.launch(arrayOf("*/*")) }
}
