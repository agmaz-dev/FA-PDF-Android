package de.fapdf.ui.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fapdf.data.DocumentEntity
import de.fapdf.data.DocumentRepository
import de.fapdf.pdf.PasswordRequiredException
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.PdfPageSource
import de.fapdf.pdf.PreviewFactory
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.Tool
import de.fapdf.ui.components.BusyDialog
import de.fapdf.ui.components.FileActions
import de.fapdf.ui.components.GroupCard
import de.fapdf.ui.components.PagePreviewStrip
import de.fapdf.ui.components.PageThumb
import de.fapdf.ui.components.rememberDocSource
import de.fapdf.ui.components.MessageDialog
import de.fapdf.ui.components.PasswordDialog
import de.fapdf.ui.components.ScreenScaffold
import de.fapdf.ui.components.Thumbnail
import de.fapdf.ui.components.ToolIconBox
import de.fapdf.ui.components.file
import de.fapdf.ui.components.rememberExportLauncher
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Gemeinsamer Ablauf aller Werkzeuge: ausführen, Fortschritt, Fehler, Passwort, Ergebnisse. */
class ToolRunner(private val scope: CoroutineScope, private val appVm: AppViewModel, val tmpDir: File) {
    var busy by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<Long>>(emptyList())
    var password by mutableStateOf<String?>(null)
    var askPassword by mutableStateOf<(() -> Unit)?>(null)

    val repo get() = appVm.container.documents
    val fonts get() = appVm.container.fonts

    fun tmp(name: String = "out") = File(tmpDir, "${name}_${System.nanoTime()}.pdf")

    /** [block] erzeugt Dateien + Namen; diese werden als neue Dokumente gespeichert. */
    fun run(label: String, block: suspend () -> List<Pair<File, String>>) {
        scope.launch {
            busy = label
            try {
                val outputs = withContext(PdfIo.pdfDispatcher) { block() }
                val ids = outputs.map { (f, n) -> repo.importFile(f, n).also { f.delete() } }
                results = ids
            } catch (e: PasswordRequiredException) {
                askPassword = { run(label, block) }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = null
            }
        }
    }
}

@Composable
fun rememberToolRunner(appVm: AppViewModel): ToolRunner {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember { ToolRunner(scope, appVm, File(context.cacheDir, "tools").apply { mkdirs() }) }
}

@Composable
fun ToolScaffold(
    tool: Tool,
    runner: ToolRunner,
    onBack: () -> Unit,
    onOpenDoc: (Long) -> Unit,
    replaceTarget: DocumentEntity? = null,
    content: @Composable () -> Unit,
) {
    ScreenScaffold(tool.title, onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolIconBox(tool.icon, tool.iconColor, tool.iconTint, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(tool.title, style = MaterialTheme.typography.titleLarge)
                    tool.limitedNote?.let { Text("Eingeschränkt: $it", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel) }
                }
            }
            if (runner.results.isNotEmpty()) ResultCard(runner, onOpenDoc, replaceTarget)
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
    runner.busy?.let { BusyDialog(it) }
    runner.error?.let { MessageDialog("Nicht möglich", it) { runner.error = null } }
    runner.info?.let { MessageDialog("Hinweis", it) { runner.info = null } }
    runner.askPassword?.let { retry ->
        PasswordDialog("Passwortgeschützte PDF", "Bitte geben Sie das Passwort der PDF ein.", onConfirm = {
            runner.password = it; runner.askPassword = null; retry()
        }, onDismiss = { runner.askPassword = null })
    }
}

