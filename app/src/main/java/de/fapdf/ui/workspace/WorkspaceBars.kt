package de.fapdf.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import de.fapdf.pdf.Alignment as TextAlignment
import de.fapdf.pdf.FontFamily
import de.fapdf.pdf.PdfPageSource
import de.fapdf.pdf.TextStyle
import de.fapdf.ui.components.PageThumb
import de.fapdf.ui.theme.FaColors

private val sizes = listOf(6f, 7f, 8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 36f, 48f, 60f, 72f)
private val inkColors = listOf(
    0xFF000000.toInt(), 0xFF3A3A3C.toInt(), 0xFF1F5BE0.toInt(), 0xFF0A3D91.toInt(),
    0xFFC62828.toInt(), 0xFFE8731F.toInt(), 0xFF1B5E20.toInt(), 0xFFFFFFFF.toInt(),
)

private fun fmtSize(s: Float) = if (s % 1f == 0f) s.toInt().toString() else ((s * 10).toInt() / 10f).toString()

/**
 * Formatleiste wie in der Vorlage: Schrift ▾ · Größe ▾ · Farbe · B · I · Ausrichtung.
 * [originalName] = erkannte Originalschrift (null, wenn nicht verfügbar).
 */
@Composable
fun FormatBar(
    style: TextStyle, align: TextAlignment, originalName: String?, extraColor: Int?,
    onStyle: (TextStyle) -> Unit, onAlign: (TextAlignment) -> Unit,
) {
    var fontMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box {
            val fontLabel = if (style.useOriginalFont && originalName != null) originalName else style.family.title
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { fontMenu = true }.padding(horizontal = 8.dp, vertical = 8.dp).widthIn(max = 120.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fontLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
            }
            DropdownMenu(fontMenu, { fontMenu = false }) {
                if (originalName != null) DropdownMenuItem(
                    text = { Text("$originalName (Original)") },
                    onClick = { fontMenu = false; onStyle(style.copy(useOriginalFont = true)) },
                )
                FontFamily.entries.forEach { f ->
                    DropdownMenuItem(text = { Text(f.title) }, onClick = { fontMenu = false; onStyle(style.copy(family = f, useOriginalFont = false)) })
                }
            }
        }
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { sizeMenu = true }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fmtSize(style.size), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
            }
            DropdownMenu(sizeMenu, { sizeMenu = false }, Modifier.heightIn(max = 320.dp)) {
                (sizes + style.size).distinct().sorted().forEach { s ->
                    DropdownMenuItem(text = { Text(fmtSize(s) + " pt", fontWeight = if (s == style.size) FontWeight.Bold else null) }, onClick = { sizeMenu = false; onStyle(style.copy(size = s)) })
                }
            }
        }
        Box {
            Box(
                Modifier.padding(6.dp).size(26.dp).clip(CircleShape).background(Color(style.colorArgb))
                    .border(1.dp, FaColors.CardBorder, CircleShape).clickable { colorMenu = true }
            )
            DropdownMenu(colorMenu, { colorMenu = false }) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    (listOfNotNull(extraColor) + inkColors).distinct().forEach { c ->
                        Box(
                            Modifier.padding(4.dp).size(28.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == style.colorArgb) 3.dp else 1.dp, if (c == style.colorArgb) FaColors.Accent else FaColors.CardBorder, CircleShape)
                                .clickable { colorMenu = false; onStyle(style.copy(colorArgb = c)) }
                        )
                    }
                }
            }
        }
        FormatToggle(Icons.Outlined.FormatBold, "Fett", style.bold) { onStyle(style.copy(bold = !style.bold)) }
        FormatToggle(Icons.Outlined.FormatItalic, "Kursiv", style.italic) { onStyle(style.copy(italic = !style.italic)) }
        val alignIcon = when (align) {
            TextAlignment.LEFT -> Icons.AutoMirrored.Outlined.FormatAlignLeft
            TextAlignment.CENTER -> Icons.Outlined.FormatAlignCenter
            TextAlignment.RIGHT -> Icons.AutoMirrored.Outlined.FormatAlignRight
        }
        IconButton(onClick = {
            onAlign(when (align) { TextAlignment.LEFT -> TextAlignment.CENTER; TextAlignment.CENTER -> TextAlignment.RIGHT; TextAlignment.RIGHT -> TextAlignment.LEFT })
        }) { Icon(alignIcon, "Ausrichtung") }
    }
}

@Composable
private fun FormatToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(if (on) FaColors.AccentLight else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) FaColors.Accent else FaColors.Label) }
}

/**
 * Bearbeitung der markierten Textzeile direkt über der Werkzeugleiste: Textfeld + Formatleiste.
 * Bold/Italic wechseln auf eine passende Schrift, weil eine eingebettete Schrift nicht fett werden kann.
 */
