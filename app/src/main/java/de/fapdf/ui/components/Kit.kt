package de.fapdf.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fapdf.data.DocumentEntity
import de.fapdf.pdf.PdfPageSource
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Rotes PDF-Symbol wie in der Vorlage. */
@Composable
fun PdfBadge(size: Dp = 34.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(FaColors.PdfRed), contentAlignment = Alignment.Center) {
        Text("PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.3f).sp)
    }
}

/** Dokumentkarte: Symbol, Dateiname, „12 Seiten · 2,4 MB“ oder eigener Untertitel. */
@Composable
fun DocCard(
    doc: DocumentEntity,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onChange: (() -> Unit)? = null,
) {
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(FaColors.Card).border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp))
            .let { if (onChange != null) it.clickable(onClick = onChange) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfBadge()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle ?: (if (doc.pageCount > 0) "${doc.pageCount} Seiten · ${Format.size(doc.sizeBytes)}" else "Passwortgeschützt · ${Format.size(doc.sizeBytes)}"),
                style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
            )
        }
        if (onChange != null) Text("Ändern", color = FaColors.Accent, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = FaColors.Label, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Hellblaue Info-Karte mit Symbol. */
@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info, color: Color = FaColors.Accent) {
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.09f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = FaColors.Label)
    }
}

/** Blaue Hauptschaltfläche (volle Breite). */
@Composable
fun FaButton(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
    color: Color = FaColors.Accent, onClick: () -> Unit,
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Helle Schaltfläche mit Rahmen (z. B. „+ Dateien hinzufügen“, „Löschen“). */
@Composable
fun FaSoftButton(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = FaColors.Accent,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.06f), contentColor = color),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RadioRow(selected: Boolean, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, icon: ImageVector? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = FaColors.Label); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = FaColors.Accent))
    }
}

/** Auswahlliste im Stil eines Eingabefelds mit Pfeil. */
@Composable
fun <T> FaDropdown(value: T, options: List<T>, label: (T) -> String, modifier: Modifier = Modifier, leading: ImageVector? = null, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, FaColors.CardBorder, RoundedCornerShape(10.dp))
                .clickable { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { Icon(leading, null, tint = FaColors.SecondaryLabel, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(label(value), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Outlined.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(label(o)) }, onClick = { open = false; onSelect(o) }) }
        }
    }
}

/** Reiter mit Unterstrich (z. B. Text | Bild). */
@Composable
fun UnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        tabs.forEachIndexed { i, t ->
            Column(Modifier.weight(1f).clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t, Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelLarge,
                    color = if (i == selected) FaColors.Accent else FaColors.SecondaryLabel,
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (i == selected) FaColors.Accent else FaColors.Separator))
            }
        }
    }
}

/** Auswahlkästen nebeneinander (z. B. Automatisch | Manuell, Kurz | Ausführlich | Stichpunkte). */
@Composable
fun ChoiceBoxes(options: List<String>, selected: Int, modifier: Modifier = Modifier, subtitles: List<String>? = null, onSelect: (Int) -> Unit) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) FaColors.AccentLight else FaColors.Surface)
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) FaColors.Accent else FaColors.CardBorder, RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = if (subtitles == null) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(o, style = MaterialTheme.typography.labelMedium, color = if (sel) FaColors.Accent else FaColors.Label, textAlign = TextAlign.Center)
                subtitles?.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel) }
            }
        }
    }
}

/** Zahl mit − und + (z. B. Anzahl der Fragen). */
@Composable
fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, FaColors.CardBorder, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }) { Icon(Icons.Outlined.Remove, "Weniger", tint = FaColors.Accent) }
        Text("$value", Modifier.width(56.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }) { Icon(Icons.Outlined.Add, "Mehr", tint = FaColors.Accent) }
    }
}

/** Seitenwechsel mit ‹ › und „Seite 1 von 12“. */
@Composable
fun PageNavigator(page: Int, count: Int, label: String = "Seite ${page + 1} von $count", onChange: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((page - 1).coerceAtLeast(0)) }, enabled = page > 0) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Vorherige Seite", tint = FaColors.Accent)
        }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = FaColors.SecondaryLabel)
        IconButton(onClick = { onChange((page + 1).coerceAtMost(count - 1)) }, enabled = page < count - 1) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Nächste Seite", tint = FaColors.Accent)
        }
    }
}

/** Eingabefeld mit Beschriftung darüber, wie in der Vorlage. */
@Composable
fun LabeledField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, minLines: Int = 1, isError: Boolean = false,
    password: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = singleLine, minLines = minLines, isError = isError,
            shape = RoundedCornerShape(10.dp),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            trailingIcon = if (password) {
                { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Anzeigen") } }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FaColors.CardBorder),
        )
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = if (isError) FaColors.Destructive else FaColors.SecondaryLabel, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Große Seitenvorschau mit echter Ausrichtung und optionaler zusätzlicher Drehung.
 * [overlay] zeichnet über die Seite (Koordinaten: Box-Größe der angezeigten Seite).
 */
@Composable
fun LargePagePreview(
    src: PdfPageSource, page: Int, modifier: Modifier = Modifier, extraRotation: Int = 0, maxHeight: Dp = 420.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val img by produceState<ImageBitmap?>(null, src, page) {
        value = withContext(Dispatchers.IO) { runCatching { src.render(page, 1000).asImageBitmap() }.getOrNull() }
    }
    val (w, h) = src.pageSizes.getOrElse(page) { 595f to 842f }
    val odd = ((extraRotation % 180) + 180) % 180 == 90
    val aspect = if (odd) h / w else w / h
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(max = maxHeight).aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f)
                .shadow(3.dp, RoundedCornerShape(4.dp)).background(Color.White)
        ) {
            val i = img
            if (i != null) {
                if (extraRotation % 360 == 0) {
                    Image(i, "Seite ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        val bw = if (odd) i.height.toFloat() else i.width.toFloat()
                        val bh = if (odd) i.width.toFloat() else i.height.toFloat()
                        val s = min(size.width / bw, size.height / bh)
                        val dw = i.width * s
                        val dh = i.height * s
                        rotate(extraRotation.toFloat(), Offset(size.width / 2, size.height / 2)) {
                            drawImage(
                                i, IntOffset.Zero, IntSize(i.width, i.height),
                                IntOffset(((size.width - dw) / 2).roundToInt(), ((size.height - dh) / 2).roundToInt()),
                                IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                            )
                        }
                    }
                }
            } else {
                Text("Seite wird geladen …", Modifier.align(Alignment.Center), color = FaColors.SecondaryLabel)
            }
            overlay()
        }
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier.padding(horizontal = 16.dp), color = FaColors.Separator)

/** Kleine runde Auswahlmarke wie in den Seitenrastern der Vorlage. */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .background(if (checked) FaColors.Accent else FaColors.Surface)
            .border(1.5.dp, if (checked) FaColors.Accent else Color(0xFFB0B5BF), CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (checked) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}
