package de.fapdf.ui.tools

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.fapdf.data.DocumentEntity
import de.fapdf.pdf.DocFontCache
import de.fapdf.pdf.OcrEngine
import de.fapdf.pdf.OcrLayer
import de.fapdf.pdf.PageAnalyzer
import de.fapdf.pdf.PageRaster
import de.fapdf.pdf.PasswordRequiredException
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.PdfOps
import de.fapdf.pdf.PdfUserException
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.components.ChoiceBoxes
import de.fapdf.ui.components.DocCard
import de.fapdf.ui.components.FaButton
import de.fapdf.ui.components.FaSoftButton
import de.fapdf.ui.components.Format
import de.fapdf.ui.components.InfoCard
import de.fapdf.ui.components.LabeledField
import de.fapdf.ui.components.PageThumb
import de.fapdf.ui.components.SectionLabel
import de.fapdf.ui.components.ToggleRow
import de.fapdf.ui.components.file
import de.fapdf.ui.components.findActivity
import de.fapdf.ui.components.rememberDocSource
import de.fapdf.ui.theme.FaColors

private fun sizeAndPages(doc: DocumentEntity) =
    Format.size(doc.sizeBytes) + if (doc.pageCount > 0) " · ${doc.pageCount} Seiten" else ""

@Composable
fun RemovePasswordToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    DocCard(doc, subtitle = sizeAndPages(doc), onChange = onChange)
    InfoCard(
        if (doc.encrypted) "Dieses PDF ist durch ein Passwort geschützt. Geben Sie das aktuelle Passwort ein, um es zu entfernen."
        else "Dieses PDF scheint nicht verschlüsselt zu sein.",
        icon = Icons.Outlined.Lock,
    )
    Spacer(Modifier.height(8.dp))
    LabeledField("Aktuelles Passwort", pw, { pw = it }, password = true, hint = "Passwort des Dokuments eingeben. Bei PDFs mit reinen Bearbeitungssperren genügt ein leeres Feld.")
    Spacer(Modifier.height(16.dp))
    FaButton("Entsperren") {
        runner.run("Passwort wird entfernt …") {
            val out = runner.tmp("decrypt")
            val wasEncrypted = try {
                PdfIo.load(doc.file(), pw.ifEmpty { null }).use { d ->
                    val enc = d.isEncrypted
                    d.isAllSecurityToBeRemoved = true
                    PdfIo.save(d, out)
                    enc
                }
            } catch (e: PasswordRequiredException) {
                throw PdfUserException("Das Passwort ist falsch.")
            }
            if (!wasEncrypted) { out.delete(); throw PdfUserException("Diese PDF ist nicht verschlüsselt.") }
            listOf(out to outName(doc, "ohne Passwort"))
        }
    }
}

@Composable
fun EncryptToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(true) }
    var modify by remember { mutableStateOf(false) }
    DocCard(doc, subtitle = sizeAndPages(doc), onChange = onChange)
    LabeledField("Neues Passwort", pw, { pw = it }, password = true, hint = "Mindestens 6 Zeichen.", isError = pw.isNotEmpty() && pw.length < 6)
    LabeledField("Passwort wiederholen", pw2, { pw2 = it }, password = true, hint = if (pw2.isNotEmpty() && pw != pw2) "Die Passwörter stimmen nicht überein." else "Passwort zur Bestätigung erneut eingeben.", isError = pw2.isNotEmpty() && pw != pw2)
    Spacer(Modifier.height(8.dp))
    ToggleRow("Drucken erlauben", icon = Icons.Outlined.Print, checked = print) { print = it }
    ToggleRow("Bearbeiten erlauben", icon = Icons.Outlined.Edit, checked = modify) { modify = it }
    Text(
        "Verschlüsselung mit AES-256. Bewahren Sie das Passwort sicher auf – ohne Passwort lässt sich die Datei nicht mehr öffnen.",
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
    )
    FaButton("PDF verschlüsseln", enabled = pw.length >= 6 && pw == pw2) {
        val p = print; val m = modify
        runner.run("PDF wird verschlüsselt …") {
            val out = runner.tmp("encrypt")
            PdfIo.load(doc.file(), runner.password).use { d ->
                if (d.isEncrypted) d.isAllSecurityToBeRemoved = true
                PdfOps.encrypt(d, pw, null, allowPrint = p, allowCopy = m, allowModify = m)
                PdfIo.save(d, out)
            }
            listOf(out to outName(doc, "geschützt"))
        }
    }
}

