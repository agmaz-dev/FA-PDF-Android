package de.fapdf.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import de.fapdf.ui.theme.FaColors
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolIconBox(icon: ImageVector, background: Color, tint: Color = Color.White, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    actionText: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = FaColors.Background,
        // Tastatur: Inhalt schrumpft, damit das aktive Eingabefeld sichtbar bleibt
        contentWindowInsets = androidx.compose.material3.ScaffoldDefaults.contentWindowInsets
            .union(androidx.compose.foundation.layout.WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = FaColors.Label)
                    }
                },
                actions = {
                    actions()
                    if (actionText != null) {
                        Button(
                            onClick = onAction, enabled = actionEnabled, shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FaColors.Accent),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(actionText, style = MaterialTheme.typography.labelMedium) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FaColors.Background),
            )
        },
        bottomBar = bottomBar,
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = FaColors.SecondaryLabel, style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** Weiße, abgerundete Gruppe wie in iOS-Einstellungen. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FaColors.Card)
            .border(1.dp, FaColors.CardBorder, RoundedCornerShape(12.dp)),
        content = content,
    )
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = FaColors.Accent,
    destructive: Boolean = false,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { ToolIconBox(icon, iconColor, size = 30.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = if (destructive) FaColors.Destructive else FaColors.Label, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) Text(subtitle, color = FaColors.SecondaryLabel, style = MaterialTheme.typography.bodySmall)
            }
            trailing()
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = if (icon != null) 58.dp else 16.dp), color = FaColors.Separator)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = FaColors.Accent, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun BusyDialog(text: String, progress: Float? = null) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (progress == null) CircularProgressIndicator() else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String, message: String, confirmText: String, destructive: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = if (destructive) FaColors.Destructive else FaColors.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun PasswordDialog(title: String, message: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true, label = { Text("Passwort") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = pw.isNotEmpty()) { Text("Öffnen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun InfoBanner(text: String, color: Color = FaColors.Accent, action: Pair<String, () -> Unit>? = null) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.09f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = FaColors.Label)
        if (action != null) TextButton(onClick = action.second) { Text(action.first, color = color, fontWeight = FontWeight.SemiBold) }
    }
}

object Format {
    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.GERMANY, "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(ms))
    fun dateShort(ms: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(ms))
}

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Teilen, Drucken, E-Mail – immer über eine Kopie mit lesbarem Dateinamen und sicherer content://-URI. */
object FileActions {
    private fun shareCopy(context: Context, file: File, displayName: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000) it.delete() }
        val target = File(dir, displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        file.copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
    }

    fun share(context: Context, file: File, displayName: String) {
        val uri = shareCopy(context, file, displayName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "PDF weitergeben"))
    }

    /** Direkt an eine bestimmte App teilen; falls nicht vorhanden, normale Auswahl öffnen. */
    fun shareTo(context: Context, file: File, displayName: String, pkg: String, cls: String?) {
        val uri = shareCopy(context, file, displayName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (cls != null) setClassName(pkg, cls) else setPackage(pkg)
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            toast(context, "App nicht installiert – bitte eine andere App wählen.")
            share(context, file, displayName)
        }
    }

    fun shareMany(context: Context, files: List<Pair<File, String>>) {
        val uris = ArrayList(files.map { (f, n) -> shareCopy(context, f, n) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "PDFs weitergeben"))
    }

    fun email(context: Context, file: File, displayName: String, to: List<String>, subject: String, body: String) {
        val uri = shareCopy(context, file, displayName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "E-Mail-App wählen"))
    }

    fun shareText(context: Context, text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(Intent.createChooser(intent, "Text teilen"))
    }

    fun print(context: Context, file: File, displayName: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback, extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                val info = PrintDocumentInfo.Builder(displayName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                try {
                    file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        val activity = context.findActivity() ?: context
        (activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: pm).print(displayName, adapter, null)
    }

    fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/** Startet „Speichern unter“ (Android-Dateiauswahl) für eine Datei. */
@Composable
fun rememberExportLauncher(onDone: (Boolean) -> Unit = {}): (File, String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pending
        if (uri != null && f != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out -> f.inputStream().use { it.copyTo(out) } } != null
            }.getOrDefault(false)
            FileActions.toast(context, if (ok) "Gespeichert" else "Speichern fehlgeschlagen")
            onDone(ok)
        }
        pending = null
    }
    return { file, name -> pending = file; launcher.launch(name) }
}

object Biometric {
    /** Fragt Fingerabdruck/Gesicht oder die Geräte-PIN ab. Ohne eingerichtete Sperre: direkt erfolgreich. */
    fun authenticate(context: Context, title: String, onResult: (Boolean) -> Unit) {
        val activity = context.findActivity() ?: return onResult(false)
        val authenticators = if (Build.VERSION.SDK_INT >= 30)
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK
        val can = BiometricManager.from(activity).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // Keine Biometrie eingerichtet: Zugriff erlauben, aber Hinweis geben
            FileActions.toast(activity, "Keine Displaysperre/Biometrie eingerichtet – Schutz nicht verfügbar.")
            return onResult(true)
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(authenticators).apply {
            if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Abbrechen")
        }.build()
        prompt.authenticate(info)
    }
}