@Composable
fun InlineTextEditor(vm: WorkspaceViewModel, sel: TextSelection, onMore: () -> Unit) {
    var text by remember(sel) { mutableStateOf(sel.text) }
    var style by remember(sel) { mutableStateOf(sel.detected.toTextStyle().copy(useOriginalFont = sel.canUseOriginalFont)) }
    var align by remember(sel) { mutableStateOf(sel.detected.alignment) }
    val original = if (sel.canUseOriginalFont) sel.detected.fontName else null
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), maxLines = 3, shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { vm.applyTextEdit(sel, text, style, align, false) }, enabled = text.isNotBlank()) {
                Icon(Icons.Outlined.Check, "Übernehmen", tint = FaColors.Accent)
            }
            IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, "Weitere Optionen") }
        }
        // Bei Fett/Kursiv abweichend vom Original setzt die Engine automatisch die passende Ersatzschrift
        FormatBar(style, align, original, sel.detected.colorArgb, onStyle = { style = it }, onAlign = { align = it })
        HorizontalDivider(color = FaColors.Separator)
    }
}

/** Seitenübersicht (Raster-Symbol im Reader). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageGridSheet(source: PdfPageSource, current: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FaColors.Surface) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Seitenübersicht", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                GridCells.Adaptive(96.dp), Modifier.heightIn(max = 520.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(source.pageCount) { p ->
                    PageThumb(source, p, Modifier.width(96.dp), selected = p == current, label = "${p + 1}", renderWidth = 220, onClick = { onGo(p) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


/**
 * Direkte Eingabe in ein Formularfeld auf der Seite: Das Textfeld liegt exakt über dem PDF-Feld,
 * die Tastatur öffnet sich sofort, „Weiter“ springt ins nächste Feld.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineFieldEditor(vm: WorkspaceViewModel, f: de.fapdf.pdf.FormFieldInfo, w: de.fapdf.pdf.WidgetInfo, s: Float) {
    val density = LocalDensity.current
    val focus = remember(f.id) { FocusRequester() }
    val bring = remember(f.id) { BringIntoViewRequester() }
    var value by remember(f.id) {
        val start = vm.displayValue(f).ifEmpty { f.value }
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    val multi = f.kind == de.fapdf.pdf.FieldKind.MULTILINE
    val b = w.viewBox
    val boxH = b.height * s
    val fontPx = if (multi) (11f * s).coerceAtMost(boxH * 0.8f) else (boxH * 0.62f).coerceIn(10f, 40f * s)
    LaunchedEffect(f.id) {
        focus.requestFocus()
        kotlinx.coroutines.delay(350) // Tastatur einblenden lassen, dann Feld sichtbar scrollen
        bring.bringIntoView()
    }
    val next = vm.nextTextField(f.id)
    BasicTextField(
        value = value,
        onValueChange = { nv ->
            if (f.maxLength > 0 && nv.text.length > f.maxLength) return@BasicTextField
            value = nv
            vm.setPending(f.id, de.fapdf.pdf.FieldValue.Text(nv.text))
        },
        singleLine = !multi,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color(0xFF0A3CA0), fontSize = with(density) { fontPx.toSp() },
        ),
        cursorBrush = SolidColor(FaColors.Accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (f.kind == de.fapdf.pdf.FieldKind.DATE) KeyboardType.Number else KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (multi) ImeAction.Default else if (next != null) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onNext = { next?.let { vm.activateField(it) } },
            onDone = { vm.inlineFieldId = null },
        ),
        modifier = Modifier
            .offset { IntOffset((b.left * s).roundToInt(), (b.top * s).roundToInt()) }
            .size(with(density) { (b.width * s).toDp() }, with(density) { boxH.toDp() })
            .bringIntoViewRequester(bring)
            .background(Color.White.copy(alpha = 0.92f))
            .border(2.dp, FaColors.Accent)
            .padding(horizontal = 3.dp)
            .focusRequester(focus),
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = if (multi) Alignment.TopStart else Alignment.CenterStart) { inner() } },
    )
}

/** Freier Text direkt auf der Seite: tippen, mit ✓ oder „Fertig“ übernehmen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineNewTextEditor(vm: WorkspaceViewModel, page: Int, u: Float, v: Float, s: Float) {
    val density = LocalDensity.current
    val focus = remember(page, u, v) { FocusRequester() }
    val bring = remember(page, u, v) { BringIntoViewRequester() }
    var text by remember(page, u, v) { mutableStateOf("") }
    val style = vm.newTextStyle
    val fontPx = style.size * s
    LaunchedEffect(page, u, v) {
        focus.requestFocus()
        kotlinx.coroutines.delay(350)
        bring.bringIntoView()
    }
    val commit = {
        if (text.isNotBlank()) vm.addText(page, u, v, text, style)
        vm.inlineNewText = null
    }
    Row(
        Modifier.offset { IntOffset((u * s).roundToInt(), (v * s - fontPx * 0.2f).roundToInt()) }
            .bringIntoViewRequester(bring)
            .background(Color.White.copy(alpha = 0.92f)).border(2.dp, FaColors.Accent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            text, { text = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color(style.colorArgb), fontSize = with(density) { fontPx.toSp() }),
            cursorBrush = SolidColor(FaColors.Accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.widthIn(min = 80.dp, max = 320.dp).padding(horizontal = 4.dp, vertical = 2.dp).focusRequester(focus),
        )
        Box(Modifier.size(32.dp).background(FaColors.Accent).clickable { commit() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, "Übernehmen", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