@Composable
fun FlattenToolV2(doc: DocumentEntity, runner: ToolRunner, onChange: () -> Unit) {
    var comments by remember { mutableStateOf(true) }
    var forms by remember { mutableStateOf(true) }
    var drawings by remember { mutableStateOf(true) }
    val src = rememberDocSource(doc, runner.password) { runner.askPassword = {} }
    DocCard(doc, subtitle = sizeAndPages(doc), onChange = onChange)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp)).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (src != null) PageThumb(src, 0, Modifier.fillMaxWidth(), renderWidth = 320)
            Text("Seite 1 von ${maxOf(1, doc.pageCount)}", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(FaColors.AccentLight).padding(10.dp)) {
            Text("Ebenen auswählen", style = MaterialTheme.typography.titleSmall)
            CheckLine("Kommentare", comments) { comments = it }
            CheckLine("Formularfelder", forms) { forms = it }
            CheckLine("Zeichnungen", drawings) { drawings = it }
        }
    }
    InfoCard("Ausgewählte Ebenen werden zusammengeführt und aus der PDF entfernt. Der sichtbare Inhalt bleibt erhalten.")
    FaButton("Ebenen reduzieren", enabled = comments || forms || drawings) {
        val c = comments; val f = forms; val dr = drawings
        runner.run("Ebenen werden reduziert …") {
            val out = runner.tmp("flatten")
            PdfIo.transform(doc.file(), out, runner.password) { d -> PdfOps.flattenSelective(d, c, f, dr) }
            listOf(out to outName(doc, "reduziert"))
        }
    }
}

@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------- PDF-Scanner ----------------

@Composable
fun ScannerToolV2(appVm: AppViewModel, runner: ToolRunner) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 Dokument, 1 Ausweis
    var gallery by remember { mutableStateOf(true) }
    var lastId by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uri = result?.pdf?.uri
            if (uri == null) { runner.error = "Der Scan hat keine PDF geliefert."; return@rememberLauncherForActivityResult }
            if (mode == 1) {
                // Ausweis: Vorder- und Rückseite auf eine A4-Seite setzen
                runner.run("Ausweis wird auf A4 gesetzt …") {
                    val tmpIn = runner.tmp("id_in")
                    context.contentResolver.openInputStream(uri)?.use { i -> tmpIn.outputStream().use { i.copyTo(it) } }
                    val out = runner.tmp("id")
                    PdfOps.idCardLayout(tmpIn, out)
                    tmpIn.delete()
                    listOf(out to "Ausweis-Scan.pdf")
                }
            } else {
                appVm.import(uri, persist = false) { id -> lastId = id; runner.results = listOf(id) }
            }
        }
    }
    Box(
        Modifier.padding(16.dp).fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(FaColors.Card),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(Icons.Outlined.CameraAlt, null, tint = FaColors.Accent, modifier = Modifier.padding(8.dp))
            Text(
                "Die Kamera erkennt die Ränder, schneidet automatisch zu und korrigiert die Perspektive.",
                Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
            )
        }
    }
    SectionLabel("Modus")
    ChoiceBoxes(listOf("Dokument", "Ausweis"), mode, subtitles = listOf("Mehrseitig, A4", "Vorder- und Rückseite auf einer A4-Seite")) { mode = it }
    ToggleRow("Import aus Galerie erlauben", "Fotos vorhandener Dokumente verwenden", checked = gallery) { gallery = it }
    FaButton("Scan starten", icon = Icons.Outlined.CameraAlt) {
        val activity = context.findActivity()
        if (activity == null) { runner.error = "Scanner kann nicht gestartet werden."; return@FaButton }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(gallery)
            .setPageLimit(if (mode == 1) 2 else 60)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> runner.error = "Scanner nicht verfügbar (Google Play-Dienste nötig): ${e.message}" }
    }
    Text(
        "Die Aufnahme übernimmt der Dokumentscanner von Google (auf dem Gerät, kostenlos) mit automatischer Auslösung, Zuschnitt und Filtern.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel,
    )
    val id = lastId
    if (id != null) {
        SectionLabel("Texterkennung")
        FaSoftButton("Durchsuchbar machen (OCR)") {
            runner.run("Texterkennung läuft …") {
                val d = runner.repo.get(id) ?: throw PdfUserException("Scan nicht gefunden")
                val ocr = OcrEngine()
                try {
                    val count = PdfIo.load(d.file()).use { it.numberOfPages }
                    val pages = (0 until count).map { p ->
                        val bmp = PageRaster.render(d.file(), p, OcrEngine.OCR_SCALE, runner.tmpDir)
                        ocr.recognize(p, bmp, OcrEngine.OCR_SCALE).also { bmp.recycle() }
                    }
                    val out = runner.tmp("ocr")
                    PdfIo.transform(d.file(), out) { doc ->
                        val cache = DocFontCache(doc, runner.fonts)
                        pages.forEach { OcrLayer.addInvisibleText(doc, it.pageIndex, it.lines, cache) }
                    }
                    val found = PdfIo.load(out).use { doc -> (0 until doc.numberOfPages).sumOf { PageAnalyzer.analyze(doc, it).hiddenLines.size } }
                    runner.info = "Texterkennung abgeschlossen: $found Zeilen erkannt."
                    listOf(out to d.displayName.replace(".pdf", " (durchsuchbar).pdf"))
                } finally {
                    ocr.close()
                }
            }
        }
    }
}

