package de.fapdf.ui.tools

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.Tool
import de.fapdf.ui.components.DocChooserSheet
import de.fapdf.ui.components.InfoBanner
import de.fapdf.ui.components.PrimaryButton

/** Einstieg für alle Werkzeuge ohne Seitenansicht. */
@Composable
fun ToolScreen(tool: Tool, docId: Long?, appVm: AppViewModel, onBack: () -> Unit, onOpenDoc: (Long) -> Unit) {
    val docs by appVm.documents.collectAsState()
    var currentId by remember { mutableStateOf(docId) }
    var choose by remember { mutableStateOf(false) }
    val doc = docs.firstOrNull { it.id == currentId }
    val runner = rememberToolRunner(appVm)

    if (tool.needsDocument && doc == null) {
        ToolScaffold(tool, runner, onBack, onOpenDoc) {
            InfoBanner("Bitte wählen Sie zuerst ein Dokument.")
            PrimaryButton("Dokument auswählen", Modifier.padding(16.dp)) { choose = true }
        }
    } else {
        val replaceTarget = if (tool in setOf(Tool.MERGE, Tool.SPLIT, Tool.EXTRACT_PAGES, Tool.SCANNER, Tool.SHARE, Tool.REQUEST_SIGNATURES)) null else doc
        val actionHolder = remember { arrayOf<(() -> Unit)?>(null) }
        val register: (() -> Unit) -> Unit = { actionHolder[0] = it }
        val actionText = when (tool) {
            Tool.PAGE_NUMBERS -> "Anwenden"
            Tool.CROP -> "Zuschneiden"
            else -> null
        }
        ToolScaffold(tool, runner, onBack, onOpenDoc, replaceTarget, actionText = actionText, onAction = { actionHolder[0]?.invoke() }) {
            val change = { choose = true }
            when (tool) {
                Tool.MERGE -> MergeToolV2(appVm, runner, docs, docId)
                Tool.SPLIT -> SplitToolV2(doc!!, runner, change)
                Tool.ROTATE -> RotateToolV2(doc!!, runner, change)
                Tool.DELETE_PAGES -> DeletePagesTool(doc!!, runner, change)
                Tool.EXTRACT_PAGES -> ExtractPagesTool(doc!!, runner, change)
                Tool.ORGANIZE -> OrganizeToolV2(appVm, doc!!, runner, docs, change)
                Tool.PAGE_NUMBERS -> PageNumberToolV2(doc!!, runner, change, register)
                Tool.CROP -> CropToolV2(doc!!, runner, change, register)
                Tool.WATERMARK -> WatermarkToolV2(doc!!, runner, change, register)
                Tool.SHARE -> ShareToolV2(doc!!, change)
                Tool.REQUEST_SIGNATURES -> RequestSignaturesToolV2(doc!!, runner, change)
                Tool.REMOVE_PASSWORD -> RemovePasswordToolV2(doc!!, runner, change)
                Tool.ENCRYPT -> EncryptToolV2(doc!!, runner, change)
                Tool.FLATTEN -> FlattenToolV2(doc!!, runner, change)
                Tool.SCANNER -> ScannerToolV2(appVm, runner)
                else -> Text("Dieses Werkzeug wird in der Seitenansicht geöffnet.", Modifier.padding(16.dp))
            }
        }
    }
    if (choose) DocChooserSheet(
        documents = docs, onImport = { uri -> choose = false; appVm.import(uri, persist = true) { currentId = it; runner.results = emptyList() } },
        onDismiss = { choose = false },
    ) { d -> choose = false; currentId = d.id; runner.results = emptyList() }
}

