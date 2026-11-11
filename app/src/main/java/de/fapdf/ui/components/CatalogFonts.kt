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