@Composable
private fun ResultCard(runner: ToolRunner, onOpenDoc: (Long) -> Unit, replaceTarget: DocumentEntity?) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val exporter = rememberExportLauncher()
    var docs by remember { mutableStateOf<List<DocumentEntity>>(emptyList()) }
    LaunchedEffect(runner.results) { docs = runner.results.mapNotNull { runner.repo.get(it) } }
    GroupCard(Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = FaColors.Success)
            Spacer(Modifier.width(8.dp))
            Text(if (docs.size == 1) "Fertig – neue PDF erstellt" else "Fertig – ${docs.size} PDFs erstellt", fontWeight = FontWeight.SemiBold)
        }
        docs.forEach { d ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(d.thumbnailPath, 34.dp, 44.dp, d.modifiedAt)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text("${d.pageCount} Seiten", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                }
            }
            Row(Modifier.padding(start = 56.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { onOpenDoc(d.id) }) { Text("Öffnen") }
                TextButton(onClick = { FileActions.share(context, d.file(), d.displayName) }) { Text("Teilen") }
                TextButton(onClick = { exporter(d.file(), d.displayName) }) { Text("Exportieren") }
                TextButton(onClick = { FileActions.print(context, d.file(), d.displayName) }) { Text("Drucken") }
            }
        }
        if (replaceTarget != null && docs.size == 1) {
            TextButton(onClick = {
                scope.launch {
                    runner.repo.replaceContent(replaceTarget.id, docs.first().file())
                    runner.repo.removeFromHistory(docs.first().id)
                    runner.results = listOf(replaceTarget.id)
                    FileActions.toast(context, "Originaldokument ersetzt")
                }
            }, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) { Text("Stattdessen das Originaldokument ersetzen") }
        }
    }
}

/**
 * Dokumentkopf mit einheitlicher Seitenvorschau. [showPages] = false, wenn das Werkzeug
 * bereits ein eigenes Seitenraster zeigt.
 */
@Composable
fun DocHeader(
    doc: DocumentEntity?,
    onChange: (() -> Unit)? = null,
    showPages: Boolean = true,
    password: String? = null,
    onNeedPassword: () -> Unit = {},
) {
    if (doc == null) return
    GroupCard(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(doc.thumbnailPath, 40.dp, 52.dp, doc.modifiedAt)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.displayName, style = MaterialTheme.typography.bodyLarge)
                Text("${if (doc.pageCount > 0) "${doc.pageCount} Seiten" else "passwortgeschützt"}", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
            }
            if (onChange != null) TextButton(onClick = onChange) { Text("Ändern") }
        }
        if (showPages) PagePreviewStrip(doc, password, onNeedPassword)
    }
}

fun outName(doc: DocumentEntity, suffix: String) = "${DocumentRepository.baseName(doc.displayName)} ($suffix).pdf"

/**
 * Seitenminiaturen zum Auswählen. [order] = Anzeigereihenfolge (Originalindizes),
 * [rotations] = geplante zusätzliche Drehung pro Originalseite (wird in der Vorschau gezeigt),
 * [accentFor]/[labelFor] = optionale Farbe und Beschriftung (z. B. Teile beim Teilen).
 */
@Composable
fun PageThumbGrid(
    doc: DocumentEntity,
    password: String?,
    order: List<Int>?,
    selected: Set<Int>,
    rotations: Map<Int, Int> = emptyMap(),
    onToggle: (Int) -> Unit,
    onNeedPassword: () -> Unit,
    modifier: Modifier = Modifier,
    accentFor: (Int) -> Color? = { null },
    labelFor: ((pos: Int, page: Int) -> String)? = null,
) {
    val src = rememberDocSource(doc, password, onNeedPassword)
    if (src == null) {
        Text("Seiten werden geladen …", modifier = Modifier.padding(20.dp), color = FaColors.SecondaryLabel)
        return
    }
    val pages = order ?: (0 until src.pageCount).toList()
    // Raster mit fester Höhe, da es in einer scrollbaren Spalte liegt
    val rows = (pages.size + 2) / 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.padding(horizontal = 12.dp).fillMaxWidth().height((rows * 178).coerceAtMost(6000).dp),
        userScrollEnabled = false,
    ) {
        itemsIndexed(pages, key = { _, p -> p }) { pos, p ->
            PageThumb(
                src, p, Modifier.padding(6.dp), extraRotation = rotations[p] ?: 0, selected = p in selected,
                accent = accentFor(p),
                label = labelFor?.invoke(pos, p) ?: (if (order != null) "${pos + 1} (S. ${p + 1})" else "Seite ${p + 1}"),
                onClick = { onToggle(p) },
            )
        }
    }
}
