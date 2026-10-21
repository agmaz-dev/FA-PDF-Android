package de.fapdf.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.fapdf.data.DocumentEntity
import de.fapdf.ui.theme.FaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun Thumbnail(path: String?, width: Dp = 46.dp, height: Dp = 60.dp, key: Any? = null) {
    val img by produceState<ImageBitmap?>(null, path, key) {
        value = path?.let { p -> withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull() } }
    }
    Box(
        Modifier.size(width, height).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, FaColors.Separator, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val i = img
        if (i != null) Image(i, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        else Icon(Icons.Outlined.PictureAsPdf, null, tint = FaColors.Encrypt)
    }
}

/** Auswahl eines Dokuments: aus „Letzte Dokumente“ oder über die Android-Dateiauswahl. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocChooserSheet(
    documents: List<DocumentEntity>,
    title: String = "Dokument auswählen",
    onImport: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit,
    onPick: (DocumentEntity) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onImport(uri) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("PDF aus Dateien öffnen")
            }
            Spacer(Modifier.height(10.dp))
            if (documents.isNotEmpty()) Text("Letzte Dokumente", color = FaColors.SecondaryLabel, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(documents, key = { it.id }) { d ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumbnail(d.thumbnailPath, 34.dp, 44.dp, d.modifiedAt)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${d.pageCount} Seiten · ${Format.size(d.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = FaColors.SecondaryLabel)
                        }
                    }
                    HorizontalDivider(color = FaColors.Separator)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

fun DocumentEntity.file() = File(filePath)
